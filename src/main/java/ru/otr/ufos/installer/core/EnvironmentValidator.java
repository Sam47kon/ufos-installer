package ru.otr.ufos.installer.core;

import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Проверка окружения перед выполнением сценария
 */
public class EnvironmentValidator {

	private final InstallerLogger logger;

	public EnvironmentValidator(InstallerLogger logger) {
		this.logger = logger;
	}

	public void validateJava() {
		logger.step("Проверка Java");

		int feature = Runtime.version().feature();
		String version = System.getProperty("java.version");

		if (feature < 17) {
			throw new IllegalStateException(
					"Требуется Java 17 или выше. Текущая версия: " + version
			);
		}

		logger.info("Java: " + version + " -- OK");
	}

	public void validateOs() {
		logger.step("Проверка ОС");

		String osName = System.getProperty("os.name");
		String osArch = System.getProperty("os.arch");

		logger.info("ОС: " + osName);
		logger.info("Архитектура: " + osArch);

		String lower = osName.toLowerCase();

		if (lower.contains("win") || lower.contains("linux")) {
			logger.info("ОС поддерживается");
		} else {
			logger.warn("ОС не входит в основной список поддерживаемых: " + osName);
		}
	}

	public void validateStandParentWritable(Path standDir, boolean mustExist) {
		logger.step("Проверка каталога стенда");

		if (mustExist && !Files.exists(standDir)) {
			throw new IllegalStateException("Каталог стенда не найден: " + standDir);
		}

		Path parent = standDir.getParent();

		if (parent == null) {
			throw new IllegalStateException("Не удалось определить родительский каталог для: " + standDir);
		}

		// Ищем ближайший существующий родитель
		Path current = parent;
		while (current != null && !Files.exists(current)) {
			current = current.getParent();
		}

		if (current == null) {
			throw new IllegalStateException("Не найден существующий родительский каталог для: " + standDir);
		}

		if (!Files.isWritable(current)) {
			throw new IllegalStateException("Нет прав на запись в каталог: " + current);
		}

		logger.info("Каталог доступен для работы: " + current);
	}

	public void validateDirectoryWritable(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			Files.createDirectories(dir);
		}

		if (!Files.isWritable(dir)) {
			throw new IllegalStateException("Нет прав на запись в каталог: " + dir);
		}
	}
}