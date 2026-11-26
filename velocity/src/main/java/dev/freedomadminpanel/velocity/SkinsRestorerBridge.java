package dev.freedomadminpanel.velocity;

import java.util.Optional;
import java.util.UUID;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinProperty;
import org.jetbrains.annotations.Nullable;

final class SkinsRestorerBridge {
    SkinsRestorerBridge() {
        SkinsRestorerProvider.get();
    }

    @Nullable
    SkinService.Skin lookup(UUID uuid, String name) throws Exception {
        SkinsRestorer api = SkinsRestorerProvider.get();
        Optional<SkinProperty> property = api.getPlayerStorage().getSkinForPlayer(uuid, name);
        return property.<SkinService.Skin>map(p -> new SkinService.Skin(p.getValue(), p.getSignature())).orElse(null);
    }
}
