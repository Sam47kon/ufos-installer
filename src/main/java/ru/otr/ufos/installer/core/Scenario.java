package ru.otr.ufos.installer.core;

/**
 * Сценарии выполнения инсталлятора
 */
public enum Scenario {
	INIT("Первичная установка", "init"),
	UPDATE_CORE("Обновление ядра и приклада", "update-core"),
	UPDATE_JETTY("Обновление Jetty", "update-jetty");

	private final String description;
	private final String command;

	Scenario(String description, String command) {
		this.description = description;
		this.command = command;
	}

	public String getDescription() {
		return description;
	}

	public String getCommand() {
		return command;
	}

	public static Scenario fromCommand(String command) {
		for (Scenario s : values()) {
			if (s.command.equals(command)) {
				return s;
			}
		}
		throw new IllegalArgumentException("Неизвестный сценарий: " + command);
	}
}