package ru.otr.ufos.installer.scenarios;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitScenarioH2Test {

	@TempDir
	Path tempDir;

	private Path artifactsDir;
	private Path standDir;
	private Path templateDir;

	private Path coreWar;
	private Path libsZip;
	private Path appZip;
	private Path initSql;

	private String dbUrl;

	@BeforeEach
	void setUp() throws Exception {
		InstallerLogger.init(tempDir.resolve("installer-test.log"), true);

		artifactsDir = tempDir.resolve("artifacts");
		standDir = tempDir.resolve("STAND");
		templateDir = tempDir.resolve("template");

		Files.createDirectories(artifactsDir);
		Files.createDirectories(templateDir.resolve("etc"));

		createTemplate();
		createCoreWar();
		createLibsZip();
		createAppZip();
		createInitSql();

		String dbName = "ufos_test_" + UUID.randomUUID().toString().replace("-", "").toLowerCase();
		dbUrl = "jdbc:h2:mem:" + dbName
				+ ";MODE=PostgreSQL"
				+ ";DATABASE_TO_LOWER=TRUE"
				+ ";CASE_INSENSITIVE_IDENTIFIERS=TRUE"
				+ ";DB_CLOSE_DELAY=-1";
	}

	@Test
	void initScenarioShouldCreateStandAndInitializeDatabase() throws Exception {
		InstallerContext context = createContext();
		new InitScenario().execute(context);

		// 1. Проверка структуры стенда
		assertTrue(Files.exists(standDir));
		assertTrue(Files.isDirectory(standDir));

		// 2. Проверка ядра
		assertTrue(Files.exists(standDir.resolve("webapps/sufd-server-1.2.3.war")));

		// 3. Проверка библиотек
		assertTrue(Files.exists(standDir.resolve("lib/ext/demo-lib.txt")));

		// 4. Проверка приклада
		assertTrue(Files.exists(standDir.resolve("sufd.config/app.txt")));
		assertTrue(Files.exists(standDir.resolve("sql-migrations/V1__test.sql")));

		// 5. Проверка version.txt
		assertTrue(Files.exists(standDir.resolve("version.txt")));
		assertEquals("1.0.0-test", Files.readString(standDir.resolve("version.txt")).trim());

		// 6. Проверка подстановки переменных
		Path generatedConfig = standDir.resolve("etc/sufd-db.properties");
		assertTrue(Files.exists(generatedConfig));

		String configContent = Files.readString(generatedConfig);

		assertTrue(configContent.contains("db.url=" + dbUrl));
		assertTrue(configContent.contains("db.user=sa"));
		assertTrue(configContent.contains("custom=custom-value"));

		// 7. Проверка, что init SQL реально выполнен в H2
		//noinspection SqlNoDataSourceInspection,SqlResolve
		try (
				Connection connection = DriverManager.getConnection(dbUrl, "sa", "");
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT name FROM installer_test WHERE id = 1")
		) {
			assertTrue(rs.next());
			assertEquals("installed", rs.getString("name"));
		}
	}

	private @NotNull InstallerContext createContext() {
		InstallerContext context = new InstallerContext(Scenario.INIT, question -> true);

		context.setStandDir(standDir);
		context.setArtifactsDir(artifactsDir);
		context.setBackupDir(tempDir.resolve("backups"));

		context.setJettyTemplate(templateDir);
		context.setCoreWar(coreWar);
		context.setLibsZip(libsZip);
		context.setAppZip(appZip);
		context.setInitSql(initSql);

		context.setDbUrl(dbUrl);
		context.setDbUser("sa");
		context.setDbPassword("");

		context.setCustomVariables(Map.of(
				"CUSTOM_TEST_VAR", "custom-value"
		));
		return context;
	}

	private void createTemplate() throws IOException {
		String configTemplate = """
				db.url=${DB_URL}
				db.user=${DB_USER}
				custom=${CUSTOM_TEST_VAR}
				""";

		Files.writeString(
				templateDir.resolve("etc/sufd-db.properties"),
				configTemplate,
				StandardCharsets.UTF_8
		);
	}

	private void createCoreWar() throws IOException {
		coreWar = artifactsDir.resolve("sufd-server-1.2.3.war");
		Files.writeString(coreWar, "fake-war-content");
	}

	private void createLibsZip() throws IOException {
		libsZip = artifactsDir.resolve("sufd-server-libs-1.2.3.zip");
		createZip(
				libsZip,
				Map.of(
						"demo-lib.txt", "demo library file"
				)
		);
	}

	private void createAppZip() throws IOException {
		appZip = artifactsDir.resolve("func-test.zip");

		createZip(
				appZip,
				Map.of(
						"sufd.config/app.txt", "app content",
						"sql-migrations/V1__test.sql", "-- test migration",
						"version.txt", "1.0.0-test"
				)
		);
	}

	private void createInitSql() throws IOException {
		initSql = artifactsDir.resolve("init.sql");

		String sql = """
				CREATE TABLE IF NOT EXISTS installer_test (
				    id INT PRIMARY KEY,
				    name VARCHAR(100)
				);
				
				INSERT INTO installer_test (id, name)
				VALUES (1, 'installed');
				""";

		Files.writeString(initSql, sql, StandardCharsets.UTF_8);
	}

	private void createZip(Path zipFile, @NotNull Map<String, String> entries) throws IOException {
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
			for (Map.Entry<String, String> entry : entries.entrySet()) {
				zos.putNextEntry(new ZipEntry(entry.getKey()));
				zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
				zos.closeEntry();
			}
		}
	}
}