package dev.freedomadminpanel.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile.Property;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

final class SkinService {
    private static final long TTL = 600000L;
    private final ProxyServer proxy;
    private final Logger logger;
    @Nullable
    private final SkinsRestorerBridge skinsRestorer;
    private final Map<String, SkinService.Cached> cache = new ConcurrentHashMap<>();

    SkinService(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        SkinsRestorerBridge bridge = null;
        if (proxy.getPluginManager().getPlugin("skinsrestorer").isPresent()) {
            try {
                bridge = new SkinsRestorerBridge();
            } catch (Throwable e) {
                logger.warn("SkinsRestorer найден, но его API недоступно — у офлайн-игроков будут стандартные головы", e);
            }
        }

        this.skinsRestorer = bridge;
    }

    boolean usesSkinsRestorer() {
        return this.skinsRestorer != null;
    }

    @Nullable
    SkinService.Skin lookup(String name, @Nullable UUID knownUuid) {
        String key = name.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        SkinService.Cached cached = this.cache.get(key);
        if (cached != null && now - cached.at() < 600000L) {
            return cached.skin();
        } else {
            SkinService.Skin skin = null;
            Optional<Player> online = this.proxy.getPlayer(name);
            if (online.isPresent()) {
                skin = fromProfile(online.get());
            }

            if (skin == null && this.skinsRestorer != null) {
                UUID uuid = knownUuid != null ? knownUuid : offlineUuid(name);

                try {
                    skin = this.skinsRestorer.lookup(uuid, name);
                } catch (Throwable e) {
                    this.logger.debug("SkinsRestorer: не удалось получить скин {}", name, e);
                }
            }

            this.cache.put(key, new SkinService.Cached(skin, now));
            return skin;
        }
    }

    @Nullable
    private static SkinService.Skin fromProfile(Player player) {
        for (Property p : player.getGameProfileProperties()) {
            if ("textures".equals(p.getName()) && p.getValue() != null && !p.getValue().isEmpty()) {
                return new SkinService.Skin(p.getValue(), p.getSignature());
            }
        }

        return null;
    }

    static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private record Cached(@Nullable SkinService.Skin skin, long at) {
    }

    record Skin(String value, @Nullable String signature) {
    }
}
