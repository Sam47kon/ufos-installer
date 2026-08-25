package ru.otr.ufos.installer.core;

import ru.otr.ufos.installer.logging.InstallerLogger;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Контекст выполнения инсталлятора
 */
public class InstallerContext {
	private final Scenario scenario;
	private final Path standDir;
	private final Path artifactsDir;
	private final Path backupDir;
	private final Path logFile;
	private final Map<String, Object> parameters;
	private final LocalDateTime startTime;
	private final InstallerLogger logger;

	public InstallerContext(Scenario scenario, Path standDir, Path artifactsDir,
							Path backupDir, InstallerLogger logger) {
		this.scenario = scenario;
		this.standDir = standDir;
		this.artifactsDir = artifactsDir;
		this.backupDir = backupDir;
		this.parameters = new HashMap<>();
		this.startTime = LocalDateTime.now();
		this.logger = logger;

		// Создаём путь к лог-файлу
		String timestamp = startTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		this.logFile = artifactsDir.getParent().resolve("installer-" + timestamp + ".log");
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

	public Path getLogFile() {
		return logFile;
	}

	public InstallerLogger getLogger() {
		return logger;
	}

	public void setParameter(String key, Object value) {
		parameters.put(key, value);
	}

	public Object getParameter(String key) {
		return parameters.get(key);
	}

	public String getStringParameter(String key) {
		Object value = parameters.get(key);
		return value != null ? value.toString() : null;
	}

	public boolean getBooleanParameter(String key, boolean defaultValue) {
		Object value = parameters.get(key);
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if (value instanceof String) {
			return Boolean.parseBoolean((String) value);
		}
		return defaultValue;
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public Map<String, Object> getParameters() {
		return new HashMap<>(parameters);
	}
}