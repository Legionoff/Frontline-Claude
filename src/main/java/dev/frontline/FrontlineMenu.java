package dev.frontline;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chest-GUI (works for vanilla clients).
 * Operators get a hub with sub-pages (round, points, teams, settings); everybody else only gets a small
 * menu for choosing their own team. Permissions are re-checked on every click.
 */
public final class FrontlineMenu extends ChestMenu {
	private enum Page { HUB, ROUND, SETTINGS, POINTS, POINT_EDIT, TEAMS, PLAYER }

	private static final String PRESET = "default";
	private static final int POINTS_FIRST = 9;
	private static final int POINT_MAX = 27;
	private static final int PLAYERS_PER_PAGE = 36;

	private final ServerPlayer viewer;
	private final Container container;
	private Page page;
	private int editing = -1;
	private int teamPage = 0;

	private FrontlineMenu(int id, Inventory inv, Container container, ServerPlayer viewer, Page page, int editing) {
		super(page == Page.PLAYER ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6, id, inv, container, page == Page.PLAYER ? 3 : 6);
		this.container = container;
		this.viewer = viewer;
		this.page = page;
		this.editing = editing;
		refresh();
	}

	// ------------------------------------------------------------------ opening

	public static boolean canUse(ServerPlayer p) {
		return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(p.createCommandSourceStack());
	}

	/** Operators: full hub. Everybody else: the small team menu. */
	public static void open(ServerPlayer p) {
		if (canUse(p)) show(p, Page.HUB, -1);
		else show(p, Page.PLAYER, -1);
	}

	public static void openEditing(ServerPlayer p, int editing) {
		if (canUse(p)) show(p, Page.POINT_EDIT, editing);
		else show(p, Page.PLAYER, -1);
	}

	private static void show(ServerPlayer p, Page page, int editing) {
		String lang = Lang.of(p);
		int size = page == Page.PLAYER ? 27 : 54;
		p.openMenu(new SimpleMenuProvider(
				(id, inv, player) -> new FrontlineMenu(id, inv, new SimpleContainer(size), p, page, editing),
				Lang.comp(lang, page == Page.PLAYER ? "menu.titlePlayer" : "menu.title")));
	}

	// ------------------------------------------------------------------ item helpers

