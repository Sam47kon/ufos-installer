package ru.otr.ufos.installer.core;

import ru.otr.ufos.installer.logging.InstallerLogger;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Контекст выполнения инсталлятора
 */
public class InstallerContext {

	private final Scenario scenario;
	private final Path standDir;
	private final Path artifactsDir;
	private final Path backupDir;
	private final InstallerLogger logger;
	private final LocalDateTime startTime;

	private final Map<String, Object> parameters;
	private final Map<String, String> report;
	private final List<String> changes;

	private UserPrompt userPrompt;

	public InstallerContext(Scenario scenario,
							Path standDir,
							Path artifactsDir,
							Path backupDir,
							InstallerLogger logger) {
		this.scenario = scenario;
		this.standDir = standDir;
		this.artifactsDir = artifactsDir;
		this.backupDir = backupDir;
		this.logger = logger;
		this.startTime = LocalDateTime.now();

		this.parameters = new LinkedHashMap<>();
		this.report = new LinkedHashMap<>();
		this.changes = new ArrayList<>();

		// По умолчанию консольный prompt.
		// GUI потом может подменить свой реализацией.
		this.userPrompt = question -> {
			System.out.print(question + " [y/N]: ");
			Scanner scanner = new Scanner(System.in);
			if (scanner.hasNextLine()) {
				String answer = scanner.nextLine().trim().toLowerCase();
				return answer.equals("y") || answer.equals("yes") || answer.equals("да");
			}
			return false;
		};
	}

	public Scenario getScenario() {
		return scenario;
	}

	public Path getStandDir() {
		return standDir;
	}

	public Path getArtifactsDir() {
		return artifactsDir;
	}

	public Path getBackupDir() {
		return backupDir;
	}

	public InstallerLogger getLogger() {
		return logger;
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public void setParameter(String key, Object value) {
		parameters.put(key, value);
	}

	public Object getParameter(String key) {
		return parameters.get(key);
	}

	public String getStringParameter(String key) {
		Object value = parameters.get(key);
		return value == null ? null : value.toString();
	}

	public Path getPathParameter(String key) {
		Object value = parameters.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Path path) {
			return path;
		}
		return Path.of(value.toString());
	}

	public boolean getBooleanParameter(String key, boolean defaultValue) {
		Object value = parameters.get(key);
		if (value == null) {
			return defaultValue;
		}
		if (value instanceof Boolean b) {
			return b;
		}
		return Boolean.parseBoolean(value.toString());
	}

	public void addReport(String key, String value) {
		report.put(key, value);
	}

	public Map<String, String> getReport() {
		return report;
	}

	public void addChange(String change) {
		changes.add(change);
	}

	public List<String> getChanges() {
		return changes;
	}

	public UserPrompt getUserPrompt() {
		return userPrompt;
	}

	public void setUserPrompt(UserPrompt userPrompt) {
		this.userPrompt = userPrompt;
	}
}