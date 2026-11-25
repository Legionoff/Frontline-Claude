package dev.frontline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class Storage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Storage() {}

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("frontline");
	}

	/** The points live inside the world folder, so copying the world to a server brings them along. */
	public static Path worldFile(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("frontline_points.json");
	}

	public static void saveWorld(MinecraftServer server, Data d) {
		try {
			Data copy = GSON.fromJson(GSON.toJson(d), Data.class);
			copy.teams.clear();
			Files.writeString(worldFile(server), GSON.toJson(copy), StandardCharsets.UTF_8);
		} catch (Exception e) {
			FrontlineMod.LOGGER.error("Could not write frontline_points.json into the world folder", e);
		}
	}

	/** @return true if the world file existed and was applied to {@code into} */
	public static boolean loadWorld(MinecraftServer server, Data into) {
		Path f = worldFile(server);
		try {
			if (!Files.exists(f)) return false;
			Data d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
			if (d == null || d.points == null) return false;
			into.copySetupFrom(d);
			return true;
		} catch (Exception e) {
			FrontlineMod.LOGGER.error("Could not read frontline_points.json from the world folder", e);
			return false;
		}
	}

	private static Path presets() {
		return dir().resolve("presets");
	}

	public static Data load() {
		Path f = dir().resolve("state.json");
		try {
			if (Files.exists(f)) {
				Data d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
				if (d != null) return d;
			}
		} catch (Exception e) {
			FrontlineMod.LOGGER.error("Could not read state.json", e);
		}
		return new Data();
	}

	public static void save(Data d) {
		try {
			Files.createDirectories(dir());
			Files.writeString(dir().resolve("state.json"), GSON.toJson(d), StandardCharsets.UTF_8);
		} catch (IOException e) {
			FrontlineMod.LOGGER.error("Could not write state.json", e);
		}
	}

	public static String sanitize(String name) {
		return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
	}

	public static boolean savePreset(Data d, String name) {
		try {
			Data copy = GSON.fromJson(GSON.toJson(d), Data.class);
			copy.teams.clear();
			Files.createDirectories(presets());
			Files.writeString(presets().resolve(sanitize(name) + ".json"), GSON.toJson(copy), StandardCharsets.UTF_8);
			return true;
		} catch (Exception e) {
			FrontlineMod.LOGGER.error("Could not save preset", e);
			return false;
		}
	}

	public static Data loadPreset(String name) {
		Path f = presets().resolve(sanitize(name) + ".json");
		try {
			if (Files.exists(f)) return GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
		} catch (Exception e) {
			FrontlineMod.LOGGER.error("Could not load preset", e);
		}
		return null;
	}

	public static boolean deletePreset(String name) {
		try {
			return Files.deleteIfExists(presets().resolve(sanitize(name) + ".json"));
		} catch (IOException e) {
			return false;
		}
	}

	public static List<String> listPresets() {
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(presets())) return out;
		try (Stream<Path> s = Files.list(presets())) {
			s.forEach(p -> {
				String n = p.getFileName().toString();
				if (n.endsWith(".json")) out.add(n.substring(0, n.length() - 5));
			});
		} catch (IOException ignored) {
		}
		return out;
	}
}
