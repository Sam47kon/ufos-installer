package ru.otr.ufos.installer.scenarios;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.otr.ufos.installer.backup.BackupManager;
import ru.otr.ufos.installer.backup.BackupResult;
import ru.otr.ufos.installer.backup.RollbackManager;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.filesystem.ArchiveHelper;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для сценария 2: обновление ядра и приклада.
 */
class UpdateCoreScenarioTest {

	@TempDir
	Path tempDir;

	private Path artifactsDir;
	private Path standDir;
	private Path backupDir;

	@BeforeEach
	void setUp() throws Exception {
		InstallerLogger.init(tempDir.resolve("update-core-scenario.log"), true);

		artifactsDir = tempDir.resolve("artifacts");
		standDir = tempDir.resolve("STAND");
		backupDir = tempDir.resolve("backups");

		Files.createDirectories(artifactsDir);
	}

	@Test
	@DisplayName("Сценарий 2: успешное обновление ядра и приклада")
	void updateCoreShouldUpdateCoreAndApp() throws Exception {
		createExistingStand(true);

		Path newWar = TestArtifactUtils.createCoreWar(artifactsDir, "2.0.0");
		Path newLibs = TestArtifactUtils.createLibsZip(
				artifactsDir,
				"2.0.0",
				"new-lib.txt",
				"new-lib"
		);
		Path newApp = TestArtifactUtils.createAppZip(artifactsDir, "2.0.0");

		InstallerContext context = createContext(newWar, newLibs, newApp);

		new UpdateCoreScenario().execute(context);

		// WAR
		assertTrue(
				Files.exists(standDir.resolve("webapps/sufd-server-2.0.0.war")),
				"Новый WAR должен быть размещён"
		);

		assertFalse(
				Files.exists(standDir.resolve("webapps/sufd-server-1.0.0.war")),
				"Старый WAR должен быть удалён"
		);

		// Библиотеки
		assertTrue(
				Files.exists(standDir.resolve("lib/ext/new-lib.txt")),
				"Новые библиотеки должны быть распакованы"
		);

		assertFalse(
				Files.exists(standDir.resolve("lib/ext/old-lib.txt")),
				"Старые библиотеки должны быть удалены"
		);

		// Приклад
		assertTrue(
				Files.exists(standDir.resolve("sufd.config/new-app.txt")),
				"Новый приклад должен быть размещён"
		);

		assertFalse(
				Files.exists(standDir.resolve("sufd.config/old-app.txt")),
				"Старый приклад должен быть удалён"
		);

		// Миграции
		assertTrue(
				Files.exists(standDir.resolve("sql-migrations/new.sql")),
				"Новые миграции должны быть размещены"
		);

		assertFalse(
				Files.exists(standDir.resolve("sql-migrations/old.sql")),
				"Старые миграции должны быть удалены"
		);

		// Версия
		assertEquals(
				"2.0.0-app",
				Files.readString(standDir.resolve("version.txt")).trim(),
				"version.txt должен быть обновлён"
		);

		// Пользовательские конфиги не трогаем
		assertEquals(
				"custom=true",
				Files.readString(standDir.resolve("etc/site.properties")).trim(),
				"Конфиги в etc/ не должны меняться при обновлении"
		);
	}

