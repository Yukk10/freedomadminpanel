package dev.freedomadminpanel.mixin;

import dev.freedomadminpanel.spectate.SpectateMode;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayNetworkHandler.class})
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(
        method = {"onPlayerAbilities(Lnet/minecraft/network/packet/s2c/play/PlayerAbilitiesS2CPacket;)V"},
        at = {@At("RETURN")}
    )
    private void freedomadminpanel$afterAbilities(PlayerAbilitiesS2CPacket packet, CallbackInfo ci) {
        SpectateMode.onServerAbilities();
    }

    @Inject(
        method = {"onPlayerRespawn(Lnet/minecraft/network/packet/s2c/play/PlayerRespawnS2CPacket;)V"},
        at = {@At("RETURN")}
    )
    private void freedomadminpanel$afterRespawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        SpectateMode.onServerAbilities();
    }
}
