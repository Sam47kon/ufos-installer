package ru.otr.ufos.installer.scenarios;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.db.DataBaseException;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static ru.otr.ufos.installer.artifacts.ArtifactResolver.*;
import static ru.otr.ufos.installer.scenarios.TestArtifactUtils.*;

/**
 * Дополнительные тесты для сценария 1: первичная установка.
 */
class InitScenarioAdditionalTest {

	@TempDir
	Path tempDir;

	private Path artifactsDir;
	private Path standDir;

	@BeforeEach
	void setUp() throws Exception {
		InstallerLogger.init(tempDir.resolve("init-scenario.log"), true);
		artifactsDir = tempDir.resolve("artifacts");
		standDir = tempDir.resolve("STAND");
		Files.createDirectories(artifactsDir);
	}

	@Test
	@DisplayName("Сценарий 1: стенд уже существует и не пуст, --force не указан")
	void initShouldFailWhenStandNotEmptyWithoutForce() throws Exception {
		Files.createDirectories(standDir);
		writeFile(standDir.resolve("marker.txt"), "old stand");

		Path template = createTemplate(tempDir);
		Path war = createCoreWar(artifactsDir, "1.2.3");
		Path libs = createLibsZip(artifactsDir, "1.2.3", "lib.txt", "lib");
		Path app = createAppZip(artifactsDir, "1.0.0");
		Path sql = createInitSql(artifactsDir, true);

		InstallerContext context = createContext(tempDir, standDir, artifactsDir, unavailableH2Url(), true, template, war, libs, app, sql);

		IllegalStateException e = assertThrows(
				IllegalStateException.class,
				() -> new InitScenario().execute(context)
		);

		assertEquals(
				STAND_EXISTS.formatted(standDir),
				e.getMessage()
		);

		assertTrue(
				Files.exists(standDir.resolve("marker.txt")),
				"Существующий стенд не должен быть изменён, если установка упала до шага развертывания шаблона"
		);
	}

	@Test
	@DisplayName("Сценарий 1: стенд уже существует, указан --force")
	void initShouldRecreateStandWhenForceEnabled() throws Exception {
		Files.createDirectories(standDir);
		writeFile(standDir.resolve("marker.txt"), "old stand");

		Path template = createTemplate(tempDir);
		Path war = createCoreWar(artifactsDir, "1.2.3");
		Path libs = createLibsZip(artifactsDir, "1.2.3", "lib.txt", "lib");
		Path app = createAppZip(artifactsDir, "1.0.0");
		Path sql = createInitSql(artifactsDir, true);

		InstallerContext context = createContext(tempDir, standDir, artifactsDir, unavailableH2Url(), true, template, war, libs, app, sql);
		context.setForce(true);
		new InitScenario().execute(context);

		assertFalse(
				Files.exists(standDir.resolve("marker.txt")),
				"Старый стенд должен быть удалён при --force"
		);

		assertTrue(
				Files.exists(standDir.resolve("webapps/sufd-server-1.2.3.war")),
				"Новый WAR должен быть размещён"
		);

		assertTrue(
				Files.exists(standDir.resolve("lib/ext/lib.txt")),
				"Библиотеки должны быть распакованы"
		);

		assertTrue(
				Files.exists(standDir.resolve("sufd.config/new-app.txt")),
				"Приклад должен быть размещён"
		);
	}

	@Test
	@DisplayName("Сценарий 1: не найден шаблон")
	void initShouldFailWhenTemplateNotFound() throws Exception {
		Path war = createCoreWar(artifactsDir, "1.2.3");
		Path libs = createLibsZip(artifactsDir, "1.2.3", "lib.txt", "lib");
		Path app = createAppZip(artifactsDir, "1.0.0");
		Path sql = createInitSql(artifactsDir, true);

		InstallerContext context = createContext(tempDir, standDir, artifactsDir, unavailableH2Url(), true, null, war, libs, app, sql);

		IllegalStateException e = assertThrows(
				IllegalStateException.class,
				() -> new InitScenario().execute(context)
		);

		assertEquals(
				JETTY_TEMPLATE_NOT_FOUND.formatted(artifactsDir),
				e.getMessage()
		);
	}

	@Test
	@DisplayName("Сценарий 1: не найден WAR")
	void initShouldFailWhenCoreWarNotFound() throws Exception {
		Path template = createTemplate(tempDir);
		Path libs = createLibsZip(artifactsDir, "1.2.3", "lib.txt", "lib");
		Path app = createAppZip(artifactsDir, "1.0.0");
		Path sql = createInitSql(artifactsDir, true);

		InstallerContext context = createContext(tempDir, standDir, artifactsDir, unavailableH2Url(), true, template, null, libs, app, sql);

		IllegalStateException e = assertThrows(
				IllegalStateException.class,
				() -> new InitScenario().execute(context)
		);

		assertEquals(
				WAR_NOT_FOUND.formatted(artifactsDir),
				e.getMessage()
		);
	}

