package ru.otr.ufos.installer.report;

import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Формирование итогового отчёта
 */
public class ReportGenerator {

	private final InstallerLogger logger;

	public ReportGenerator(InstallerLogger logger) {
		this.logger = logger;
	}

	public void printReport(InstallerContext context) {
		logger.step("Итоговый отчёт");

		List<String> lines = new ArrayList<>();

		lines.add("======================================================================");
		lines.add("ОТЧЁТ О РАБОТЕ ИНСТАЛЛЯТОРА УФОС");
		lines.add("======================================================================");
		lines.add("Дата и время начала: " + context.getStartTime());
		lines.add("Дата и время конца:  " + LocalDateTime.now());
		lines.add("Сценарий:            " + context.getScenario().getDescription());
		lines.add("");

		for (Map.Entry<String, String> entry : context.getReport().entrySet()) {
			String line = String.format("%-25s: %s", entry.getKey(), entry.getValue());
			lines.add(line);
		}

		if (!context.getChanges().isEmpty()) {
			lines.add("");
			lines.add("Изменения:");
			for (String change : context.getChanges()) {
				lines.add("  - " + change);
			}
		}

		lines.add("======================================================================");

		for (String line : lines) {
			logger.info(line);
		}

		saveReportToFile(context, lines);
	}

	private void saveReportToFile(InstallerContext context, List<String> lines) {
		try {
			String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			Path reportFile = Path.of("installer-report-" + timestamp + ".txt");

			Files.write(reportFile, lines);

			logger.info("Отчёт сохранён: " + reportFile.toAbsolutePath());

		} catch (IOException e) {
			logger.warn("Не удалось сохранить файл отчёта: " + e.getMessage());
		}
	}
}