package ru.otr.ufos.installer.filesystem;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Распаковщик ZIP-архивов с защитой от zip-slip атак
 */
@UtilityClass
public class ArchiveExtractor {

	/**
	 * Распаковать ZIP-архив в указанную директорию
	 *
	 * @param zipFile   путь к ZIP-файлу
	 * @param targetDir целевая директория
	 * @throws IOException при ошибке распаковки
	 */
	public static void extract(@NotNull Path zipFile, @NotNull Path targetDir) throws IOException {
		Path base = targetDir.toAbsolutePath().normalize();
		LOGGER.info("Распаковка архива: " + zipFile.getFileName() + " -> " + targetDir);
		FileManager.createDirectories(targetDir, LOGGER);

		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
			ZipEntry entry;
			int fileCount = 0;
			while ((entry = zis.getNextEntry()) != null) {
				Path entryPath = base.resolve(entry.getName()).normalize();
				// Защита от zip-slip атаки
				if (!entryPath.startsWith(base)) {
					throw new IOException("Обнаружена zip-slip атака: " + entry.getName());
				}
				if (entry.isDirectory()) {
					FileManager.createDirectories(entryPath, LOGGER);
				} else {
					// Создаём родительские директории если нужно
					FileManager.createDirectories(entryPath.getParent(), LOGGER);
					// Копируем файл
					Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
					fileCount++;
				}
				zis.closeEntry();
			}
			LOGGER.info("Распаковано файлов: " + fileCount);
		}
	}

	/**
	 * Распаковать ZIP-архив с очисткой целевой директории
	 */
	public static void extractAndClear(@NotNull Path zipFile, @NotNull Path targetDir) throws IOException {
		LOGGER.info("Очистка и распаковка: " + zipFile.getFileName() + " -> " + targetDir);
		FileManager.clearDirectory(targetDir, LOGGER);
		extract(zipFile, targetDir);
	}

	/**
	 * Распаковать только определённые директории из архива
	 */
	public static void extractDirectories(@NotNull Path zipFile, @NotNull Path targetDir, String... dirNames) throws IOException {
		Path base = targetDir.toAbsolutePath().normalize();
		LOGGER.info("Выборочная распаковка из: " + zipFile.getFileName());
		FileManager.createDirectories(targetDir, LOGGER);
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
			ZipEntry entry;
			int fileCount = 0;
			while ((entry = zis.getNextEntry()) != null) {
				String entryName = entry.getName();
				boolean shouldExtract = false;
				for (String dirName : dirNames) {
					if (entryName.startsWith(dirName + "/") || entryName.equals(dirName)) {
						shouldExtract = true;
						break;
					}
				}
				if (shouldExtract) {
					Path entryPath = base.resolve(entryName).normalize();
					if (!entryPath.startsWith(base)) {
						throw new IOException("Обнаружена zip-slip атака: " + entryName);
					}
					if (entry.isDirectory()) {
						FileManager.createDirectories(entryPath, LOGGER);
					} else {
						FileManager.createDirectories(entryPath.getParent(), LOGGER);
						Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
						fileCount++;
					}
				}
				zis.closeEntry();
			}
			LOGGER.info("Распаковано файлов: " + fileCount);
		}
	}
}
