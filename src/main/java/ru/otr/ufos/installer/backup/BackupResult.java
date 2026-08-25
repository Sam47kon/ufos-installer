package ru.otr.ufos.installer.backup;

import java.nio.file.Path;
import java.util.List;

/**
 * Результат создания бэкапа
 */
public class BackupResult {

	private final Path backupDir;
	private final List<String> relativePaths;
	private final boolean full;

	public BackupResult(Path backupDir, List<String> relativePaths, boolean full) {
		this.backupDir = backupDir;
		this.relativePaths = relativePaths;
		this.full = full;
	}

	public Path getBackupDir() {
		return backupDir;
	}

	public List<String> getRelativePaths() {
		return relativePaths;
	}

	public boolean isFull() {
		return full;
	}
}