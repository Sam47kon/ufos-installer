package ru.otr.ufos.installer.config;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Обработка конфигурационных файлов:
 * - замена ${VAR};
 * - замена $[VAR];
 * - поиск незаменённых переменных.
 */
@UtilityClass
public class ConfigProcessor {

	private static final Pattern UNRESOLVED_CURLY_PATTERN =
			Pattern.compile("\\$\\{[A-Za-z_][A-Za-z0-9_]*}");

	private static final Pattern UNRESOLVED_BRACKET_PATTERN =
			Pattern.compile("\\$\\[[A-Za-z_][A-Za-z0-9_]*]");

	private static final Set<String> TEXT_EXTENSIONS = Set.of(
			".properties",
			".xml",
			".txt",
			".sh",
			".bat",
			".cmd",
			".ini",
			".conf",
			".template"
	);

	private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

	public static int applyVariables(Path etcDir, Map<String, String> variables) throws IOException {
		if (!Files.exists(etcDir)) {
			LOGGER.warn("Каталог конфигов не найден: " + etcDir);
			return 0;
		}
		LOGGER.step("Подстановка переменных в конфиги: " + etcDir);

		int changedCount = 0;
		int unresolvedCount = 0;

		List<Path> files;
		try (Stream<Path> stream = Files.walk(etcDir)) {
			files = stream
					.filter(Files::isRegularFile)
					.filter(ConfigProcessor::isProbablyTextFile)
					.toList();
		}

		for (Path file : files) {
			String original = Files.readString(file);
			String updated = original;

			for (Map.Entry<String, String> entry : variables.entrySet()) {
				String key = entry.getKey();
				String value = StringUtils.isEmpty(entry.getValue()) ? EMPTY : entry.getValue();

				updated = updated.replace("${" + key + "}", value);
				updated = updated.replace("$[" + key + "]", value);
			}

			if (!updated.equals(original)) {
				Files.writeString(file, updated);
				changedCount++;
				LOGGER.info("Обновлён конфиг: " + etcDir.relativize(file));
			}

			List<String> unresolved = findUnresolvedVariables(updated);
			if (!unresolved.isEmpty()) {
				unresolvedCount++;
				LOGGER.warn("Незаменённые переменные в " + file.getFileName() + ": " + String.join(", ", unresolved));
			}
		}

		LOGGER.info("Обработано файлов: " + files.size());
		LOGGER.info("Изменено файлов: " + changedCount);
		if (unresolvedCount > 0) {
			LOGGER.warn("Файлов с незаменёнными переменными: " + unresolvedCount);
		}
		return changedCount;
	}

	private static boolean isProbablyTextFile(Path file) {
		try {
			if (Files.size(file) > MAX_FILE_SIZE) {
				return false;
			}
		} catch (IOException e) {
			return false;
		}

		String name = file.getFileName().toString().toLowerCase();
		return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
	}

	private static @NotNull List<String> findUnresolvedVariables(String content) {
		List<String> result = new java.util.ArrayList<>();

		Matcher curlyMatcher = UNRESOLVED_CURLY_PATTERN.matcher(content);
		while (curlyMatcher.find()) {
			String var = curlyMatcher.group();
			if (!result.contains(var)) {
				result.add(var);
			}
		}

		Matcher bracketMatcher = UNRESOLVED_BRACKET_PATTERN.matcher(content);
		while (bracketMatcher.find()) {
			String var = bracketMatcher.group();
			if (!result.contains(var)) {
				result.add(var);
			}
		}
		return result;
	}
}
