package ru.otr.ufos.installer.filesystem;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Менеджер файловых операций
 */
@UtilityClass
public class FileManager {

	/**
	 * Создать директорию (включая родительские)
	 */
	public static void createDirectories(Path dir, @Nullable InstallerLogger logger) throws IOException {
		if (Files.exists(dir)) {
			return;
		}
		Files.createDirectories(dir);
		if (null != logger) {
			logger.debug("Создана директория: " + dir);
		}
	}

	public static void createDirectories(Path dir) throws IOException {
		createDirectories(dir, null);
	}

	/**
	 * Удалить директорию рекурсивно
	 */
	public static void deleteRecursively(Path dir, @Nullable InstallerLogger logger) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			// обратный порядок для удаления
			walk.sorted(Comparator.reverseOrder())
					.forEach(path -> {
						try {
							Files.delete(path);
						} catch (IOException e) {
							if (null != logger) {
								logger.warn("Не удалось удалить: " + path + " - " + e.getMessage());
							}
						}
					});
		}
		if (null != logger) {
			logger.debug("Удалена директория: " + dir);
		}
	}

	/**
	 * Очистить содержимое директории (саму директорию не удалять)
	 */
	public static void clearDirectory(Path dir, @Nullable InstallerLogger logger) throws IOException {
		if (!Files.exists(dir)) {
			createDirectories(dir, logger);
			return;
		}

		try (Stream<Path> stream = FileManager.listFiles(dir)) {
			stream.forEach(path -> {
				try {
					if (Files.isDirectory(path)) {
						deleteRecursively(path, logger);
					} else {
						Files.delete(path);
					}
				} catch (IOException e) {
					if (null != logger) {
						logger.warn("Не удалось удалить: " + path + " - " + e.getMessage());
					}
				}
			});
		}
		if (null != logger) {
			logger.debug("Очищена директория: " + dir);
		}
	}

	/**
	 * Копировать файл с перезаписью
	 */
	public static void copyFile(Path source, @NotNull Path target, @Nullable InstallerLogger logger) throws IOException {
		createDirectories(target.getParent(), logger);
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		if (null != logger) {
			logger.debug("Скопирован файл: " + source + " -> " + target);
		}
	}

	/**
	 * Копировать директорию рекурсивно
	 */
	public static void copyDirectory(Path source, Path target, @Nullable InstallerLogger logger) throws IOException {
		createDirectories(target, logger);

		try (Stream<Path> stream = Files.walk(source)) {
			stream.forEach(path -> {
				try {
					Path relative = source.relativize(path);
					Path targetPath = target.resolve(relative);

					if (Files.isDirectory(path)) {
						createDirectories(targetPath, logger);
					} else {
						copyFile(path, targetPath, logger);
					}
				} catch (IOException e) {
					throw new RuntimeException("Ошибка копирования: " + path, e);
				}
			});
		}
		if (null != logger) {
			logger.debug("Скопирована директория: " + source + " -> " + target);
		}
	}

	/**
	 * Переместить файл/директорию
	 */
	public void move(Path source, @NotNull Path target, @Nullable InstallerLogger logger) throws IOException {
		createDirectories(target.getParent(), logger);
		Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		if (null != logger) {
			logger.debug("Перемещено: " + source + " -> " + target);
		}
	}

	public static boolean existsChild(@NotNull Path parentPatch, String name) {
		return Files.exists(parentPatch.resolve(name));
	}

	public static boolean existsAnyChild(Path parentPatch, String... names) {
		return Arrays.stream(names).anyMatch(patch -> existsChild(parentPatch, patch));
	}

	/**
	 * Получить список файлов в директории
	 */
	public static Stream<Path> listFiles(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return Stream.empty();
		}
		return Files.list(dir);
	}
}
