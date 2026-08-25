package ru.otr.ufos.installer.backup;

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

	public boolean requestRollback(BackupResult backup,
								   Path standDir,
								   UserPrompt prompt,
								   Exception error) {

		logger.error("");
		logger.error("=== ОБНАРУЖЕНА ОШИБКА ===");
		logger.error("Сообщение: " + error.getMessage());
		logger.error("Доступен бэкап: " + backup.getBackupDir());
		logger.error("");

		boolean confirmed = prompt.confirm("Выполнить откат из бэкапа?");

		if (!confirmed) {
			logger.warn("Откат отменён пользователем");
			return false;
		}

		return performRollback(backup, standDir);
	}

	public boolean performRollback(BackupResult backup, Path standDir) {
		try {
			logger.step("Выполнение отката");

			if (backup.isFull()) {
				logger.info("Очистка стенда перед полным восстановлением");
				fileManager.clearDirectory(standDir);

				logger.info("Восстановление из полного бэкапа");
				fileManager.copyDirectory(backup.getBackupDir(), standDir);
			} else {
				for (String relativePath : backup.getRelativePaths()) {
					Path target = standDir.resolve(relativePath);
					Path source = backup.getBackupDir().resolve(relativePath);

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
			logger.error("Попробуйте восстановить вручную из: " + backup.getBackupDir());
			return false;
		}
	}
}