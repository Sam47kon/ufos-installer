package ru.otr.ufos.installer.db;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.otr.ufos.installer.logging.InstallerLogger.LINE_SEPARATOR;
import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Работа с PostgreSQL:
 * - проверка доступности;
 * - проверка таблиц;
 * - выполнение init-скрипта.
 */
@SuppressWarnings("SqlNoDataSourceInspection")
@UtilityClass
public class DatabaseInitializer {
	private static final Pattern JDBC_PG_URL_PATTERN = Pattern.compile("jdbc:postgresql://([^/:?]+)(?::(\\d+))?/([^?]+)(?:\\?.*)?", Pattern.CASE_INSENSITIVE);

	public static void checkConnection(@Nullable String dbUrl, String host, String port, String dbName, String user, String password) {
		LOGGER.step("Проверка доступности БД");
		String url = resolveJdbcUrl(dbUrl, host, port, dbName);
		try (Connection connection = getConnection(url, user, password);
			 Statement statement = connection.createStatement();
			 ResultSet rs = statement.executeQuery("SELECT 1")) {
			if (rs.next()) {
				LOGGER.info("БД доступна: " + url);
			}
		} catch (SQLException e) {
			throw new DataBaseException(String.format("Не удалось подключиться к БД: %s, пользователь: %s, ошибка: %s", url, user, e.getMessage()), e);
		}
	}

	public static void logTableCount(@Nullable String dbUrl, String host, String port, String dbName, String user, String password) {
		String url = resolveJdbcUrl(dbUrl, host, port, dbName);
		String sql = """
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = current_schema()
				  AND table_type = 'BASE TABLE'
				""";
		try (Connection connection = getConnection(url, user, password);
			 Statement statement = connection.createStatement();
			 ResultSet rs = statement.executeQuery(sql)) {
			if (!rs.next()) {
				return;
			}
			int count = rs.getInt(1);
			LOGGER.info("Количество таблиц в текущей схеме: " + count);
			if (count > 0) {
				LOGGER.warn("База данных уже содержит таблицы. " +
							"Если это первичная установка, убедитесь, что это ожидаемое состояние.");
			}
		} catch (SQLException e) {
			LOGGER.warn("Не удалось получить количество таблиц: " + e.getMessage());
		}
	}

	/**
	 * Единая точка входа выполнения init-скрипта:
	 * - usePsql = true  -> psql -f (поддерживает COPY ... FROM stdin)
	 * - usePsql = false -> JDBC + SqlScriptSplitter (для H2 и простых SQL)
	 */
	public static void executeInitSql(boolean usePsql, @NotNull Path sqlFile, @Nullable String dbUrl, String host, String port, String dbName, String user, String password) throws IOException {
		if (usePsql) {
			executeSqlFileViaPsql(sqlFile, dbUrl, host, port, dbName, user, password);
		} else {
			executeSqlFile(sqlFile, dbUrl, host, port, dbName, user, password);
		}
	}

	public static @NotNull String resolveJdbcUrl(@Nullable String dbUrl, String host, String port, String dbName) {
		if (StringUtils.isNotBlank(dbUrl)) {
			return dbUrl;
		}
		requireNonBlank(host, port, dbName);
		return buildJdbcPgUrl(host, port, dbName);
	}

	/**
	 * Проверка, что psql доступен в PATH.
	 * Используется для ранней проверки окружения и для тестов (Assumptions).
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean isPsqlAvailable() {
		try {
			Process process = new ProcessBuilder("psql", "--version")
					.redirectErrorStream(true)
					.start();

			if (!process.waitFor(10, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Проверяет наличие пользователей 'ufos' и 'postgres' в БД и создаёт их, если они отсутствуют.
	 * Пароль для обоих пользователей устанавливается в 'ufos', права - SUPERUSER.
	 */
	public static void ensureRequiredUsers(@Nullable String dbUrl, String host, String port, String dbName, String user, String password) {
		LOGGER.step("Проверка и создание требуемых пользователей БД (ufos, postgres)");
		String url = resolveJdbcUrl(dbUrl, host, port, dbName);
		try (Connection connection = getConnection(url, user, password)) {
			ensureUserExists(connection, "ufos", "ufos");
			ensureUserExists(connection, "postgres", "ufos");
		} catch (SQLException e) {
			LOGGER.warn("Не удалось подключиться для проверки/создания пользователей БД: " + e.getMessage());
		}
	}

