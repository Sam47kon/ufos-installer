package ru.otr.ufos.installer.scenarios;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.db.DatabaseInitializer;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static ru.otr.ufos.installer.scenarios.TestArtifactUtils.*;

/**
 * Интеграционный тест сценария 1 на реальном PostgreSQL.
 * <p>
 * Использует Testcontainers, поэтому для запуска нужен работающий Docker.
 * <p>
 * Запуск:
 * <pre>
 *   mvn test -Dtest=InitScenarioPostgresTest
 * </pre>
 * <p>
 * Если Docker недоступен, тест будет пропущен.
 * Для принудительного пропуска можно выставить переменную окружения SKIP_DOCKER_TESTS=true.
 */
@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
@Testcontainers
@EnabledIfSystemProperty(
		named = "ENABLE_DOCKER_TESTS",
		matches = "^(true)?$",
		disabledReason = "Тест InitScenarioPostgresTest (Docker tests) не включен, отсутствует флаг 'ENABLE_DOCKER_TESTS'"
)
class InitScenarioPostgresTest {

	private static final String POSTGRES_IMAGE = "postgres:15-alpine";
	private static final String DB_NAME = "ufos_test";
	private static final String DB_USER = "ufos_test";
	private static final String DB_PASSWORD = "ufos_test";

	private static final String DB_CREATE_SQL_PATH = "/db/create.sql";
	private static final String SCHEMA_UFOS = "ufos";

	@SuppressWarnings("resource")
	@Container
	private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName(DB_NAME)
			.withUsername(DB_USER)
			.withPassword(DB_PASSWORD)
			.withInitScript("db/postgres-init.sql");

	@TempDir
	Path tempDir;

	private Path artifactsDir;
	private Path standDir;

	@BeforeEach
	void setUp() throws Exception {
		InstallerLogger.init(tempDir.resolve("init-scenario-postgres.log"), true);

		artifactsDir = tempDir.resolve("artifacts");
		standDir = tempDir.resolve("STAND");

		Files.createDirectories(artifactsDir);
	}

