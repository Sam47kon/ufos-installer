package ru.otr.ufos.installer.scenarios;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.filesystem.ArchiveHelper;
import ru.otr.ufos.installer.filesystem.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

@UtilityClass
public class ScenariosUtils {

	public static void execute(@NotNull Scenario scenario, InstallerContext context) throws Exception {
		createExecutor(scenario).execute(context);
	}

	@Contract("_ -> new")
	private static @NotNull ScenarioExecutor createExecutor(@NotNull Scenario scenario) {
		return switch (scenario) {
			case INIT -> new InitScenario();
			case UPDATE_CORE -> new UpdateCoreScenario();
		};
	}

	public static void showDryRun(@NotNull InstallerContext context) {
		LOGGER.step("[DRY-RUN] План выполнения");
		LOGGER.info(EMPTY);
		Scenario scenario = context.getScenario();
		LOGGER.info("Сценарий:   " + scenario.getDescription());
		LOGGER.info("Стенд:      " + context.getStandDir());
		LOGGER.info("Артефакты:  " + context.getArtifactsDir());
		LOGGER.info("Бэкапы:     " + context.getBackupDir());
		LOGGER.info(EMPTY);

		switch (scenario) {
			case INIT -> {
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
			}
			case UPDATE_CORE -> {
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
			}
			default -> LOGGER.warn("Неизвестный сценарий");
		}

		LOGGER.info(EMPTY);
		LOGGER.info("[DRY-RUN] Для реального выполнения уберите флаг --dry-run");
	}

	static void deployApp(@NotNull InstallerContext context,
						  @Nullable Path appZip,
						  @NotNull Path standDir) throws IOException {
		if (null == appZip) {
			LOGGER.info("Артефакт приклада не задан. Установка приклада пропущена");
			return;
		}

		Path tempDir = ArchiveHelper.extractToTemp(appZip);
		try {
			Path appRoot = ArchiveHelper.detectEffectiveRoot(tempDir);

			Path srcConfig = appRoot.resolve("sufd.config");
			Path srcMigrations = appRoot.resolve("sql-migrations");

			Path targetConfig = standDir.resolve("sufd.config");
			Path targetMigrations = standDir.resolve("sql-migrations");

			if (Files.exists(srcConfig)) {
				FileManager.clearDirectory(targetConfig, LOGGER);
				ArchiveHelper.copyContents(srcConfig, targetConfig);
				LOGGER.info("Обновлено: sufd.config/");
				context.addChange("Обновлено: sufd.config/");
			} else {
				LOGGER.warn("В архиве приклада не найдена папка sufd.config");
			}

			if (Files.exists(srcMigrations)) {
				FileManager.clearDirectory(targetMigrations, LOGGER);
				ArchiveHelper.copyContents(srcMigrations, targetMigrations);
				LOGGER.info("Обновлено: sql-migrations/");
				context.addChange("Обновлено: sql-migrations/");
			} else {
				LOGGER.warn("В архиве приклада не найдена папка sql-migrations");
			}

			// version.txt
			Path versionFile = findVersionFile(appRoot);
			if (versionFile != null) {
				Path targetVersion = standDir.resolve("version.txt");
				FileManager.copyFile(versionFile, targetVersion, LOGGER);
				LOGGER.info("Обновлено: version.txt");
				context.addChange("Обновлено: version.txt");
			} else {
				LOGGER.warn("version.txt не найден в архиве приклада");
			}

		} finally {
			FileManager.deleteRecursively(tempDir, LOGGER);
		}
	}

	private static @Nullable Path findVersionFile(@NotNull Path appRoot) {
		Path direct = appRoot.resolve("version.txt");
		if (Files.exists(direct)) {
			return direct;
		}

		Path inConfig = appRoot.resolve("sufd.config/version.txt");
		if (Files.exists(inConfig)) {
			return inConfig;
		}

		Path funcVersion = appRoot.resolve("sufd.config/func.version");
		if (Files.exists(funcVersion)) {
			return funcVersion;
		}

		return null;
	}
}
