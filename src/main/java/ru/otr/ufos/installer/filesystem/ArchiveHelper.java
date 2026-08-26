package ru.otr.ufos.installer.filesystem;

import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Вспомогательные операции для архивов:
 * - распаковка во временный каталог;
 * - определение единственного корня;
 * - перенос содержимого в целевой каталог.
 */
public class ArchiveHelper {

	private final InstallerLogger logger;
	private final ArchiveExtractor extractor;
	private final FileManager fileManager;

	public ArchiveHelper(InstallerLogger logger) {
		this.logger = logger;
		this.extractor = new ArchiveExtractor(logger);
		this.fileManager = new FileManager(logger);
	}

	/**
	 * Распаковать архив в целевую папку.
	 * Если внутри архива одна корневая папка, она будет "развёрнута".
	 */
	public void extractSmart(@NotNull Path zipFile, Path targetDir, boolean clearTarget) throws IOException {
		logger.info("Распаковка архива: " + zipFile.getFileName());

		Path tempDir = Files.createTempDirectory("ufos-installer-");

		try {
			extractor.extract(zipFile, tempDir);
			Path effectiveRoot = detectEffectiveRoot(tempDir);

			if (clearTarget) {
				fileManager.clearDirectory(targetDir);
			} else {
				fileManager.createDirectories(targetDir);
			}

			copyContents(effectiveRoot, targetDir);
		} finally {
			fileManager.deleteRecursively(tempDir);
		}
	}

	/**
	 * Распаковать архив и вернуть путь к эффективному корню внутри временной папки.
	 * Вызывающий сам удаляет tempDir.
	 */
	public Path extractToTemp(Path zipFile) throws IOException {
		Path tempDir = Files.createTempDirectory("ufos-installer-");
		extractor.extract(zipFile, tempDir);
		return tempDir;
	}

	public Path detectEffectiveRoot(Path extractedDir) throws IOException {
		List<Path> children;

		try (Stream<Path> stream = Files.list(extractedDir)) {
			children = stream.toList();
		}

		if (children.size() == 1 && Files.isDirectory(children.get(0))) {
			Path singleChild = children.get(0);

			// Если внутри единственной папки есть характерные каталоги, считаем её корнем
			if (Files.exists(singleChild.resolve("etc"))
					|| Files.exists(singleChild.resolve("webapps"))
					|| Files.exists(singleChild.resolve("lib"))
					|| Files.exists(singleChild.resolve("modules"))
					|| Files.exists(singleChild.resolve("sufd.config"))
					|| Files.exists(singleChild.resolve("sql-migrations"))
					|| Files.exists(singleChild.resolve("start.jar"))) {
				logger.debug("Архив имеет единственную корневую папку: " + singleChild.getFileName());
				return singleChild;
			}

			// Для архива библиотек: если внутри много jar, тоже разворачиваем
			try (Stream<Path> inner = Files.list(singleChild)) {
				boolean hasJars = inner.limit(20).anyMatch(p ->
						p.getFileName().toString().toLowerCase().endsWith(".jar")
				);

				if (hasJars) {
					logger.debug("Архив имеет единственную корневую папку с jar-файлами: " + singleChild.getFileName());
					return singleChild;
				}
			}
		}

		return extractedDir;
	}

	public void copyContents(Path sourceDir, Path targetDir) throws IOException {
		fileManager.createDirectories(targetDir);

		try (Stream<Path> stream = Files.walk(sourceDir)) {
			List<Path> paths = stream.toList();

			for (Path sourcePath : paths) {
				if (sourcePath.equals(sourceDir)) {
					continue;
				}

				Path relative = sourceDir.relativize(sourcePath);
				Path targetPath = targetDir.resolve(relative.toString());

				if (Files.isDirectory(sourcePath)) {
					fileManager.createDirectories(targetPath);
				} else {
					fileManager.copyFile(sourcePath, targetPath);
				}
			}
		}
	}
}
