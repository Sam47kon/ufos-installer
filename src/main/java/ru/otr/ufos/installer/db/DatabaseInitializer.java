package ru.otr.ufos.installer.db;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.Properties;

/**
 * Работа с PostgreSQL:
 * - проверка доступности;
 * - проверка таблиц;
 * - выполнение init-скрипта.
 */
public class DatabaseInitializer {

	private final InstallerLogger logger;

	public DatabaseInitializer(InstallerLogger logger) {
		this.logger = logger;
	}

	public void checkConnection(String host, String port, String dbName, String user, String password) {
		logger.step("Проверка доступности БД");

		String url = buildJdbcUrl(host, port, dbName);

		try (Connection connection = getConnection(url, user, password);
			 Statement statement = connection.createStatement();
			 ResultSet rs = statement.executeQuery("SELECT 1")) {

			if (rs.next()) {
				logger.info("БД доступна: " + url);
			}

		} catch (SQLException e) {
			throw new IllegalStateException(
					"Не удалось подключиться к БД: " + url +
							", пользователь: " + user +
							", ошибка: " + e.getMessage(),
					e
			);
		}
	}

	public void logTableCount(String host, String port, String dbName, String user, String password) {
		String url = buildJdbcUrl(host, port, dbName);

		String sql = """
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = current_schema()
				  AND table_type = 'BASE TABLE'
				""";

		try (Connection connection = getConnection(url, user, password);
			 Statement statement = connection.createStatement();
			 ResultSet rs = statement.executeQuery(sql)) {

			if (rs.next()) {
				int count = rs.getInt(1);
				logger.info("Количество таблиц в текущей схеме: " + count);

				if (count > 0) {
					logger.warn("База данных уже содержит таблицы. " +
							"Если это первичная установка, убедитесь, что это ожидаемое состояние.");
				}
			}

		} catch (SQLException e) {
			logger.warn("Не удалось получить количество таблиц: " + e.getMessage());
		}
	}

	public void executeSqlFile(@NotNull Path sqlFile,
							   String host,
							   String port,
							   String dbName,
							   String user,
							   String password) throws IOException, SQLException {

		logger.step("Выполнение init SQL: " + sqlFile.getFileName());

		String script = Files.readString(sqlFile);
		List<String> statements = SqlScriptSplitter.split(script);

		if (statements.isEmpty()) {
			logger.warn("SQL-файл пуст или не содержит операторов");
			return;
		}

		String url = buildJdbcUrl(host, port, dbName);
		int total = statements.size();

		logger.info("Найдено SQL-операторов: " + total);

		try (Connection connection = getConnection(url, user, password);
			 Statement statement = connection.createStatement()) {

			for (int i = 0; i < total; i++) {
				String sql = statements.get(i);

				try {
					statement.execute(sql);
				} catch (SQLException e) {
					logger.error("Ошибка выполнения SQL-оператора #" + (i + 1));
					logger.error("SQL: " + truncate(sql, 500));
					throw e;
				}

				if ((i + 1) % 100 == 0 || i + 1 == total) {
					logger.info("Выполнено операторов: " + (i + 1) + " из " + total);
				}
			}
		}

		logger.info("Init SQL выполнен успешно");
	}

	private Connection getConnection(String url, String user, String password) throws SQLException {
		Properties properties = new Properties();
		properties.setProperty("user", user);

		if (password != null) {
			properties.setProperty("password", password);
		}

		properties.setProperty("ApplicationName", "UFOS Installer");

		return DriverManager.getConnection(url, properties);
	}

	@Contract(pure = true)
	private @NotNull String buildJdbcUrl(String host, String port, String dbName) {
		return "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
	}

	private @NotNull String truncate(String text, int maxLength) {
		if (text == null) {
			return "";
		}

		String oneLine = text.replaceAll("\\s+", " ").trim();

		if (oneLine.length() <= maxLength) {
			return oneLine;
		}

		return oneLine.substring(0, maxLength) + "...";
	}
}
