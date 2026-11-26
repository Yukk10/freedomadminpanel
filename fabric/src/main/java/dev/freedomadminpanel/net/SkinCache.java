package dev.freedomadminpanel.net;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.SkinTextures;
import org.jetbrains.annotations.Nullable;

public final class SkinCache {
    private static final int BATCH = 48;
    private static final Map<String, SkinTextures> ready = new HashMap<>();
    private static final Set<String> requested = new HashSet<>();
    private static final Deque<String> queue = new ArrayDeque<>();
    private static int cooldown;
    private static int generation;

    private SkinCache() {
    }

    @Nullable
    public static SkinTextures get(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        SkinTextures skin = ready.get(key);
        if (skin != null) {
            return skin;
        } else {
            if (PanelNet.available() && requested.add(key)) {
                queue.add(name);
            }

            return null;
        }
    }

    static void tick() {
        if (!queue.isEmpty() && --cooldown <= 0) {
            cooldown = 5;
            List<String> batch = new ArrayList<>(48);

            while (!queue.isEmpty() && batch.size() < 48) {
                batch.add(queue.poll());
            }

            PanelNet.requestSkins(batch);
        }
    }

    static void accept(String name, String value, @Nullable String signature) {
        String key = name.toLowerCase(Locale.ROOT);
        int gen = generation;
        Property textures = new Property("textures", value, signature);
        GameProfile profile = new GameProfile(offlineUuid(name), name, new PropertyMap(ImmutableMultimap.of("textures", textures)));
        MinecraftClient client = MinecraftClient.getInstance();
        client.getSkinProvider().fetchSkinTextures(profile).thenAccept(result -> result.ifPresent(skin -> client.execute(() -> {
            if (gen == generation) {
                ready.put(key, skin);
            }
        })));
    }

    static void clear() {
        ready.clear();
        requested.clear();
        queue.clear();
        generation++;
    }

    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
