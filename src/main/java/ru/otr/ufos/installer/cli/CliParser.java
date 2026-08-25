package ru.otr.ufos.installer.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import ru.otr.ufos.installer.core.Scenario;

import java.nio.file.Path;

/**
 * Парсер аргументов командной строки
 */
@Command(
		name = "ufos-installer",
		description = "Инструмент для развертывания и обновления стендов УФОС",
		version = "UFOS Installer 1.0.0"
)
public class CliParser implements Runnable {

	@Option(names = {"--mode", "-m"},
			description = "Режим работы: init, update-core, update-jetty",
			required = true)
	private String mode;

	@Option(names = {"--stand-dir", "-s"},
			description = "Путь к каталогу стенда",
			required = true)
	private Path standDir;

	@Option(names = {"--artifacts-dir", "-a"},
			description = "Путь к каталогу с артефактами",
			defaultValue = "./artifacts")
	private Path artifactsDir;

	@Option(names = {"--backup-dir", "-b"},
			description = "Путь к каталогу бэкапов",
			defaultValue = "")
	private Path backupDir;

	@Option(names = {"--db-host"},
			description = "Хост PostgreSQL (для init)",
			defaultValue = "localhost")
	private String dbHost;

	@Option(names = {"--db-port"},
			description = "Порт PostgreSQL (для init)",
			defaultValue = "5432")
	private String dbPort;

	@Option(names = {"--db-name"},
			description = "Имя БД (для init)",
			defaultValue = "sufd")
	private String dbName;

	@Option(names = {"--db-user"},
			description = "Пользователь БД (для init)",
			defaultValue = "sufd")
	private String dbUser;

	@Option(names = {"--db-password"},
			description = "Пароль БД (для init)",
			interactive = true,
			arity = "0..1")
	private String dbPassword;

	@Option(names = {"--init-sql"},
			description = "Путь к init SQL файлу (для init)")
	private Path initSql;

	@Option(names = {"--chown-user"},
			description = "Пользователь для chown после установки")
	private String chownUser;

	@Option(names = {"--force", "-f"},
			description = "Принудительная перезапись")
	private boolean force;

	@Option(names = {"--dry-run"},
			description = "Показать план без выполнения")
	private boolean dryRun;

	@Option(names = {"--verbose", "-v"},
			description = "Подробный вывод")
	private boolean verbose;

	@Option(names = {"--gui"},
			description = "Запустить GUI режим")
	private boolean gui;

	@Option(names = {"--help", "-h"},
			description = "Показать справку",
			usageHelp = true)
	private boolean helpRequested;

	@Option(names = {"--version", "-V"},
			description = "Показать версию",
			versionHelp = true)
	private boolean versionRequested;

	@Option(names = {"--ufos-version"},
			description = "Версия УФОС (если её нельзя определить из имени WAR)")
	private String ufosVersion;

	@Option(names = {"--core-war"},
			description = "Путь к WAR-файлу ядра")
	private Path coreWar;

	@Option(names = {"--libs-zip"},
			description = "Путь к архиву библиотек")
	private Path libsZip;

	@Option(names = {"--app-zip"},
			description = "Путь к архиву приклада")
	private Path appZip;

	@Option(names = {"--jetty-template"},
			description = "Путь к архиву или каталогу шаблона Jetty")
	private Path jettyTemplate;

	@Option(names = {"--skip-db"},
			description = "Пропустить проверку и инициализацию БД")
	private boolean skipDb;

	@Override
	public void run() {
		// Этот метод не используется, так как мы парсим аргументы вручную
	}

	public Scenario getScenario() {
		return Scenario.fromCommand(mode);
	}

	public Path getStandDir() {
		return standDir;
	}

	public Path getArtifactsDir() {
		return artifactsDir;
	}

	public Path getBackupDir() {
		if (backupDir == null || backupDir.toString().isEmpty()) {
			// По умолчанию: ../backups/STAND/
			return standDir.getParent().resolve("backups").resolve(standDir.getFileName());
		}
		return backupDir;
	}

	public String getDbHost() {
		return dbHost;
	}

	public String getDbPort() {
		return dbPort;
	}

	public String getDbName() {
		return dbName;
	}

	public String getDbUser() {
		return dbUser;
	}

	public String getDbPassword() {
		return dbPassword;
	}

	public Path getInitSql() {
		return initSql;
	}

	public String getChownUser() {
		return chownUser;
	}

	public boolean isForce() {
		return force;
	}

	public boolean isDryRun() {
		return dryRun;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public boolean isGui() {
		return gui;
	}

	public boolean isHelpRequested() {
		return helpRequested;
	}

	public boolean isVersionRequested() {
		return versionRequested;
	}

	public String getUfosVersion() {
		return ufosVersion;
	}

	public Path getCoreWar() {
		return coreWar;
	}

	public Path getLibsZip() {
		return libsZip;
	}

	public Path getAppZip() {
		return appZip;
	}

	public Path getJettyTemplate() {
		return jettyTemplate;
	}

	public boolean isSkipDb() {
		return skipDb;
	}
}