	private static ItemStack item(ItemLike it, String name, ChatFormatting color, String... lore) {
		ItemStack s = new ItemStack(it);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name).setStyle(Style.EMPTY.withItalic(false).withColor(color)));
		if (lore.length > 0) {
			List<Component> l = new ArrayList<>();
			for (String line : lore) l.add(Component.literal(line).setStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY)));
			s.set(DataComponents.LORE, new ItemLore(l));
		}
		return s;
	}

	private static ItemStack filler() {
		return item(Items.STAINED_GLASS_PANE.gray(), " ", ChatFormatting.DARK_GRAY);
	}

	private static String fmt(double v) {
		return (v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v)) + "X";
	}

	/** steps through lo/2 .. hi/2 in 0.5 increments (lo and hi in half-steps), wrapping around */
	private static double cycle(double v, boolean forward, int lo, int hi) {
		int i = Math.min(hi, Math.max(lo, (int) Math.round(v * 2))) + (forward ? 1 : -1);
		if (i > hi) i = lo;
		if (i < lo) i = hi;
		return i / 2.0;
	}

	private static String coords(int[] p, String lang) {
		return p == null ? Lang.str(lang, "item.notSet") : p[0] + " " + p[1] + " " + p[2];
	}

	private static ItemLike banner(Team t) {
		if (t == Team.RED) return Items.BANNER.red();
		if (t == Team.BLUE) return Items.BANNER.blue();
		return Items.BANNER.white();
	}

	private static ChatFormatting color(Team t) {
		return t == null ? ChatFormatting.WHITE : t.fmt;
	}

	private static String teamName(String lang, Team t) {
		return t == null ? Lang.str(lang, "team.none") : Lang.str(lang, t.key);
	}

	private static String stateText(String lang) {
		return Lang.str(lang, switch (Game.INSTANCE.phase) {
			case STOPPED -> "menu.state.stopped";
			case RUNNING -> "menu.state.running";
			case PAUSED -> "menu.state.paused";
		});
	}

	private static void back(ContainerSetter c, String lang, int slot) {
		c.set(slot, item(Items.OAK_DOOR, Lang.str(lang, "item.back"), ChatFormatting.YELLOW));
	}

	private interface ContainerSetter {
		void set(int slot, ItemStack s);
	}

	// ------------------------------------------------------------------ rendering

	private void refresh() {
		String lang = Lang.of(viewer);
		for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, filler());
		ContainerSetter c = container::setItem;
		switch (page) {
			case HUB -> renderHub(c, lang);
			case ROUND -> renderRound(c, lang);
			case SETTINGS -> renderSettings(c, lang);
			case POINTS -> renderPoints(c, lang);
			case POINT_EDIT -> renderPointEdit(c, lang);
			case TEAMS -> renderTeams(c, lang);
			case PLAYER -> renderPlayer(c, lang);
		}
	}

	private void renderHub(ContainerSetter c, String lang) {
		Team mine = Game.INSTANCE.teamOf(viewer);
		c.set(4, item(Items.NETHER_STAR, Lang.str(lang, "menu.title"), ChatFormatting.GOLD,
				stateText(lang), Lang.str(lang, "menu.yourTeam", teamName(lang, mine))));
		c.set(19, item(Items.CLOCK, Lang.str(lang, "menu.hub.round"), ChatFormatting.GREEN, Lang.str(lang, "menu.hub.round.lore")));
		c.set(21, item(Items.BEACON, Lang.str(lang, "menu.hub.points"), ChatFormatting.GOLD, Lang.str(lang, "menu.hub.points.lore")));
		c.set(23, item(banner(null), Lang.str(lang, "menu.hub.teams"), ChatFormatting.AQUA, Lang.str(lang, "menu.hub.teams.lore")));
		c.set(25, item(Items.COMPARATOR, Lang.str(lang, "menu.hub.settings"), ChatFormatting.LIGHT_PURPLE, Lang.str(lang, "menu.hub.settings.lore")));
		c.set(38, item(banner(Team.RED), Lang.str(lang, "item.joinRed"), ChatFormatting.RED));
		c.set(40, item(banner(null), Lang.str(lang, "item.leave"), ChatFormatting.WHITE));
		c.set(42, item(banner(Team.BLUE), Lang.str(lang, "item.joinBlue"), ChatFormatting.BLUE));
	}

	private void renderRound(ContainerSetter c, String lang) {
		Game g = Game.INSTANCE;
		c.set(4, item(Items.CLOCK, Lang.str(lang, "menu.hub.round"), ChatFormatting.GREEN, stateText(lang)));
		c.set(20, item(Items.CONCRETE.lime(), Lang.str(lang, "item.start"), ChatFormatting.GREEN));
		c.set(22, item(Items.CONCRETE.yellow(), Lang.str(lang, g.phase == Game.Phase.PAUSED ? "item.resume" : "item.pause"), ChatFormatting.YELLOW));
		c.set(24, item(Items.CONCRETE.red(), Lang.str(lang, "item.stop"), ChatFormatting.RED));
		c.set(31, item(Items.COMPASS, Lang.str(lang, "item.swap"), ChatFormatting.AQUA,
				Lang.str(lang, g.data.redPushesUp ? "cmd.swapUp" : "cmd.swapDown")));
		back(c, lang, 49);
	}

	private void renderSettings(ContainerSetter c, String lang) {
		Data d = Game.INSTANCE.data;
		c.set(4, item(Items.COMPARATOR, Lang.str(lang, "menu.hub.settings"), ChatFormatting.LIGHT_PURPLE));
		c.set(10, item(Items.STAINED_GLASS_PANE.red(), "-10", ChatFormatting.RED));
		c.set(11, item(Items.CLOCK, Lang.str(lang, "item.capTime", d.captureSeconds), ChatFormatting.WHITE));
		c.set(12, item(Items.STAINED_GLASS_PANE.lime(), "+10", ChatFormatting.GREEN));
		c.set(14, item(Items.STAINED_GLASS_PANE.red(), "-10", ChatFormatting.RED));
		c.set(15, item(Items.CLOCK, Lang.str(lang, "item.holdTime", d.holdSeconds), ChatFormatting.WHITE));
		c.set(16, item(Items.STAINED_GLASS_PANE.lime(), "+10", ChatFormatting.GREEN));
		String l1 = Lang.str(lang, "item.cycle.lore1"), l2 = Lang.str(lang, "item.cycle.lore2");
		c.set(29, item(Items.REPEATER, Lang.str(lang, "item.capDecay", fmt(d.captureDecay)), ChatFormatting.WHITE, Lang.str(lang, "item.capDecay.lore"), l1, l2));
		c.set(31, item(Items.COMPARATOR, Lang.str(lang, "item.holdDecay", fmt(d.holdDecay)), ChatFormatting.WHITE, Lang.str(lang, "item.holdDecay.lore"), l1, l2));
		c.set(33, item(Items.SUGAR, Lang.str(lang, "item.rollbackAccel", fmt(d.rollbackAccel)), ChatFormatting.AQUA, Lang.str(lang, "item.rollbackAccel.lore"), l1, l2));
		c.set(38, item(Items.WRITABLE_BOOK, Lang.str(lang, "item.savePreset", PRESET), ChatFormatting.GOLD));
		c.set(42, item(Items.BOOK, Lang.str(lang, "item.loadPreset", PRESET), ChatFormatting.GOLD));
		back(c, lang, 49);
	}

	private void renderPoints(ContainerSetter c, String lang) {
		Data d = Game.INSTANCE.data;
		c.set(4, item(Items.BEACON, Lang.str(lang, "menu.hub.points"), ChatFormatting.GOLD));
		int center = d.centerIndex();
		for (int i = 0; i < d.points.size() && i < POINT_MAX; i++) {
			CapPoint p = d.points.get(i);
			String title = (i + 1) + ". " + p.name + (i == center ? " " + Lang.str(lang, "cmd.list.center") : "")
					+ (p.complete() ? "" : " " + Lang.str(lang, "cmd.list.incomplete"));
			c.set(POINTS_FIRST + i, item(i == center ? Items.BEACON : Items.CONCRETE.white(), title,
					i == center ? ChatFormatting.GOLD : ChatFormatting.WHITE,
					Lang.str(lang, "item.point.lore1"), Lang.str(lang, "item.point.lore2"), Lang.str(lang, "item.point.lore3")));
		}
		back(c, lang, 49);
		c.set(50, item(Items.NETHER_STAR, Lang.str(lang, "item.createPoint"), ChatFormatting.GOLD));
	}

	private void renderPointEdit(ContainerSetter c, String lang) {
		Data d = Game.INSTANCE.data;
		if (editing < 0 || editing >= d.points.size()) {
			page = Page.POINTS;
			renderPoints(c, lang);
			return;
		}
		CapPoint p = d.points.get(editing);
		c.set(4, item(Items.BEACON, Lang.str(lang, "item.pointInfo", p.name, editing + 1, d.points.size()), ChatFormatting.GOLD,
				editing == d.centerIndex() ? Lang.str(lang, "cmd.list.center") : "-"));
		c.set(10, item(Items.DYE.lime(), Lang.str(lang, "item.setPos1"), ChatFormatting.GREEN, coords(p.p1, lang)));
		c.set(12, item(Items.DYE.cyan(), Lang.str(lang, "item.setPos2"), ChatFormatting.AQUA, coords(p.p2, lang)));
		c.set(14, item(Items.NETHER_STAR, Lang.str(lang, "item.setCenter"), ChatFormatting.GOLD));
		c.set(16, item(Items.BARRIER, Lang.str(lang, "item.delete"), ChatFormatting.RED));
		c.set(22, item(Items.NAME_TAG, Lang.str(lang, "item.rename"), ChatFormatting.YELLOW, p.name));
		c.set(28, item(Items.ARROW, Lang.str(lang, "item.moveEarlier"), ChatFormatting.WHITE));
		c.set(30, item(Items.SPECTRAL_ARROW, Lang.str(lang, "item.moveLater"), ChatFormatting.WHITE));
		back(c, lang, 49);
	}

	private List<ServerPlayer> sortedPlayers() {
		List<ServerPlayer> l = new ArrayList<>(Game.INSTANCE.server.getPlayerList().getPlayers());
		l.sort(Comparator.comparing(p -> p.getGameProfile().name().toLowerCase()));
		return l;
	}

	private void renderTeams(ContainerSetter c, String lang) {
		Game g = Game.INSTANCE;
		List<ServerPlayer> players = sortedPlayers();
		int maxPage = Math.max(0, (players.size() - 1) / PLAYERS_PER_PAGE);
		teamPage = Math.min(teamPage, maxPage);
		for (int i = 0; i < PLAYERS_PER_PAGE; i++) {
			int idx = teamPage * PLAYERS_PER_PAGE + i;
			if (idx >= players.size()) break;
			ServerPlayer p = players.get(idx);
			Team t = g.teamOf(p);
			c.set(i, item(banner(t), p.getGameProfile().name(), color(t), Lang.str(lang, "menu.team", teamName(lang, t)),
					Lang.str(lang, "menu.players.lore1"), Lang.str(lang, "menu.players.lore2"), Lang.str(lang, "menu.players.lore3")));
		}
		if (teamPage > 0) c.set(45, item(Items.ARROW, Lang.str(lang, "item.page.prev"), ChatFormatting.YELLOW));
		if (teamPage < maxPage) c.set(53, item(Items.ARROW, Lang.str(lang, "item.page.next"), ChatFormatting.YELLOW));
		c.set(46, item(Items.PAPER, Lang.str(lang, "menu.counts", g.count(Team.RED), g.count(Team.BLUE), g.count(null)), ChatFormatting.WHITE));
		c.set(47, item(Items.ENDER_EYE, Lang.str(lang, "item.auto"), ChatFormatting.LIGHT_PURPLE));
		c.set(48, item(Items.BARRIER, Lang.str(lang, "item.clearTeams"), ChatFormatting.RED));
		back(c, lang, 49);
	}

	private void renderPlayer(ContainerSetter c, String lang) {
		Game g = Game.INSTANCE;
		Team mine = g.teamOf(viewer);
		c.set(4, item(Items.NETHER_STAR, Lang.str(lang, "menu.titlePlayer"), ChatFormatting.GOLD,
				Lang.str(lang, "menu.yourTeam", teamName(lang, mine)), stateText(lang)));
		c.set(11, item(banner(Team.RED), Lang.str(lang, "item.joinRed"), ChatFormatting.RED, Lang.str(lang, "menu.playersCount", g.count(Team.RED))));
		c.set(13, item(banner(null), Lang.str(lang, "item.leave"), ChatFormatting.WHITE));
		c.set(15, item(banner(Team.BLUE), Lang.str(lang, "item.joinBlue"), ChatFormatting.BLUE, Lang.str(lang, "menu.playersCount", g.count(Team.BLUE))));
	}

	// ------------------------------------------------------------------ interaction

	private void join(ServerPlayer p, Team t) {
		Game.INSTANCE.setTeam(p, t);
		String lang = Lang.of(p);
		p.sendSystemMessage(t == null ? Lang.comp(lang, "cmd.left") : Lang.comp(lang, "cmd.joined", t.display(lang)));
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		if (!(player instanceof ServerPlayer sp)) return;
		if (slotId < 0 || slotId >= container.getContainerSize()) return;
		String lang = Lang.of(sp);

		if (page == Page.PLAYER) {
			switch (slotId) {
				case 11 -> join(sp, Team.RED);
				case 13 -> join(sp, null);
				case 15 -> join(sp, Team.BLUE);
				default -> { }
			}
			refresh();
			broadcastChanges();
			return;
		}

		if (!canUse(sp)) { // permission was taken away while the menu was open
			sp.sendSystemMessage(Lang.comp(lang, "cmd.noPermission"));
			open(sp);
			return;
		}

		switch (page) {
			case HUB -> clickHub(sp, slotId);
			case ROUND -> clickRound(sp, slotId, lang);
			case SETTINGS -> clickSettings(sp, slotId, button, lang);
			case POINTS -> clickPoints(sp, slotId, button, input, lang);
			case POINT_EDIT -> clickPointEdit(sp, slotId, lang);
			case TEAMS -> clickTeams(sp, slotId, button, input);
			default -> { }
		}
		refresh();
		broadcastChanges();
	}

	private void clickHub(ServerPlayer sp, int slot) {
		switch (slot) {
			case 19 -> page = Page.ROUND;
			case 21 -> page = Page.POINTS;
			case 23 -> { page = Page.TEAMS; teamPage = 0; }
			case 25 -> page = Page.SETTINGS;
			case 38 -> join(sp, Team.RED);
			case 40 -> join(sp, null);
			case 42 -> join(sp, Team.BLUE);
			default -> { }
		}
	}

	private void clickRound(ServerPlayer sp, int slot, String lang) {
		Game g = Game.INSTANCE;
		switch (slot) {
			case 20 -> {
				String[] err = g.start();
				if (err != null) sp.sendSystemMessage(Lang.comp(lang, err[0], err[1]));
			}
			case 22 -> {
				if (g.phase == Game.Phase.PAUSED) g.resume();
				else g.pause();
			}
			case 24 -> g.stop();
			case 31 -> g.swapSides();
			case 49 -> page = Page.HUB;
			default -> { }
		}
	}

	private void clickSettings(ServerPlayer sp, int slot, int button, String lang) {
		Game g = Game.INSTANCE;
		Data d = g.data;
		switch (slot) {
			case 10 -> d.captureSeconds = Math.max(1, d.captureSeconds - 10);
			case 12 -> d.captureSeconds = Math.min(3600, d.captureSeconds + 10);
			case 14 -> d.holdSeconds = Math.max(1, d.holdSeconds - 10);
			case 16 -> d.holdSeconds = Math.min(3600, d.holdSeconds + 10);
			case 29 -> d.captureDecay = cycle(d.captureDecay, button != 1, 1, 5);
			case 31 -> d.holdDecay = cycle(d.holdDecay, button != 1, 1, 5);
			case 33 -> d.rollbackAccel = cycle(d.rollbackAccel, button != 1, 2, 10);
			case 38 -> {
				Storage.savePreset(d, PRESET);
				sp.sendSystemMessage(Lang.comp(lang, "cmd.preset.saved", PRESET));
			}
			case 42 -> {
				if (Cmds.loadPreset(PRESET)) sp.sendSystemMessage(Lang.comp(lang, "cmd.preset.loaded", PRESET));
				else sp.sendSystemMessage(Lang.comp(lang, "cmd.preset.missing", PRESET));
			}
			case 49 -> page = Page.HUB;
			default -> { }
		}
		g.save();
	}

	private void clickPoints(ServerPlayer sp, int slot, int button, ContainerInput input, String lang) {
		Game g = Game.INSTANCE;
		Data d = g.data;
		if (slot == 49) {
			page = Page.HUB;
			return;
		}
		if (slot == 50) {
			if (d.points.size() < POINT_MAX) {
				int n = d.points.size() + 1;
				String name;
				do name = Lang.str(lang, "point.defaultName") + " " + n++; while (d.find(name) != null);
				d.points.add(new CapPoint(name));
				if (d.center == null) d.center = name;
				g.save();
				editing = d.points.size() - 1;
				page = Page.POINT_EDIT;
			}
			return;
		}
		int idx = slot - POINTS_FIRST;
		if (idx < 0 || idx >= d.points.size()) return;
		if (button == 1 && input == ContainerInput.QUICK_MOVE) g.movePoint(idx, 1);   // shift + right click: later
		else if (button == 1) g.movePoint(idx, -1);                                   // right click: earlier
		else { editing = idx; page = Page.POINT_EDIT; }                               // left click: settings
	}

	private void clickPointEdit(ServerPlayer sp, int slot, String lang) {
		Game g = Game.INSTANCE;
		Data d = g.data;
		if (editing < 0 || editing >= d.points.size()) {
			page = Page.POINTS;
			return;
		}
		CapPoint p = d.points.get(editing);
		switch (slot) {
			case 10, 12 -> {
				int[] v = new int[]{sp.blockPosition().getX(), sp.blockPosition().getY(), sp.blockPosition().getZ()};
				if (slot == 10) p.p1 = v;
				else p.p2 = v;
				p.dim = sp.level().dimension().toString();
				g.save();
				sp.sendSystemMessage(Lang.comp(lang, "cmd.edge", p.name, slot == 10 ? 1 : 2, v[0] + " " + v[1] + " " + v[2]));
			}
			case 14 -> {
				d.center = p.name;
				g.save();
			}
			case 16 -> {
				g.stop();
				d.points.remove(p);
				if (d.center != null && d.center.equalsIgnoreCase(p.name)) d.center = null;
				g.save();
				sp.sendSystemMessage(Lang.comp(lang, "cmd.removed", p.name));
				editing = -1;
				page = Page.POINTS;
			}
			case 22 -> {
				g.pendingRename.put(sp.getUUID(), p.name);
				sp.sendSystemMessage(Lang.comp(lang, "item.renamePrompt", p.name));
				sp.closeContainer();
			}
			case 28 -> {
				g.movePoint(editing, -1);
				editing = d.points.indexOf(p);
			}
			case 30 -> {
				g.movePoint(editing, 1);
				editing = d.points.indexOf(p);
			}
			case 49 -> page = Page.POINTS;
			default -> { }
		}
	}

	private void clickTeams(ServerPlayer sp, int slot, int button, ContainerInput input) {
		Game g = Game.INSTANCE;
		String lang = Lang.of(sp);
		if (slot < PLAYERS_PER_PAGE) {
			List<ServerPlayer> players = sortedPlayers();
			int idx = teamPage * PLAYERS_PER_PAGE + slot;
			if (idx >= players.size()) return;
			ServerPlayer target = players.get(idx);
			Team t;
			if (input == ContainerInput.QUICK_MOVE) t = null;   // shift + click: no team
			else if (button == 1) t = Team.BLUE;                // right click
			else t = Team.RED;                                  // left click
			if (target == sp) join(sp, t);
			else {
				g.setTeam(target, t);
				String tl = Lang.of(target);
				target.sendSystemMessage(t == null ? Lang.comp(tl, "cmd.left") : Lang.comp(tl, "cmd.joined", t.display(tl)));
			}
			return;
		}
		switch (slot) {
			case 45 -> teamPage = Math.max(0, teamPage - 1);
			case 53 -> teamPage++;
			case 47 -> {
				int n = g.autoBalance();
				sp.sendSystemMessage(Lang.comp(lang, "cmd.teamAuto", n));
			}
			case 48 -> {
				g.clearTeams();
				sp.sendSystemMessage(Lang.comp(lang, "cmd.teamsCleared"));
			}
			case 49 -> page = Page.HUB;
			default -> { }
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
