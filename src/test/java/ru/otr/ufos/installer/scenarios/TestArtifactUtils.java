package ru.otr.ufos.installer.scenarios;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Вспомогательный класс для подготовки тестовых артефактов.
 */
@UtilityClass
final class TestArtifactUtils {

	/**
	 * Уникальная in-memory H2 база для позитивных тестов.
	 */
	static @NotNull String uniqueH2Url() {
		String dbName = "ufos_" + UUID.randomUUID().toString().replace("-", "").toLowerCase();
		return "jdbc:h2:mem:" + dbName
			   + ";MODE=PostgreSQL"
			   + ";DATABASE_TO_LOWER=TRUE"
			   + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE"
			   + ";DB_CLOSE_DELAY=-1";
	}

	/**
	 * URL заведомо недоступной базы.
	 * <p>
	 * Используем режим:
	 * - IFEXISTS=TRUE
	 * <p>
	 * База заранее не создаётся, поэтому подключение должно быстро падать.
	 */
	static @NotNull String unavailableH2Url() {
		String dbName = "missing_" + UUID.randomUUID().toString().replace("-", "").toLowerCase();
		return "jdbc:h2:mem:" + dbName + ";IFEXISTS=TRUE;DB_CLOSE_DELAY=-1";
	}

	static @NotNull String unavailableH2Url(String dbName) {
		return "jdbc:h2:mem:" + dbName + ";IFEXISTS=TRUE;DB_CLOSE_DELAY=-1";
	}

	static @NotNull String errorDbName() {
		return "missing_" + UUID.randomUUID().toString().replace("-", "").toLowerCase();
	}

	static @NotNull Path writeFile(@NotNull Path file, String content) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	static @NotNull Path createZip(@NotNull Path zipFile, @NotNull Map<String, String> entries) throws IOException {
		Path parent = zipFile.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
			for (Map.Entry<String, String> entry : entries.entrySet()) {
				zos.putNextEntry(new ZipEntry(entry.getKey()));
				zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
				zos.closeEntry();
			}
		}
		return zipFile;
	}

	/**
	 * Создаёт шаблон стенда.
	 * <p>
	 * Внутри:
	 * - etc/sufd-db.properties с плейсхолдерами ${DB_URL}, ${DB_USER}
	 */
	static @NotNull Path createTemplate(@NotNull Path baseDir) throws IOException {
		Path templateDir = baseDir.resolve("template");
		writeFile(
				templateDir.resolve("etc/sufd-db.properties"),
				"""
						db.url=${DB_URL}
						db.user=${DB_USER}
						custom=${CUSTOM_TEST_VAR}
						"""
		);
		return templateDir;
	}

	static @NotNull Path createCoreWar(@NotNull Path artifactsDir, String version) throws IOException {
		return writeFile(
				artifactsDir.resolve("sufd-server-" + version + ".war"),
				"war-" + version
		);
	}

	static @NotNull Path createLibsZip(@NotNull Path artifactsDir,
									   String version,
									   String fileName,
									   String content) throws IOException {

		return createZip(
				artifactsDir.resolve("sufd-server-libs-" + version + ".zip"),
				Map.of(fileName, content)
		);
	}

	/**
	 * Создаёт битый архив
	 * При попытке взаимодействия: {@link java.util.zip.ZipException} {@code "invalid entry CRC (expected 0xdeadbeef but got 0x5138dfcd)"}
	 *
	 * @since Создано ИИ
	 */
	static @NotNull Path createBrokenLibsZip(@NotNull Path artifactsDir) throws IOException {
		Path zipFile = artifactsDir.resolve("sufd-server-libs-N-M.zip");
		Path parent = zipFile.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		byte[] fileName = "lib.txt".getBytes(StandardCharsets.US_ASCII);
		byte[] fileData = "new-lib".getBytes(StandardCharsets.UTF_8);

		ByteBuffer buffer = ByteBuffer
				.allocate(30 + fileName.length + fileData.length)
				.order(ByteOrder.LITTLE_ENDIAN);

		// Local file header signature
		buffer.putInt(0x04034b50);
		// Version needed to extract
		buffer.putShort((short) 20);
		// General purpose bit flag
		buffer.putShort((short) 0);
		// Compression method: 0 = stored
		buffer.putShort((short) 0);
		// Last mod file time
		buffer.putShort((short) 0);
		// Last mod file date
		buffer.putShort((short) 0);

		// --> CRC-32: намеренно неверный <--
		buffer.putInt(0xDEADBEEF);

		// Compressed size
		buffer.putInt(fileData.length);
		// Uncompressed size
		buffer.putInt(fileData.length);
		// File name length
		buffer.putShort((short) fileName.length);
		// Extra field length
		buffer.putShort((short) 0);
		// File name
		buffer.put(fileName);
		// File data
		buffer.put(fileData);

		Files.write(zipFile, buffer.array());
		return zipFile;
	}

	static @NotNull Path createAppZip(@NotNull Path artifactsDir, String version) throws IOException {
		return createZip(
				artifactsDir.resolve("func-" + version + ".zip"),
				Map.of(
						"sufd.config/new-app.txt", "app " + version,
						"sql-migrations/new.sql", "-- new migration " + version,
						"version.txt", version + "-app"
				)
		);
	}

	static @NotNull Path createInitSql(@NotNull Path artifactsDir, boolean valid) throws IOException {
		String sql;
		if (valid) {
			sql = """
					CREATE TABLE IF NOT EXISTS installer_test (
					    id INT PRIMARY KEY,
					    name VARCHAR(100)
					);
					
					INSERT INTO installer_test (id, name)
					VALUES (1, 'installed');
					""";
		} else {
			sql = """
					CREATE TABLE IF NOT EXISTS installer_fail_test (
					    id INT PRIMARY KEY
					);
					
					INSERT INTO table_that_does_not_exist (id)
					VALUES (1);
					""";
		}
		return writeFile(artifactsDir.resolve("init.sql"), sql);
	}

	static @NotNull InstallerContext createContext(@NotNull Path tempDir, Path standDir, Path artifactsDir,
												   String dbUrl, boolean skipDb,
												   Path template, Path coreWar, Path libsZip, Path appZip, Path initSql) {
		InstallerContext context = new InstallerContext(Scenario.INIT, question -> true);

		context.setStandDir(standDir);
		context.setArtifactsDir(artifactsDir);
		context.setBackupDir(tempDir.resolve("backups"));

		context.setJettyTemplate(template);
		context.setCoreWar(coreWar);
		context.setLibsZip(libsZip);
		context.setAppZip(appZip);
		context.setInitSql(initSql);

		context.setDbUrl(dbUrl);
		context.setDbUser("sa");
		context.setDbPassword("");
		context.setSkipDb(skipDb);
		// H2 не поддерживает COPY, psql в тестах не нужен
		context.setUsePsql(false);

		context.setCustomVariables(Map.of("CUSTOM_TEST_VAR", "custom-value"));
		return context;
	}
}
