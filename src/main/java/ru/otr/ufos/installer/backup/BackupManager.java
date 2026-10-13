package ru.otr.ufos.installer.backup;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.filesystem.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Менеджер бэкапов
 */
@UtilityClass
public class BackupManager {

	@Contract("_, _, _ -> new")
	public static @NotNull BackupResult createFullBackup(Path standDir, String reason, @NotNull Path backupDir) throws IOException {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		backupDir = backupDir.resolve("backup-" + timestamp + "-" + reason + "-full");

		LOGGER.step("Создание полного бэкапа стенда");
		LOGGER.info("Источник: " + standDir);
		LOGGER.info("Назначение: " + backupDir);

		FileManager.createDirectories(backupDir, LOGGER);
		FileManager.copyDirectory(standDir, backupDir, LOGGER);

		LOGGER.info("Полный бэкап создан");
		return new BackupResult(backupDir, List.of(EMPTY), true);
	}

	@Contract("_, _, _, _ -> new")
	public static @NotNull BackupResult createPartialBackup(Path standDir, String reason, @NotNull List<String> relativePaths, @NotNull Path backupDir) throws IOException {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		backupDir = backupDir.resolve("backup-" + timestamp + "-" + reason);

		LOGGER.step("Создание частичного бэкапа");
		LOGGER.info("Каталог бэкапа: " + backupDir);

		FileManager.createDirectories(backupDir, LOGGER);
		List<String> affectedPaths = new ArrayList<>();
		for (String relativePath : relativePaths) {
			String normalized = relativePath.replace('\\', '/');
			affectedPaths.add(normalized);
			Path source = standDir.resolve(normalized);
			if (!Files.exists(source)) {
				LOGGER.warn("Пропуск копирования в бэкап, не существует: " + normalized);
				continue;
			}
			Path target = backupDir.resolve(normalized);
			if (Files.isDirectory(source)) {
				FileManager.copyDirectory(source, target, LOGGER);
				LOGGER.info("  ✓ " + normalized + "/");
			} else {
				FileManager.copyFile(source, target, LOGGER);
				LOGGER.info("  ✓ " + normalized);
			}
		}
		LOGGER.info("Частичный бэкап создан");
		return new BackupResult(backupDir, affectedPaths, false);
	}
}
