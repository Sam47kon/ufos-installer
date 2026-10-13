package ru.otr.ufos.installer.scenarios;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.filesystem.ArchiveHelper;
import ru.otr.ufos.installer.filesystem.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

@UtilityClass
public class ScenariosUtils {

	static @Nullable Path findVersionFile(@NotNull Path appRoot) {
		Path direct = appRoot.resolve("version.txt");
		if (Files.exists(direct)) {
			return direct;
		}

		Path inConfig = appRoot.resolve("sufd.config/version.txt");
		if (Files.exists(inConfig)) {
			return inConfig;
		}

		Path funcVersion = appRoot.resolve("sufd.config/func.version");
		if (Files.exists(funcVersion)) {
			return funcVersion;
		}

		return null;
	}

	static void deployApp(@NotNull InstallerContext context,
						  @Nullable Path appZip,
						  @NotNull Path standDir) throws IOException {
		if (null == appZip) {
			LOGGER.info("Артефакт приклада не задан. Установка приклада пропущена");
			return;
		}

		Path tempDir = ArchiveHelper.extractToTemp(appZip);
		try {
			Path appRoot = ArchiveHelper.detectEffectiveRoot(tempDir);

			Path srcConfig = appRoot.resolve("sufd.config");
			Path srcMigrations = appRoot.resolve("sql-migrations");

			Path targetConfig = standDir.resolve("sufd.config");
			Path targetMigrations = standDir.resolve("sql-migrations");

			if (Files.exists(srcConfig)) {
				FileManager.clearDirectory(targetConfig, LOGGER);
				ArchiveHelper.copyContents(srcConfig, targetConfig);
				LOGGER.info("Обновлено: sufd.config/");
				context.addChange("Обновлено: sufd.config/");
			} else {
				LOGGER.warn("В архиве приклада не найдена папка sufd.config");
			}

			if (Files.exists(srcMigrations)) {
				FileManager.clearDirectory(targetMigrations, LOGGER);
				ArchiveHelper.copyContents(srcMigrations, targetMigrations);
				LOGGER.info("Обновлено: sql-migrations/");
				context.addChange("Обновлено: sql-migrations/");
			} else {
				LOGGER.warn("В архиве приклада не найдена папка sql-migrations");
			}

			// version.txt
			Path versionFile = findVersionFile(appRoot);
			if (versionFile != null) {
				Path targetVersion = standDir.resolve("version.txt");
				FileManager.copyFile(versionFile, targetVersion, LOGGER);
				LOGGER.info("Обновлено: version.txt");
				context.addChange("Обновлено: version.txt");
			} else {
				LOGGER.warn("version.txt не найден в архиве приклада");
			}

		} finally {
			FileManager.deleteRecursively(tempDir, LOGGER);
		}
	}
}
