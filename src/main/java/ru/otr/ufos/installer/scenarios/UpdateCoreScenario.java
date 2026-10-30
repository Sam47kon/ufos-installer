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
		if (!Files.isDirectory(standDir)) {
			throw new IllegalStateException("Стенд не является папкой: " + standDir);
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

		// Ядро опционально: обновляется только при наличии полного набора артефактов (WAR + библиотеки).
		// Если задан только один из двух артефактов ядра — это ошибка конфигурации.
		Path newCoreWar = resolver.resolveCoreWarOptional(context.getCoreWar());
		Path libsZip = resolver.resolveLibsZipOptional(context.getLibsZip());
		@Nullable Path appZip = resolver.resolveAppZip(context.getAppZip());

		if ((newCoreWar == null) != (libsZip == null)) {
			throw new IllegalStateException(
					"Заданы не все артефакты ядра: WAR и архив библиотек должны указываться вместе (либо оба опущены)"
			);
		}
		boolean updateCore = newCoreWar != null;
		if (!updateCore && appZip == null) {
			throw new IllegalStateException(
					"Нет артефактов для обновления: не найдены ни артефакты ядра (WAR + библиотеки), ни приклад (func-*.zip)"
			);
		}

		Path checksumsFile = context.getArtifactsDir().resolve("checksums.sha256");

		if (!ChecksumValidator.validate(checksumsFile, context.getArtifactsDir())) {
			throw new IllegalStateException("Проверка контрольных сумм завершилась с ошибками");
		}

		// Текущие WAR-файлы
		List<Path> currentWars = updateCore ? findCurrentWars(webappsDir) : List.of();

		String oldVersion = "unknown";
		Path oldWar = currentWars.isEmpty() ? null : currentWars.get(0);
		if (oldWar != null) {
			oldVersion = resolver.extractCoreVersion(oldWar);
		}

		String newVersion = updateCore ? resolver.extractCoreVersion(newCoreWar) : null;

		LOGGER.info("Текущая версия ядра: " + oldVersion);
		LOGGER.info("Новая версия ядра:   " + (updateCore ? newVersion : "не обновляется"));

		// Права до изменений
		PermissionsManager permissionsManager = new PermissionsManager(context.getChownUser());
		if (!PermissionsManager.IS_WINDOWS) {
			if (updateCore) {
				permissionsManager.snapshot(libExtDir);
			}
			if (appZip != null) {
				permissionsManager.snapshot(sufdConfigDir, sqlMigrationsDir);
			}
			if (appZip != null || Files.exists(versionFile)) {
				permissionsManager.snapshot(versionFile);
			}
		}
		PermissionsManager.Snapshot oldWarSnapshot = (updateCore && oldWar != null) ? permissionsManager.createSnapshot(oldWar) : null;

		// Новый WAR
		Path targetWar = updateCore ? webappsDir.resolve(newCoreWar.getFileName()) : null;

		BackupResult backup = context.isNeedBackup()
				? createBackup(currentWars, standDir, targetWar, appZip, versionFile, context.getBackupDir(), updateCore)
				: null;
		context.addReport("Бэкап", null == backup ? "не создавался" : backup.backupDir().toAbsolutePath().toString());
		try {
			if (updateCore) {
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
			} else {
				LOGGER.info("Артефакты ядра не заданы — ядро и библиотеки не обновляются");
			}

			// Обновление приклада
			if (appZip != null) {
				LOGGER.step("Обновление приклада");
				ScenariosUtils.deployApp(context, appZip, standDir);
			} else {
				LOGGER.info("Архив приклада не задан — приклад не обновляется");
			}

			if (!PermissionsManager.IS_WINDOWS) {
				// Восстановление прав
				LOGGER.step("Восстановление прав доступа");
				permissionsManager.restore();
				if (updateCore) {
					if (oldWarSnapshot != null) {
						permissionsManager.applyFilePermissions(targetWar, oldWarSnapshot.getRootPermissions());
					} else {
						permissionsManager.applyDefaults(targetWar);
					}
				}
			}

			// Отчёт
			context.addReport("Стенд", standDir.toAbsolutePath().toString());
			context.addReport("Старая версия ядра", oldVersion);
			context.addReport("Новая версия ядра", updateCore ? newVersion : "не обновляется");
			context.addReport("Новый WAR", updateCore ? targetWar.toAbsolutePath().toString() : "не обновляется");
			context.addReport("Архив библиотек", updateCore ? libsZip.getFileName().toString() : "не обновляется");
			context.addReport("Архив приклада", null == appZip ? "Установка приклада пропущена" : appZip.getFileName().toString());
			ReportGenerator.printReport(context);
		} catch (Exception e) {
			LOGGER.error("Ошибка обновления", e);
			if (backup == null) {
				context.addReport("Откат", "недоступен (бэкап не создавался)");
			} else {
				boolean rolledBack = RollbackManager.requestRollback(backup, standDir, context.getUserPrompt(), e);
				context.addReport("Откат", rolledBack ? "выполнен" : "отменён");
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

	private @NotNull BackupResult createBackup(@NotNull List<Path> currentWars, Path standDir, @Nullable Path targetWar, @Nullable Path appZip, Path versionFile, Path backupDir, boolean updateCore) throws IOException {
		// Бэкап только того, что заменяем
		List<String> backupPaths = new ArrayList<>();
		if (updateCore) {
			// Старые WAR
			currentWars.stream().map(war -> relativize(standDir, war)).forEach(backupPaths::add);

			// Чтобы при откате удалить новый war, если он уже был скопирован
			String newWarRelative = relativize(standDir, targetWar);
			if (!backupPaths.contains(newWarRelative)) {
				backupPaths.add(newWarRelative);
			}

			// Библиотеки
			backupPaths.add("lib/ext");
		}
		// Приклад
		if (null != appZip) {
			backupPaths.add("sufd.config");
			backupPaths.add("sql-migrations");
			backupPaths.add("version.txt");
		} else if (Files.exists(versionFile)) {
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
