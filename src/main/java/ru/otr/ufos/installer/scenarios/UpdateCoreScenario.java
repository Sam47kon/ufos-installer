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
import ru.otr.ufos.installer.logging.InstallerLogger;
import ru.otr.ufos.installer.report.ReportGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Сценарий 2: обновление ядра и приклада без пересоздания Jetty
 */
public class UpdateCoreScenario implements ScenarioExecutor {

	@Override
	public void execute(@NotNull InstallerContext context) throws Exception {
		InstallerLogger logger = context.getLogger();

		logger.info("==================================================");
		logger.info("СЦЕНАРИЙ: ОБНОВЛЕНИЕ ЯДРА И ПРИКЛАДА");
		logger.info("==================================================");

		EnvironmentValidator environmentValidator = new EnvironmentValidator(logger);
		environmentValidator.validateJava();
		environmentValidator.validateOs();
		environmentValidator.validateStandParentWritable(context.getStandDir(), true);

		FileManager fileManager = new FileManager(logger);
		ArchiveHelper archiveHelper = new ArchiveHelper(logger);
		BackupManager backupManager = new BackupManager(logger, context.getBackupDir());
		RollbackManager rollbackManager = new RollbackManager(logger);
		PermissionsManager permissionsManager = new PermissionsManager(logger);

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

		ArtifactResolver resolver = new ArtifactResolver(logger, context.getArtifactsDir());

		Path newCoreWar = resolver.resolveCoreWar(context.getCoreWar());
		Path libsZip = resolver.resolveLibsZip(context.getLibsZip());
		@Nullable Path appZip = resolver.resolveAppZip(context.getAppZip());

		ChecksumValidator checksumValidator = new ChecksumValidator(logger);
		Path checksumsFile = context.getArtifactsDir().resolve("checksums.sha256");

		if (!checksumValidator.validate(checksumsFile, context.getArtifactsDir())) {
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

		logger.info("Текущая версия: " + oldVersion);
		logger.info("Новая версия:   " + newVersion);

		// Права до изменений
		PermissionsManager.Snapshot libExtSnapshot = permissionsManager.snapshot(libExtDir);
		PermissionsManager.Snapshot sufdConfigSnapshot = permissionsManager.snapshot(sufdConfigDir);
		PermissionsManager.Snapshot sqlMigrationsSnapshot = permissionsManager.snapshot(sqlMigrationsDir);
		PermissionsManager.Snapshot versionSnapshot = permissionsManager.snapshot(versionFile);
		PermissionsManager.Snapshot oldWarSnapshot = oldWar == null ? null : permissionsManager.snapshot(oldWar);

		BackupResult backup = null;
		try {
			// Бэкап только того, что заменяем
			List<String> backupPaths = new ArrayList<>();
			currentWars.stream().map(war -> relativize(standDir, war)).forEach(backupPaths::add);

			backupPaths.add("lib/ext");
			if (null != appZip) {
				backupPaths.add("sufd.config");
				backupPaths.add("sql-migrations");
			}
			if (Files.exists(versionFile)) {
				backupPaths.add("version.txt");
			}

			backup = backupManager.createPartialBackup(standDir, "update-core", backupPaths);
			context.addReport("Бэкап", backup.backupDir().toAbsolutePath().toString());

			// Обновление WAR
			logger.step("Обновление ядра");
			for (Path war : currentWars) {
				logger.info("Удаление старого WAR: " + war.getFileName());
				Files.deleteIfExists(war);
				context.addChange("Удалено: " + relativize(standDir, war));
			}
			// Удаляем возможные распакованные директории
			deleteExplodedWars(webappsDir);
			Path targetWar = webappsDir.resolve(newCoreWar.getFileName());
			fileManager.copyFile(newCoreWar, targetWar);
			logger.info("Новый WAR размещён: " + targetWar);
			context.addChange("Создано: " + relativize(standDir, targetWar));

			// Обновление библиотек
			logger.step("Обновление библиотек lib/ext");
			archiveHelper.extractSmart(libsZip, libExtDir, true);
			context.addChange("Обновлено: lib/ext/");

			// Обновление приклада
			logger.step("Обновление приклада");
			ScenariosUtils.deployApp(context, appZip, standDir, archiveHelper, fileManager);

			if (!permissionsManager.isWindows()) {
				// Восстановление прав
				logger.step("Восстановление прав доступа");
				String chownUser = context.getChownUser();

				permissionsManager.restore(libExtSnapshot, libExtDir, chownUser);
				permissionsManager.restore(sufdConfigSnapshot, sufdConfigDir, chownUser);
				permissionsManager.restore(sqlMigrationsSnapshot, sqlMigrationsDir, chownUser);
				permissionsManager.restore(versionSnapshot, versionFile, chownUser);

				if (oldWarSnapshot != null) {
					permissionsManager.applyFilePermissions(targetWar, oldWarSnapshot.rootPermissions);
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

			ReportGenerator reportGenerator = new ReportGenerator(logger);
			reportGenerator.printReport(context);

		} catch (Exception e) {
			logger.error("Ошибка обновления", e);

			if (backup != null) {
				boolean rolledBack = rollbackManager.requestRollback(backup, standDir, context.getUserPrompt(), e);
				if (rolledBack) {
					context.addReport("Откат", "выполнен");
				} else {
					context.addReport("Откат", "отменён");
				}
			}

			throw e;
		}
	}

	private List<Path> findCurrentWars(Path webappsDir) throws IOException {
		try (Stream<Path> stream = Files.list(webappsDir)) {
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

	private void deleteExplodedWars(Path webappsDir) throws IOException {
		try (Stream<Path> stream = Files.list(webappsDir)) {
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
