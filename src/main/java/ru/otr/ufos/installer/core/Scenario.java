package ru.otr.ufos.installer.core;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Сценарии выполнения инсталлятора
 */
@Getter
public enum Scenario {
	INIT("Первичная установка", "init"),
	UPDATE_CORE("Обновление ядра и приклада", "update-core");

	private final String description;
	private final String command;

	Scenario(String description, String command) {
		this.description = description;
		this.command = command;
	}

	public static @NotNull Scenario fromCommand(String command) {
		for (Scenario s : values()) {
			if (s.command.equals(command)) {
				return s;
			}
		}
		throw new IllegalArgumentException("Неизвестный сценарий: " + command);
	}

	@Override
	public String toString() {
		return description + " (" + command + ')';
	}
}