	@Test
	@DisplayName("Сценарий 1: полная установка на реальном PostgreSQL")
	void initScenarioShouldCreateStandOnRealPostgres() throws Exception {
		InstallerContext context = createContext();
		context.setInitSql(createPostgresInitSql(artifactsDir));
		// Проверка работы SqlScriptSplitter
		context.setUsePsql(false);

		context.setCustomVariables(Map.of("CUSTOM_TEST_VAR", "custom-value"));

		new InitScenario().execute(context);

		// === 1. Проверка файловой структуры стенда ===

		assertTrue(Files.exists(standDir), "Стенд должен быть создан");
		assertTrue(Files.isDirectory(standDir), "Стенд должен быть каталогом");

		// Ядро
		assertTrue(
				Files.exists(standDir.resolve("webapps/sufd-server-1.2.3.war")),
				"WAR должен быть размещён в webapps/"
		);

		// Библиотеки
		assertTrue(
				Files.exists(standDir.resolve("lib/ext/lib.txt")),
				"Библиотеки должны быть распакованы в lib/ext/"
		);

		// Приклад
		assertTrue(
				Files.exists(standDir.resolve("sufd.config/new-app.txt")),
				"Приклад sufd.config должен быть размещён"
		);
		// Миграции из приклада должны быть скопированы в стенд
		assertTrue(
				Files.exists(standDir.resolve("sql-migrations/new.sql")),
				"SQL-миграции должны быть размещены"
		);

		// version.txt
		assertTrue(
				Files.exists(standDir.resolve("version.txt")),
				"version.txt должен быть создан"
		);
		assertEquals(
				"1.0.0-app",
				Files.readString(standDir.resolve("version.txt")).trim(),
				"version.txt должен содержать версию из архива приклада"
		);

		// === 2. Проверка подстановки переменных ===

		Path generatedConfig = standDir.resolve("etc/sufd-db.properties");
		assertTrue(Files.exists(generatedConfig), "etc/sufd-db.properties должен существовать");

		String configContent = Files.readString(generatedConfig);

		assertTrue(
				configContent.contains("db.url=" + postgres.getJdbcUrl()),
				"DB_URL должен быть подставлен"
		);
		assertTrue(
				configContent.contains("db.user=" + DB_USER),
				"DB_USER должен быть подставлен"
		);
		assertTrue(
				configContent.contains("custom=custom-value"),
				"Кастомная переменная должна быть подставлена"
		);

		// === 3. Проверка реального выполнения SQL в PostgreSQL ===

		try (
				Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				Statement statement = connection.createStatement()
		) {
			// Обычная таблица из init.sql
			try (ResultSet rs = statement.executeQuery(
					"SELECT name FROM installer_test WHERE id = 1"
			)) {
				assertTrue(rs.next(), "Запись из init SQL должна существовать");
				assertEquals("installed", rs.getString("name"));
			}

			// Функция из dollar-quoted блока (проверка SqlScriptSplitter)
			try (ResultSet rs = statement.executeQuery(
					"SELECT installer_test_concat('hello', 'world')"
			)) {
				assertTrue(rs.next(), "Функция из dollar-quoted блока должна быть создана");
				assertEquals("hello-world", rs.getString(1));
			}

			// Строковый литерал с ';' внутри (проверка SqlScriptSplitter)
			try (ResultSet rs = statement.executeQuery(
					"SELECT name FROM installer_test WHERE id = 2"
			)) {
				assertTrue(rs.next(), "Запись с ';' в строке должна существовать");
				assertEquals("value;with;semicolons", rs.getString("name"));
			}

			// Количество таблиц в схеме (проверка logTableCount)
			try (ResultSet rs = statement.executeQuery(
					"SELECT COUNT(*) FROM information_schema.tables " +
					"WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'"
			)) {
				assertTrue(rs.next());
				int count = rs.getInt(1);
				assertTrue(
						count >= 1,
						"Должна быть как минимум 1 таблица (installer_test), найдено: " + count
				);
			}
		}
	}

	/**
	 * Создаёт init SQL с типичными PostgreSQL-конструкциями:
	 * - обычные таблицы;
	 * - dollar-quoted блоки с функциями;
	 * - блочные комментарии;
	 * - строковые литералы с ';'.
	 * <p>
	 * Это реальная проверка корректности {@link ru.otr.ufos.installer.db.SqlScriptSplitter}.
	 */
	private @NotNull Path createPostgresInitSql(@NotNull Path artifactsDir) throws Exception {
		String sql = """
				-- Базовая таблица для проверки
				CREATE TABLE IF NOT EXISTS installer_test (
				    id INT PRIMARY KEY,
				    name VARCHAR(100) NOT NULL,
				    created_at TIMESTAMP DEFAULT now()
				);
				
				/*
				 * Блочный комментарий с символом ';' внутри:
				 * "some;text;with;semicolons"
				 * не должен разбивать скрипт.
				 */
				INSERT INTO installer_test (id, name)
				VALUES (1, 'installed');
				
				-- Строковый литерал с ';' внутри
				INSERT INTO installer_test (id, name)
				VALUES (2, 'value;with;semicolons');
				
				-- Dollar-quoted блок: PL/pgSQL функция
				CREATE OR REPLACE FUNCTION installer_test_concat(a TEXT, b TEXT)
				RETURNS TEXT
				LANGUAGE plpgsql
				AS $$
				BEGIN
				    -- внутри функции ';' не должен разбивать скрипт
				    RETURN a || '-' || b;
				END;
				$$;
				
				-- Dollar-quoted блок с именованным тегом
				CREATE OR REPLACE FUNCTION installer_test_version()
				RETURNS TEXT
				LANGUAGE plpgsql
				AS $func$
				BEGIN
				    RETURN '1.2.3';
				END;
				$func$;
				
				-- Вызов функции для самопроверки
				SELECT installer_test_concat('a', 'b');
				""";

		return writeFile(artifactsDir.resolve("init-postgres.sql"), sql);
	}

