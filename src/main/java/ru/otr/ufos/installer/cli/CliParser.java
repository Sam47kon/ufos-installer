package ru.otr.ufos.installer.cli;

import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Парсер аргументов командной строки
 */
@Command(
		name = "ufos-installer",
		description = "Инструмент для развертывания и обновления стендов УФОС",
		mixinStandardHelpOptions = true,
		version = "UFOS Stand Installer 1.0.0"
)
@Getter
public class CliParser implements Runnable {

	@Option(
			names = {"--mode", "-m"},
			description = "Режим работы: init, update-core, update-jetty",
			required = true
	)
	private String mode;

	@Option(
			names = {"--stand-dir", "-s"},
			description = "Путь к каталогу стенда",
			required = true
	)
	private Path standDir;

	@Option(
			names = {"--artifacts-dir", "-a"},
			description = "Путь к каталогу с артефактами",
			defaultValue = "./artifacts"
	)
	private Path artifactsDir;

	@Option(
			names = {"--backup-dir", "-b"},
			description = "Путь к каталогу бэкапов. По умолчанию: <родитель стенда>/backups/<имя стенда>",
			defaultValue = ""
	)
	private Path backupDir;

	@Option(
			names = {"--ufos-version"},
			description = "Версия УФОС, если её нельзя определить из имени WAR"
	)
	private String ufosVersion;

	@Option(
			names = {"--core-war"},
			description = "Путь к WAR-файлу ядра. Если не указан, ищется автоматически в --artifacts-dir"
	)
	private Path coreWar;

	@Option(
			names = {"--libs-zip"},
			description = "Путь к архиву библиотек. Если не указан, ищется автоматически в --artifacts-dir"
	)
	private Path libsZip;

	@Option(
			names = {"--app-zip"},
			description = "Путь к архиву приклада. Если не указан, ищется автоматически в --artifacts-dir"
	)
	private Path appZip;

	@Option(
			names = {"--app-version"},
			description = "Версия приклада"
	)
	private String appVersion;

	@Option(
			names = {"--jetty-template"},
			description = "Путь к архиву или каталогу шаблона Jetty"
	)
	private Path jettyTemplate;

	@Option(
			names = {"--init-sql"},
			description = "Путь к init SQL файлу для первичной инициализации БД"
	)
	private Path initSql;

	@Option(
			names = {"--db-host"},
			description = "Хост PostgreSQL",
			defaultValue = "localhost"
	)
	private String dbHost;

	@Option(
			names = {"--db-port"},
			description = "Порт PostgreSQL",
			defaultValue = "5432"
	)
	private String dbPort;

	@Option(
			names = {"--db-name"},
			description = "Имя базы данных",
			defaultValue = "sufd"
	)
	private String dbName;

	@Option(
			names = {"--db-user"},
			description = "Пользователь БД",
			defaultValue = "sufd"
	)
	private String dbUser;

	@Option(
			names = {"--db-password"},
			description = "Пароль БД",
			interactive = true,
			arity = "0..1"
	)
	private String dbPassword;

	@Option(
			names = {"--skip-db"},
			description = "Пропустить проверку и инициализацию БД"
	)
	private boolean skipDb;

	@Option(
			names = {"--chown-user"},
			description = "Пользователь для chown -R после установки (только Linux)"
	)
	private String chownUser;

	@Option(
			names = {"--var"},
			paramLabel = "KEY=VALUE",
			description = "Дополнительная переменная для подстановки в конфиги, например --var=DB_HOST=localhost"
	)
	private Map<String, String> customVariables = new HashMap<>();

	@Option(
			names = {"--force", "-f"},
			description = "Разрешить перезапись существующего стенда"
	)
	private boolean force;

	@Option(
			names = {"--dry-run"},
			description = "Показать план действий без выполнения"
	)
	private boolean dryRun;

	@Option(
			names = {"--verbose", "-v"},
			description = "Подробный вывод"
	)
	private boolean verbose;

	@Option(
			names = {"--gui"},
			description = "Принудительно запустить GUI"
	)
	private boolean gui;

	@Option(
			names = {"--cli"},
			description = "Принудительно запустить CLI"
	)
	private boolean cli;

	@Override
	public void run() {
		// Класс используется только для парсинга аргументов.
	}

	public Path getBackupDir() {
		if (backupDir != null && !backupDir.toString().isBlank()) {
			return backupDir;
		}

		Path absoluteStandDir = standDir.toAbsolutePath().normalize();
		Path parent = absoluteStandDir.getParent();

		if (parent == null) {
			return Path.of("backups").resolve(absoluteStandDir.getFileName());
		}

		return parent.resolve("backups").resolve(absoluteStandDir.getFileName());
	}
}
