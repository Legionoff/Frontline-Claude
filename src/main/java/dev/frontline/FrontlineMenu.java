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
import java.util.List;

/** Chest-GUI control panel; works for vanilla clients. */
public final class FrontlineMenu extends ChestMenu {
	private static final String PRESET = "default";
	private static final int POINT_START = 27;
	private static final int POINT_MAX = 27;

	private final ServerPlayer viewer;
	private final Container container;

	private FrontlineMenu(int id, Inventory inv, Container container, ServerPlayer viewer) {
		super(MenuType.GENERIC_9x6, id, inv, container, 6);
		this.container = container;
		this.viewer = viewer;
		refresh();
	}

	public static boolean canUse(ServerPlayer p) {
		return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(p.createCommandSourceStack());
	}

	public static void open(ServerPlayer p) {
		String lang = Lang.of(p);
		p.openMenu(new SimpleMenuProvider(
				(id, inv, player) -> new FrontlineMenu(id, inv, new SimpleContainer(54), p),
				Lang.comp(lang, "menu.title")));
	}

	// ------------------------------------------------------------------ rendering

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

	private int editing = -1;

	private static String coords(int[] p, String lang) {
		return p == null ? Lang.str(lang, "item.notSet") : p[0] + " " + p[1] + " " + p[2];
	}

	private void refresh() {
		String lang = Lang.of(viewer);
		Game g = Game.INSTANCE;
		Data d = g.data;
		for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, ItemStack.EMPTY);

		if (editing >= 0 && editing < d.points.size()) {
			CapPoint p = d.points.get(editing);
			container.setItem(4, item(Items.BEACON, Lang.str(lang, "item.pointInfo", p.name, editing + 1, d.points.size()), ChatFormatting.GOLD,
					editing == d.centerIndex() ? Lang.str(lang, "cmd.list.center") : "-"));
			container.setItem(10, item(Items.DYE.lime(), Lang.str(lang, "item.setPos1"), ChatFormatting.GREEN, coords(p.p1, lang)));
			container.setItem(12, item(Items.DYE.cyan(), Lang.str(lang, "item.setPos2"), ChatFormatting.AQUA, coords(p.p2, lang)));
			container.setItem(14, item(Items.NETHER_STAR, Lang.str(lang, "item.setCenter"), ChatFormatting.GOLD));
			container.setItem(16, item(Items.BARRIER, Lang.str(lang, "item.delete"), ChatFormatting.RED));
			container.setItem(28, item(Items.ARROW, Lang.str(lang, "item.moveEarlier"), ChatFormatting.WHITE));
			container.setItem(30, item(Items.SPECTRAL_ARROW, Lang.str(lang, "item.moveLater"), ChatFormatting.WHITE));
			container.setItem(49, item(Items.OAK_DOOR, Lang.str(lang, "item.back"), ChatFormatting.YELLOW));
			return;
		}
		editing = -1;

		container.setItem(0, item(Items.CONCRETE.lime(), Lang.str(lang, "item.start"), ChatFormatting.GREEN));
		if (g.phase == Game.Phase.PAUSED) {
			container.setItem(1, item(Items.CONCRETE.yellow(), Lang.str(lang, "item.resume"), ChatFormatting.YELLOW));
		} else {
			container.setItem(1, item(Items.CONCRETE.yellow(), Lang.str(lang, "item.pause"), ChatFormatting.YELLOW));
		}
		container.setItem(2, item(Items.CONCRETE.red(), Lang.str(lang, "item.stop"), ChatFormatting.RED));
		container.setItem(4, item(Items.COMPASS, Lang.str(lang, "item.swap"), ChatFormatting.AQUA,
				Lang.str(lang, d.redPushesUp ? "cmd.swapUp" : "cmd.swapDown")));
		container.setItem(6, item(Items.WRITABLE_BOOK, Lang.str(lang, "item.savePreset", PRESET), ChatFormatting.GOLD));
		container.setItem(7, item(Items.BOOK, Lang.str(lang, "item.loadPreset", PRESET), ChatFormatting.GOLD));

		container.setItem(9, item(Items.STAINED_GLASS_PANE.red(), "-10", ChatFormatting.RED));
		container.setItem(10, item(Items.CLOCK, Lang.str(lang, "item.capTime", d.captureSeconds), ChatFormatting.WHITE));
		container.setItem(11, item(Items.STAINED_GLASS_PANE.lime(), "+10", ChatFormatting.GREEN));
		container.setItem(13, item(Items.STAINED_GLASS_PANE.red(), "-10", ChatFormatting.RED));
		container.setItem(14, item(Items.CLOCK, Lang.str(lang, "item.holdTime", d.holdSeconds), ChatFormatting.WHITE));
		container.setItem(15, item(Items.STAINED_GLASS_PANE.lime(), "+10", ChatFormatting.GREEN));

