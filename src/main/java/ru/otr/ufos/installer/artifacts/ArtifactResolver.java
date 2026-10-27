package ru.otr.ufos.installer.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.otr.ufos.installer.filesystem.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Поиск и проверка артефактов в каталоге artifacts
 */
public class ArtifactResolver {
	public static final String STAND_EXISTS = "Каталог стенда уже существует и не пуст: [%s]. Используйте --force для перезаписи.";
	public static final String WAR_NOT_FOUND = "Не найден WAR-файл ядра в каталоге артефактов: [%s]. Ожидался файл вида sufd-server-<version>.war";
	public static final String LIB_NOT_FOUND = "Не найден архив библиотек в каталоге артефактов: [%s]. Ожидался файл вида sufd-server-libs-<version>.zip";
	public static final String JETTY_TEMPLATE_NOT_FOUND = "Не найден шаблон Jetty в каталоге артефактов: [%s]. Ожидался архив вида jetty-template-<version>.zip или каталог template/";

	private static final Pattern CORE_WAR_VERSION_PATTERN = Pattern.compile("^sufd-server-(.+)\\.war$", Pattern.CASE_INSENSITIVE);

	private final Path artifactsDir;

	public ArtifactResolver(Path artifactsDir) {
		this.artifactsDir = artifactsDir;
	}

