package ru.otr.ufos.installer.core;

import lombok.Getter;
import lombok.Setter;

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

	// region Поля
	private final LocalDateTime startTime;

	private final Map<String, String> report;
	private final List<String> changes;
	private final UserPrompt userPrompt;

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
	// "--jetty-template"
	private Path jettyTemplate;
	// "--init-sql"
	private Path initSql;
	// "--db-url"
	private String dbUrl;
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

	/**
	 * "--use-psql"
	 * Выполнять init SQL через psql (true) или через JDBC (false).
	 * По умолчанию true, так как боевой create.sql содержит {@code COPY ... FROM stdin;}
	 */
	private boolean usePsql = true;
	// endregion Поля

	public InstallerContext(Scenario scenario, UserPrompt userPrompt) {
		this.scenario = scenario;
		this.userPrompt = userPrompt;

		this.startTime = LocalDateTime.now();
		this.report = new LinkedHashMap<>();
		this.changes = new ArrayList<>();
	}

	public InstallerContext(Scenario scenario, Path standDir, Path artifactsDir, Path backupDir, UserPrompt userPrompt) {
		this.scenario = scenario;
		this.standDir = standDir;
		this.artifactsDir = artifactsDir;
		this.backupDir = backupDir;
		this.userPrompt = userPrompt;

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
