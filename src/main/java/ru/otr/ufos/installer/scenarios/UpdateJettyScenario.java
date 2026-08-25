package ru.otr.ufos.installer.scenarios;

import ru.otr.ufos.installer.core.InstallerContext;

/**
 * Сценарий 3: обновление с пересозданием Jetty.
 * Пока находится в разработке.
 */
public class UpdateJettyScenario implements ScenarioExecutor {

	@Override
	public void execute(InstallerContext context) throws Exception {
		context.getLogger().error(
				"Сценарий 'Обновление с пересозданием Jetty' ещё не реализован в текущей сборке."
		);

		throw new UnsupportedOperationException(
				"Сценарий UPDATE_JETTY ещё не реализован"
		);
	}
}