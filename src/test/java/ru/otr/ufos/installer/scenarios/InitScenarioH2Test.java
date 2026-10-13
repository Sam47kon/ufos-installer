package ru.otr.ufos.installer.scenarios;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.otr.ufos.installer.scenarios.TestArtifactUtils.*;

class InitScenarioH2Test {

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
	void initScenarioShouldCreateStandAndInitializeDatabase() throws Exception {
		String dbUrl = uniqueH2Url();
		Path template = TestArtifactUtils.createTemplate(tempDir);
		Path war = createCoreWar(artifactsDir, "1.2.3");
		Path libs = createLibsZip(artifactsDir, "1.2.3", "lib.txt", "lib");
		Path app = createAppZip(artifactsDir, "1.0.0");
		Path invalidSql = createInitSql(artifactsDir, true);

		InstallerContext context = createContext(tempDir, standDir, artifactsDir, dbUrl, false, template, war, libs, app, invalidSql);
		new InitScenario().execute(context);

		// 1. Проверка структуры стенда
		assertTrue(Files.exists(standDir));
		assertTrue(Files.isDirectory(standDir));

		// 2. Проверка ядра
		assertTrue(Files.exists(standDir.resolve("webapps/sufd-server-1.2.3.war")));

		// 3. Проверка библиотек
		assertTrue(Files.exists(standDir.resolve("lib/ext/lib.txt")));

		// 4. Проверка приклада
		assertTrue(Files.exists(standDir.resolve("sufd.config/new-app.txt")));
		assertTrue(Files.exists(standDir.resolve("sql-migrations/new.sql")));

		// 5. Проверка version.txt
		assertTrue(Files.exists(standDir.resolve("version.txt")));
		assertEquals("1.0.0-app", Files.readString(standDir.resolve("version.txt")).trim());

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
}
