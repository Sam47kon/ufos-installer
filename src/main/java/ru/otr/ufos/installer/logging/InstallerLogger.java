package ru.otr.ufos.installer.logging;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Логгер инсталлятора с поддержкой файла и GUI callback
 */
public class InstallerLogger {
	private static final Logger slf4jLogger = LoggerFactory.getLogger(InstallerLogger.class);
	private static final DateTimeFormatter TIMESTAMP_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Getter
	private final Path logFile;
	private final List<LogListener> listeners;
	private final boolean verbose;

	public interface LogListener {
		void onLog(Level level, String message);
	}

	public InstallerLogger(Path logFile, boolean verbose) {
		this.logFile = logFile;
		this.listeners = new CopyOnWriteArrayList<>();
		this.verbose = verbose;

		if (logFile != null) {
			try {
				Files.createDirectories(logFile.getParent());
				Files.writeString(logFile, "", StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				slf4jLogger.error("Не удалось создать лог-файл: {}", logFile, e);
			}
		}
	}

	public void addListener(LogListener listener) {
		listeners.add(listener);
	}

	public void removeListener(LogListener listener) {
		listeners.remove(listener);
	}

	public void info(String message) {
		log(Level.INFO, message);
	}

	public void info(String format, Object... args) {
		log(Level.INFO, String.format(format, args));
	}

	public void warn(String message) {
		log(Level.WARN, message);
	}

	public void warn(String format, Object... args) {
		log(Level.WARN, String.format(format, args));
	}

	public void error(String message) {
		log(Level.ERROR, message);
	}

	public void error(String message, @NotNull Throwable throwable) {
		log(Level.ERROR, message + ": " + throwable.getMessage());
		if (verbose) {
			error(throwableToString(throwable));
		}
	}

	public void error(String format, Object... args) {
		log(Level.ERROR, String.format(format, args));
	}

	public void step(String message) {
		log(Level.INFO, "[STEP] " + message);
	}

	public void debug(String message) {
		if (verbose) {
			log(Level.DEBUG, message);
		}
	}

	private void log(Level level, String message) {
		String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
		String formattedMessage = String.format("[%s] [%-5s] %s", timestamp, level, message);

		// Запись в файл
		if (logFile != null) {
			try {
				Files.writeString(logFile, formattedMessage + "\n",
						StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			} catch (IOException e) {
				slf4jLogger.error("Ошибка записи в лог-файл", e);
			}
		}

		// Вывод в консоль
		switch (level) {
			case ERROR:
				System.err.println("\u001B[31m" + formattedMessage + "\u001B[0m"); // Red
				break;
			case WARN:
				System.out.println("\u001B[33m" + formattedMessage + "\u001B[0m"); // Yellow
				break;
			case DEBUG:
				System.out.println("\u001B[36m" + formattedMessage + "\u001B[0m"); // Cyan
				break;
			default:
				System.out.println("\u001B[32m" + formattedMessage + "\u001B[0m"); // Green
		}

		// Уведомление слушателей (для GUI)
		for (LogListener listener : listeners) {
			try {
				listener.onLog(level, message);
			} catch (Exception e) {
				slf4jLogger.error("Ошибка в LogListener", e);
			}
		}
	}

	private @NotNull String throwableToString(@NotNull Throwable throwable) {
		StringBuilder sb = new StringBuilder();
		sb.append(throwable).append("\n");
		for (StackTraceElement element : throwable.getStackTrace()) {
			sb.append("\tat ").append(element).append("\n");
		}
		return sb.toString();
	}
}
