package ru.otr.ufos.installer.core;

/**
 * Интерфейс запроса подтверждения у пользователя.
 * В CLI - консоль, в GUI - диалог.
 */
public interface UserPrompt {
	boolean confirm(String question);
}
