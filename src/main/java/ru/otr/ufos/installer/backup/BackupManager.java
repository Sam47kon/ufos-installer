package ru.otr.ufos.installer.backup;

import ru.otr.ufos.installer.filesystem.FileManager;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Менеджер бэкапов
 */
public class BackupManager {
	private final InstallerLogger logger;
	private final FileManager fileManager;
	private final Path backupBaseDir;

	public BackupManager(InstallerLogger logger, Path backupBaseDir) {
		this.logger = logger;
		this.fileManager = new FileManager(logger);
		this.backupBaseDir = backupBaseDir;
	}

	/**
	 * Создать бэкап директории
	 *
	 * @param sourceDir директория для бэкапа
	 * @param backupName имя бэкапа (например, "core" или "full")
	 * @return путь к созданному бэкапу
	 */
	public Path createBackup(Path sourceDir, String backupName) throws IOException {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path backupDir = backupBaseDir.resolve("backup-" + timestamp + "-" + backupName);

		logger.info("Создание бэкапа: " + sourceDir + " -> " + backupDir);

		fileManager.createDirectories(backupDir);
		fileManager.copyDirectory(sourceDir, backupDir);

		logger.info("Бэкап создан: " + backupDir);
		return backupDir;
	}

	/**
	 * Создать бэкап отдельных файлов/директорий
	 */
	public Path createPartialBackup(Path standDir, String backupName, String... relativePaths) throws IOException {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path backupDir = backupBaseDir.resolve("backup-" + timestamp + "-" + backupName);

		logger.info("Создание частичного бэкапа: " + backupName);

		fileManager.createDirectories(backupDir);

		for (String relativePath : relativePaths) {
			Path source = standDir.resolve(relativePath);
			Path target = backupDir.resolve(relativePath);

			if (fileManager.exists(source)) {
				if (fileManager.isDirectory(source)) {
					fileManager.copyDirectory(source, target);
					logger.info("  ✓ " + relativePath + "/");
				} else {
					fileManager.copyFile(source, target);
					logger.info("  ✓ " + relativePath);
				}
			} else {
				logger.warn("  ✗ " + relativePath + " (не существует)");
			}
		}

		logger.info("Бэкап создан: " + backupDir);
		return backupDir;
	}

	/**
	 * Восстановить из бэкапа
	 */
	public void restore(Path backupDir, Path targetDir) throws IOException {
		logger.info("Восстановление из бэкапа: " + backupDir + " -> " + targetDir);

		if (!fileManager.exists(backupDir)) {
			throw new IOException("Бэкап не найден: " + backupDir);
		}

		fileManager.copyDirectory(backupDir, targetDir);
		logger.info("Восстановление завершено");
	}

	/**
	 * Удалить бэкап
	 */
	public void deleteBackup(Path backupDir) throws IOException {
		logger.info("Удаление бэкапа: " + backupDir);
		fileManager.deleteRecursively(backupDir);
	}

	/**
	 * Получить базовую директорию бэкапов
	 */
	public Path getBackupBaseDir() {
		return backupBaseDir;
	}
}