	@Test
	@DisplayName("Сценарий 2: успешное обновление только ядра, без приклада")
	void updateCoreShouldUpdateOnlyCoreWhenAppZipAbsent() throws Exception {
		createExistingStand(true);

		Path newWar = TestArtifactUtils.createCoreWar(artifactsDir, "2.0.0");
		Path newLibs = TestArtifactUtils.createLibsZip(
				artifactsDir,
				"2.0.0",
				"new-lib.txt",
				"new-lib"
		);

		InstallerContext context = createContext(newWar, newLibs, null);

		new UpdateCoreScenario().execute(context);

		// WAR
		assertTrue(
				Files.exists(standDir.resolve("webapps/sufd-server-2.0.0.war")),
				"Новый WAR должен быть размещён"
		);

		assertFalse(
				Files.exists(standDir.resolve("webapps/sufd-server-1.0.0.war")),
				"Старый WAR должен быть удалён"
		);

		// Библиотеки
		assertTrue(
				Files.exists(standDir.resolve("lib/ext/new-lib.txt")),
				"Новые библиотеки должны быть распакованы"
		);

		assertFalse(
				Files.exists(standDir.resolve("lib/ext/old-lib.txt")),
				"Старые библиотеки должны быть удалены"
		);

		// Приклад должен остаться старым
		assertTrue(
				Files.exists(standDir.resolve("sufd.config/old-app.txt")),
				"Старый приклад должен остаться, если архив приклада не задан"
		);

		assertTrue(
				Files.exists(standDir.resolve("sql-migrations/old.sql")),
				"Старые миграции должны остаться, если архив приклада не задан"
		);

		// Версия должна остаться старой
		assertEquals(
				"1.0.0-app",
				Files.readString(standDir.resolve("version.txt")).trim(),
				"version.txt не должен меняться, если приклад не обновляется"
		);

		// Пользовательские конфиги не трогаем
		assertEquals(
				"custom=true",
				Files.readString(standDir.resolve("etc/site.properties")).trim(),
				"Конфиги в etc/ не должны меняться при обновлении"
		);
	}

	@Test
	@DisplayName("Сценарий 2: обновление только приклада, ядро не трогается")
	void updateCoreShouldUpdateOnlyAppWhenCoreArtifactsAbsent() throws Exception {
		createExistingStand(true);

		// В каталоге артефактов только приклад — WAR и библиотеки не кладём
		Path newApp = TestArtifactUtils.createAppZip(artifactsDir, "2.0.0");

		InstallerContext context = createContext(null, null, newApp);

		new UpdateCoreScenario().execute(context);

		// Ядро не тронуто
		assertTrue(
				Files.exists(standDir.resolve("webapps/sufd-server-1.0.0.war")),
				"Старый WAR должен остаться при обновлении только приклада"
		);

		assertTrue(
				Files.exists(standDir.resolve("lib/ext/old-lib.txt")),
				"Библиотеки не должны обновляться при обновлении только приклада"
		);

		// Приклад обновлён
		assertTrue(
				Files.exists(standDir.resolve("sufd.config/new-app.txt")),
				"Новый приклад должен быть размещён"
		);

		assertFalse(
				Files.exists(standDir.resolve("sufd.config/old-app.txt")),
				"Старый приклад должен быть удалён"
		);

		assertTrue(
				Files.exists(standDir.resolve("sql-migrations/new.sql")),
				"Новые миграции должны быть размещены"
		);

		assertEquals(
				"2.0.0-app",
				Files.readString(standDir.resolve("version.txt")).trim(),
				"version.txt должен быть обновлён"
		);
	}

	@Test
	@DisplayName("Сценарий 2: задан только WAR без библиотек — ошибка")
	void updateCoreShouldFailWhenOnlyWarGivenWithoutLibs() throws Exception {
		createExistingStand(true);

		Path newWar = TestArtifactUtils.createCoreWar(artifactsDir, "2.0.0");

		InstallerContext context = createContext(newWar, null, null);

		IllegalStateException e = assertThrows(
				IllegalStateException.class,
				() -> new UpdateCoreScenario().execute(context)
		);
		assertTrue(
				e.getMessage().contains("WAR и архив библиотек"),
				"Сообщение должно пояснять, что ядро задано не полностью"
		);
	}

	@Test
	@DisplayName("Сценарий 2: при --no-need-backup бэкап не создаётся")
	void updateCoreShouldSkipBackupWhenNotNeeded() throws Exception {
		createExistingStand(true);

		Path newWar = TestArtifactUtils.createCoreWar(artifactsDir, "2.0.0");
		Path newLibs = TestArtifactUtils.createLibsZip(
				artifactsDir,
				"2.0.0",
				"new-lib.txt",
				"new-lib"
		);
		Path newApp = TestArtifactUtils.createAppZip(artifactsDir, "2.0.0");

		InstallerContext context = createContext(newWar, newLibs, newApp);
		context.setNeedBackup(false);

		new UpdateCoreScenario().execute(context);

		// Бэкап не должен появиться
		if (Files.exists(backupDir)) {
			try (Stream<Path> stream = Files.list(backupDir)) {
				assertTrue(
						stream.noneMatch(Files::isDirectory),
						"Каталог бэкапа не должен создаваться при --no-need-backup"
				);
			}
		}

		// Обновление при этом происходит штатно
		assertTrue(
				Files.exists(standDir.resolve("webapps/sufd-server-2.0.0.war")),
				"Новый WAR должен быть размещён"
		);

		assertEquals(
				"2.0.0-app",
				Files.readString(standDir.resolve("version.txt")).trim(),
				"version.txt должен быть обновлён"
		);
	}

