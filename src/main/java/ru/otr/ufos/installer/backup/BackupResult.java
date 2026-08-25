package ru.otr.ufos.installer.backup;

import java.nio.file.Path;
import java.util.List;

/**
 * Результат создания бэкапа
 */
public record BackupResult(Path backupDir, List<String> relativePaths, boolean full) {
}
