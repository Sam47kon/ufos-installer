package ru.otr.ufos.installer.filesystem;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * Управление правами доступа.
 * Для Windows большинство операций пропускается.
 */
public class PermissionsManager {

	public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

	@Getter
	private final @Nullable String chownUser;
	private final @NotNull List<Snapshot> snapshots;

	public PermissionsManager(@Nullable String chownUser) {
		this.chownUser = chownUser;
		this.snapshots = new ArrayList<>();
	}

	public void snapshot(Path... paths) {
		if (IS_WINDOWS) {
			return;
		}
		for (Path path : paths) {
			Snapshot snapshot = createSnapshot(path);
			if (snapshot != null) {
				snapshots.add(snapshot);
			}
		}
	}

	public @Nullable Snapshot createSnapshot(Path path) {
		if (IS_WINDOWS) {
			return null;
		}
		if (!Files.exists(path)) {
			return null;
		}
		try {
			return new Snapshot(path);
		} catch (IOException e) {
			LOGGER.warn("Не удалось сохранить права для: " + path + " - " + e.getMessage());
			return null;
		}
	}

	public void restore() {
		if (IS_WINDOWS) {
			return;
		}
		if (CollectionUtils.isEmpty(snapshots)) {
			return;
		}
		snapshots.forEach(this::restoreSnapshot);
	}

	private void restoreSnapshot(Snapshot snapshot) {
		if (IS_WINDOWS) {
			return;
		}
		if (snapshot == null) {
			return;
		}
		if (!Files.exists(snapshot.originalPath)) {
			LOGGER.warn("Не удалось восстановить права, путь не существует: " + snapshot.originalPath);
			return;
		}

		try {
			// Корневые права
			if (CollectionUtils.isNotEmpty(snapshot.rootPermissions)) {
				setPermissions(snapshot.originalPath, snapshot.rootPermissions);
			}
			// Внутренние файлы и папки
			if (snapshot.directory) {
				try (Stream<Path> stream = Files.walk(snapshot.originalPath)) {
					stream.toList().forEach(p -> {
						String rel = relativize(snapshot.originalPath, p);
						Set<PosixFilePermission> permissions = snapshot.entryPermissions.get(rel);
						if (permissions != null) {
							setPermissions(p, permissions);
						} else {
							applyDefaultPermissions(p);
						}
					});
				}
			}
			// Владелец
			if (hasValidChownUser()) {
				changeOwnerChownUser(snapshot.originalPath);
			} else if (snapshot.owner != null) {
				changeOwner(snapshot.originalPath, snapshot.owner);
			}
		} catch (IOException e) {
			LOGGER.warn("Не удалось восстановить права для: " + snapshot.originalPath + " - " + e.getMessage());
		}
	}

	public void applyDefaults(Path root) {
		if (IS_WINDOWS) {
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
			LOGGER.warn("Не удалось применить права по умолчанию для: " + root + " - " + e.getMessage());
		}
	}

	public void applyFilePermissions(Path file, Set<PosixFilePermission> permissions) {
		if (IS_WINDOWS) {
			return;
		}
		if (!Files.exists(file)) {
			return;
		}
		if (CollectionUtils.isEmpty(permissions)) {
			applyDefaultPermissions(file);
			return;
		}
		setPermissions(file, permissions);
	}

	public boolean hasValidChownUser() {
		return StringUtils.isNotBlank(chownUser);
	}

	public boolean changeOwnerChownUser(Path path) {
		if (IS_WINDOWS) {
			LOGGER.debug("chown пропускается на Windows");
			return false;
		}
		return changeOwner(path, chownUser);
	}

	private boolean changeOwner(Path path, String owner) {
		if (IS_WINDOWS) {
			LOGGER.debug("chown пропускается на Windows");
			return false;
		}
		if (StringUtils.isBlank(owner)) {
			return false;
		}
		try {
			ProcessBuilder pb = new ProcessBuilder("chown", "-R", owner, path.toString());
			pb.redirectErrorStream(true);
			Process process = pb.start();
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				LOGGER.info("Выполнен chown -R " + owner + " для: " + path);
				return true;
			} else {
				LOGGER.warn("chown завершился с кодом " + exitCode + " для: " + path);
				return false;
			}
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			LOGGER.warn("Не удалось выполнить chown для: " + path + " - " + e.getMessage());
			return false;
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
			LOGGER.debug("Не удалось применить права по умолчанию для: " + path);
		}
	}

	private void setPermissions(Path path, Set<PosixFilePermission> permissions) {
		if (permissions == null) {
			return;
		}
		try {
			PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
			if (view != null) {
				view.setPermissions(permissions);
			}
		} catch (IOException e) {
			LOGGER.debug("Не удалось установить права для: " + path);
		}
	}

	private static @NotNull String relativize(@NotNull Path root, Path path) {
		return root.relativize(path).toString().replace('\\', '/');
	}

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	public static class Snapshot {
		Path originalPath;
		boolean directory;
		String owner;
		@Getter
		@NotNull Set<PosixFilePermission> rootPermissions;
		Map<String, Set<PosixFilePermission>> entryPermissions;

		public Snapshot(Path originalPath) throws IOException {
			this.originalPath = originalPath;
			this.directory = Files.isDirectory(originalPath);
			this.owner = getOwnerName(originalPath);
			this.rootPermissions = getPermissions(originalPath);

			if (!this.directory) {
				this.entryPermissions = Collections.emptyMap();
				return;
			}
			try (Stream<Path> stream = Files.walk(originalPath)) {
				this.entryPermissions = stream.collect(
						Collectors.toMap(
								p -> relativize(originalPath, p),
								this::getPermissionsQuietly,
								(a, b) -> a
						)
				);
			}
		}

		private static @Nullable String getOwnerName(Path path) {
			try {
				UserPrincipal owner = Files.getOwner(path);
				return owner.getName();
			} catch (IOException e) {
				return null;
			}
		}

		private @NotNull Set<PosixFilePermission> getPermissions(Path path) {
			try {
				PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
				if (view == null) {
					return Collections.emptySet();
				}
				return view.readAttributes().permissions();
			} catch (IOException e) {
				return Collections.emptySet();
			}
		}

		private @NotNull Set<PosixFilePermission> getPermissionsQuietly(Path path) {
			return getPermissions(path);
		}
	}
}