	@Test
	@DisplayName("Сценарий 2: Проверка, что архив битый")
	void brokenLibsZipShouldFailExtraction() throws Exception {
		InstallerLogger.init(tempDir.resolve("broken-zip-test.log"), true);

		Path artifactsDir = tempDir.resolve("artifacts");
		Files.createDirectories(artifactsDir);

		Path brokenZip = TestArtifactUtils.createBrokenLibsZip(artifactsDir);
		Path extractDir = tempDir.resolve("extract");

		assertThrows(
				ZipException.class,
				() -> ArchiveHelper.extractSmart(brokenZip, extractDir, true)
		);
	}

	@Test
	@DisplayName("Сценарий 2: ошибка при обновлении приводит к откату из бэкапа")
	void updateCoreShouldRollbackWhenErrorOccurs() throws Exception {
		createExistingStand(true);

		Path newWar = TestArtifactUtils.createCoreWar(artifactsDir, "2.0.0");

		// Битый архив библиотек должен привести к ошибке уже после создания бэкапа.
		Path brokenLibs = TestArtifactUtils.createBrokenLibsZip(artifactsDir);

		Path newApp = TestArtifactUtils.createAppZip(artifactsDir, "2.0.0");

		InstallerContext context = createContext(newWar, brokenLibs, newApp);

		Exception e = assertThrows(
				Exception.class,
				() -> new UpdateCoreScenario().execute(context)
		);

		assertEquals(ZipException.class, e.getClass());
		//noinspection SpellCheckingInspection
		assertEquals(
				"invalid entry CRC (expected 0xdeadbeef but got 0x5138dfcd)",
				e.getMessage()
		);

		// WAR должен быть откачен
		assertTrue(
				Files.exists(standDir.resolve("webapps/sufd-server-1.0.0.war")),
				"Старый WAR должен быть восстановлен после отката"
		);

		assertFalse(
				Files.exists(standDir.resolve("webapps/sufd-server-2.0.0.war")),
				"Новый WAR должен быть удалён после отката"
		);

		// Библиотеки должны быть откачены
		assertTrue(
				Files.exists(standDir.resolve("lib/ext/old-lib.txt")),
				"Старые библиотеки должны быть восстановлены после отката"
		);

		assertFalse(
				Files.exists(standDir.resolve("lib/ext/new-lib.txt")),
				"Новые библиотеки не должны остаться после отката"
		);

		// Приклад в данном тесте не должен был успеть обновиться,
		// потому что ошибка происходит на этапе библиотек.
		assertTrue(
				Files.exists(standDir.resolve("sufd.config/old-app.txt")),
				"Старый приклад должен остаться"
		);

		assertTrue(
				Files.exists(standDir.resolve("sql-migrations/old.sql")),
				"Старые миграции должны остаться"
		);

		// Версия должна остаться старой
		assertEquals(
				"1.0.0-app",
				Files.readString(standDir.resolve("version.txt")).trim(),
				"version.txt должен остаться старым после отката"
		);

		// Пользовательские конфиги не должны быть изменены
		assertEquals(
				"custom=true",
				Files.readString(standDir.resolve("etc/site.properties")).trim(),
				"Конфиги в etc/ не должны меняться"
		);
	}

