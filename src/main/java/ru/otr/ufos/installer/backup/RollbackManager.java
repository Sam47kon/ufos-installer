package ru.otr.ufos.installer.backup;

import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.filesystem.FileManager;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * Менеджер отката изменений
 */
public class RollbackManager {
	private final InstallerLogger logger;
	private final FileManager fileManager;
	private final BackupManager backupManager;

	public RollbackManager(InstallerContext context) {
		this.logger = context.getLogger();
		this.fileManager = new FileManager(logger);
		this.backupManager = new BackupManager(logger, context.getBackupDir());
	}

	/**
	 * Запросить откат при ошибке
	 *
	 * @param backupDir директория с бэкапом
	 * @param standDir директория стенда
	 * @return true если пользователь согласился на откат
	 */
	public boolean requestRollback(Path backupDir, Path standDir) {
		logger.error("");
		logger.error("=== ОБНАРУЖЕНА ОШИБКА ===");
		logger.error("Стенд может быть в неконсистентном состоянии.");
		logger.error("Доступен бэкап: " + backupDir);
		logger.error("");

		// В CLI режиме спрашиваем пользователя
		if (!isGuiMode()) {
			System.out.print("Выполнить откат? (y/n): ");
			Scanner scanner = new Scanner(System.in);
			String answer = scanner.nextLine().trim().toLowerCase();

			if (!answer.equals("y") && !answer.equals("yes")) {
				logger.info("Откат отменён пользователем");
				return false;
			}
		} else {
			// В GUI режиме можно показать диалог (реализуем позже)
			logger.info("В GUI режиме откат будет запрошен через диалоговое окно");
			return true; // пока всегда откатываем
		}

		return performRollback(backupDir, standDir);
	}

	/**
	 * Выполнить откат
	 */
	public boolean performRollback(Path backupDir, Path standDir) {
		try {
			logger.step("Выполнение отката...");

			// Очищаем стенд
			logger.info("Очистка стенда...");
			fileManager.clearDirectory(standDir);

			// Восстанавливаем из бэкапа
			logger.info("Восстановление из бэкапа...");
			backupManager.restore(backupDir, standDir);

			logger.info("✓ Откат выполнен успешно");
			return true;

		} catch (IOException e) {
			logger.error("✗ Критическая ошибка при откате", e);
			logger.error("Стенд может быть повреждён!");
			logger.error("Попробуйте восстановить вручную из: " + backupDir);
			return false;
		}
	}

	/**
	 * Проверить, запущен ли инсталлятор в GUI режиме
	 */
	private boolean isGuiMode() {
		// Простая проверка - если нет консоли, значит GUI
		return System.console() == null;
	}
}