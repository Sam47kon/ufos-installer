package ru.otr.ufos.installer.scenarios;

import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.core.InstallerContext;

/**
 * Интерфейс для выполнения сценариев
 */
public interface ScenarioExecutor {
	/**
	 * Выполнить сценарий
	 *
	 * @param context контекст выполнения
	 * @throws Exception при ошибке выполнения
	 */
	void execute(@NotNull InstallerContext context) throws Exception;
}