	@Test
	@DisplayName("Сценарий 2: если version.txt не было, при обновлении он создаётся")
	void updateCoreShouldCreateVersionFileWhenItWasMissing() throws Exception {
		createExistingStand(false);

		Path newWar = TestArtifactUtils.createCoreWar(artifactsDir, "2.0.0");
		Path newLibs = TestArtifactUtils.createLibsZip(
				artifactsDir,
				"2.0.0",
				"new-lib.txt",
				"new-lib"
		);
		Path newApp = TestArtifactUtils.createAppZip(artifactsDir, "2.0.0");

		InstallerContext context = createContext(newWar, newLibs, newApp);

		new UpdateCoreScenario().execute(context);

		assertTrue(
				Files.exists(standDir.resolve("version.txt")),
				"version.txt должен быть создан, если до обновления его не было"
		);

		assertEquals(
				"2.0.0-app",
				Files.readString(standDir.resolve("version.txt")).trim(),
				"version.txt должен содержать версию из архива приклада"
		);
	}

	@Test
	@DisplayName("Сценарий 2: при откате удаляется созданный version.txt, если раньше его не было")
	void rollbackShouldDeleteCreatedVersionFileWhenItDidNotExistBefore() throws Exception {
		Path stand = tempDir.resolve("STAND-version");
		Path webapps = stand.resolve("webapps");

		Files.createDirectories(webapps);

		Path oldWar = webapps.resolve("sufd-server-1.0.0.war");
		Path newWar = webapps.resolve("sufd-server-2.0.0.war");
		Path versionFile = stand.resolve("version.txt");

		TestArtifactUtils.writeFile(oldWar, "old-war");

		// Файл 'version.txt' до обновления не существует.
		// Но он должен быть указан в affectedPaths, чтобы при откате его можно было удалить.
		List<String> affectedPaths = List.of(
				"webapps/sufd-server-1.0.0.war",
				"webapps/sufd-server-2.0.0.war",
				"version.txt"
		);

		BackupResult backup = BackupManager.createPartialBackup(
				stand,
				"version-test",
				affectedPaths,
				backupDir
		);

		// Имитируем успешную часть обновления:
		// старый WAR удалён, новый WAR создан, version.txt создан.
		Files.deleteIfExists(oldWar);
		TestArtifactUtils.writeFile(newWar, "new-war");
		TestArtifactUtils.writeFile(versionFile, "2.0.0-app");

		boolean rollbackResult = RollbackManager.performRollback(backup, stand);

		assertTrue(rollbackResult, "Откат должен завершиться успешно");

		assertTrue(
				Files.exists(oldWar),
				"Старый WAR должен быть восстановлен"
		);

		assertFalse(
				Files.exists(newWar),
				"Новый WAR должен быть удалён"
		);

		assertFalse(
				Files.exists(versionFile),
				"Созданный version.txt должен быть удалён, если до обновления его не было"
		);
	}

	private void createExistingStand(boolean withVersion) throws Exception {
		Files.createDirectories(standDir.resolve("webapps"));
		Files.createDirectories(standDir.resolve("lib/ext"));
		Files.createDirectories(standDir.resolve("sufd.config"));
		Files.createDirectories(standDir.resolve("sql-migrations"));
		Files.createDirectories(standDir.resolve("etc"));

		TestArtifactUtils.writeFile(
				standDir.resolve("webapps/sufd-server-1.0.0.war"),
				"old-war"
		);

		TestArtifactUtils.writeFile(
				standDir.resolve("lib/ext/old-lib.txt"),
				"old-lib"
		);

		TestArtifactUtils.writeFile(
				standDir.resolve("sufd.config/old-app.txt"),
				"old-app"
		);

		//noinspection GrazieInspection
		TestArtifactUtils.writeFile(
				standDir.resolve("sql-migrations/old.sql"),
				"-- old sql"
		);

		TestArtifactUtils.writeFile(
				standDir.resolve("etc/site.properties"),
				"custom=true"
		);

		if (withVersion) {
			TestArtifactUtils.writeFile(
					standDir.resolve("version.txt"),
					"1.0.0-app"
			);
		}
	}

	private @NotNull InstallerContext createContext(Path coreWar, Path libsZip, Path appZip) {
		InstallerContext context = new InstallerContext(Scenario.UPDATE_CORE, question -> true);

		context.setStandDir(standDir);
		context.setArtifactsDir(artifactsDir);
		context.setBackupDir(backupDir);

		context.setCoreWar(coreWar);
		context.setLibsZip(libsZip);
		context.setAppZip(appZip);

		return context;
	}
}
