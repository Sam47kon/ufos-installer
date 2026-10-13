package ru.otr.ufos.installer.scenarios;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.otr.ufos.installer.artifacts.ArtifactResolver;
import ru.otr.ufos.installer.artifacts.ChecksumValidator;
import ru.otr.ufos.installer.backup.BackupManager;
import ru.otr.ufos.installer.backup.BackupResult;
import ru.otr.ufos.installer.backup.RollbackManager;
import ru.otr.ufos.installer.core.EnvironmentValidator;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.filesystem.ArchiveHelper;
import ru.otr.ufos.installer.filesystem.FileManager;
import ru.otr.ufos.installer.filesystem.PermissionsManager;
import ru.otr.ufos.installer.report.ReportGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Сценарий 2: обновление ядра и приклада без пересоздания Jetty
 */
public class UpdateCoreScenario implements ScenarioExecutor {

	@Override
	public void execute(@NotNull InstallerContext context) throws Exception {
		LOGGER.info("==================================================");
		LOGGER.info("СЦЕНАРИЙ: ОБНОВЛЕНИЕ ЯДРА И ПРИКЛАДА");
		LOGGER.info("==================================================");

		EnvironmentValidator.validateJava();
		EnvironmentValidator.validateOs();
		EnvironmentValidator.validateStandParentWritable(context.getStandDir(), true);

		Path standDir = context.getStandDir();
		if (!Files.exists(standDir)) {
			throw new IllegalStateException("Стенд не найден: " + standDir);
		}

		Path webappsDir = standDir.resolve("webapps");
		Path libExtDir = standDir.resolve("lib/ext");
		Path sufdConfigDir = standDir.resolve("sufd.config");
		Path sqlMigrationsDir = standDir.resolve("sql-migrations");
		Path versionFile = standDir.resolve("version.txt");

		if (!Files.exists(webappsDir)) {
			throw new IllegalStateException("Каталог webapps не найден. Похоже, это не стенд УФОС: " + standDir);
		}

		ArtifactResolver resolver = new ArtifactResolver(context.getArtifactsDir());

		Path newCoreWar = resolver.resolveCoreWar(context.getCoreWar());
		Path libsZip = resolver.resolveLibsZip(context.getLibsZip());
		@Nullable Path appZip = resolver.resolveAppZip(context.getAppZip());

		Path checksumsFile = context.getArtifactsDir().resolve("checksums.sha256");

		if (!ChecksumValidator.validate(checksumsFile, context.getArtifactsDir())) {
			throw new IllegalStateException("Проверка контрольных сумм завершилась с ошибками");
		}

		// Текущие WAR-файлы
		List<Path> currentWars = findCurrentWars(webappsDir);

		String oldVersion = "unknown";
		Path oldWar = currentWars.isEmpty() ? null : currentWars.get(0);
		if (oldWar != null) {
			oldVersion = resolver.extractCoreVersion(oldWar, null);
		}

		String newVersion = resolver.extractCoreVersion(newCoreWar, context.getUfosVersion());

		LOGGER.info("Текущая версия: " + oldVersion);
		LOGGER.info("Новая версия:   " + newVersion);

		// Права до изменений
		PermissionsManager permissionsManager = new PermissionsManager(context.getChownUser());
		if (!PermissionsManager.IS_WINDOWS) {
			permissionsManager.snapshot(libExtDir, sufdConfigDir, sqlMigrationsDir, versionFile);
		}
		PermissionsManager.Snapshot oldWarSnapshot = oldWar == null ? null : permissionsManager.createSnapshot(oldWar);

		// Новый WAR
		Path targetWar = webappsDir.resolve(newCoreWar.getFileName());

		BackupResult backup = createBackup(currentWars, standDir, targetWar, appZip, versionFile, context.getBackupDir());
		context.addReport("Бэкап", backup.backupDir().toAbsolutePath().toString());
		try {
			// Обновление WAR
			LOGGER.step("Обновление ядра");
			for (Path war : currentWars) {
				LOGGER.info("Удаление старого WAR: " + war.getFileName());
				Files.deleteIfExists(war);
				context.addChange("Удалено: " + relativize(standDir, war));
			}
			// Удаляем возможные распакованные директории
			deleteExplodedWars(webappsDir);
			FileManager.copyFile(newCoreWar, targetWar, LOGGER);
			LOGGER.info("Новый WAR размещён: " + targetWar);
			context.addChange("Создано: " + relativize(standDir, targetWar));

			// Обновление библиотек
			LOGGER.step("Обновление библиотек lib/ext");
			ArchiveHelper.extractSmart(libsZip, libExtDir, true);
			context.addChange("Обновлено: lib/ext/");

			// Обновление приклада
			LOGGER.step("Обновление приклада");
			ScenariosUtils.deployApp(context, appZip, standDir);

			if (!PermissionsManager.IS_WINDOWS) {
				// Восстановление прав
				LOGGER.step("Восстановление прав доступа");
				permissionsManager.restore();
				if (oldWarSnapshot != null) {
					permissionsManager.applyFilePermissions(targetWar, oldWarSnapshot.getRootPermissions());
				} else {
					permissionsManager.applyDefaults(targetWar);
				}
			}

			// Отчёт
			context.addReport("Стенд", standDir.toAbsolutePath().toString());
			context.addReport("Старая версия ядра", oldVersion);
			context.addReport("Новая версия ядра", newVersion);
			context.addReport("Новый WAR", targetWar.toAbsolutePath().toString());
			context.addReport("Архив библиотек", libsZip.getFileName().toString());
			context.addReport("Архив приклада", null == appZip ? "Установка приклада пропущена" : appZip.getFileName().toString());
			ReportGenerator.printReport(context);
		} catch (Exception e) {
			LOGGER.error("Ошибка обновления", e);
			boolean rolledBack = RollbackManager.requestRollback(backup, standDir, context.getUserPrompt(), e);
			if (rolledBack) {
				context.addReport("Откат", "выполнен");
			} else {
				context.addReport("Откат", "отменён");
			}
			throw e;
		}
	}

