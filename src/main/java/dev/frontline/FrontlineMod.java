package dev.frontline;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

		PayloadTypeRegistry.playC2S().register(OpenMenuPayload.TYPE, OpenMenuPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenMenuPayload.TYPE, (payload, context) -> {
			if (FrontlineMenu.canUse(context.player())) {
				FrontlineMenu.open(context.player());
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				Cmds.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(Game.INSTANCE::onStart);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Game.INSTANCE.onStop());
		ServerTickEvents.END_SERVER_TICK.register(server -> Game.INSTANCE.tick());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				Game.INSTANCE.removePlayerFromBars(handler.getPlayer()));

		LOGGER.info("Frontline Gamemode loaded");
	}
}
