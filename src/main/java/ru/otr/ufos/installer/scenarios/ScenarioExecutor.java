package ru.otr.ufos.installer.scenarios;

import ru.otr.ufos.installer.core.InstallerContext;

/**
 * Интерфейс для выполнения сценариев
 */
public interface ScenarioExecutor {
	/**
	 * Выполнить сценарий
	 * @param context контекст выполнения
	 * @throws Exception при ошибке выполнения
	 */
	void execute(InstallerContext context) throws Exception;
}