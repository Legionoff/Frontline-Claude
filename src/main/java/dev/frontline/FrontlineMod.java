package dev.frontline;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrontlineMod implements ModInitializer {
	public static final String MOD_ID = "frontline";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Lang.load();

		PayloadTypeRegistry.serverboundPlay().register(OpenMenuPayload.TYPE, OpenMenuPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenMenuPayload.TYPE, (payload, context) -> {
			FrontlineMenu.open(context.player()); // operators get the hub, everybody else the team menu
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				Cmds.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(Game.INSTANCE::onStart);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Game.INSTANCE.onStop());
		ServerTickEvents.END_SERVER_TICK.register(server -> Game.INSTANCE.tick());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				Game.INSTANCE.removePlayerFromBars(handler.getPlayer()));

		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			String pointName = Game.INSTANCE.pendingRename.remove(sender.getUUID());
			if (pointName == null) return true;
			if (!FrontlineMenu.canUse(sender)) return true;
			String text = message.signedContent().trim();
			String lang = Lang.of(sender);
			CapPoint p = Game.INSTANCE.data.find(pointName);
			int idx = Game.INSTANCE.data.points.indexOf(p);
			if (p != null && !text.equalsIgnoreCase("cancel") && !text.equalsIgnoreCase("отмена") && !text.equalsIgnoreCase("скасувати")) {
				String old = p.name;
				String err = Game.INSTANCE.rename(p, text);
				if (err != null) sender.sendSystemMessage(Lang.comp(lang, err, text));
				else sender.sendSystemMessage(Lang.comp(lang, "cmd.rename", old, text));
			}
			if (p != null) FrontlineMenu.openEditing(sender, idx);
			return false;
		});

		LOGGER.info("Frontline Gamemode loaded");
	}
}
