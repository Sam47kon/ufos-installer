package ru.otr.ufos.installer;

import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import ru.otr.ufos.installer.cli.CliParser;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.filesystem.FileManager;
import ru.otr.ufos.installer.gui.InstallerFrame;
import ru.otr.ufos.installer.logging.InstallerLogger;
import ru.otr.ufos.installer.scenarios.ScenariosUtils;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Scanner;

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
				ScenariosUtils.showDryRun(context);
				System.exit(0);
				return;
			}

			// Выполнение
			ScenariosUtils.execute(context.getScenario(), context);

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
		context.setCoreWar(cli.getCoreWar());
		context.setLibsZip(cli.getLibsZip());
		context.setAppZip(cli.getAppZip());
		context.setJettyTemplate(cli.getJettyTemplate());
		context.setInitSql(cli.getInitSql());

		context.setDbUrl(cli.getDbUrl());
		context.setDbHost(cli.getDbHost());
		context.setDbPort(cli.getDbPort());
		context.setDbName(cli.getDbName());
		context.setDbUser(cli.getDbUser());
		context.setDbPassword(cli.getDbPassword());

		context.setSkipDb(cli.isSkipDb());
		context.setNeedBackup(cli.isNeedBackup());
		context.setChownUser(cli.getChownUser());
		context.setCustomVariables(cli.getCustomVariables());

		context.setForce(cli.isForce());
		context.setDryRun(cli.isDryRun());
		context.setVerbose(cli.isVerbose());
		context.setUsePsql(cli.isUsePsql());
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
			URL location = UfosInstaller.class.getProtectionDomain().getCodeSource().getLocation();
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
