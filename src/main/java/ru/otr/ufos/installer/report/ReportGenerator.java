package ru.otr.ufos.installer.report;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.core.InstallerContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Формирование итогового отчёта
 */
@UtilityClass
public class ReportGenerator {

	public static void printReport(@NotNull InstallerContext context) {
		LOGGER.step("Итоговый отчёт");
		List<String> lines = new ArrayList<>();
		lines.add("======================================================================");
		lines.add("ОТЧЁТ О РАБОТЕ ИНСТАЛЛЯТОРА УФОС");
		lines.add("======================================================================");
		LocalDateTime endTime = LocalDateTime.now();
		lines.add("Дата и время начала: " + context.getStartTime());
		lines.add("Дата и время конца:  " + endTime);
		lines.add("Время выполнения:    " + formatDuration(context.getStartTime(), endTime));
		lines.add("Сценарий:            " + context.getScenario().getDescription());
		lines.add(EMPTY);
		for (Map.Entry<String, String> entry : context.getReport().entrySet()) {
			String line = String.format("%-25s: %s", entry.getKey(), entry.getValue());
			lines.add(line);
		}
		if (!context.getChanges().isEmpty()) {
			lines.add(EMPTY);
			lines.add("Изменения:");
			for (String change : context.getChanges()) {
				lines.add("  - " + change);
			}
		}
		lines.add("======================================================================");
		for (String line : lines) {
			LOGGER.info(line);
		}
		saveReportToFile(lines);
	}

	private static void saveReportToFile(List<String> lines) {
		try {
			String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			Path reportFile = Path.of("installer-report-" + timestamp + ".txt");
			Files.write(reportFile, lines);
			LOGGER.info("Отчёт сохранён: " + reportFile.toAbsolutePath());
		} catch (IOException e) {
			LOGGER.warn("Не удалось сохранить файл отчёта: " + e.getMessage());
		}
	}

	private static @NotNull String formatDuration(LocalDateTime start, LocalDateTime end) {
		Duration duration = Duration.between(start, end);

		long totalMillis = duration.toMillis();
		long totalSeconds = duration.toSeconds();
		long totalMinutes = duration.toMinutes();

		// 1. Меньше 1 секунды -> только миллисекунды (мс)
		if (totalSeconds < 1) {
			return totalMillis + " мс";
		}

		// Получаем части для вывода
		int millisPart = duration.toMillisPart();
		int secsPart = duration.toSecondsPart();
		int minsPart = duration.toMinutesPart();
		long hours = duration.toHours(); // Часы берем целиком, они могут быть > 24

		// 2. Меньше 1 минуты -> сек + мс
		if (totalMinutes < 1) {
			return String.format("%d сек %d мс", secsPart, millisPart);
		}
		// 3. Меньше 1 часа -> мин + сек + мс
		if (hours < 1) {
			return String.format("%d мин %d сек %d мс", minsPart, secsPart, millisPart);
		}
		// 4. Более 1 часа -> часы + мин + сек + мс
		return String.format("%d ч %d мин %d сек %d мс", hours, minsPart, secsPart, millisPart);
	}
}
