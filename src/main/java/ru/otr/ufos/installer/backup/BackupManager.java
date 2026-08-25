package ru.otr.ufos.installer.backup;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.filesystem.FileManager;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер бэкапов
 */
public class BackupManager {

	private final InstallerLogger logger;
	private final FileManager fileManager;
	@Getter
	private final Path backupBaseDir;

	public BackupManager(InstallerLogger logger, Path backupBaseDir) {
		this.logger = logger;
		this.fileManager = new FileManager(logger);
		this.backupBaseDir = backupBaseDir;
	}

	public BackupResult createFullBackup(Path standDir, String reason) throws IOException {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path backupDir = backupBaseDir.resolve("backup-" + timestamp + "-" + reason + "-full");

		logger.step("Создание полного бэкапа стенда");
		logger.info("Источник: " + standDir);
		logger.info("Назначение: " + backupDir);

		fileManager.createDirectories(backupDir);
		fileManager.copyDirectory(standDir, backupDir);

		logger.info("Полный бэкап создан");

		return new BackupResult(backupDir, List.of(""), true);
	}

	public BackupResult createPartialBackup(Path standDir, String reason, @NotNull List<String> relativePaths) throws IOException {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path backupDir = backupBaseDir.resolve("backup-" + timestamp + "-" + reason);

		logger.step("Создание частичного бэкапа");
		logger.info("Каталог бэкапа: " + backupDir);

		fileManager.createDirectories(backupDir);

		List<String> backedUp = new ArrayList<>();

		for (String relativePath : relativePaths) {
			String normalized = relativePath.replace('\\', '/');
			Path source = standDir.resolve(normalized);

			if (!fileManager.exists(source)) {
				logger.warn("Пропуск, не существует: " + normalized);
				continue;
			}

			Path target = backupDir.resolve(normalized);

			if (fileManager.isDirectory(source)) {
				fileManager.copyDirectory(source, target);
				logger.info("  ✓ " + normalized + "/");
			} else {
				fileManager.copyFile(source, target);
				logger.info("  ✓ " + normalized);
			}

			backedUp.add(normalized);
		}

		logger.info("Частичный бэкап создан");

		return new BackupResult(backupDir, backedUp, false);
	}
}
