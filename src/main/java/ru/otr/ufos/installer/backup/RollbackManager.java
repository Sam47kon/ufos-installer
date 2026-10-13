package ru.otr.ufos.installer.backup;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.core.UserPrompt;
import ru.otr.ufos.installer.filesystem.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Откат изменений из бэкапа
 */
@UtilityClass
public class RollbackManager {

	public static boolean requestRollback(@NotNull BackupResult backup,
										  Path standDir,
										  @NotNull UserPrompt prompt,
										  @NotNull Exception error) {
		LOGGER.error(EMPTY);
		LOGGER.error("=== ОБНАРУЖЕНА ОШИБКА ===");
		LOGGER.error("Сообщение: " + error.getMessage());
		LOGGER.error("Доступен бэкап: " + backup.backupDir());
		LOGGER.error(EMPTY);

		boolean confirmed = prompt.confirm("Выполнить откат из бэкапа?");
		if (!confirmed) {
			LOGGER.warn("Откат отменён пользователем");
			return false;
		}
		return performRollback(backup, standDir);
	}

	public static boolean performRollback(@NotNull BackupResult backup, Path standDir) {
		try {
			LOGGER.step("Выполнение отката");

			if (backup.full()) {
				LOGGER.info("Очистка стенда перед полным восстановлением");
				FileManager.clearDirectory(standDir, LOGGER);

				LOGGER.info("Восстановление из полного бэкапа");
				FileManager.copyDirectory(backup.backupDir(), standDir, LOGGER);
			} else {
				for (String affectedPath : backup.affectedPaths()) {
					Path target = standDir.resolve(affectedPath);
					Path source = backup.backupDir().resolve(affectedPath);
					if (Files.exists(target)) {
						LOGGER.info("Удаление: " + affectedPath);
						FileManager.deleteRecursively(target, LOGGER);
					}
					if (Files.exists(source)) {
						LOGGER.info("Восстановление: " + affectedPath);

						if (Files.isDirectory(source)) {
							FileManager.copyDirectory(source, target, LOGGER);
						} else {
							FileManager.copyFile(source, target, LOGGER);
						}
					}
				}
			}
			LOGGER.info("Откат выполнен успешно");
			return true;
		} catch (IOException e) {
			LOGGER.error("Критическая ошибка при откате", e);
			LOGGER.error("Стенд может быть повреждён.");
			LOGGER.error("Попробуйте восстановить вручную из: " + backup.backupDir());
			return false;
		}
	}
}
