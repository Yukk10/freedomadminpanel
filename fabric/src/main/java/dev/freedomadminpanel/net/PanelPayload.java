package dev.freedomadminpanel.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PanelPayload(byte[] data) implements CustomPayload {
    public static final Id<PanelPayload> ID = new Id<>(Identifier.of("freedomadminpanel", "main"));
    public static final PacketCodec<RegistryByteBuf, PanelPayload> CODEC = PacketCodec.of((value, buf) -> buf.writeBytes(value.data()), buf -> {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new PanelPayload(data);
    });

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
