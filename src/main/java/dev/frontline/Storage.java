package dev.frontline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

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
