package dev.frontline;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.stream.Stream;

public final class Cmds {
	private Cmds() {}

	private static final SuggestionProvider<CommandSourceStack> POINTS = (ctx, b) ->
			SharedSuggestionProvider.suggest(Game.INSTANCE.data.points.stream().map(p -> quote(p.name)), b);
	private static final SuggestionProvider<CommandSourceStack> PRESETS = (ctx, b) ->
			SharedSuggestionProvider.suggest(Storage.listPresets().stream(), b);

	private static String quote(String s) {
		return s.matches("[A-Za-z0-9_.+\\-]+") ? s : "\"" + s + "\"";
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> pointArg() {
		return Commands.argument("name", StringArgumentType.string()).suggests(POINTS);
	}

	private static void ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
		String lang = Lang.of(c.getSource());
		Component msg = Lang.comp(lang, key, args);
		c.getSource().sendSuccess(() -> msg, false);
	}

	private static int fail(CommandContext<CommandSourceStack> c, String key, Object... args) {
		c.getSource().sendFailure(Lang.comp(Lang.of(c.getSource()), key, args));
		return 0;
	}

	private static Data data() {
		return Game.INSTANCE.data;
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		LiteralArgumentBuilder<CommandSourceStack> cap = Commands.literal("cappoint")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

		cap.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.string()).executes(Cmds::create)));
		cap.then(Commands.literal("remove").then(pointArg().executes(Cmds::remove)));
		cap.then(Commands.literal("list").executes(Cmds::list));
		cap.then(Commands.literal("center").then(pointArg().executes(Cmds::center)));
		cap.then(Commands.literal("move").then(pointArg()
				.then(Commands.literal("earlier").executes(c -> move(c, -1)))
				.then(Commands.literal("later").executes(c -> move(c, 1)))));
		cap.then(Commands.literal("edge").then(pointArg()
				.then(Commands.literal("pos1").executes(c -> edge(c, 1, null))
						.then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> edge(c, 1, BlockPosArgument.getBlockPos(c, "pos")))))
				.then(Commands.literal("pos2").executes(c -> edge(c, 2, null))
						.then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> edge(c, 2, BlockPosArgument.getBlockPos(c, "pos")))))));
		d.register(cap);

		LiteralArgumentBuilder<CommandSourceStack> fl = Commands.literal("frontline")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

		fl.then(Commands.literal("start").executes(Cmds::start));
		fl.then(Commands.literal("stop").executes(c -> { Game.INSTANCE.stop(); return 1; }));
		fl.then(Commands.literal("pause").executes(c -> { Game.INSTANCE.pause(); return 1; }));
		fl.then(Commands.literal("resume").executes(c -> { Game.INSTANCE.resume(); return 1; }));
		fl.then(Commands.literal("menu").executes(c -> {
			ServerPlayer p = c.getSource().getPlayer();
			if (p == null) return fail(c, "cmd.notPlayer");
			FrontlineMenu.open(p);
			return 1;
		}));
		fl.then(Commands.literal("swap").executes(c -> {
			Game.INSTANCE.swapSides();
			ok(c, data().redPushesUp ? "cmd.swapUp" : "cmd.swapDown");
			return 1;
		}));
		fl.then(Commands.literal("time")
				.then(Commands.literal("capture").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600)).executes(c -> {
					data().captureSeconds = IntegerArgumentType.getInteger(c, "seconds");
					Game.INSTANCE.save();
					ok(c, "cmd.time", "capture", data().captureSeconds);
					return 1;
				})))
				.then(Commands.literal("hold").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600)).executes(c -> {
					data().holdSeconds = IntegerArgumentType.getInteger(c, "seconds");
					Game.INSTANCE.save();
					ok(c, "cmd.time", "hold", data().holdSeconds);
					return 1;
				}))));
		fl.then(Commands.literal("team")
				.then(Commands.literal("red").then(Commands.argument("targets", EntityArgument.players()).executes(c -> team(c, Team.RED))))
				.then(Commands.literal("blue").then(Commands.argument("targets", EntityArgument.players()).executes(c -> team(c, Team.BLUE))))
				.then(Commands.literal("none").then(Commands.argument("targets", EntityArgument.players()).executes(c -> team(c, null))))
				.then(Commands.literal("auto").executes(c -> {
					int n = Game.INSTANCE.autoBalance();
					ok(c, "cmd.teamAuto", n);
					return n;
				})));
		fl.then(Commands.literal("preset")
				.then(Commands.literal("save").then(Commands.argument("preset", StringArgumentType.word()).executes(c -> {
					String n = StringArgumentType.getString(c, "preset");
					if (!Storage.savePreset(data(), n)) return fail(c, "cmd.preset.error");
					ok(c, "cmd.preset.saved", n);
					return 1;
				})))
				.then(Commands.literal("load").then(Commands.argument("preset", StringArgumentType.word()).suggests(PRESETS).executes(c -> {
					String n = StringArgumentType.getString(c, "preset");
					if (!loadPreset(n)) return fail(c, "cmd.preset.missing", n);
					ok(c, "cmd.preset.loaded", n);
					return 1;
				})))
				.then(Commands.literal("delete").then(Commands.argument("preset", StringArgumentType.word()).suggests(PRESETS).executes(c -> {
					String n = StringArgumentType.getString(c, "preset");
					if (!Storage.deletePreset(n)) return fail(c, "cmd.preset.missing", n);
					ok(c, "cmd.preset.deleted", n);
					return 1;
				})))
				.then(Commands.literal("list").executes(c -> {
					ok(c, "cmd.preset.list", String.join(", ", Storage.listPresets()));
					return 1;
				})));
		d.register(fl);
	}

	public static boolean loadPreset(String name) {
		Data p = Storage.loadPreset(name);
		if (p == null) return false;
		Data cur = Game.INSTANCE.data;
		Game.INSTANCE.stop();
		cur.points = p.points;
		cur.center = p.center;
		cur.captureSeconds = p.captureSeconds;
		cur.holdSeconds = p.holdSeconds;
		cur.redPushesUp = p.redPushesUp;
		Game.INSTANCE.save();
		return true;
	}

	private static int create(CommandContext<CommandSourceStack> c) {
		String name = StringArgumentType.getString(c, "name");
		if (data().find(name) != null) return fail(c, "cmd.exists", name);
		data().points.add(new CapPoint(name));
		if (data().center == null) data().center = name;
		Game.INSTANCE.save();
		ok(c, "cmd.created", name);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> c) {
		String name = StringArgumentType.getString(c, "name");
		CapPoint p = data().find(name);
		if (p == null) return fail(c, "cmd.unknown", name);
		Game.INSTANCE.stop();
		data().points.remove(p);
		if (data().center != null && data().center.equalsIgnoreCase(p.name)) data().center = null;
		Game.INSTANCE.save();
		ok(c, "cmd.removed", p.name);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		String lang = Lang.of(c.getSource());
		Data d = data();
		ok(c, "cmd.list.header", d.points.size());
		int center = d.centerIndex();
		for (int i = 0; i < d.points.size(); i++) {
			CapPoint p = d.points.get(i);
			String mark = i == center ? Lang.str(lang, "cmd.list.center") : "";
			String state = p.complete() ? "" : Lang.str(lang, "cmd.list.incomplete");
			ok(c, "cmd.list.entry", i + 1, p.name, mark, state);
		}
		return d.points.size();
	}

	private static int center(CommandContext<CommandSourceStack> c) {
		String name = StringArgumentType.getString(c, "name");
		CapPoint p = data().find(name);
		if (p == null) return fail(c, "cmd.unknown", name);
		data().center = p.name;
		Game.INSTANCE.save();
		ok(c, "cmd.center", p.name);
		return 1;
	}

	private static int move(CommandContext<CommandSourceStack> c, int delta) {
		String name = StringArgumentType.getString(c, "name");
		CapPoint p = data().find(name);
		if (p == null) return fail(c, "cmd.unknown", name);
		Game.INSTANCE.movePoint(data().points.indexOf(p), delta);
		ok(c, "cmd.moved", p.name, data().points.indexOf(p) + 1);
		return 1;
	}

	private static int edge(CommandContext<CommandSourceStack> c, int which, BlockPos pos) {
		String name = StringArgumentType.getString(c, "name");
		CapPoint p = data().find(name);
		if (p == null) return fail(c, "cmd.unknown", name);
		BlockPos bp = pos != null ? pos : BlockPos.containing(c.getSource().getPosition());
		int[] v = new int[]{bp.getX(), bp.getY(), bp.getZ()};
		if (which == 1) p.p1 = v;
		else p.p2 = v;
		p.dim = c.getSource().getLevel().dimension().toString();
		Game.INSTANCE.save();
		ok(c, "cmd.edge", p.name, which, bp.getX() + " " + bp.getY() + " " + bp.getZ());
		return 1;
	}

	private static int start(CommandContext<CommandSourceStack> c) {
		String[] err = Game.INSTANCE.start();
		if (err != null) return fail(c, err[0], err[1]);
		return 1;
	}

	private static int team(CommandContext<CommandSourceStack> c, Team t) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
		for (ServerPlayer p : players) Game.INSTANCE.setTeam(p, t);
		String lang = Lang.of(c.getSource());
		Component tn = t == null ? Component.literal(Lang.str(lang, "team.none")) : t.display(lang);
		ok(c, "cmd.team", players.size(), tn);
		return players.size();
	}
}
