package ru.otr.ufos.installer.artifacts;

import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Валидатор контрольных сумм SHA-256
 */
public class ChecksumValidator {
	private final InstallerLogger logger;

	public ChecksumValidator(InstallerLogger logger) {
		this.logger = logger;
	}

	/**
	 * Проверить контрольные суммы файлов из файла checksums.sha256
	 *
	 * @param checksumsFile файл с контрольными суммами
	 * @param artifactsDir директория с артефактами
	 * @return true если все суммы совпадают
	 */
	public boolean validate(Path checksumsFile, Path artifactsDir) throws IOException {
		if (!Files.exists(checksumsFile)) {
			logger.warn("Файл контрольных сумм не найден: " + checksumsFile);
			return true; // пропускаем проверку
		}

		logger.info("Проверка контрольных сумм...");

		Map<String, String> expectedChecksums = parseChecksumsFile(checksumsFile);
		boolean allValid = true;

		for (Map.Entry<String, String> entry : expectedChecksums.entrySet()) {
			String fileName = entry.getKey();
			String expectedHash = entry.getValue();
			Path filePath = artifactsDir.resolve(fileName);

			if (!Files.exists(filePath)) {
				logger.warn("Файл не найден: " + fileName);
				allValid = false;
				continue;
			}

			String actualHash = calculateSHA256(filePath);

			if (expectedHash.equalsIgnoreCase(actualHash)) {
				logger.info("  ✓ " + fileName);
			} else {
				logger.error("  ✗ " + fileName + " - контрольная сумма не совпадает");
				logger.error("    Ожидалось: " + expectedHash);
				logger.error("    Получено:  " + actualHash);
				allValid = false;
			}
		}

		return allValid;
	}

	/**
	 * Рассчитать SHA-256 хеш файла
	 */
	public String calculateSHA256(Path file) throws IOException {
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
	private Map<String, String> parseChecksumsFile(Path checksumsFile) throws IOException {
		Map<String, String> checksums = new HashMap<>();
		List<String> lines = Files.readAllLines(checksumsFile);

		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			String[] parts = line.split("\\s+", 2);
			if (parts.length == 2) {
				String hash = parts[0];
				String fileName = parts[1];
				checksums.put(fileName, hash);
			}
		}

		return checksums;
	}

	/**
	 * Конвертировать байты в hex-строку
	 */
	private String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
}