	private static void ensureUserExists(Connection connection, String username, @SuppressWarnings("SameParameterValue") String pwd) {
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT 1 FROM pg_roles WHERE rolname = ?"
		)) {
			ps.setString(1, username);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					LOGGER.info("Пользователь БД [" + username + "] не найден. Создаю с паролем [" + pwd + "] и правами SUPERUSER...");
					try (Statement stmt = connection.createStatement()) {
						// Имена пользователей захардкожены, поэтому SQL-инъекция исключена
						stmt.execute(String.format("CREATE ROLE %s WITH LOGIN SUPERUSER PASSWORD '%s'", username, pwd));
						LOGGER.info("Пользователь БД [" + username + "] успешно создан.");
					}
				} else {
					LOGGER.debug("Пользователь БД [" + username + "] уже существует.");
				}
			}
		} catch (SQLException e) {
			LOGGER.warn("Не удалось проверить/создать пользователя [" + username + "]: " + e.getMessage());
		}
	}

	private static void executeSqlFileViaPsql(@NotNull Path sqlFile, @Nullable String dbUrl, String host, String port, String dbName, String user, String password) throws IOException {
		LOGGER.step("Выполнение init SQL через psql: " + sqlFile.getFileName());

		if (!isPsqlAvailable()) {
			throw new DataBaseException(
					"Утилита psql не найдена в PATH. Установите PostgreSQL Client " +
					"или используйте JDBC-режим (--no-use-psql). " +
					"Обратите внимание: JDBC-режим не поддерживает COPY ... FROM stdin."
			);
		}

		DbEndpoint endpoint = resolveEndpoint(dbUrl, host, port, dbName);

		ProcessBuilder pb = new ProcessBuilder(
				"psql",
				"-h", endpoint.host(),
				"-p", endpoint.port(),
				"-U", user,
				"-d", endpoint.dbName(),
				"-f", sqlFile.toAbsolutePath().toString(),
				"-v", "ON_ERROR_STOP=1",
				"--no-psqlrc"
		);

		if (password != null) {
			// Передаём пароль через переменную окружения
			pb.environment().put("PGPASSWORD", password);
		}
		pb.redirectErrorStream(true);

		Process process = pb.start();

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				LOGGER.info("[psql] " + line);
			}
		}

		int exitCode;
		try {
			exitCode = process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new DataBaseException("Выполнение psql прервано", e);
		}

		if (exitCode != 0) {
			throw new DataBaseException("psql завершился с ошибкой, код: " + exitCode);
		}

		LOGGER.info("Init SQL выполнен успешно через psql");
	}

	private static void executeSqlFile(@NotNull Path sqlFile, String dbUrl, String host, String port, String dbName, String user, String password) throws IOException {
		LOGGER.step("Выполнение init SQL: " + sqlFile.getFileName());
		String script = Files.readString(sqlFile);
		List<String> statements = SqlScriptSplitter.split(script);
		if (statements.isEmpty()) {
			LOGGER.warn("SQL-файл пуст или не содержит операторов");
			return;
		}
		String url = resolveJdbcUrl(dbUrl, host, port, dbName);
		int total = statements.size();

		LOGGER.info("Найдено SQL-операторов: " + total);
		try (Connection connection = getConnection(url, user, password);
			 Statement statement = connection.createStatement()) {
			for (int i = 0; i < total; i++) {
				String sql = statements.get(i);
				try {
					statement.execute(sql);
				} catch (SQLException e) {
					LOGGER.error("Ошибка выполнения SQL-оператора #" + (i + 1));
					LOGGER.error("SQL:" + LINE_SEPARATOR + sql);
					throw e;
				}
				if ((i + 1) % 100 == 0 || i + 1 == total) {
					LOGGER.info("Выполнено операторов: " + (i + 1) + " из " + total);
				}
			}
		} catch (Exception e) {
			throw new DataBaseException(e);
		}
		LOGGER.info("Init SQL выполнен успешно");
	}

	private static void requireNonBlank(String @NotNull ... args) {
		for (String arg : args) {
			if (StringUtils.isBlank(arg)) {
				throw new DataBaseException("Не заданы параметры подключения к БД");
			}
		}
	}

	private static Connection getConnection(String url, String user, String password) throws SQLException {
		Properties properties = new Properties();
		properties.setProperty("user", user);
		if (password != null) {
			properties.setProperty("password", password);
		}
		properties.setProperty("ApplicationName", "UFOS Installer");
		return DriverManager.getConnection(url, properties);
	}

	@Contract(pure = true)
	private static @NotNull String buildJdbcPgUrl(String host, String port, String dbName) {
		return "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
	}

	/**
	 * Резолвит хост/порт/имя БД.
	 * Если задан dbUrl - параметры извлекаются из него.
	 */
	private static @NotNull DbEndpoint resolveEndpoint(@Nullable String dbUrl, String host, String port, String dbName) {
		if (StringUtils.isNotBlank(dbUrl)) {
			Matcher matcher = JDBC_PG_URL_PATTERN.matcher(dbUrl);

			if (!matcher.find()) {
				throw new DataBaseException(
						"Не удалось разобрать JDBC URL для запуска psql: " + dbUrl +
						". Ожидается формат jdbc:postgresql://host:port/dbname"
				);
			}

			String parsedHost = matcher.group(1);
			String parsedPort = StringUtils.defaultIfBlank(matcher.group(2), "5432");
			String parsedDb = matcher.group(3);

			return new DbEndpoint(parsedHost, parsedPort, parsedDb);
		}

		requireNonBlank(host, port, dbName);
		return new DbEndpoint(host, port, dbName);
	}

	private record DbEndpoint(String host, String port, String dbName) {
	}
}
