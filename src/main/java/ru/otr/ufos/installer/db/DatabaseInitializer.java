package ru.otr.ufos.installer.db;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.Properties;

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

	public static void logTableCount(@Nullable String dbUrl,
									 String host, String port, String dbName, String user, String password) {
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

	public static void executeSqlFile(@NotNull Path sqlFile, String dbUrl, String host, String port, String dbName, String user, String password) throws IOException {
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

	public static @NotNull String resolveJdbcUrl(@Nullable String dbUrl, String host, String port, String dbName) {
		if (StringUtils.isNotBlank(dbUrl)) {
			return dbUrl;
		}
		requireNonBlank(host, port, dbName);
		return buildJdbcPgUrl(host, port, dbName);
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
}
