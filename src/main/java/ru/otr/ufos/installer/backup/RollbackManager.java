package ru.otr.ufos.installer.backup;

import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.core.UserPrompt;
import ru.otr.ufos.installer.filesystem.FileManager;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Откат изменений из бэкапа
 */
public class RollbackManager {

	private final InstallerLogger logger;
	private final FileManager fileManager;

	public RollbackManager(InstallerLogger logger) {
		this.logger = logger;
		this.fileManager = new FileManager(logger);
	}

	public boolean requestRollback(@NotNull BackupResult backup,
								   Path standDir,
								   @NotNull UserPrompt prompt,
								   @NotNull Exception error) {

		logger.error("");
		logger.error("=== ОБНАРУЖЕНА ОШИБКА ===");
		logger.error("Сообщение: " + error.getMessage());
		logger.error("Доступен бэкап: " + backup.backupDir());
		logger.error("");

		boolean confirmed = prompt.confirm("Выполнить откат из бэкапа?");

		if (!confirmed) {
			logger.warn("Откат отменён пользователем");
			return false;
		}

		return performRollback(backup, standDir);
	}

	public boolean performRollback(@NotNull BackupResult backup, Path standDir) {
		try {
			logger.step("Выполнение отката");

			if (backup.full()) {
				logger.info("Очистка стенда перед полным восстановлением");
				fileManager.clearDirectory(standDir);

				logger.info("Восстановление из полного бэкапа");
				fileManager.copyDirectory(backup.backupDir(), standDir);
			} else {
				for (String relativePath : backup.relativePaths()) {
					Path target = standDir.resolve(relativePath);
					Path source = backup.backupDir().resolve(relativePath);

					if (fileManager.exists(target)) {
						logger.info("Удаление: " + relativePath);
						fileManager.deleteRecursively(target);
					}

					if (fileManager.exists(source)) {
						logger.info("Восстановление: " + relativePath);

						if (fileManager.isDirectory(source)) {
							fileManager.copyDirectory(source, target);
						} else {
							fileManager.copyFile(source, target);
						}
					}
				}
			}

			logger.info("Откат выполнен успешно");
			return true;

		} catch (IOException e) {
			logger.error("Критическая ошибка при откате", e);
			logger.error("Стенд может быть повреждён.");
			logger.error("Попробуйте восстановить вручную из: " + backup.backupDir());
			return false;
		}
	}
}
