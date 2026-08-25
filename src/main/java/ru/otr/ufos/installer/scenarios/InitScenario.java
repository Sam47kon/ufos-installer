package ru.otr.ufos.installer.scenarios;

import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.artifacts.ArtifactResolver;
import ru.otr.ufos.installer.artifacts.ChecksumValidator;
import ru.otr.ufos.installer.config.ConfigProcessor;
import ru.otr.ufos.installer.core.EnvironmentValidator;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.db.DatabaseInitializer;
import ru.otr.ufos.installer.filesystem.ArchiveHelper;
import ru.otr.ufos.installer.filesystem.FileManager;
import ru.otr.ufos.installer.filesystem.PermissionsManager;
import ru.otr.ufos.installer.logging.InstallerLogger;
import ru.otr.ufos.installer.report.ReportGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static ru.otr.ufos.installer.scenarios.ScenariosUtils.findVersionFile;

/**
 * Сценарий 1: первичная развёртка стенда
 */
public class InitScenario implements ScenarioExecutor {

	@Override
	public void execute(@NotNull InstallerContext context) throws Exception {
		InstallerLogger logger = context.getLogger();

		logger.info("==================================================");
		logger.info("СЦЕНАРИЙ: ПЕРВИЧНАЯ УСТАНОВКА СТЕНДА");
		logger.info("==================================================");

		EnvironmentValidator environmentValidator = new EnvironmentValidator(logger);
		environmentValidator.validateJava();
		environmentValidator.validateOs();
		environmentValidator.validateStandParentWritable(context.getStandDir(), false);

		ArtifactResolver resolver =
				new ArtifactResolver(logger, context.getArtifactsDir());

		// Поиск артефактов
		Path templateSource = resolver.resolveJettyTemplate(
				context.getPathParameter("artifact.jetty.template")
		);

		Path coreWar = resolver.resolveCoreWar(
				context.getPathParameter("artifact.core.war")
		);

		Path libsZip = resolver.resolveLibsZip(
				context.getPathParameter("artifact.libs.zip")
		);

		Path appZip = resolver.resolveAppZip(
				context.getPathParameter("artifact.app.zip")
		);

		Path initSql = resolver.resolveInitSql(
				context.getPathParameter("artifact.init.sql")
		);

		// Контрольные суммы
		ChecksumValidator checksumValidator = new ChecksumValidator(logger);
		Path checksumsFile = context.getArtifactsDir().resolve("checksums.sha256");

		if (!checksumValidator.validate(checksumsFile, context.getArtifactsDir())) {
			throw new IllegalStateException("Проверка контрольных сумм завершилась с ошибками");
		}

		// Параметры БД
		String dbHost = context.getStringParameter("db.host");
		String dbPort = context.getStringParameter("db.port");
		String dbName = context.getStringParameter("db.name");
		String dbUser = context.getStringParameter("db.user");
		String dbPassword = context.getStringParameter("db.password");

		boolean skipDb = context.getBooleanParameter("skip.db", false);

		DatabaseInitializer databaseInitializer = new DatabaseInitializer(logger);

		if (!skipDb) {
			databaseInitializer.checkConnection(dbHost, dbPort, dbName, dbUser, dbPassword);
			databaseInitializer.logTableCount(dbHost, dbPort, dbName, dbUser, dbPassword);
		} else {
			logger.warn("Проверка БД пропущена (--skip-db)");
		}

		// Развёртывание шаблона
		FileManager fileManager = new FileManager(logger);
		ArchiveHelper archiveHelper = new ArchiveHelper(logger);

		Path standDir = context.getStandDir();

		boolean force = context.getBooleanParameter("force", false);

		if (Files.exists(standDir)) {
			try (var stream = Files.list(standDir)) {
				boolean notEmpty = stream.findAny().isPresent();

				if (notEmpty && !force) {
					throw new IllegalStateException(
							"Каталог стенда уже существует и не пуст: " + standDir +
									". Используйте --force для перезаписи."
					);
				}

				if (notEmpty) {
					logger.warn("Каталог стенда не пуст. Будет удалён из-за --force");
					fileManager.deleteRecursively(standDir);
				}
			}
		}

		logger.step("Создание структуры стенда из шаблона");

		if (Files.isDirectory(templateSource)) {
			fileManager.copyDirectory(templateSource, standDir);
			context.addChange("Создана структура стенда из каталога шаблона: " + templateSource);
		} else {
			archiveHelper.extractSmart(templateSource, standDir, false);
			context.addChange("Создана структура стенда из архива шаблона: " + templateSource.getFileName());
		}

		// Версия ядра
		String ufosVersion = context.getStringParameter("ufos.version");
		String coreVersion = resolver.extractCoreVersion(coreWar, ufosVersion);

		// Подстановка переменных в etc
		Map<String, String> variables = buildVariables(context, coreVersion);
		ConfigProcessor configProcessor = new ConfigProcessor(logger);
		int changedConfigs = configProcessor.applyVariables(standDir.resolve("etc"), variables);

		context.addChange("Обновлено конфигурационных файлов: " + changedConfigs);

		// Размещение ядра
		logger.step("Размещение ядра");

		Path webappsDir = standDir.resolve("webapps");
		fileManager.createDirectories(webappsDir);

		Path targetWar = webappsDir.resolve(coreWar.getFileName());
		fileManager.copyFile(coreWar, targetWar);

		logger.info("Ядро размещено: " + targetWar);
		context.addChange("Создано: " + targetWar);

		// Библиотеки
		logger.step("Распаковка библиотек в lib/ext");

		Path libExtDir = standDir.resolve("lib/ext");
		archiveHelper.extractSmart(libsZip, libExtDir, true);

		context.addChange("Обновлено: lib/ext/");

		// Приклад
		logger.step("Размещение приклада");

		deployApp(context, appZip, standDir, archiveHelper, fileManager);

		// Init SQL
		if (!skipDb) {
			if (initSql != null) {
				databaseInitializer.executeSqlFile(
						initSql,
						dbHost,
						dbPort,
						dbName,
						dbUser,
						dbPassword
				);

				context.addChange("Выполнен init SQL: " + initSql.getFileName());
			} else {
				logger.warn("Init SQL не найден. Инициализация БД пропущена.");
			}
		} else {
			logger.warn("Выполнение init SQL пропущено (--skip-db)");
		}

		// Права доступа
		PermissionsManager permissionsManager = new PermissionsManager(logger);
		permissionsManager.applyDefaults(standDir);

		String chownUser = context.getStringParameter("chown.user");

		if (chownUser != null && !chownUser.isBlank()) {
			permissionsManager.changeOwner(standDir, chownUser);
			context.addChange("Выполнен chown -R " + chownUser + " для каталога стенда");
		}

		// Отчёт
		context.addReport("Стенд", standDir.toAbsolutePath().toString());
		context.addReport("Версия ядра", coreVersion);
		context.addReport("WAR-файл ядра", targetWar.toAbsolutePath().toString());
		context.addReport("Архив библиотек", libsZip.getFileName().toString());
		context.addReport("Архив приклада", appZip.getFileName().toString());
		context.addReport("Шаблон", templateSource.toString());
		context.addReport("Init SQL", initSql == null ? "не выполнен" : initSql.getFileName().toString());
		context.addReport("БД", dbUser + "@" + dbHost + ":" + dbPort + "/" + dbName);

		ReportGenerator reportGenerator = new ReportGenerator(logger);
		reportGenerator.printReport(context);
	}