	@Test
	@DisplayName("Сценарий 1: не найден архив библиотек")
	void initShouldFailWhenLibsZipNotFound() throws Exception {
		Path template = createTemplate(tempDir);
		Path war = createCoreWar(artifactsDir, "1.2.3");
		Path app = createAppZip(artifactsDir, "1.0.0");
		Path sql = createInitSql(artifactsDir, true);

		InstallerContext context = createContext(tempDir, standDir, artifactsDir, unavailableH2Url(), true, template, war, null, app, sql);

		IllegalStateException e = assertThrows(
				IllegalStateException.class,
				() -> new InitScenario().execute(context)
		);

		assertEquals(
				LIB_NOT_FOUND.formatted(artifactsDir),
				e.getMessage()
		);
	}

	@Test
	@DisplayName("Сценарий 1: ошибка в init SQL")
	void initShouldFailWhenInitSqlInvalid() throws Exception {
		Path template = createTemplate(tempDir);
		Path war = createCoreWar(artifactsDir, "1.2.3");
		Path libs = createLibsZip(artifactsDir, "1.2.3", "lib.txt", "lib");
		Path app = createAppZip(artifactsDir, "1.0.0");
		Path invalidSql = createInitSql(artifactsDir, false);

		InstallerContext context = createContext(tempDir, standDir, artifactsDir, uniqueH2Url(), false, template, war, libs, app, invalidSql);

		DataBaseException e = assertThrows(
				DataBaseException.class,
				() -> new InitScenario().execute(context)
		);
		assertTrue(
				e.getMessage().contains(
						"org.h2.jdbc.JdbcSQLSyntaxErrorException: Таблица \"table_that_does_not_exist\" не найдена"
				)
		);

	}

	@Test
	@DisplayName("Сценарий 1: БД недоступна")
	void initShouldFailWhenDatabaseUnavailable() throws Exception {
		String dbName = errorDbName();
		String dbUrl = unavailableH2Url(dbName);
		Path template = createTemplate(tempDir);
		Path war = createCoreWar(artifactsDir, "1.2.3");
		Path libs = createLibsZip(artifactsDir, "1.2.3", "lib.txt", "lib");
		Path app = createAppZip(artifactsDir, "1.0.0");
		Path sql = createInitSql(artifactsDir, true);

		InstallerContext context = createContext(tempDir, standDir, artifactsDir, dbUrl, false, template, war, libs, app, sql);

		DataBaseException e = assertThrows(
				DataBaseException.class,
				() -> new InitScenario().execute(context)
		);

		assertEquals(
				String.format("Не удалось подключиться к БД: %s, пользователь: %s, ошибка: База данных \"mem:%s\" не найдена и её автоматическое создание запрещено флагом IFEXISTS=true\n" +
							  "Database \"mem:%s\" not found, and IFEXISTS=true, so we cant auto-create it [90146-240]",
						dbUrl, context.getDbUser(), dbName, dbName),
				e.getMessage()
		);
	}

	@Test
	@DisplayName("Сценарий 1: --skip-db пропускает проверку и выполнение SQL")
	void initShouldSkipDatabaseWhenSkipDbEnabled() throws Exception {
		Path template = createTemplate(tempDir);
		Path war = createCoreWar(artifactsDir, "1.2.3");
		Path libs = createLibsZip(artifactsDir, "1.2.3", "lib.txt", "lib");
		Path app = createAppZip(artifactsDir, "1.0.0");

		// SQL намеренно битый.
		// Если --skip-db работает, установка должна пройти успешно.
		Path invalidSql = createInitSql(artifactsDir, false);

		InstallerContext context = createContext(tempDir, standDir, artifactsDir, unavailableH2Url(), true, template, war, libs, app, invalidSql);

		new InitScenario().execute(context);

		assertTrue(
				Files.exists(standDir.resolve("webapps/sufd-server-1.2.3.war")),
				"WAR должен быть размещён даже при --skip-db"
		);

		assertTrue(
				Files.exists(standDir.resolve("lib/ext/lib.txt")),
				"Библиотеки должны быть распакованы даже при --skip-db"
		);

		assertTrue(
				Files.exists(standDir.resolve("sufd.config/new-app.txt")),
				"Приклад должен быть размещён даже при --skip-db"
		);
	}
}