		container.setItem(18, item(Items.BANNER.red(), Lang.str(lang, "item.joinRed"), ChatFormatting.RED));
		container.setItem(19, item(Items.BANNER.blue(), Lang.str(lang, "item.joinBlue"), ChatFormatting.BLUE));
		container.setItem(20, item(Items.BANNER.white(), Lang.str(lang, "item.leave"), ChatFormatting.WHITE));
		container.setItem(21, item(Items.ENDER_EYE, Lang.str(lang, "item.auto"), ChatFormatting.LIGHT_PURPLE));
		container.setItem(22, item(Items.NETHER_STAR, Lang.str(lang, "item.createPoint"), ChatFormatting.GOLD));

		int center = d.centerIndex();
		for (int i = 0; i < d.points.size() && i < POINT_MAX; i++) {
			CapPoint p = d.points.get(i);
			String title = (i + 1) + ". " + p.name + (i == center ? " " + Lang.str(lang, "cmd.list.center") : "")
					+ (p.complete() ? "" : " " + Lang.str(lang, "cmd.list.incomplete"));
			container.setItem(POINT_START + i, item(i == center ? Items.BEACON : Items.CONCRETE.white(), title,
					i == center ? ChatFormatting.GOLD : ChatFormatting.WHITE,
					Lang.str(lang, "item.point.lore1")));
		}
	}

	// ------------------------------------------------------------------ interaction

	private void editPoint(ServerPlayer sp, Game g, Data d, int slotId, String lang) {
		CapPoint p = d.points.get(editing);
		switch (slotId) {
			case 10, 12 -> {
				int[] v = new int[]{sp.blockPosition().getX(), sp.blockPosition().getY(), sp.blockPosition().getZ()};
				if (slotId == 10) p.p1 = v;
				else p.p2 = v;
				p.dim = sp.level().dimension().toString();
				g.save();
				sp.sendSystemMessage(Lang.comp(lang, "cmd.edge", p.name, slotId == 10 ? 1 : 2, v[0] + " " + v[1] + " " + v[2]));
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
			}
			case 28 -> {
				g.movePoint(editing, -1);
				editing = d.points.indexOf(p);
			}
			case 30 -> {
				g.movePoint(editing, 1);
				editing = d.points.indexOf(p);
			}
			case 49 -> editing = -1;
			default -> { }
		}
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (!(player instanceof ServerPlayer sp) || !canUse(sp)) return;
		if (slotId < 0 || slotId >= 54) return;
		Game g = Game.INSTANCE;
		Data d = g.data;
		String lang = Lang.of(sp);

		if (editing >= 0 && editing < d.points.size()) {
			editPoint(sp, g, d, slotId, lang);
			refresh();
			broadcastChanges();
			return;
		}

		switch (slotId) {
			case 0 -> {
				String[] err = g.start();
				if (err != null) sp.sendSystemMessage(Lang.comp(lang, err[0], err[1]));
			}
			case 1 -> {
				if (g.phase == Game.Phase.PAUSED) g.resume();
				else g.pause();
			}
			case 2 -> g.stop();
			case 4 -> g.swapSides();
			case 6 -> {
				Storage.savePreset(d, PRESET);
				sp.sendSystemMessage(Lang.comp(lang, "cmd.preset.saved", PRESET));
			}
			case 7 -> {
				if (Cmds.loadPreset(PRESET)) sp.sendSystemMessage(Lang.comp(lang, "cmd.preset.loaded", PRESET));
				else sp.sendSystemMessage(Lang.comp(lang, "cmd.preset.missing", PRESET));
			}
			case 9 -> { d.captureSeconds = Math.max(1, d.captureSeconds - 10); g.save(); }
			case 11 -> { d.captureSeconds = Math.min(3600, d.captureSeconds + 10); g.save(); }
			case 13 -> { d.holdSeconds = Math.max(1, d.holdSeconds - 10); g.save(); }
			case 15 -> { d.holdSeconds = Math.min(3600, d.holdSeconds + 10); g.save(); }
			case 18 -> g.setTeam(sp, Team.RED);
			case 19 -> g.setTeam(sp, Team.BLUE);
			case 20 -> g.setTeam(sp, null);
			case 21 -> g.autoBalance();
			case 22 -> {
				if (d.points.size() < POINT_MAX) {
					int n = d.points.size() + 1;
					String name;
					do name = Lang.str(lang, "point.defaultName") + " " + n++; while (d.find(name) != null);
					d.points.add(new CapPoint(name));
					if (d.center == null) d.center = name;
					g.save();
					editing = d.points.size() - 1;
				}
			}
			default -> {
				int idx = slotId - POINT_START;
				if (idx >= 0 && idx < d.points.size()) editing = idx;
			}
		}
		refresh();
		broadcastChanges();
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