	private List<Path> findCurrentWars(Path webappsDir) throws IOException {
		try (Stream<Path> stream = FileManager.listFiles(webappsDir)) {
			return stream
					.filter(Files::isRegularFile)
					.filter(p -> {
						String name = p.getFileName().toString().toLowerCase();
						return name.startsWith("sufd-server") && name.endsWith(".war");
					})
					.sorted()
					.collect(Collectors.toList());
		}
	}

	private @NotNull BackupResult createBackup(@NotNull List<Path> currentWars, Path standDir, Path targetWar, @Nullable Path appZip, Path versionFile, Path backupDir) throws IOException {
		// Бэкап только того, что заменяем
		List<String> backupPaths = new ArrayList<>();
		// Старые WAR
		currentWars.stream().map(war -> relativize(standDir, war)).forEach(backupPaths::add);

		// Чтобы при откате удалить новый war, если он уже был скопирован
		String newWarRelative = relativize(standDir, targetWar);
		if (!backupPaths.contains(newWarRelative)) {
			backupPaths.add(newWarRelative);
		}

		// Библиотеки
		backupPaths.add("lib/ext");
		// Приклад
		if (null != appZip) {
			backupPaths.add("sufd.config");
			backupPaths.add("sql-migrations");
		}
		if (Files.exists(versionFile)) {
			backupPaths.add("version.txt");
		}
		return BackupManager.createPartialBackup(standDir, "update-core", backupPaths, backupDir);
	}

	private void deleteExplodedWars(Path webappsDir) throws IOException {
		try (Stream<Path> stream = FileManager.listFiles(webappsDir)) {
			List<Path> dirs = stream
					.filter(Files::isDirectory)
					.filter(p -> p.getFileName().toString().toLowerCase().startsWith("sufd-server"))
					.toList();

			for (Path dir : dirs) {
				deleteRecursivelySimple(dir);
			}
		}
	}

	private void deleteRecursivelySimple(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}

		try (Stream<Path> stream = Files.walk(path)) {
			List<Path> paths = stream
					.sorted(Comparator.reverseOrder())
					.toList();

			for (Path p : paths) {
				Files.deleteIfExists(p);
			}
		}
	}

	private @NotNull String relativize(@NotNull Path base, Path path) {
		return base.relativize(path).toString().replace('\\', '/');
	}
}