	public Path resolveCoreWar(Path explicitPath) throws IOException {
		if (explicitPath != null) {
			requireFile(explicitPath);
			LOGGER.info("Ядро указано явно: " + explicitPath);
			return explicitPath;
		}

		List<Path> candidates = findFiles(name -> name.toLowerCase().startsWith("sufd-server") && name.toLowerCase().endsWith(".war"));
		if (candidates.isEmpty()) {
			throw new IllegalStateException(WAR_NOT_FOUND.formatted(artifactsDir));
		}

		if (candidates.size() > 1) {
			throw new IllegalStateException("Найдено несколько WAR-файлов ядра. Укажите явно через --core-war: " + candidates.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", ")));
		}

		Path result = candidates.get(0);
		LOGGER.info("Найден файл ядра: " + result);
		return result;
	}

	public Path resolveLibsZip(Path explicitPath) throws IOException {
		if (explicitPath != null) {
			requireFile(explicitPath);
			LOGGER.info("Архив библиотек указан явно: " + explicitPath);
			return explicitPath;
		}

		List<Path> candidates = findFiles(name -> name.toLowerCase().startsWith("sufd-server-libs") && name.toLowerCase().endsWith(".zip"));
		if (candidates.isEmpty()) {
			throw new IllegalStateException(LIB_NOT_FOUND.formatted(artifactsDir));
		}

		if (candidates.size() > 1) {
			throw new IllegalStateException("Найдено несколько архивов библиотек. Укажите явно через --libs-zip: " + candidates.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", ")));
		}

		Path result = candidates.get(0);
		LOGGER.info("Найден архив библиотек: " + result);
		return result;
	}

	public @Nullable Path resolveAppZip(Path explicitPath) throws IOException {
		if (explicitPath != null) {
			requireFile(explicitPath);
			LOGGER.info("Архив приклада указан явно: " + explicitPath);
			return explicitPath;
		}

		List<Path> candidates = findFiles(name -> name.toLowerCase().endsWith(".zip") && name.toLowerCase().contains("func"));
		if (candidates.isEmpty()) {
			return null;
		}

		if (candidates.size() > 1) {
			throw new IllegalStateException("Найдено несколько возможных архивов приклада. Ожидался один файл вида func-<version>.zip. Укажите явно через --app-zip: " + candidates.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", ")));
		}

		Path result = candidates.get(0);
		LOGGER.info("Найден архив приклада: " + result);
		return result;
	}

	public Path resolveJettyTemplate(Path explicitPath) throws IOException {
		if (explicitPath != null) {
			if (!Files.exists(explicitPath)) {
				throw new IllegalStateException("Шаблон Jetty не найден: " + explicitPath);
			}
			LOGGER.info("Шаблон Jetty указан явно: " + explicitPath);
			return explicitPath;
		}

		List<Path> candidates = findFiles(name -> name.toLowerCase().endsWith(".zip") && isLikelyJettyTemplate(name));
		if (candidates.isEmpty()) {
			// Также разрешаем каталог template рядом с артефактами
			Path templateDir = artifactsDir.resolve("template");
			if (Files.exists(templateDir) && Files.isDirectory(templateDir)) {
				LOGGER.info("Найден каталог шаблона: " + templateDir);
				return templateDir;
			}
			throw new IllegalStateException(JETTY_TEMPLATE_NOT_FOUND.formatted(artifactsDir));
		}

		if (candidates.size() > 1) {
			throw new IllegalStateException("Найдено несколько возможных шаблонов Jetty. Укажите явно через --jetty-template: " + candidates.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", ")));
		}

		Path result = candidates.get(0);
		LOGGER.info("Найден шаблон Jetty: " + result);
		return result;
	}

	public Path resolveInitSql(Path explicitPath) throws IOException {
		// --- 1. Явно указанный путь ---
		if (explicitPath != null) {
			if (!Files.exists(explicitPath)) {
				throw new IllegalStateException("Init SQL не найден: " + explicitPath);
			}

			// Директория: ищем create.sql внутри
			if (Files.isDirectory(explicitPath)) {
				Path createSql = explicitPath.resolve("create.sql");
				if (Files.exists(createSql) && Files.isRegularFile(createSql)) {
					LOGGER.info("Init SQL найден в директории: " + createSql);
					return createSql;
				}
				throw new IllegalStateException("В указанной директории не найден файл create.sql: " + explicitPath);
			}

			if (!Files.isRegularFile(explicitPath)) {
				throw new IllegalStateException("Ожидался файл или архив, но найдено: " + explicitPath);
			}

			String name = explicitPath.getFileName().toString().toLowerCase();
			// Обычный .sql файл
			if (name.endsWith(".sql")) {
				LOGGER.info("Init SQL указан явно: " + explicitPath);
				return explicitPath;
			}
			// ZIP-архив: ищем create.sql внутри
			if (name.endsWith(".zip")) {
				Path extracted = extractCreateSqlFromZip(explicitPath);
				LOGGER.info("Init SQL извлечён из архива: " + explicitPath.getFileName() + " -> " + extracted);
				return extracted;
			}

			throw new IllegalStateException("Неподдерживаемый формат init SQL. Ожидается .sql, .zip или директория: " + explicitPath);
		}

		// --- 2. Автоматический поиск в artifactsDir ---

		// 2.1. Сначала ищем обычные .sql файлы
		List<Path> sqlCandidates = findFiles(n -> n.toLowerCase().endsWith(".sql"));
		if (sqlCandidates.size() == 1) {
			Path result = sqlCandidates.get(0);
			LOGGER.info("Найден init SQL: " + result);
			return result;
		}

		if (sqlCandidates.size() > 1) {
			throw new IllegalStateException(
					"Найдено несколько SQL-файлов. Укажите явно через --init-sql: " +
					sqlCandidates.stream()
							.map(Path::getFileName)
							.map(Path::toString)
							.collect(Collectors.joining(", "))
			);
		}

		// 2.2. Если .sql не найден - ищем ZIP с create.sql внутри
		List<Path> zipCandidates = findFiles(n -> n.toLowerCase().endsWith(".zip"));
		for (Path zip : zipCandidates) {
			if (zipContainsCreateSql(zip)) {
				Path extracted = extractCreateSqlFromZip(zip);
				LOGGER.info("Init SQL извлечён из архива: " + zip.getFileName() + " -> " + extracted);
				return extracted;
			}
		}

		LOGGER.warn("Init SQL не найден (.sql файл или .zip с create.sql). Инициализация БД будет пропущена.");
		return null;
	}

	public String extractCoreVersion(@NotNull Path warFile, String fallbackVersion) {
		String fileName = warFile.getFileName().toString();
		Matcher matcher = CORE_WAR_VERSION_PATTERN.matcher(fileName);

		if (matcher.matches()) {
			String version = matcher.group(1);
			LOGGER.info("Версия ядра из имени файла: " + version);
			return version;
		}

		if (fallbackVersion != null && !fallbackVersion.isBlank()) {
			LOGGER.info("Версия ядра взята из параметра: " + fallbackVersion);
			return fallbackVersion;
		}

		LOGGER.warn("Не удалось определить версию ядра из имени файла: " + fileName);
		return "unknown";
	}

	private boolean isLikelyJettyTemplate(@NotNull String fileName) {
		String lower = fileName.toLowerCase();
		return lower.contains("jetty")
			   && lower.contains("template") || lower.startsWith("template") || lower.contains("stand-template");
	}

	/**
	 * Проверяет, содержит ли ZIP-архив запись create.sql (в корне или в поддиректории).
	 */
	private boolean zipContainsCreateSql(@NotNull Path zipFile) {
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String entryName = entry.getName().replace('\\', '/');
				if (entryName.equals("create.sql") || entryName.endsWith("/create.sql")) {
					return true;
				}
				zis.closeEntry();
			}
		} catch (IOException e) {
			LOGGER.warn("Не удалось прочитать ZIP-архив " + zipFile.getFileName() + ": " + e.getMessage());
		}
		return false;
	}

	/**
	 * Извлекает create.sql из ZIP-архива во временную директорию.
	 * Возвращает путь к распакованному create.sql.
	 */
	private @NotNull Path extractCreateSqlFromZip(@NotNull Path zipFile) throws IOException {
		Path tempDir = Files.createTempDirectory("ufos-init-sql-");
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String entryName = entry.getName().replace('\\', '/');

				if (entryName.equals("create.sql") || entryName.endsWith("/create.sql")) {
					// Копируем всегда как create.sql в корне tempDir - плоская структура.
					// Это также защищает от zip-slip: путь всегда tempDir/create.sql.
					Path target = tempDir.resolve("create.sql");
					Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
					return target;
				}

				zis.closeEntry();
			}
		}

		// Если дошли сюда - в архиве нет create.sql
		try {
			FileManager.deleteRecursively(tempDir, LOGGER);
		} catch (IOException ignored) {
			// не критично
		}
		throw new IllegalStateException("В архиве не найден файл create.sql: " + zipFile.getFileName());
	}

	private List<Path> findFiles(FilePredicate predicate) throws IOException {
		if (!Files.exists(artifactsDir)) {
			throw new IllegalStateException("Каталог артефактов не найден: " + artifactsDir);
		}

		try (Stream<Path> stream = FileManager.listFiles(artifactsDir)) {
			return stream
					.filter(Files::isRegularFile)
					.filter(p -> predicate.test(p.getFileName().toString()))
					.sorted()
					.collect(Collectors.toList());
		}
	}

	private void requireFile(Path path) {
		if (!Files.exists(path)) {
			throw new IllegalStateException("Файл не найден: " + path);
		}
		if (!Files.isRegularFile(path)) {
			throw new IllegalStateException("Ожидался файл, но найдено не регулярное: " + path);
		}
	}

	@FunctionalInterface
	private interface FilePredicate {
		boolean test(String fileName);
	}
}
