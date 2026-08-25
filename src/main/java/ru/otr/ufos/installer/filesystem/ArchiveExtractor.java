package ru.otr.ufos.installer.filesystem;

import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Распаковщик ZIP-архивов с защитой от zip-slip атак
 */
public class ArchiveExtractor {
	private final InstallerLogger logger;
	private final FileManager fileManager;

	public ArchiveExtractor(InstallerLogger logger) {
		this.logger = logger;
		this.fileManager = new FileManager(logger);
	}

	/**
	 * Распаковать ZIP-архив в указанную директорию
	 *
	 * @param zipFile путь к ZIP-файлу
	 * @param targetDir целевая директория
	 * @throws IOException при ошибке распаковки
	 */
	public void extract(Path zipFile, Path targetDir) throws IOException {
		logger.info("Распаковка архива: " + zipFile.getFileName() + " -> " + targetDir);

		fileManager.createDirectories(targetDir);

		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
			ZipEntry entry;
			int fileCount = 0;

			while ((entry = zis.getNextEntry()) != null) {
				Path entryPath = targetDir.resolve(entry.getName()).normalize();

				// Защита от zip-slip атаки
				if (!entryPath.startsWith(targetDir)) {
					throw new IOException("Обнаружена zip-slip атака: " + entry.getName());
				}

				if (entry.isDirectory()) {
					fileManager.createDirectories(entryPath);
				} else {
					// Создаём родительские директории если нужно
					fileManager.createDirectories(entryPath.getParent());

					// Копируем файл
					Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
					fileCount++;
				}

				zis.closeEntry();
			}

			logger.info("Распаковано файлов: " + fileCount);
		}
	}

	/**
	 * Распаковать ZIP-архив с очисткой целевой директории
	 */
	public void extractAndClear(Path zipFile, Path targetDir) throws IOException {
		logger.info("Очистка и распаковка: " + zipFile.getFileName() + " -> " + targetDir);
		fileManager.clearDirectory(targetDir);
		extract(zipFile, targetDir);
	}

	/**
	 * Распаковать только определённые директории из архива
	 */
	public void extractDirectories(Path zipFile, Path targetDir, String... dirNames) throws IOException {
		logger.info("Выборочная распаковка из: " + zipFile.getFileName());

		fileManager.createDirectories(targetDir);

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
					Path entryPath = targetDir.resolve(entryName).normalize();

					if (!entryPath.startsWith(targetDir)) {
						throw new IOException("Обнаружена zip-slip атака: " + entryName);
					}

					if (entry.isDirectory()) {
						fileManager.createDirectories(entryPath);
					} else {
						fileManager.createDirectories(entryPath.getParent());
						Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
						fileCount++;
					}
				}

				zis.closeEntry();
			}

			logger.info("Распаковано файлов: " + fileCount);
		}
	}
}