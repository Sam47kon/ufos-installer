package ru.otr.ufos.installer.filesystem;

import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Менеджер файловых операций
 */
public class FileManager {
	private final InstallerLogger logger;

	public FileManager(InstallerLogger logger) {
		this.logger = logger;
	}

	/**
	 * Создать директорию (включая родительские)
	 */
	public void createDirectories(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			Files.createDirectories(dir);
			logger.debug("Создана директория: " + dir);
		}
	}

	/**
	 * Удалить директорию рекурсивно
	 */
	public void deleteRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}

		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()) // обратный порядок для удаления
					.forEach(path -> {
						try {
							Files.delete(path);
						} catch (IOException e) {
							logger.warn("Не удалось удалить: " + path + " - " + e.getMessage());
						}
					});
		}
		logger.debug("Удалена директория: " + dir);
	}

	/**
	 * Очистить содержимое директории (саму директорию не удалять)
	 */
	public void clearDirectory(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			createDirectories(dir);
			return;
		}

		try (Stream<Path> stream = Files.list(dir)) {
			stream.forEach(path -> {
				try {
					if (Files.isDirectory(path)) {
						deleteRecursively(path);
					} else {
						Files.delete(path);
					}
				} catch (IOException e) {
					logger.warn("Не удалось удалить: " + path + " - " + e.getMessage());
				}
			});
		}
		logger.debug("Очищена директория: " + dir);
	}

	/**
	 * Копировать файл с перезаписью
	 */
	public void copyFile(Path source, @NotNull Path target) throws IOException {
		createDirectories(target.getParent());
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		logger.debug("Скопирован файл: " + source + " -> " + target);
	}

	/**
	 * Копировать директорию рекурсивно
	 */
	public void copyDirectory(Path source, Path target) throws IOException {
		createDirectories(target);

		try (Stream<Path> stream = Files.walk(source)) {
			stream.forEach(path -> {
				try {
					Path relative = source.relativize(path);
					Path targetPath = target.resolve(relative);

					if (Files.isDirectory(path)) {
						createDirectories(targetPath);
					} else {
						copyFile(path, targetPath);
					}
				} catch (IOException e) {
					throw new RuntimeException("Ошибка копирования: " + path, e);
				}
			});
		}
		logger.debug("Скопирована директория: " + source + " -> " + target);
	}

	/**
	 * Переместить файл/директорию
	 */
	public void move(Path source, @NotNull Path target) throws IOException {
		createDirectories(target.getParent());
		Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		logger.debug("Перемещено: " + source + " -> " + target);
	}

	/**
	 * Проверить существование файла/директории
	 */
	public boolean exists(Path path) {
		return Files.exists(path);
	}

	/**
	 * Проверить, что путь является директорией
	 */
	public boolean isDirectory(Path path) {
		return Files.isDirectory(path);
	}

	/**
	 * Получить размер файла в байтах
	 */
	public long getFileSize(Path file) throws IOException {
		return Files.size(file);
	}

	/**
	 * Установить POSIX права доступа (только для Linux/Unix)
	 */
	public void setPosixPermissions(Path path, String permissions) throws IOException {
		if (isWindows()) {
			logger.debug("Пропуск установки POSIX прав на Windows: " + path);
			return;
		}

		try {
			Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permissions);
			Files.setPosixFilePermissions(path, perms);
			logger.debug("Установлены права " + permissions + " для: " + path);
		} catch (UnsupportedOperationException e) {
			logger.debug("POSIX права не поддерживаются: " + path);
		}
	}

	/**
	 * Изменить владельца файла (только для Linux/Unix, требует root)
	 */
	public void changeOwner(Path path, String owner) throws IOException {
		if (isWindows()) {
			logger.debug("Пропуск chown на Windows: " + path);
			return;
		}

		try {
			ProcessBuilder pb = new ProcessBuilder("chown", "-R", owner, path.toString());
			Process process = pb.start();
			int exitCode = process.waitFor();

			if (exitCode == 0) {
				logger.debug("Изменён владелец на " + owner + " для: " + path);
			} else {
				logger.warn("Не удалось изменить владельца для: " + path);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Прервано изменение владельца", e);
		}
	}

	/**
	 * Проверить, что это Windows
	 */
	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}

	/**
	 * Получить список файлов в директории
	 */
	public Stream<Path> listFiles(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return Stream.empty();
		}
		return Files.list(dir);
	}
}
