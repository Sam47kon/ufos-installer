package ru.otr.ufos.installer.scenarios;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

@UtilityClass
public class ScenariosUtils {
	static @Nullable Path findVersionFile(@NotNull Path appRoot) {
		Path direct = appRoot.resolve("version.txt");
		if (Files.exists(direct)) {
			return direct;
		}

		Path inConfig = appRoot.resolve("sufd.config/version.txt");
		if (Files.exists(inConfig)) {
			return inConfig;
		}

		Path funcVersion = appRoot.resolve("sufd.config/func.version");
		if (Files.exists(funcVersion)) {
			return funcVersion;
		}

		return null;
	}
}
