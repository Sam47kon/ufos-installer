package ru.otr.ufos.installer.scenarios;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.otr.ufos.installer.artifacts.ArtifactResolver;
import ru.otr.ufos.installer.artifacts.ChecksumValidator;
import ru.otr.ufos.installer.config.ConfigProcessor;
import ru.otr.ufos.installer.core.EnvironmentValidator;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.db.DatabaseInitializer;
import ru.otr.ufos.installer.filesystem.ArchiveHelper;
import ru.otr.ufos.installer.filesystem.FileManager;
import ru.otr.ufos.installer.filesystem.PermissionsManager;
import ru.otr.ufos.installer.report.ReportGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static ru.otr.ufos.installer.artifacts.ArtifactResolver.STAND_EXISTS;
import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Сценарий 1: первичная развёртка стенда
 */
public class InitScenario implements ScenarioExecutor {

	@Override
	public void execute(@NotNull InstallerContext context) throws Exception {
		LOGGER.info("==================================================");
		LOGGER.info("СЦЕНАРИЙ: ПЕРВИЧНАЯ УСТАНОВКА СТЕНДА");
		LOGGER.info("==================================================");

		EnvironmentValidator.validateJava();
		EnvironmentValidator.validateOs();
		EnvironmentValidator.validateStandParentWritable(context.getStandDir(), false);

		ArtifactResolver resolver = new ArtifactResolver(context.getArtifactsDir());
		// Поиск артефактов
		Path templateSource = resolver.resolveJettyTemplate(context.getJettyTemplate());
		Path coreWar = resolver.resolveCoreWar(context.getCoreWar());
		Path libsZip = resolver.resolveLibsZip(context.getLibsZip());
		@Nullable Path appZip = resolver.resolveAppZip(context.getAppZip());
		Path initSql = resolver.resolveInitSql(context.getInitSql());

		// Контрольные суммы
		Path checksumsFile = context.getArtifactsDir().resolve("checksums.sha256");
		if (!ChecksumValidator.validate(checksumsFile, context.getArtifactsDir())) {
			throw new IllegalStateException("Проверка контрольных сумм завершилась с ошибками");
		}

		// Параметры БД
		String dbUrl = context.getDbUrl();
		String dbHost = context.getDbHost();
		String dbPort = context.getDbPort();
		String dbName = context.getDbName();
		String dbUser = context.getDbUser();
		String dbPassword = context.getDbPassword();

		boolean skipDb = context.isSkipDb();
		if (!skipDb) {
			DatabaseInitializer.checkConnection(dbUrl, dbHost, dbPort, dbName, dbUser, dbPassword);
			DatabaseInitializer.ensureRequiredUsers(dbUrl, dbHost, dbPort, dbName, dbUser, dbPassword);
			DatabaseInitializer.logTableCount(dbUrl, dbHost, dbPort, dbName, dbUser, dbPassword);

			if (context.isUsePsql() && initSql != null && !DatabaseInitializer.isPsqlAvailable()) {
				throw new IllegalStateException(
						"Утилита psql не найдена в PATH. Установите PostgreSQL Client или используйте JDBC-режим (--no-use-psql). " +
						"Обратите внимание: JDBC-режим не поддерживает 'COPY ... FROM stdin'"
				);
			}
		} else {
			LOGGER.warn("Проверка БД пропущена (--skip-db)");
		}

		// Развёртывание шаблона
		Path standDir = context.getStandDir();
		boolean force = context.isForce();
		if (Files.exists(standDir)) {
			if (!Files.isDirectory(standDir)) {
				if (!force) {
					throw new IllegalStateException("Путь к стенду существует, но не является каталогом: " + standDir);
				}
				LOGGER.warn("Обнаружен файл [%s]. Будет удалён из-за --force", standDir);
				Files.delete(standDir);
			} else {
				try (Stream<Path> stream = FileManager.listFiles(standDir)) {
					boolean notEmpty = stream.findAny().isPresent();
					if (notEmpty && !force) {
						throw new IllegalStateException(STAND_EXISTS.formatted(standDir));
					}
					if (notEmpty) {
						LOGGER.warn("Каталог стенда не пуст. Будет удалён из-за --force");
						FileManager.deleteRecursively(standDir, LOGGER);
					}
				}
			}
		}

		LOGGER.step("Создание структуры стенда из шаблона");
		if (Files.isDirectory(templateSource)) {
			FileManager.copyDirectory(templateSource, standDir, LOGGER);
			context.addChange("Создана структура стенда из каталога шаблона: " + templateSource);
		} else {
			ArchiveHelper.extractSmart(templateSource, standDir, false);
			context.addChange("Создана структура стенда из архива шаблона: " + templateSource.getFileName());
		}

		// Версия ядра
		String coreVersion = resolver.extractCoreVersion(coreWar);

		// Подстановка переменных в etc
		int changedConfigs = ConfigProcessor.applyVariables(standDir.resolve("etc"), buildVariables(context, coreVersion));
		context.addChange("Обновлено конфигурационных файлов: " + changedConfigs);

		// Размещение ядра
		LOGGER.step("Размещение ядра");
		Path webappsDir = standDir.resolve("webapps");
		FileManager.createDirectories(webappsDir, LOGGER);

		Path targetWar = webappsDir.resolve(coreWar.getFileName());
		FileManager.copyFile(coreWar, targetWar, LOGGER);
		LOGGER.info("Ядро размещено: " + targetWar);
		context.addChange("Создано: " + targetWar);

		// Библиотеки
		LOGGER.step("Распаковка библиотек в lib/ext");
		Path libExtDir = standDir.resolve("lib/ext");
		ArchiveHelper.extractSmart(libsZip, libExtDir, true);
		context.addChange("Обновлено: lib/ext/");

		// Приклад
		LOGGER.step("Размещение приклада");
		ScenariosUtils.deployApp(context, appZip, standDir);

		// Init SQL
		if (!skipDb) {
			if (initSql != null) {
				DatabaseInitializer.executeInitSql(
						context.isUsePsql(), initSql, dbUrl, dbHost, dbPort, dbName, dbUser, dbPassword
				);
				context.addChange("Выполнен init SQL (" +
								  (context.isUsePsql() ? "psql" : "JDBC") + "): " + initSql.getFileName());
			} else {
				LOGGER.warn("Init SQL не найден. Инициализация БД пропущена.");
			}
		} else {
			LOGGER.warn("Выполнение init SQL пропущено (--skip-db)");
		}

		// Права доступа
		PermissionsManager permissionsManager = new PermissionsManager(context.getChownUser());
		permissionsManager.applyDefaults(standDir);
		if (permissionsManager.hasValidChownUser() && permissionsManager.changeOwnerChownUser(standDir)) {
			context.addChange("Выполнен chown -R " + permissionsManager.getChownUser() + " для каталога стенда");
		}

		// Отчёт
		context.addReport("Стенд", standDir.toAbsolutePath().toString());
		context.addReport("Версия ядра", coreVersion);
		context.addReport("WAR-файл ядра", targetWar.toAbsolutePath().toString());
		context.addReport("Архив библиотек", libsZip.getFileName().toString());
		context.addReport("Архив приклада", null == appZip ? "Установка приклада пропущена" : appZip.getFileName().toString());
		context.addReport("Шаблон", templateSource.toString());
		context.addReport("Init SQL", initSql == null ? "не выполнен" : initSql.getFileName() + (skipDb ? " (пропущено)" : " (" + (context.isUsePsql() ? "psql" : "JDBC") + ")"));
		context.addReport("БД", dbUser + "@" + dbHost + ":" + dbPort + "/" + dbName);
		ReportGenerator.printReport(context);
	}

	private @NotNull Map<String, String> buildVariables(@NotNull InstallerContext context, String coreVersion) {
		Map<String, String> variables = new HashMap<>();

		putIfNotNull(variables, "DB_URL", DatabaseInitializer.resolveJdbcUrl(context.getDbUrl(), context.getDbHost(), context.getDbPort(), context.getDbName()));
		putIfNotNull(variables, "DB_HOST", context.getDbHost());
		putIfNotNull(variables, "DB_PORT", context.getDbPort());
		putIfNotNull(variables, "DB_NAME", context.getDbName());
		putIfNotNull(variables, "DB_USER", context.getDbUser());
		putIfNotNull(variables, "DB_PASSWORD", context.getDbPassword());

		putIfNotNull(variables, "VERSION", coreVersion);
		putIfNotNull(variables, "STAND_DIR", context.getStandDir().toAbsolutePath().toString());

		if (MapUtils.isNotEmpty(context.getCustomVariables())) {
			context.getCustomVariables().forEach((key, value) -> putIfNotNull(variables, key, value));
		}
		return variables;
	}

	private void putIfNotNull(@NotNull Map<String, String> map, String key, String value) {
		map.put(key, StringUtils.isEmpty(value) ? EMPTY : value);
	}
}