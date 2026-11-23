package dev.frontline.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.frontline.OpenMenuPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Optional client part: a rebindable key that opens the control menu on the server. */
public class FrontlineClient implements ClientModInitializer {
	private static KeyMapping openMenu;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("frontline", "general"));
		openMenu = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.frontline.menu", InputConstants.KEY_K, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenu.consumeClick()) {
				if (ClientPlayNetworking.canSend(OpenMenuPayload.TYPE)) {
					ClientPlayNetworking.send(new OpenMenuPayload());
				}
			}
		});
	}
}
