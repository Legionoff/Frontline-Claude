package dev.frontline;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.BossEvent;

public enum Team {
	RED(ChatFormatting.RED, BossEvent.BossBarColor.RED, 0xFF3030, 0xFF9090, "team.red"),
	BLUE(ChatFormatting.BLUE, BossEvent.BossBarColor.BLUE, 0x3050FF, 0x90B0FF, "team.blue");

	public final ChatFormatting fmt;
	public final BossEvent.BossBarColor barColor;
	public final int fullRgb;
	public final int paleRgb;
	public final String key;

	Team(ChatFormatting fmt, BossEvent.BossBarColor barColor, int fullRgb, int paleRgb, String key) {
		this.fmt = fmt;
		this.barColor = barColor;
		this.fullRgb = fullRgb;
		this.paleRgb = paleRgb;
		this.key = key;
	}

	public Team other() {
		return this == RED ? BLUE : RED;
	}

	public MutableComponent display(String lang) {
		return Lang.comp(lang, key).withStyle(fmt);
	}

	public static Team parse(String s) {
		if (s == null) return null;
		for (Team t : values()) if (t.name().equalsIgnoreCase(s)) return t;
		return null;
	}
}
