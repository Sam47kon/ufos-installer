package ru.otr.ufos.installer.filesystem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.otr.ufos.installer.logging.InstallerLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Управление правами доступа.
 * Для Windows большинство операций пропускается.
 */
public class PermissionsManager {

	private final InstallerLogger logger;

	public PermissionsManager(InstallerLogger logger) {
		this.logger = logger;
	}

	public static class Snapshot {
		public Path originalPath;
		public boolean directory;
		public String owner;
		public Set<PosixFilePermission> rootPermissions;
		public Map<String, Set<PosixFilePermission>> entryPermissions = new HashMap<>();
	}

	public Snapshot snapshot(Path path) {
		if (FileManager.isWindows()) {
			return null;
		}

		if (!Files.exists(path)) {
			return null;
		}

		try {
			Snapshot snapshot = new Snapshot();
			snapshot.originalPath = path;
			snapshot.directory = Files.isDirectory(path);
			snapshot.owner = getOwnerName(path);
			snapshot.rootPermissions = getPermissions(path);

			if (snapshot.directory) {
				try (Stream<Path> stream = Files.walk(path)) {
					snapshot.entryPermissions = stream.collect(
							Collectors.toMap(
									p -> relativize(path, p),
									this::getPermissionsQuietly,
									(a, b) -> a
							)
					);
				}
			}

			return snapshot;

		} catch (IOException e) {
			logger.warn("Не удалось сохранить права для: " + path + " - " + e.getMessage());
			return null;
		}
	}

	public void restore(Snapshot snapshot, Path newPath, String chownUser) {
		if (FileManager.isWindows()) {
			return;
		}

		if (snapshot == null) {
			applyDefaults(newPath);
			if (chownUser != null && !chownUser.isBlank()) {
				changeOwner(newPath, chownUser);
			}
			return;
		}

		if (!Files.exists(newPath)) {
			logger.warn("Не удалось восстановить права, путь не существует: " + newPath);
			return;
		}

		try {
			// Корневые права
			if (snapshot.rootPermissions != null) {
				setPermissions(newPath, snapshot.rootPermissions);
			}

			// Внутренние файлы и папки
			if (snapshot.directory) {
				try (Stream<Path> stream = Files.walk(newPath)) {
					for (Path p : stream.toList()) {
						String rel = relativize(newPath, p);
						Set<PosixFilePermission> permissions = snapshot.entryPermissions.get(rel);

						if (permissions != null) {
							setPermissions(p, permissions);
						} else {
							applyDefaultPermissions(p);
						}
					}
				}
			}

			// Владелец
			if (chownUser != null && !chownUser.isBlank()) {
				changeOwner(newPath, chownUser);
			} else if (snapshot.owner != null) {
				changeOwner(newPath, snapshot.owner);
			}

		} catch (IOException e) {
			logger.warn("Не удалось восстановить права для: " + newPath + " - " + e.getMessage());
		}
	}

	public void applyDefaults(Path root) {
		if (FileManager.isWindows()) {
			return;
		}

		if (!Files.exists(root)) {
			return;
		}

		try (Stream<Path> stream = Files.walk(root)) {
			for (Path path : stream.toList()) {
				applyDefaultPermissions(path);
			}
		} catch (IOException e) {
			logger.warn("Не удалось применить права по умолчанию для: " + root + " - " + e.getMessage());
		}
	}

	public void applyFilePermissions(Path file, Set<PosixFilePermission> permissions) {
		if (FileManager.isWindows()) {
			return;
		}

		if (!Files.exists(file)) {
			return;
		}

		if (permissions == null) {
			applyDefaultPermissions(file);
			return;
		}

		setPermissions(file, permissions);
	}

	public void changeOwner(Path path, String owner) {
		if (FileManager.isWindows()) {
			logger.debug("chown пропускается на Windows");
			return;
		}

		if (owner == null || owner.isBlank()) {
			return;
		}

		try {
			ProcessBuilder pb = new ProcessBuilder("chown", "-R", owner, path.toString());
			pb.redirectErrorStream(true);
			Process process = pb.start();

			int exitCode = process.waitFor();

			if (exitCode == 0) {
				logger.info("Выполнен chown -R " + owner + " для: " + path);
			} else {
				logger.warn("chown завершился с кодом " + exitCode + " для: " + path);
			}

		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			logger.warn("Не удалось выполнить chown для: " + path + " - " + e.getMessage());
		}
	}

	private void applyDefaultPermissions(Path path) {
		try {
			if (Files.isDirectory(path)) {
				setPermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
			} else {
				String name = path.getFileName().toString().toLowerCase();

				if (name.endsWith(".sh")) {
					setPermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
				} else {
					setPermissions(path, PosixFilePermissions.fromString("rw-r--r--"));
				}
			}
		} catch (Exception e) {
			logger.debug("Не удалось применить права по умолчанию для: " + path);
		}
	}

	private @Nullable String getOwnerName(Path path) {
		try {
			UserPrincipal owner = Files.getOwner(path);
			return owner.getName();
		} catch (IOException e) {
			return null;
		}
	}

	private @Nullable Set<PosixFilePermission> getPermissions(Path path) {
		try {
			PosixFileAttributeView view =
					Files.getFileAttributeView(path, PosixFileAttributeView.class);

			if (view == null) {
				return null;
			}

			return view.readAttributes().permissions();

		} catch (IOException e) {
			return null;
		}
	}

	private Set<PosixFilePermission> getPermissionsQuietly(Path path) {
		return getPermissions(path);
	}

	private void setPermissions(Path path, Set<PosixFilePermission> permissions) {
		if (permissions == null) {
			return;
		}

		try {
			PosixFileAttributeView view =
					Files.getFileAttributeView(path, PosixFileAttributeView.class);

			if (view != null) {
				view.setPermissions(permissions);
			}
		} catch (IOException e) {
			logger.debug("Не удалось установить права для: " + path);
		}
	}

	private @NotNull String relativize(@NotNull Path root, Path path) {
		return root.relativize(path).toString().replace('\\', '/');
	}
}
