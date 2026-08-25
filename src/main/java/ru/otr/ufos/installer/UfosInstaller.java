package ru.otr.ufos.installer;

import picocli.CommandLine;
import ru.otr.ufos.installer.cli.CliParser;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.gui.InstallerFrame;
import ru.otr.ufos.installer.logging.InstallerLogger;
import ru.otr.ufos.installer.scenarios.ScenarioExecutor;
import ru.otr.ufos.installer.scenarios.InitScenario;
import ru.otr.ufos.installer.scenarios.UpdateCoreScenario;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UFOS Stand Installer - Main Entry Point
 */
public class UfosInstaller {

	public static void main(String[] args) {
		// Проверяем, нужно ли запустить GUI
		boolean forceGui = false;
		for (String arg : args) {
			if ("--gui".equals(arg)) {
				forceGui = true;
				break;
			}
		}

		// Определяем режим
		if (forceGui && !GraphicsEnvironment.isHeadless()) {
			launchGui(args);
		} else if (args.length == 0 && !GraphicsEnvironment.isHeadless()) {
			// Если нет аргументов и есть GUI - запускаем GUI
			launchGui(args);
		} else {
			launchCli(args);
		}
	}

	private static void launchGui(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			// Используем default L&F
		}

		SwingUtilities.invokeLater(() -> {
			InstallerFrame frame = new InstallerFrame();
			frame.setVisible(true);
		});
	}

	private static void launchCli(String[] args) {
		// Парсим аргументы
		CliParser cli = new CliParser();
		CommandLine cmd = new CommandLine(cli);

		// Настраиваем вывод ошибок
		cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
			System.err.println("\u001B[31m[ERROR] " + ex.getMessage() + "\u001B[0m");
			return 1;
		});

		try {
			cmd.parseArgs(args);
		} catch (CommandLine.ParameterException e) {
			System.err.println("\u001B[31m[ERROR] " + e.getMessage() + "\u001B[0m");
			cmd.usage(System.err);
			System.exit(1);
		}

		// Проверяем help/version
		if (cli.isHelpRequested()) {
			cmd.usage(System.out);
			System.exit(0);
		}
		if (cli.isVersionRequested()) {
			System.out.println("UFOS Stand Installer v1.0.0");
			System.exit(0);
		}

		// Создаём логгер
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path logFile = Paths.get("installer-" + timestamp + ".log");
		InstallerLogger logger = new InstallerLogger(logFile, cli.isVerbose());

		logger.info("=== UFOS Stand Installer ===");
		logger.info("Режим: " + cli.getScenario().getDescription());
		logger.info("Стенд: " + cli.getStandDir());
		logger.info("Артефакты: " + cli.getArtifactsDir());
		logger.info("Лог-файл: " + logFile.toAbsolutePath());

		try {
			// Создаём контекст
			InstallerContext context = new InstallerContext(
					cli.getScenario(),
					cli.getStandDir(),
					cli.getArtifactsDir(),
					cli.getBackupDir(),
					logger
			);

			// Заполняем параметры
			context.setParameter("db.host", cli.getDbHost());
			context.setParameter("db.port", cli.getDbPort());
			context.setParameter("db.name", cli.getDbName());
			context.setParameter("db.user", cli.getDbUser());
			context.setParameter("db.password", cli.getDbPassword());
			context.setParameter("init.sql", cli.getInitSql());
			context.setParameter("chown.user", cli.getChownUser());
			context.setParameter("force", cli.isForce());
			context.setParameter("dry-run", cli.isDryRun());
			context.setParameter("ufos.version", cli.getUfosVersion());
			context.setParameter("artifact.core.war", cli.getCoreWar());
			context.setParameter("artifact.libs.zip", cli.getLibsZip());
			context.setParameter("artifact.app.zip", cli.getAppZip());
			context.setParameter("artifact.jetty.template", cli.getJettyTemplate());
			context.setParameter("artifact.init.sql", cli.getInitSql());
			context.setParameter("chown.user", cli.getChownUser());
			context.setParameter("force", cli.isForce());
			context.setParameter("dry-run", cli.isDryRun());
			context.setParameter("skip.db", cli.isSkipDb());

			// Dry-run режим
			if (cli.isDryRun()) {
				showDryRun(context);
				System.exit(0);
			}

			// Выполняем сценарий
			ScenarioExecutor executor = createExecutor(cli.getScenario());
			executor.execute(context);

			logger.info("=== УСПЕШНО ЗАВЕРШЕНО ===");
			System.exit(0);

		} catch (Exception e) {
			logger.error("Критическая ошибка", e);
			System.exit(1);
		}
	}

	private static ScenarioExecutor createExecutor(Scenario scenario) {
		switch (scenario) {
			case INIT:
				return new InitScenario();
			case UPDATE_CORE:
				return new UpdateCoreScenario();
			case UPDATE_JETTY:
				// TODO: реализовать
				throw new UnsupportedOperationException("Сценарий UPDATE_JETTY ещё не реализован");
			default:
				throw new IllegalArgumentException("Неизвестный сценарий: " + scenario);
		}
	}

	private static void showDryRun(InstallerContext context) {
		InstallerLogger logger = context.getLogger();
		logger.step("[DRY-RUN] План выполнения:");
		logger.info("");
		logger.info("Сценарий: " + context.getScenario().getDescription());
		logger.info("Стенд: " + context.getStandDir());
		logger.info("Артефакты: " + context.getArtifactsDir());
		logger.info("Бэкапы: " + context.getBackupDir());
		logger.info("");

		switch (context.getScenario()) {
			case INIT:
				logger.info("1. Проверка окружения (Java 17, bash)");
				logger.info("2. Проверка артефактов (war, libs, app, jetty-template)");
				logger.info("3. Проверка доступности БД");
				logger.info("4. Создание структуры каталогов стенда");
				logger.info("5. Размещение ядра (war)");
				logger.info("6. Распаковка библиотек (lib/ext/)");
				logger.info("7. Размещение приклада (sufd.config/, sql-migrations/)");
				logger.info("8. Генерация конфигураций (etc/)");
				logger.info("9. Инициализация БД (init SQL)");
				logger.info("10. Установка прав доступа");
				logger.info("11. Формирование отчёта");
				break;
			case UPDATE_CORE:
				logger.info("1. Проверка окружения");
				logger.info("2. Проверка артефактов");
				logger.info("3. Создание бэкапа изменяемых частей");
				logger.info("4. Замена ядра (webapps/)");
				logger.info("5. Замена библиотек (lib/ext/)");
				logger.info("6. Замена приклада (sufd.config/, sql-migrations/)");
				logger.info("7. Формирование отчёта");
				break;
			case UPDATE_JETTY:
				logger.info("1. Проверка окружения");
				logger.info("2. Проверка артефактов");
				logger.info("3. Создание полного бэкапа стенда");
				logger.info("4. Замена Jetty-части");
				logger.info("5. Обновление УФОС-части");
				logger.info("6. Формирование отчёта");
				break;
		}

		logger.info("");
		logger.info("[DRY-RUN] Для выполнения уберите флаг --dry-run");
	}
}