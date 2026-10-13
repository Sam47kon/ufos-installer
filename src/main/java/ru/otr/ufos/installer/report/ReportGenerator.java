package ru.otr.ufos.installer.report;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.core.InstallerContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
		lines.add("Дата и время начала: " + context.getStartTime());
		lines.add("Дата и время конца:  " + LocalDateTime.now());
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
}