	private void deployApp(@NotNull InstallerContext context,
						   Path appZip,
						   @NotNull Path standDir,
						   @NotNull ArchiveHelper archiveHelper,
						   FileManager fileManager) throws IOException {

		InstallerLogger logger = context.getLogger();

		Path tempDir = archiveHelper.extractToTemp(appZip);

		try {
			Path appRoot = archiveHelper.detectEffectiveRoot(tempDir);

			Path srcConfig = appRoot.resolve("sufd.config");
			Path srcMigrations = appRoot.resolve("sql-migrations");

			Path targetConfig = standDir.resolve("sufd.config");
			Path targetMigrations = standDir.resolve("sql-migrations");

			if (Files.exists(srcConfig)) {
				archiveHelper.copyContents(srcConfig, targetConfig);
				logger.info("Размещён приклад: sufd.config/");
				context.addChange("Обновлено: sufd.config/");
			} else {
				logger.warn("В архиве приклада не найдена папка sufd.config");
			}

			if (Files.exists(srcMigrations)) {
				archiveHelper.copyContents(srcMigrations, targetMigrations);
				logger.info("Размещены миграции: sql-migrations/");
				context.addChange("Обновлено: sql-migrations/");
			} else {
				logger.warn("В архиве приклада не найдена папка sql-migrations");
			}

			// version.txt
			Path versionFile = findVersionFile(appRoot);

			if (versionFile != null) {
				Path targetVersion = standDir.resolve("version.txt");
				fileManager.copyFile(versionFile, targetVersion);
				logger.info("Размещён файл версии: version.txt");
				context.addChange("Создано/обновлено: version.txt");
			} else {
				logger.warn("version.txt не найден в архиве приклада");
			}

		} finally {
			fileManager.deleteRecursively(tempDir);
		}
	}

	private @NotNull Map<String, String> buildVariables(@NotNull InstallerContext context, String coreVersion) {
		Map<String, String> variables = new HashMap<>();

		putIfNotNull(variables, "DB_HOST", context.getStringParameter("db.host"));
		putIfNotNull(variables, "DB_PORT", context.getStringParameter("db.port"));
		putIfNotNull(variables, "DB_NAME", context.getStringParameter("db.name"));
		putIfNotNull(variables, "DB_USER", context.getStringParameter("db.user"));

		// Пароль тоже подставляем, если нужно.
		// Если не хочется хранить его в конфигах, можно потом заменить на маску.
		putIfNotNull(variables, "DB_PASSWORD", context.getStringParameter("db.password"));

		putIfNotNull(variables, "VERSION", coreVersion);
		putIfNotNull(variables, "STAND_DIR", context.getStandDir().toAbsolutePath().toString());

		return variables;
	}

	private void putIfNotNull(@NotNull Map<String, String> map, String key, String value) {
		map.put(key, value == null ? "" : value);
	}
}