	@Test
	@DisplayName("Сценарий 1: init SQL с COPY через psql на реальном PostgreSQL")
	void initScenarioShouldExecuteCopyDumpViaPsql() throws Exception {
		Assumptions.assumeTrue(
				DatabaseInitializer.isPsqlAvailable(),
				"psql недоступен в PATH - тест psql-ветки пропускается"
		);

		InstallerContext context = createContext();
		context.setInitSql(createCopyBasedInitSql(artifactsDir));
		// идём через psql
		context.setUsePsql(true);

		new InitScenario().execute(context);

		// Проверяем, что COPY-блок реально пролит в БД
		try (
				Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				Statement statement = connection.createStatement()
		) {
			try (ResultSet rs = statement.executeQuery("""
							SELECT *
							FROM information_schema.tables
							WHERE table_schema = current_schema()
							  AND table_type = 'BASE TABLE'
					""")) {
				StringBuilder sb = new StringBuilder();
				while (rs.next()) {
					ResultSetMetaData md = rs.getMetaData();
					sb.append("Строка:");
					for (int i = 0; i < md.getColumnCount(); i++) {
						sb.append("\n\t").append(md.getColumnName(i + 1)).append('=').append(rs.getString(i + 1));
					}
					sb.append('\n');
				}
				assertEquals("""
								Строка:
									table_catalog=ufos_test
									table_schema=public
									table_name=test_container_ready
									table_type=BASE TABLE
									self_referencing_column_name=null
									reference_generation=null
									user_defined_type_catalog=null
									user_defined_type_schema=null
									user_defined_type_name=null
									is_insertable_into=YES
									is_typed=NO
									commit_action=null
								Строка:
									table_catalog=ufos_test
									table_schema=public
									table_name=installer_psql_test
									table_type=BASE TABLE
									self_referencing_column_name=null
									reference_generation=null
									user_defined_type_catalog=null
									user_defined_type_schema=null
									user_defined_type_name=null
									is_insertable_into=YES
									is_typed=NO
									commit_action=null
								""",
						sb.toString()
				);
			}

			try (ResultSet rs = statement.executeQuery(
					"SELECT name FROM installer_psql_test WHERE id = 1"
			)) {
				assertTrue(rs.next(), "Данные из COPY-блока должны быть загружены");
				assertEquals("installed", rs.getString("name"));
			}

			try (ResultSet rs = statement.executeQuery(
					"SELECT name FROM installer_psql_test WHERE id = 2"
			)) {
				assertTrue(rs.next(), "Строка с ';' в данных должна быть загружена корректно");
				assertEquals("value;with;semicolons", rs.getString("name"));
			}

			try (ResultSet rs = statement.executeQuery(
					"SELECT note FROM installer_psql_test WHERE id = 3"
			)) {
				assertTrue(rs.next());
				assertNull(rs.getString("note"), "\\N должен превратиться в SQL NULL");
			}
		}
	}

	/**
	 * Мини-дамп в формате pg_dump: COPY ... FROM stdin + \. + \N.
	 * Через JDBC такой скрипт невыполним - именно поэтому нужен psql.
	 */
	@SuppressWarnings("SpellCheckingInspection")
	private @NotNull Path createCopyBasedInitSql(@NotNull Path artifactsDir) throws Exception {
		String sql = """
				CREATE TABLE IF NOT EXISTS installer_psql_test (
				    id INT PRIMARY KEY,
				    name VARCHAR(100),
				    note VARCHAR(100)
				);
				
				COPY installer_psql_test (id, name, note) FROM stdin;
				1\tinstalled\tvia psql
				2\tvalue;with;semicolons\tok
				3\tnull-note\t\\N
				\\.
				""";

		return writeFile(artifactsDir.resolve("init-copy.sql"), sql);
	}

