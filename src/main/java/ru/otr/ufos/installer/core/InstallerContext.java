package ru.otr.ufos.installer.core;

import lombok.Getter;
import lombok.Setter;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Контекст выполнения инсталлятора
 */
@Getter
@Setter
public class InstallerContext {

	private final InstallerLogger logger;
	private final LocalDateTime startTime;

	private final Map<String, String> report;
	private final List<String> changes;
	private UserPrompt userPrompt;

	// "--mode"
	private final Scenario scenario;
	// "--stand-dir"
	private Path standDir;
	// "--artifacts-dir"
	private Path artifactsDir;
	// "--backup-dir"
	private Path backupDir;
	// "--ufos-version"
	private String ufosVersion;
	// "--core-war"
	private Path coreWar;
	// "--libs-zip"
	private Path libsZip;
	// "--app-zip"
	private Path appZip;
	// "--app-version"
	private String appVersion;
	// "--jetty-template"
	private Path jettyTemplate;
	// "--init-sql"
	private Path initSql;
	// "--db-host"
	private String dbHost;
	// "--db-port"
	private String dbPort;
	// "--db-name"
	private String dbName;
	// "--db-user"
	private String dbUser;
	// "--db-password"
	private String dbPassword;
	// "--skip-db"
	private boolean skipDb;
	// "--chown-user"
	private String chownUser;
	// "--var"
	private Map<String, String> customVariables;
	// "--force"
	private boolean force;
	// "--dry-run"
	private boolean dryRun;
	// "--verbose"
	private boolean verbose;
	// "--gui"
	private boolean gui;
	// "--cli"
	private boolean cli;

	public InstallerContext(InstallerLogger logger, Scenario scenario) {
		this.logger = logger;
		this.startTime = LocalDateTime.now();
		this.report = new LinkedHashMap<>();
		this.changes = new ArrayList<>();

		this.scenario = scenario;
	}

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

		this.report = new LinkedHashMap<>();
		this.changes = new ArrayList<>();
	}

	public void addReport(String key, String value) {
		report.put(key, value);
	}

	public void addChange(String change) {
		changes.add(change);
	}
}
