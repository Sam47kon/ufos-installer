package ru.otr.ufos.installer.filesystem;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Вспомогательные операции для архивов:
 * - распаковка во временный каталог;
 * - определение единственного корня;
 * - перенос содержимого в целевой каталог.
 */
@UtilityClass
public class ArchiveHelper {

	/**
	 * Распаковать архив в целевую папку.
	 * Если внутри архива одна корневая папка, она будет "развёрнута".
	 */
	public static void extractSmart(@NotNull Path zipFile, Path targetDir, boolean clearTarget) throws IOException {
		LOGGER.info("Распаковка архива: " + zipFile.getFileName());

		Path tempDir = Files.createTempDirectory("ufos-installer-");
		try {
			ArchiveExtractor.extract(zipFile, tempDir);
			Path effectiveRoot = detectEffectiveRoot(tempDir);

			if (clearTarget) {
				FileManager.clearDirectory(targetDir, LOGGER);
			} else {
				FileManager.createDirectories(targetDir, LOGGER);
			}

			copyContents(effectiveRoot, targetDir);
		} finally {
			FileManager.deleteRecursively(tempDir, LOGGER);
		}
	}

	/**
	 * Распаковать архив и вернуть путь к эффективному корню внутри временной папки.
	 * Вызывающий сам удаляет tempDir.
	 */
	public static Path extractToTemp(Path zipFile) throws IOException {
		Path tempDir = Files.createTempDirectory("ufos-installer-");
		ArchiveExtractor.extract(zipFile, tempDir);
		return tempDir;
	}

	public static Path detectEffectiveRoot(Path extractedDir) throws IOException {
		List<Path> children;
		try (Stream<Path> stream = FileManager.listFiles(extractedDir)) {
			children = stream.toList();
		}
		if (children.size() == 1 && Files.isDirectory(children.get(0))) {
			Path singleChild = children.get(0);
			// Если внутри единственной папки есть характерные каталоги, считаем её корнем
			if (FileManager.existsAnyChild(singleChild, "etc", "webapps", "lib", "modules", "sufd.config", "sql-migrations", "start.jar")) {
				LOGGER.debug("Архив имеет единственную корневую папку: " + singleChild.getFileName());
				return singleChild;
			}

			// Для архива библиотек: если внутри много jar, тоже разворачиваем
			try (Stream<Path> inner = FileManager.listFiles(singleChild)) {
				boolean hasJars = inner.limit(20).anyMatch(p ->
						p.getFileName().toString().toLowerCase().endsWith(".jar")
				);
				if (hasJars) {
					LOGGER.debug("Архив имеет единственную корневую папку с jar-файлами: " + singleChild.getFileName());
					return singleChild;
				}
			}
		}
		return extractedDir;
	}

	public static void copyContents(Path sourceDir, Path targetDir) throws IOException {
		FileManager.createDirectories(targetDir, LOGGER);
		try (Stream<Path> stream = Files.walk(sourceDir)) {
			List<Path> paths = stream.toList();
			for (Path sourcePath : paths) {
				if (sourcePath.equals(sourceDir)) {
					continue;
				}
				Path relative = sourceDir.relativize(sourcePath);
				Path targetPath = targetDir.resolve(relative.toString());
				if (Files.isDirectory(sourcePath)) {
					FileManager.createDirectories(targetPath, LOGGER);
				} else {
					FileManager.copyFile(sourcePath, targetPath, LOGGER);
				}
			}
		}
	}
}
