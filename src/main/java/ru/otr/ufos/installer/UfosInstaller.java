package ru.otr.ufos.installer;

import picocli.CommandLine;
import ru.otr.ufos.installer.cli.CliParser;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.gui.InstallerFrame;
import ru.otr.ufos.installer.logging.InstallerLogger;
import ru.otr.ufos.installer.scenarios.InitScenario;
import ru.otr.ufos.installer.scenarios.ScenarioExecutor;
import ru.otr.ufos.installer.scenarios.UpdateCoreScenario;
import ru.otr.ufos.installer.scenarios.UpdateJettyScenario;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Точка входа инсталлятора УФОС
 */
public class UfosInstaller {

	public static void main(String[] args) {
		boolean forceGui = hasFlag(args, "--gui");
		boolean forceCli = hasFlag(args, "--cli");

		boolean headless = GraphicsEnvironment.isHeadless();

		if (forceGui && !headless) {
			launchGui();
			return;
		}

		if (!forceCli && args.length == 0 && !headless) {
			launchGui();
			return;
		}

		launchCli(args);
	}

	private static void launchGui() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
			// Используем look and feel по умолчанию
		}

		SwingUtilities.invokeLater(() -> {
			InstallerFrame frame = new InstallerFrame();
			frame.setVisible(true);
		});
	}

	private static void launchCli(String[] args) {
		CliParser cli = new CliParser();
		CommandLine commandLine = new CommandLine(cli);

		try {
			commandLine.parseArgs(args);
		} catch (CommandLine.ParameterException e) {
			System.err.println("[ERROR] " + e.getMessage());
			commandLine.usage(System.err);
			System.exit(1);
			return;
		}

		if (commandLine.isUsageHelpRequested()) {
			commandLine.usage(System.out);
			System.exit(0);
			return;
		}

		if (commandLine.isVersionHelpRequested()) {
			commandLine.printVersionHelp(System.out);
			System.exit(0);
			return;
		}

		Path logFile = createLogFile(cli.isVerbose());

		InstallerLogger logger = new InstallerLogger(logFile, cli.isVerbose());

		logger.info("==================================================");
		logger.info("UFOS Stand Installer");
		logger.info("==================================================");
		logger.info("Лог-файл: " + logFile.toAbsolutePath());

		try {
			Scenario scenario = Scenario.fromCommand(cli.getMode());

			InstallerContext context = new InstallerContext(
					scenario,
					cli.getStandDir(),
					cli.getArtifactsDir(),
					cli.getBackupDir(),
					logger
			);

			fillContextFromCli(context, cli);

			logger.info("Режим: " + scenario.getDescription());
			logger.info("Стенд: " + cli.getStandDir());
			logger.info("Артефакты: " + cli.getArtifactsDir());
			logger.info("Бэкапы: " + cli.getBackupDir());

			if (cli.isDryRun()) {
				showDryRun(context);
				System.exit(0);
				return;
			}

			ScenarioExecutor executor = createExecutor(scenario);
			executor.execute(context);

			logger.info("==================================================");
			logger.info("Работа инсталлятора завершена успешно");
			logger.info("==================================================");

			System.exit(0);

		} catch (Exception e) {
			logger.error("Работа инсталлятора завершена с ошибкой", e);
			System.exit(1);
		}
	}

	private static void fillContextFromCli(InstallerContext context, CliParser cli) {
		context.setParameter("ufos.version", cli.getUfosVersion());
		context.setParameter("app.version", cli.getAppVersion());

		context.setParameter("artifact.core.war", cli.getCoreWar());
		context.setParameter("artifact.libs.zip", cli.getLibsZip());
		context.setParameter("artifact.app.zip", cli.getAppZip());
		context.setParameter("artifact.jetty.template", cli.getJettyTemplate());
		context.setParameter("artifact.init.sql", cli.getInitSql());

		context.setParameter("db.host", cli.getDbHost());
		context.setParameter("db.port", cli.getDbPort());
		context.setParameter("db.name", cli.getDbName());
		context.setParameter("db.user", cli.getDbUser());
		context.setParameter("db.password", cli.getDbPassword());

		context.setParameter("skip.db", cli.isSkipDb());
		context.setParameter("chown.user", cli.getChownUser());
		context.setParameter("custom.vars", cli.getCustomVariables());

		context.setParameter("force", cli.isForce());
		context.setParameter("dry-run", cli.isDryRun());
		context.setParameter("verbose", cli.isVerbose());
	}

	private static ScenarioExecutor createExecutor(Scenario scenario) {
		switch (scenario) {
			case INIT:
				return new InitScenario();
			case UPDATE_CORE:
				return new UpdateCoreScenario();
			case UPDATE_JETTY:
				return new UpdateJettyScenario();
			default:
				throw new IllegalArgumentException("Неизвестный сценарий: " + scenario);
		}
	}

	private static void showDryRun(InstallerContext context) {
		InstallerLogger logger = context.getLogger();

		logger.step("[DRY-RUN] План выполнения");
		logger.info("");
		logger.info("Сценарий:   " + context.getScenario().getDescription());
		logger.info("Стенд:      " + context.getStandDir());
		logger.info("Артефакты:  " + context.getArtifactsDir());
		logger.info("Бэкапы:     " + context.getBackupDir());
		logger.info("");

		switch (context.getScenario()) {
			case INIT:
				logger.info(" 1. Проверка окружения");
				logger.info(" 2. Поиск артефактов");
				logger.info(" 3. Проверка контрольных сумм");
				logger.info(" 4. Проверка доступности БД");
				logger.info(" 5. Создание структуры стенда из шаблона Jetty");
				logger.info(" 6. Подстановка переменных в etc/");
				logger.info(" 7. Размещение WAR-файла ядра");
				logger.info(" 8. Распаковка библиотек в lib/ext/");
				logger.info(" 9. Размещение приклада в sufd.config/ и sql-migrations/");
				logger.info("10. Выполнение init SQL");
				logger.info("11. Применение прав доступа");
				logger.info("12. Формирование отчёта");
				break;

			case UPDATE_CORE:
				logger.info(" 1. Проверка окружения");
				logger.info(" 2. Поиск артефактов");
				logger.info(" 3. Проверка контрольных сумм");
				logger.info(" 4. Создание бэкапа изменяемых частей");
				logger.info(" 5. Замена WAR-файла ядра");
				logger.info(" 6. Замена lib/ext/");
				logger.info(" 7. Замена sufd.config/");
				logger.info(" 8. Замена sql-migrations/");
				logger.info(" 9. Обновление version.txt");
				logger.info("10. Восстановление прав доступа");
				logger.info("11. Формирование отчёта");
				break;

			case UPDATE_JETTY:
				logger.info(" 1. Проверка окружения");
				logger.info(" 2. Поиск артефактов");
				logger.info(" 3. Проверка контрольных сумм");
				logger.info(" 4. Создание полного бэкапа стенда");
				logger.info(" 5. Замена Jetty-части");
				logger.info(" 6. Обновление УФОС-части");
				logger.info(" 7. Формирование отчёта");
				break;

			default:
				logger.warn("Неизвестный сценарий");
		}

		logger.info("");
		logger.info("[DRY-RUN] Для реального выполнения уберите флаг --dry-run");
	}

	private static Path createLogFile(boolean verbose) {
		try {
			Path logDir = getLogDirectory();
			Files.createDirectories(logDir);

			String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			return logDir.resolve("installer-" + timestamp + ".log");

		} catch (Exception e) {
			String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			return Path.of("installer-" + timestamp + ".log").toAbsolutePath();
		}
	}

	private static Path getLogDirectory() {
		try {
			var location = UfosInstaller.class.getProtectionDomain().getCodeSource().getLocation();
			Path path = Path.of(location.toURI());

			if (Files.isRegularFile(path)) {
				return path.getParent();
			}

			if (Files.isDirectory(path)) {
				return path;
			}

		} catch (Exception ignored) {
			// fallback ниже
		}

		return Path.of(".").toAbsolutePath();
	}

	private static boolean hasFlag(String[] args, String flag) {
		return Arrays.stream(args).anyMatch(flag::equals);
	}
}