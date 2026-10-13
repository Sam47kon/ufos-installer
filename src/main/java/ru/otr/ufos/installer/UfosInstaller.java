package ru.otr.ufos.installer;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import ru.otr.ufos.installer.cli.CliParser;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.filesystem.FileManager;
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
import java.util.Scanner;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

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

		// Инициализация общего логгера
		InstallerLogger.init(createLogFile(), cli.isVerbose());
		LOGGER.info("==================================================");
		LOGGER.info("UFOS Stand Installer");
		LOGGER.info("==================================================");
		LOGGER.info("Лог-файл: " + LOGGER.getLogFile().toAbsolutePath());

		try {
			Scenario scenario = Scenario.fromCommand(cli.getMode());
			InstallerContext context = new InstallerContext(scenario, cli.getStandDir(), cli.getArtifactsDir(), cli.getBackupDir(), question -> {
				System.out.print(question + " [y/N]: ");
				Scanner scanner = new Scanner(System.in);
				if (scanner.hasNextLine()) {
					String answer = scanner.nextLine().trim().toLowerCase();
					return answer.equals("y") || answer.equals("yes") || answer.equals("да");
				}
				return false;
			});

			fillContextFromCli(context, cli);

			LOGGER.info("Режим: " + scenario.getDescription());
			LOGGER.info("Стенд: " + cli.getStandDir());
			LOGGER.info("Артефакты: " + cli.getArtifactsDir());
			LOGGER.info("Бэкапы: " + cli.getBackupDir());

			if (cli.isDryRun()) {
				showDryRun(context);
				System.exit(0);
				return;
			}

			ScenarioExecutor executor = createExecutor(scenario);
			executor.execute(context);

			LOGGER.info("==================================================");
			LOGGER.info("Работа инсталлятора завершена успешно");
			LOGGER.info("==================================================");

			System.exit(0);

		} catch (Exception e) {
			LOGGER.error("Работа инсталлятора завершена с ошибкой", e);
			System.exit(1);
		}
	}

	private static void fillContextFromCli(@NotNull InstallerContext context, @NotNull CliParser cli) {
		context.setUfosVersion(cli.getUfosVersion());

		context.setCoreWar(cli.getCoreWar());
		context.setLibsZip(cli.getLibsZip());
		context.setAppZip(cli.getAppZip());
		context.setJettyTemplate(cli.getJettyTemplate());
		context.setInitSql(cli.getInitSql());

		context.setDbHost(cli.getDbHost());
		context.setDbPort(cli.getDbPort());
		context.setDbName(cli.getDbName());
		context.setDbUser(cli.getDbUser());
		context.setDbPassword(cli.getDbPassword());

		context.setSkipDb(cli.isSkipDb());
		context.setChownUser(cli.getChownUser());
		context.setCustomVariables(cli.getCustomVariables());

		context.setForce(cli.isForce());
		context.setDryRun(cli.isDryRun());
		context.setVerbose(cli.isVerbose());
	}

	@Contract("_ -> new")
	private static @NotNull ScenarioExecutor createExecutor(@NotNull Scenario scenario) {
		return switch (scenario) {
			case INIT -> new InitScenario();
			case UPDATE_CORE -> new UpdateCoreScenario();
			case UPDATE_JETTY -> new UpdateJettyScenario();
		};
	}

	private static void showDryRun(@NotNull InstallerContext context) {
		LOGGER.step("[DRY-RUN] План выполнения");
		LOGGER.info(EMPTY);
		LOGGER.info("Сценарий:   " + context.getScenario().getDescription());
		LOGGER.info("Стенд:      " + context.getStandDir());
		LOGGER.info("Артефакты:  " + context.getArtifactsDir());
		LOGGER.info("Бэкапы:     " + context.getBackupDir());
		LOGGER.info(EMPTY);

		switch (context.getScenario()) {
			case INIT:
				LOGGER.info(" 1. Проверка окружения");
				LOGGER.info(" 2. Поиск артефактов");
				LOGGER.info(" 3. Проверка контрольных сумм");
				LOGGER.info(" 4. Проверка доступности БД");
				LOGGER.info(" 5. Создание структуры стенда из шаблона Jetty");
				LOGGER.info(" 6. Подстановка переменных в etc/");
				LOGGER.info(" 7. Размещение WAR-файла ядра");
				LOGGER.info(" 8. Распаковка библиотек в lib/ext/");
				LOGGER.info(" 9. Размещение приклада в sufd.config/ и sql-migrations/");
				LOGGER.info("10. Выполнение init SQL");
				LOGGER.info("11. Применение прав доступа");
				LOGGER.info("12. Формирование отчёта");
				break;

			case UPDATE_CORE:
				LOGGER.info(" 1. Проверка окружения");
				LOGGER.info(" 2. Поиск артефактов");
				LOGGER.info(" 3. Проверка контрольных сумм");
				LOGGER.info(" 4. Создание бэкапа изменяемых частей");
				LOGGER.info(" 5. Замена WAR-файла ядра");
				LOGGER.info(" 6. Замена lib/ext/");
				LOGGER.info(" 7. Замена sufd.config/");
				LOGGER.info(" 8. Замена sql-migrations/");
				LOGGER.info(" 9. Обновление version.txt");
				LOGGER.info("10. Восстановление прав доступа");
				LOGGER.info("11. Формирование отчёта");
				break;

			case UPDATE_JETTY:
				LOGGER.info(" 1. Проверка окружения");
				LOGGER.info(" 2. Поиск артефактов");
				LOGGER.info(" 3. Проверка контрольных сумм");
				LOGGER.info(" 4. Создание полного бэкапа стенда");
				LOGGER.info(" 5. Замена Jetty-части");
				LOGGER.info(" 6. Обновление УФОС-части");
				LOGGER.info(" 7. Формирование отчёта");
				break;

			default:
				LOGGER.warn("Неизвестный сценарий");
		}

		LOGGER.info(EMPTY);
		LOGGER.info("[DRY-RUN] Для реального выполнения уберите флаг --dry-run");
	}

	private static Path createLogFile() {
		try {
			Path logDir = getLogDirectory();
			FileManager.createDirectories(logDir);

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

	private static boolean hasFlag(String[] args, @NotNull String flag) {
		return Arrays.asList(args).contains(flag);
	}
}
