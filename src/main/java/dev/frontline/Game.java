package dev.frontline;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Game {
	public static final Game INSTANCE = new Game();

	public enum Phase { STOPPED, RUNNING, PAUSED }

	public MinecraftServer server;
	public Data data = new Data();
	public Phase phase = Phase.STOPPED;

	// state of the active point
	public int activeIdx;
	public Team capTeam;    // team whose progress is on the bar (null = neutral)
	public double capTicks; // 0..cap; at cap the point is captured and the hold bar runs
	public double holdTicks;
	public boolean contested;

	private final Map<String, ServerBossEvent> bars = new HashMap<>();
	private int tickCounter;

	/** player UUID -> index of the point whose new name they are about to type in chat */
	public final Map<java.util.UUID, String> pendingRename = new HashMap<>();

	/** @return null if renamed, otherwise the translation key of the error */
	public String rename(CapPoint p, String newName) {
		if (newName.isBlank()) return "cmd.rename.empty";
		CapPoint other = data.find(newName);
		if (other != null && other != p) return "cmd.exists";
		boolean wasCenter = data.center != null && data.center.equalsIgnoreCase(p.name);
		p.name = newName;
		if (wasCenter) data.center = newName;
		save();
		return null;
	}

	private Game() {}

	// ------------------------------------------------------------------ lifecycle

	public void onStart(MinecraftServer s) {
		this.server = s;
		this.data = Storage.load();
		for (String code : Lang.CODES) {
			ServerBossEvent b = new ServerBossEvent(java.util.UUID.randomUUID(), Component.literal(""), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
			b.setVisible(true);
			bars.put(code, b);
		}
	}

	public void onStop() {
		Storage.save(data);
		hideBars();
		phase = Phase.STOPPED;
	}

	public void save() {
		Storage.save(data);
	}

	// ------------------------------------------------------------------ teams

	public Team teamOf(ServerPlayer p) {
		return Team.parse(data.teams.get(p.getUUID().toString()));
	}

	public void setTeam(ServerPlayer p, Team t) {
		if (t == null) data.teams.remove(p.getUUID().toString());
		else data.teams.put(p.getUUID().toString(), t.name());
		save();
	}

	public int autoBalance() {
		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
		Collections.shuffle(players);
		int i = 0;
		for (ServerPlayer p : players) {
			data.teams.put(p.getUUID().toString(), (i++ % 2 == 0 ? Team.RED : Team.BLUE).name());
		}
		save();
		return players.size();
	}

	// ------------------------------------------------------------------ round control

	/** @return null on success, otherwise {key, arg} describing the problem */
	public String[] start() {
		if (data.points.isEmpty()) return new String[]{"cmd.start.noPoints", ""};
		for (CapPoint p : data.points) {
			if (!p.complete()) return new String[]{"cmd.start.incomplete", p.name};
		}
		activeIdx = data.centerIndex();
		resetPoint();
		phase = Phase.RUNNING;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String lang = Lang.of(p);
			p.sendSystemMessage(Lang.comp(lang, "chat.roundStart", pointName(data.points.get(activeIdx).name)));
		}
		return null;
	}

	public void stop() {
		if (phase == Phase.STOPPED) return;
		phase = Phase.STOPPED;
		hideBars();
		broadcast("chat.roundStop");
	}

	public void pause() {
		if (phase != Phase.RUNNING) return;
		phase = Phase.PAUSED;
		broadcast("chat.roundPause");
	}

	public void resume() {
		if (phase != Phase.PAUSED) return;
		phase = Phase.RUNNING;
		broadcast("chat.roundResume");
	}

	private void resetPoint() {
		capTeam = null;
		capTicks = 0;
		holdTicks = 0;
		contested = false;
	}

	private void broadcast(String key, Object... args) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(Lang.comp(Lang.of(p), key, args));
		}
	}

	private void broadcastTeam(Team t, String key, String pointName) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String lang = Lang.of(p);
			p.sendSystemMessage(Lang.comp(lang, key, t.display(lang), pointName(pointName)));
		}
	}

	// ------------------------------------------------------------------ tick

	public void tick() {
		if (server == null) return;
		if (phase == Phase.STOPPED) return;
		CapPoint pt = data.points.isEmpty() ? null : data.points.get(Math.min(activeIdx, data.points.size() - 1));
		if (pt == null || !pt.complete()) return;

		if (phase == Phase.RUNNING) logic(pt);
		if (phase == Phase.STOPPED) return; // the round ended in this tick: keep the bar hidden
		updateBars(pt);
		tickCounter++;
		if (tickCounter % 10 == 0) drawBorders();
	}

	private int capLimit() {
		return Math.max(1, data.captureSeconds * 20);
	}

	private int holdLimit() {
		return Math.max(1, data.holdSeconds * 20);
	}

	/** Team that has fully captured the point (and is holding it), or null. */
	private Team owner() {
		return capTeam != null && capTicks >= capLimit() ? capTeam : null;
	}

	private void logic(CapPoint pt) {
		int red = 0, blue = 0;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || !p.isAlive()) continue;
			Team t = teamOf(p);
			if (t == null) continue;
			String dim = p.level().dimension().toString();
			if (!pt.contains(dim, p.getX(), p.getY(), p.getZ())) continue;
			if (t == Team.RED) red++;
			else blue++;
		}
		contested = red > 0 && red == blue;
		if (contested) return;

		Team sup = red > blue ? Team.RED : (blue > red ? Team.BLUE : null);
		int cap = capLimit();
		int hold = holdLimit();

		if (sup == null) {
			// empty point: the hold bar rolls back; a finished capture stays (save point), an unfinished one rolls back
			rollback(data.captureDecay, data.holdDecay, false);
			return;
		}

		if (capTeam == null || capTeam == sup) {
			if (capTeam == null) {
				capTeam = sup;
				capTicks = 0;
				holdTicks = 0;
				broadcastTeam(sup, "chat.capStart", pt.name);
			}
			if (capTicks < cap) {
				capTicks++;
				if (capTicks >= cap) {
					capTicks = cap;
					holdTicks = 0;
					broadcastTeam(sup, "chat.holdStart", pt.name);
				}
			} else {
				holdTicks++;
				checkHold(hold, pt);
			}
		} else {
			// the enemy has the progress: their presence speeds the rollback up
			double accel = Math.max(1.0, data.rollbackAccel);
			rollback(data.captureDecay * accel, data.holdDecay * accel, true);
		}
	}

	/** @param eatCapture false: a finished capture is a save point and is kept (only the hold bar rolls back) */
	private void rollback(double capRate, double holdRate, boolean eatCapture) {
		if (capTeam == null) return;
		if (holdTicks > 0) {
			holdTicks = Math.max(0, holdTicks - holdRate);
		} else if (capTicks > 0 && (eatCapture || capTicks < capLimit())) {
			capTicks -= capRate;
			if (capTicks <= 0) {
				capTicks = 0;
				capTeam = null;
			}
		}
	}

	private void checkHold(int hold, CapPoint pt) {
		Team winner = owner();
		if (holdTicks < hold || winner == null) return;
		boolean up = (winner == Team.RED) == data.redPushesUp;
		int next = activeIdx + (up ? 1 : -1);
		if (next < 0 || next >= data.points.size()) {
			finish(winner);
			return;
		}
		String nextName = data.points.get(next).name;
		activeIdx = next;
		resetPoint();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String lang = Lang.of(p);
			p.sendSystemMessage(Lang.comp(lang, "chat.advance", winner.display(lang), pointName(pt.name), pointName(nextName)));
		}
	}

	private static net.minecraft.network.chat.MutableComponent pointName(String name) {
		return Component.literal(name).withStyle(net.minecraft.ChatFormatting.GOLD);
	}

	private void finish(Team winner) {
		phase = Phase.STOPPED;
		hideBars();
		resetPoint();
		activeIdx = data.centerIndex();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String lang = Lang.of(p);
			Team mine = teamOf(p);
			p.sendSystemMessage(Lang.comp(lang, "chat.final", winner.display(lang)));
			net.minecraft.network.chat.MutableComponent title;
			if (mine == winner) {
				title = Lang.comp(lang, "title.victory").withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD);
			} else if (mine != null) {
				title = Lang.comp(lang, "title.defeat").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD);
			} else {
				title = Lang.comp(lang, "title.over").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD);
			}
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Lang.comp(lang, "title.winner", winner.display(lang))));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
		}
	}

	// ------------------------------------------------------------------ boss bar

	private void hideBars() {
		for (ServerBossEvent b : bars.values()) b.removeAllPlayers();
	}

	private void updateBars(CapPoint pt) {
		int n = data.points.size();
		int cap = capLimit();
		int hold = holdLimit();
		Team owner = owner();

		BossEvent.BossBarColor color = BossEvent.BossBarColor.WHITE;
		BossEvent.BossBarOverlay overlay = BossEvent.BossBarOverlay.PROGRESS;
		float progress = 1f;
		String key = "bar.neutral";
		Team shown = null;

		if (phase == Phase.PAUSED) {
			key = "bar.roundPaused";
			progress = barProgress(cap, hold);
		} else if (contested) {
			key = "bar.contested";
			progress = barProgress(cap, hold);
		} else if (capTeam != null && owner == null) {
			key = "bar.capturing";
			shown = capTeam;
			color = capTeam.barColor;
			overlay = BossEvent.BossBarOverlay.NOTCHED_10;
			progress = Math.min(1f, (float) (capTicks / cap));
		} else if (owner != null) {
			key = "bar.holding";
			shown = owner;
			color = owner.barColor;
			progress = Math.min(1f, (float) (holdTicks / hold));
		}

		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String lang = Lang.of(p);
			for (Map.Entry<String, ServerBossEvent> e : bars.entrySet()) {
				if (e.getKey().equals(lang)) e.getValue().addPlayer(p);
				else e.getValue().removePlayer(p);
			}
		}
		for (Map.Entry<String, ServerBossEvent> e : bars.entrySet()) {
			String lang = e.getKey();
			ServerBossEvent b = e.getValue();
			Object[] args = shown == null
					? new Object[]{pt.name}
					: new Object[]{pt.name, shown.display(lang)};
			b.setName(Lang.comp(lang, key, args));
			b.setColor(color);
			b.setOverlay(overlay);
			b.setProgress(progress);
		}
	}

	private float barProgress(int cap, int hold) {
		if (capTeam == null) return 1f;
		if (owner() != null) return Math.min(1f, (float) (holdTicks / hold));
		return Math.min(1f, (float) (capTicks / cap));
	}

	public void removePlayerFromBars(ServerPlayer p) {
		for (ServerBossEvent b : bars.values()) b.removePlayer(p);
	}

	// ------------------------------------------------------------------ borders

	private void drawBorders() {
		for (int i = 0; i < data.points.size(); i++) {
			CapPoint pt = data.points.get(i);
			if (!pt.complete()) continue;
			ServerLevel level = findLevel(pt.dim);
			if (level == null) continue;
			int rgb;
			if (i != activeIdx) {
				rgb = 0x555555;
			} else if (contested || phase == Phase.PAUSED) {
				rgb = 0x888888;
			} else if (owner() != null) {
				rgb = owner().fullRgb;
			} else if (capTeam != null) {
				rgb = capTeam.paleRgb;
			} else {
				rgb = 0xDDDDDD;
			}
			drawRect(level, pt, rgb);
		}
	}

	private ServerLevel findLevel(String dim) {
		for (ServerLevel l : server.getAllLevels()) {
			if (l.dimension().toString().equals(dim)) return l;
		}
		return null;
	}

	private void drawRect(ServerLevel level, CapPoint pt, int rgb) {
		DustParticleOptions opt = new DustParticleOptions(rgb, 1.4f);
		double y = pt.minY() + 0.15;
		double x0 = pt.minX(), x1 = pt.maxX(), z0 = pt.minZ(), z1 = pt.maxZ();
		double perimeter = 2 * ((x1 - x0) + (z1 - z0));
		double step = Math.max(1.0, perimeter / 240.0);
		for (double x = x0; x <= x1; x += step) {
			level.sendParticles(opt, x, y, z0, 1, 0, 0, 0, 0);
			level.sendParticles(opt, x, y, z1, 1, 0, 0, 0, 0);
		}
		for (double z = z0; z <= z1; z += step) {
			level.sendParticles(opt, x0, y, z, 1, 0, 0, 0, 0);
			level.sendParticles(opt, x1, y, z, 1, 0, 0, 0, 0);
		}
	}

	// ------------------------------------------------------------------ helpers for editing

	public void swapSides() {
		data.redPushesUp = !data.redPushesUp;
		save();
	}

	public void movePoint(int idx, int delta) {
		int to = idx + delta;
		if (idx < 0 || idx >= data.points.size() || to < 0 || to >= data.points.size()) return;
		CapPoint p = data.points.remove(idx);
		data.points.add(to, p);
		save();
	}

}
