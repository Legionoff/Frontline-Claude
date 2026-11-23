package dev.frontline;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-side translations (en/ru/de/uk) so vanilla clients without the mod see translated text. */
public final class Lang {
	public static final List<String> CODES = List.of("en", "ru", "de", "uk");
	private static final Map<String, String> FILES = Map.of(
			"en", "en_us", "ru", "ru_ru", "de", "de_de", "uk", "uk_ua");
	private static final Map<String, Map<String, String>> TABLES = new HashMap<>();

	private Lang() {}

	public static void load() {
		for (String code : CODES) {
			Map<String, String> table = new HashMap<>();
			String path = "/assets/frontline/lang/" + FILES.get(code) + ".json";
			try (InputStream in = Lang.class.getResourceAsStream(path)) {
				if (in != null) {
					JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
						table.put(e.getKey(), e.getValue().getAsString());
					}
				}
			} catch (Exception ex) {
				FrontlineMod.LOGGER.error("Failed to load language {}", code, ex);
			}
			TABLES.put(code, table);
		}
	}

	public static String code(String mc) {
		if (mc == null) return "en";
		String l = mc.toLowerCase();
		if (l.startsWith("ru")) return "ru";
		if (l.startsWith("de")) return "de";
		if (l.startsWith("uk")) return "uk";
		return "en";
	}

	public static String of(ServerPlayer p) {
		try {
			return code(p.clientInformation().language());
		} catch (Throwable t) {
			return "en";
		}
	}

	public static String of(CommandSourceStack src) {
		ServerPlayer p = src.getPlayer();
		return p == null ? "en" : of(p);
	}

	public static String raw(String lang, String key) {
		Map<String, String> t = TABLES.get(lang);
		String v = t == null ? null : t.get(key);
		if (v == null) {
			Map<String, String> en = TABLES.get("en");
			v = en == null ? null : en.get(key);
		}
		return v == null ? key : v;
	}

	/** Builds a component, replacing each %s with the next argument (Components keep their style). */
	public static MutableComponent comp(String lang, String key, Object... args) {
		String pattern = raw(lang, key);
		MutableComponent out = Component.empty();
		int from = 0;
		int arg = 0;
		while (true) {
			int i = pattern.indexOf("%s", from);
			if (i < 0) break;
			if (i > from) out.append(Component.literal(pattern.substring(from, i)));
			Object a = arg < args.length ? args[arg++] : "";
			out.append(a instanceof Component c ? c : Component.literal(String.valueOf(a)));
			from = i + 2;
		}
		if (from < pattern.length()) out.append(Component.literal(pattern.substring(from)));
		return out;
	}

	public static String str(String lang, String key, Object... args) {
		return comp(lang, key, args).getString();
	}
}
