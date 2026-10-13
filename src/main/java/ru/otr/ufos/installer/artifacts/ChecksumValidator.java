package ru.otr.ufos.installer.artifacts;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Валидатор контрольных сумм SHA-256
 */
@UtilityClass
public class ChecksumValidator {

	/**
	 * Проверить контрольные суммы файлов из файла checksums.sha256
	 *
	 * @param checksumsFile файл с контрольными суммами
	 * @param artifactsDir  директория с артефактами
	 * @return true если все суммы совпадают
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean validate(Path checksumsFile, Path artifactsDir) {
		if (!Files.exists(checksumsFile)) {
			LOGGER.warn("Файл контрольных сумм не найден: " + checksumsFile);
			// пропускаем проверку
			return true;
		}
		LOGGER.info("Проверка контрольных сумм...");
		try {
			Map<String, String> expectedChecksums = parseChecksumsFile(checksumsFile);
			MutableBoolean allValid = new MutableBoolean(true);
			expectedChecksums.forEach((fileName, expectedHash) -> {
				Path filePath = artifactsDir.resolve(fileName);
				if (!Files.exists(filePath)) {
					LOGGER.warn("Файл не найден: " + fileName);
					allValid.setFalse();
					return;
				}
				String actualHash;
				try {
					actualHash = calculateSHA256(filePath);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				if (expectedHash.equalsIgnoreCase(actualHash)) {
					LOGGER.info("  ✓ " + fileName);
				} else {
					LOGGER.error("  ✗ " + fileName + " - контрольная сумма не совпадает");
					LOGGER.error("    Ожидалось: " + expectedHash);
					LOGGER.error("    Получено:  " + actualHash);
					allValid.setFalse();
				}
			});
			return allValid.isTrue();
		} catch (Exception e) {
			LOGGER.error("При выполнении проверки контрольных сумм возникла ошибка", e);
			return false;
		}
	}

	/**
	 * Рассчитать SHA-256 хеш файла
	 */
	private static @NotNull String calculateSHA256(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream is = Files.newInputStream(file)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = is.read(buffer)) != -1) {
					digest.update(buffer, 0, read);
				}
			}
			byte[] hashBytes = digest.digest();
			return bytesToHex(hashBytes);
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 не поддерживается", e);
		}
	}

	/**
	 * Парсить файл контрольных сумм
	 * Формат: <hash>  <filename>
	 */
	private static @NotNull Map<String, String> parseChecksumsFile(Path checksumsFile) throws IOException {
		Map<String, String> checksums = new HashMap<>();
		List<String> lines = Files.readAllLines(checksumsFile);
		lines.stream()
				.map(String::trim)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.map(line -> line.split("\\s+", 2))
				.filter(parts -> parts.length == 2)
				.forEach(parts -> {
					String hash = parts[0];
					String fileName = parts[1];
					checksums.put(fileName, hash);
				});
		return checksums;
	}

	/**
	 * Конвертировать байты в hex-строку
	 */
	private static @NotNull String bytesToHex(byte @NotNull [] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
}
