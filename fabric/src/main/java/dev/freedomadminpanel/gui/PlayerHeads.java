package dev.freedomadminpanel.gui;

import dev.freedomadminpanel.net.SkinCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;

public final class PlayerHeads {
    private PlayerHeads() {
    }

    public static SkinTextures skin(String name) {
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) {
            PlayerListEntry entry = handler.getPlayerListEntry(name);
            if (entry != null) {
                return entry.getSkinTextures();
            }
        }

        SkinTextures fromPlugin = SkinCache.get(name);
        return fromPlugin != null ? fromPlugin : DefaultSkinHelper.getSkinTextures(SkinCache.offlineUuid(name));
    }
}
