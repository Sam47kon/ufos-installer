package ru.otr.ufos.installer.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Поиск и проверка артефактов в каталоге artifacts
 */
public class ArtifactResolver {

	private static final Pattern CORE_WAR_VERSION_PATTERN = Pattern.compile("^sufd-server-(.+)\\.war$", Pattern.CASE_INSENSITIVE);

	private final InstallerLogger logger;
	private final Path artifactsDir;

	public ArtifactResolver(InstallerLogger logger, Path artifactsDir) {
		this.logger = logger;
		this.artifactsDir = artifactsDir;
	}

	public Path resolveCoreWar(Path explicitPath) throws IOException {
		if (explicitPath != null) {
			requireFile(explicitPath);
			logger.info("Ядро указано явно: " + explicitPath);
			return explicitPath;
		}

		List<Path> candidates = findFiles(name -> name.toLowerCase().startsWith("sufd-server") && name.toLowerCase().endsWith(".war"));
		if (candidates.isEmpty()) {
			throw new IllegalStateException("Не найден WAR-файл ядра в каталоге артефактов: " + artifactsDir + ". Ожидался файл вида sufd-server-<version>.war");
		}

		if (candidates.size() > 1) {
			throw new IllegalStateException("Найдено несколько WAR-файлов ядра. Укажите явно через --core-war: " + candidates.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", ")));
		}

		Path result = candidates.get(0);
		logger.info("Найден файл ядра: " + result);
		return result;
	}

	public Path resolveLibsZip(Path explicitPath) throws IOException {
		if (explicitPath != null) {
			requireFile(explicitPath);
			logger.info("Архив библиотек указан явно: " + explicitPath);
			return explicitPath;
		}

		List<Path> candidates = findFiles(name -> name.toLowerCase().startsWith("sufd-server-libs") && name.toLowerCase().endsWith(".zip"));
		if (candidates.isEmpty()) {
			throw new IllegalStateException("Не найден архив библиотек в каталоге артефактов: " + artifactsDir + ". Ожидался файл вида sufd-server-libs-<version>.zip");
		}

		if (candidates.size() > 1) {
			throw new IllegalStateException("Найдено несколько архивов библиотек. Укажите явно через --libs-zip: " + candidates.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", ")));
		}

		Path result = candidates.get(0);
		logger.info("Найден архив библиотек: " + result);
		return result;
	}

	public @Nullable Path resolveAppZip(Path explicitPath) throws IOException {
		if (explicitPath != null) {
			requireFile(explicitPath);
			logger.info("Архив приклада указан явно: " + explicitPath);
			return explicitPath;
		}

		List<Path> candidates = findFiles(name -> name.toLowerCase().endsWith(".zip") && !name.toLowerCase().startsWith("sufd-server-libs") && !isLikelyJettyTemplate(name));
		if (candidates.isEmpty()) {
			return null;
		}

		if (candidates.size() > 1) {
			throw new IllegalStateException("Найдено несколько возможных архивов приклада. Укажите явно через --app-zip: " + candidates.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", ")));
		}

		Path result = candidates.get(0);
		logger.info("Найден архив приклада: " + result);
		return result;
	}

	public Path resolveJettyTemplate(Path explicitPath) throws IOException {
		if (explicitPath != null) {
			if (!Files.exists(explicitPath)) {
				throw new IllegalStateException("Шаблон Jetty не найден: " + explicitPath);
			}
			logger.info("Шаблон Jetty указан явно: " + explicitPath);
			return explicitPath;
		}

		List<Path> candidates = findFiles(name -> name.toLowerCase().endsWith(".zip") && isLikelyJettyTemplate(name));
		if (candidates.isEmpty()) {
			// Также разрешаем каталог template рядом с артефактами
			Path templateDir = artifactsDir.resolve("template");
			if (Files.exists(templateDir) && Files.isDirectory(templateDir)) {
				logger.info("Найден каталог шаблона: " + templateDir);
				return templateDir;
			}
			throw new IllegalStateException("Не найден шаблон Jetty в каталоге артефактов: " + artifactsDir + ". Ожидался архив вида jetty-template-<version>.zip или каталог template/");
		}

		if (candidates.size() > 1) {
			throw new IllegalStateException("Найдено несколько возможных шаблонов Jetty. Укажите явно через --jetty-template: " + candidates.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", ")));
		}

		Path result = candidates.get(0);
		logger.info("Найден шаблон Jetty: " + result);
		return result;
	}

	public Path resolveInitSql(Path explicitPath) throws IOException {
		if (explicitPath != null) {
			requireFile(explicitPath);
			logger.info("Init SQL указан явно: " + explicitPath);
			return explicitPath;
		}

		List<Path> candidates = findFiles(name -> name.toLowerCase().endsWith(".sql"));
		if (candidates.isEmpty()) {
			logger.warn("Init SQL не найден. Инициализация БД будет пропущена.");
			return null;
		}

		if (candidates.size() > 1) {
			throw new IllegalStateException("Найдено несколько SQL-файлов. Укажите явно через --init-sql: " + candidates.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", ")));
		}

		Path result = candidates.get(0);
		logger.info("Найден init SQL: " + result);
		return result;
	}

	public String extractCoreVersion(@NotNull Path warFile, String fallbackVersion) {
		String fileName = warFile.getFileName().toString();
		Matcher matcher = CORE_WAR_VERSION_PATTERN.matcher(fileName);

		if (matcher.matches()) {
			String version = matcher.group(1);
			logger.info("Версия ядра из имени файла: " + version);
			return version;
		}

		if (fallbackVersion != null && !fallbackVersion.isBlank()) {
			logger.info("Версия ядра взята из параметра: " + fallbackVersion);
			return fallbackVersion;
		}

		logger.warn("Не удалось определить версию ядра из имени файла: " + fileName);
		return "unknown";
	}

	private boolean isLikelyJettyTemplate(@NotNull String fileName) {
		String lower = fileName.toLowerCase();
		return lower.contains("jetty")
				&& lower.contains("template") || lower.startsWith("template") || lower.contains("stand-template");
	}

	private List<Path> findFiles(FilePredicate predicate) throws IOException {
		if (!Files.exists(artifactsDir)) {
			throw new IllegalStateException("Каталог артефактов не найден: " + artifactsDir);
		}

		try (Stream<Path> stream = Files.list(artifactsDir)) {
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
