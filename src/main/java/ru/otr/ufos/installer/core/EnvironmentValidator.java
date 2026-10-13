package ru.otr.ufos.installer.core;

import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;

import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Проверка окружения перед выполнением сценария
 */
@UtilityClass
public class EnvironmentValidator {

	public static void validateJava() {
		LOGGER.step("Проверка Java");
		int feature = Runtime.version().feature();
		String version = System.getProperty("java.version");
		if (feature < 17) {
			throw new IllegalStateException("Требуется Java 17 или выше. Текущая версия: " + version);
		}
		LOGGER.info("Java: " + version + " -- OK");
	}

	public static void validateOs() {
		LOGGER.step("Проверка ОС");
		String osName = System.getProperty("os.name");
		String osArch = System.getProperty("os.arch");
		LOGGER.info("ОС: " + osName);
		LOGGER.info("Архитектура: " + osArch);
		String lower = osName.toLowerCase();
		if (lower.contains("win") || lower.contains("linux")) {
			LOGGER.info("ОС поддерживается");
		} else {
			LOGGER.warn("ОС не входит в основной список поддерживаемых: " + osName);
		}
	}

	public static void validateStandParentWritable(Path standDir, boolean mustExist) {
		LOGGER.step("Проверка каталога стенда");
		if (mustExist && !Files.exists(standDir)) {
			throw new IllegalStateException("Каталог стенда не найден: " + standDir);
		}
		Path parent = standDir.getParent();
		if (null == parent) {
			throw new IllegalStateException("Не удалось определить родительский каталог для: " + standDir);
		}
		// Ищем ближайший существующий родитель
		Path current = parent;
		while (current != null && !Files.exists(current)) {
			current = current.getParent();
		}
		if (null == current) {
			throw new IllegalStateException("Не найден существующий родительский каталог для: " + standDir);
		}
		if (!Files.isWritable(current)) {
			throw new IllegalStateException("Нет прав на запись в каталог: " + current);
		}
		LOGGER.info("Каталог доступен для работы: " + current);
	}
}
