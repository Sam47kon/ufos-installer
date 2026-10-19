package ru.otr.ufos.installer.scenarios;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
@EnabledIfEnvironmentVariable(named = "SKIP_DOCKER_TESTS", matches = "^(?!true).*$", disabledReason = "Docker tests disabled by SKIP_DOCKER_TESTS=true")
class InitScenarioPostgresTest {

	private static final String POSTGRES_IMAGE = "postgres:15-alpine";
	private static final String DB_NAME = "ufos_test";
	private static final String DB_USER = "ufos_test";
	private static final String DB_PASSWORD = "ufos_test";

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
		Path template = createTemplate(tempDir);
		Path war = createCoreWar(artifactsDir, "1.2.3");
		Path libs = createLibsZip(artifactsDir, "1.2.3", "lib.txt", "lib");
		Path app = createAppZip(artifactsDir, "1.0.0");
		Path initSql = createPostgresInitSql(artifactsDir);

		InstallerContext context = new InstallerContext(Scenario.INIT, question -> true);

		context.setStandDir(standDir);
		context.setArtifactsDir(artifactsDir);
		context.setBackupDir(tempDir.resolve("backups"));

		context.setJettyTemplate(template);
		context.setCoreWar(war);
		context.setLibsZip(libs);
		context.setAppZip(app);
		context.setInitSql(initSql);

		context.setDbUrl(postgres.getJdbcUrl());
		context.setDbHost(postgres.getHost());
		context.setDbPort(String.valueOf(postgres.getMappedPort(5432)));
		context.setDbName(DB_NAME);
		context.setDbUser(DB_USER);
		context.setDbPassword(DB_PASSWORD);

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
				Connection connection = DriverManager.getConnection(
						postgres.getJdbcUrl(),
						postgres.getUsername(),
						postgres.getPassword()
				);
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
}