	@Test
	@DisplayName("Сценарий 1: выполнение реального боевого create.sql через psql")
	void initScenarioShouldExecuteRealCreateSqlDump() throws Exception {
		Assumptions.assumeTrue(
				DatabaseInitializer.isPsqlAvailable(),
				"psql недоступен в PATH - тест psql-ветки пропускается"
		);

		InstallerContext context = createContext();

		// Загружаем create.sql из ресурсов
		Path initSql = loadResourceToTempFile(artifactsDir.resolve("create.sql"));
		context.setInitSql(initSql);
		// Для 'COPY ... FROM stdin и \.' нужен именно psql
		context.setUsePsql(true);

		new InitScenario().execute(context);

		try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
			initScenarioShouldExecuteRealCreateSqlDumpTest(connection);
		}
	}

	@SuppressWarnings("SpellCheckingInspection")
	private void initScenarioShouldExecuteRealCreateSqlDumpTest(Connection connection) throws SQLException {
		// === 1. Начало скрипта ===
		assertTrue(schemaExists(connection), "Схема ufos должна быть создана");
		assertFunctionExists(connection, "java_guid");
		assertFunctionExists(connection, "brtr_dict_log_change_function");
		assertFunctionExists(connection, "tr_biu_fieldset_addinfo_function");
		assertFunctionExists(connection, "tr_dict_change_function");

		// === 2. Середина скрипта ===
		assertTableExists(connection, "dict_log");
		assertTableExists(connection, "pref_property");
		assertTableExists(connection, "profilecryptosettings");
		assertTableExists(connection, "routecontext");
		assertTableExists(connection, "rpl_object");

		// === 3. Конец скрипта ===
		assertIndexExists(connection, "schema_version_ir_idx");
		assertIndexExists(connection, "schema_version_s_idx");
		assertIndexExists(connection, "ix_dictcommonfields_td");
		assertIndexExists(connection, "ix_fs_dictcommonfields_dnumber");
		assertIndexExists(connection, "ix_fs_dictcommonfields_docdate");
		assertTriggerExists(connection, "routecontext", "tr_insert_routecontext");
		assertTriggerExists(connection, "task_history", "tr_insert_task_history");
		assertTriggerExists(connection, "doc", "tr_update_docstateid");
		assertTriggerExists(connection, "doc", "tr_update_doctypeid");

		// === 4. Корректная работа COPY ===
		assertCopyData(connection, "admincontext", "contextid", 1, "name", "Default");
		assertCopyData(connection, "adminservice", "adminserviceid", 14, "systemname", "link");
		assertCopyData(connection, "adminsubmenu", "adminsubmenuid", 45, "systemname", "linkType");
		assertCopyCount(connection, "adminsubmenu", 31);
		assertCopyCount(connection, "usertoorg", 10);
		assertCopyData(connection, "docstate", "docstateid", 1, "systemname", "CREATED");
		assertCopyData(connection, "cg_csp_info", "id", 1, "name", "Crypto-Pro GOST R 34.10-2001 Cryptographic Service Provider");
	}

	// region ==================== Вспомогательные методы для проверок ====================
	private Path loadResourceToTempFile(Path targetFile) throws IOException {
		try (InputStream is = getClass().getResourceAsStream(DB_CREATE_SQL_PATH)) {
			if (is == null) {
				throw new IllegalStateException("Resource not found: " + DB_CREATE_SQL_PATH);
			}
			Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return targetFile;
	}

	private boolean schemaExists(Connection conn) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT 1 FROM information_schema.schemata WHERE schema_name = ?"
		)) {
			ps.setString(1, SCHEMA_UFOS);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private void assertTableExists(Connection conn, @NotNull String tableName) throws SQLException {
		assertTrue(tableExists(conn, tableName), String.format("Таблица [%s] должна быть создана", tableName));
	}

	private boolean tableExists(Connection conn, @NotNull String tableName) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?"
		)) {
			ps.setString(1, SCHEMA_UFOS);
			ps.setString(2, tableName.toLowerCase());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private void assertFunctionExists(Connection conn, @NotNull String functionName) throws SQLException {
		assertTrue(functionExists(conn, functionName), String.format("Функция [%s] должна быть создана", functionName));
	}

	private boolean functionExists(Connection conn, @NotNull String functionName) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT 1 FROM information_schema.routines WHERE routine_schema = ? AND routine_name = ?"
		)) {
			ps.setString(1, SCHEMA_UFOS);
			ps.setString(2, functionName.toLowerCase());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private void assertIndexExists(Connection conn, @NotNull String indexName) throws SQLException {
		assertTrue(indexExists(conn, indexName), String.format("Индекс [%s] должен быть создан", indexName));
	}

	private boolean indexExists(Connection conn, @NotNull String indexName) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT 1 FROM pg_indexes WHERE schemaname = ? AND indexname = ?"
		)) {
			ps.setString(1, SCHEMA_UFOS);
			ps.setString(2, indexName.toLowerCase());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private void assertTriggerExists(Connection conn, @NotNull String tableName, @NotNull String triggerName) throws SQLException {
		assertTrue(triggerExists(conn, tableName, triggerName), String.format("Триггер [%s] должен быть создан", triggerName));
	}

	private boolean triggerExists(Connection conn, @NotNull String tableName, @NotNull String triggerName) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT 1 FROM information_schema.triggers WHERE trigger_schema = ? AND event_object_table = ? AND trigger_name = ?"
		)) {
			ps.setString(1, SCHEMA_UFOS);
			ps.setString(2, tableName.toLowerCase());
			ps.setString(3, triggerName.toLowerCase());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private void assertCopyData(Connection conn, String table, String idCol, int idVal, String valCol, String expectedVal) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT %s FROM %s.%s WHERE %s = ?".formatted(valCol, SCHEMA_UFOS, table, idCol)
		)) {
			ps.setInt(1, idVal);
			try (ResultSet rs = ps.executeQuery()) {
				assertTrue(rs.next(), "Данные из COPY для %s (id=%d) должны быть загружены".formatted(table, idVal));
				assertEquals(expectedVal, rs.getString(1), "Значение [%s] в [%s]".formatted(valCol, table));
			}
		}
	}

	private void assertCopyCount(Connection conn, String table, int expectedCount) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT COUNT(*) FROM %s.%s".formatted(SCHEMA_UFOS, table)
		)) {
			try (ResultSet rs = ps.executeQuery()) {
				assertTrue(rs.next());
				assertEquals(expectedCount, rs.getInt(1), "Количество строк в " + table + " после COPY");
			}
		}
	}
	// endregion

	private @NotNull InstallerContext createContext() throws Exception {
		Path template = createTemplate(tempDir);
		Path war = createCoreWar(artifactsDir, "1.2.3");
		Path libs = createLibsZip(artifactsDir, "1.2.3", "lib.txt", "lib");
		Path app = createAppZip(artifactsDir, "1.0.0");

		InstallerContext context = new InstallerContext(Scenario.INIT, question -> true);

		context.setStandDir(standDir);
		context.setArtifactsDir(artifactsDir);
		context.setBackupDir(tempDir.resolve("backups"));

		context.setJettyTemplate(template);
		context.setCoreWar(war);
		context.setLibsZip(libs);
		context.setAppZip(app);

		context.setDbUrl(postgres.getJdbcUrl());
		context.setDbHost(postgres.getHost());
		context.setDbPort(String.valueOf(postgres.getMappedPort(5432)));
		context.setDbName(DB_NAME);
		context.setDbUser(DB_USER);
		context.setDbPassword(DB_PASSWORD);
		return context;
	}
}
