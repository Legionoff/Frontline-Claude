package dev.frontline;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent by the optional client keybind to ask the server to open the control menu. */
public record OpenMenuPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenMenuPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("frontline", "open_menu"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenuPayload> CODEC =
			StreamCodec.unit(new OpenMenuPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
