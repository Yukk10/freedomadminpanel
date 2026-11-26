package dev.freedomadminpanel.spectate;

import dev.freedomadminpanel.Commands;
import dev.freedomadminpanel.config.FreedomAdminPanelConfig;
import dev.freedomadminpanel.config.Perms;
import dev.freedomadminpanel.net.PanelNet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

public final class SpectateMode {
    private static final long TP_COOLDOWN_MS = 400L;
    private static final long GM_RETRY_MS = 3000L;
    private static final int GM_MAX_TRIES = 3;
    private static boolean active;
    private static final List<String> queue = new ArrayList<>();
    @Nullable
    private static String current;
    private static int leftAt = -1;
    private static boolean finished;
    private static int netVersion;
    private static int ticks;
    private static long lastTp;
    private static float savedSpeed = -1.0F;
    private static long restoredAt;
    @Nullable
    private static Object lastPlayer;
    @Nullable
    private static Object lastWorld;
    public static boolean altSuppressed;
    private static long gmSentAt;
    private static int gmTries;
    private static boolean gmWarned;

    private SpectateMode() {
    }

    public static boolean active() {
        return active;
    }

    public static boolean allowed() {
        return Perms.allowed("spectate");
    }

    public static void toggle(MinecraftClient client) {
        if (active) {
            stop(client, "Режим слежки выключен");
        } else {
            start(client);
        }
    }

    public static void start(MinecraftClient client) {
        if (client.player != null) {
            if (!allowed()) {
                say(client, "Режим слежки доступен только модераторам", Formatting.RED);
            } else if (!PanelNet.available()) {
                say(client, "Режим слежки работает только с плагином freedomadminpanel на сервере", Formatting.RED);
            } else if (!onSpectateServer(client)) {
                say(client, "Режим слежки работает только на сервере «" + server() + "»", Formatting.RED);
            } else {
                queue.clear();
                queue.addAll(survivalPlayers(client).values());
                current = null;
                leftAt = -1;
                finished = false;
                active = true;
                netVersion = PanelNet.version();
                ticks = 0;
                savedSpeed = -1.0F;
                lastPlayer = client.player;
                lastWorld = client.world;
                rememberSpeed(client);
                gmSentAt = 0L;
                gmTries = 0;
                gmWarned = false;
                say(client, "Режим слежки: " + queue.size() + " игр. · Alt — мышь, ← → — листать", Formatting.GREEN);
                ensureSpectator(client);
                if (!queue.isEmpty()) {
                    go(client, 0);
                }
            }
        }
    }

    public static void stop(MinecraftClient client, String message) {
        active = false;
        queue.clear();
        current = null;
        leftAt = -1;
        finished = false;
        if (message != null) {
            say(client, message, Formatting.YELLOW);
        }
    }

    public static int nextIndex() {
        int pos = position();
        int target;
        if (pos >= 0) {
            target = pos + 1;
        } else if (current != null && leftAt >= 0) {
            target = leftAt;
        } else {
            target = 0;
        }

        return target < queue.size() ? target : -1;
    }

    public static int prevIndex() {
        int pos = position();
        int target;
        if (finished && pos >= 0) {
            target = pos;
        } else if (pos >= 0) {
            target = pos - 1;
        } else if (current != null && leftAt >= 0) {
            target = leftAt - 1;
        } else {
            target = -1;
        }

        return target >= 0 && target < queue.size() ? target : -1;
    }

    public static int remaining() {
        int next = nextIndex();
        return next < 0 ? 0 : queue.size() - next;
    }

    public static void next(MinecraftClient client) {
        if (active) {
            int target = nextIndex();
            if (target < 0) {
                if (!finished && current != null) {
                    finished = true;
                    say(client, "Все игроки на выживании просмотрены. Новые встанут в конец списка", Formatting.GREEN);
                }
            } else {
                go(client, target);
            }
        }
    }

    public static void prev(MinecraftClient client) {
        if (active) {
            int target = prevIndex();
            if (target >= 0) {
                go(client, target);
            }
        }
    }

    private static void go(MinecraftClient client, int index) {
        long now = System.currentTimeMillis();
        if (now - lastTp >= 400L) {
            lastTp = now;
            String name = queue.get(index);
            current = name;
            leftAt = -1;
            finished = false;
            rememberSpeed(client);
            Commands.send(Commands.build("tp", Map.of("player", name)));
        }
    }

    public static void tick(MinecraftClient client) {
        if (active) {
            if (client.player != null && client.getNetworkHandler() != null) {
                if (++ticks % 60 == 0) {
                    PanelNet.requestList();
                }

                if (PanelNet.version() != netVersion) {
                    netVersion = PanelNet.version();
                    sync(client);
                }

                if (active) {
                    ensureSpectator(client);
                    trackSpeed(client);
                }
            } else {
                stop(client, null);
            }
        }
    }

    private static void sync(MinecraftClient client) {
        if (PanelNet.available()) {
            if (!onSpectateServer(client)) {
                stop(client, "Вы ушли с сервера «" + server() + "» — режим слежки выключен");
            } else {
                Map<String, String> present = survivalPlayers(client);

                for (int i = queue.size() - 1; i >= 0; i--) {
                    String name = queue.get(i);
                    if (!present.containsKey(name.toLowerCase(Locale.ROOT))) {
                        queue.remove(i);
                        if (name.equalsIgnoreCase(current == null ? "" : current) && leftAt < 0) {
                            leftAt = i;
                        } else if (leftAt > i) {
                            leftAt--;
                        }
                    }
                }

                Set<String> inQueue = new HashSet<>();

                for (String name : queue) {
                    inQueue.add(name.toLowerCase(Locale.ROOT));
                }

                for (Entry<String, String> e : present.entrySet()) {
                    if (!inQueue.contains(e.getKey())) {
                        queue.add(e.getValue());
                    }
                }
            }
        }
    }

    private static Map<String, String> survivalPlayers(MinecraftClient client) {
        String self = client.getSession().getUsername();
        Map<String, String> result = new TreeMap<>();

        for (PanelNet.NetPlayer p : PanelNet.players()) {
            if (p.online() && p.server().equalsIgnoreCase(server()) && !p.name().equalsIgnoreCase(self)) {
                result.put(p.name().toLowerCase(Locale.ROOT), p.name());
            }
        }

        return result;
    }

    private static boolean onSpectateServer(MinecraftClient client) {
        PanelNet.NetPlayer me = PanelNet.find(client.getSession().getUsername());
        return me != null && me.online() && me.server().equalsIgnoreCase(server());
    }

    private static String server() {
        String s = FreedomAdminPanelConfig.get().spectateServer;
        return s != null && !s.isBlank() ? s : "Выживание";
    }

    /**
     * Держит модератора в режиме наблюдателя, пока слежка включена: при старте и если сервер сбросил режим
     * (смена мира, телепорт, перезаход) отправляет команду "gm3" из конфига. Без прав на неё — не спамит:
     * не больше GM_MAX_TRIES попыток подряд, потом одно предупреждение.
     */
    private static void ensureSpectator(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null || !FreedomAdminPanelConfig.get().spectateGamemode) {
            return;
        }

        if (inSpectator(client, p)) {
            gmTries = 0;
            gmWarned = false;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - gmSentAt < GM_RETRY_MS) {
            return;
        }

        if (gmTries < GM_MAX_TRIES) {
            gmSentAt = now;
            gmTries++;
            Commands.send(Commands.build("gm3", Map.of()));
        } else if (!gmWarned) {
            gmWarned = true;
            say(client, "Не удалось включить GM 3 — проверьте права на /" + Commands.build("gm3", Map.of()), Formatting.RED);
        }
    }

    public static boolean switchingGamemode() {
        return active && gmTries > 0 && !gmWarned;
    }

    private static void rememberSpeed(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p != null && inSpectator(client, p)) {
            savedSpeed = p.getAbilities().getFlySpeed();
        }
    }

    public static void onServerAbilities() {
        if (active) {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity p = client.player;
            if (p != null && inSpectator(client, p)) {
                applySavedSpeed(client, p);
            }
        }
    }

    private static void trackSpeed(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p != null && inSpectator(client, p)) {
            if (p == lastPlayer && client.world == lastWorld) {
                savedSpeed = p.getAbilities().getFlySpeed();
            } else {
                applySavedSpeed(client, p);
            }
        }
    }

    private static void applySavedSpeed(MinecraftClient client, ClientPlayerEntity p) {
        lastPlayer = p;
        lastWorld = client.world;
        float speed = p.getAbilities().getFlySpeed();
        if (savedSpeed <= 0.0F) {
            savedSpeed = speed;
        } else {
            if (Math.abs(speed - savedSpeed) > 1.0E-5F) {
                p.getAbilities().setFlySpeed(savedSpeed);
                restoredAt = System.currentTimeMillis();
            }
        }
    }

    private static boolean inSpectator(MinecraftClient client, ClientPlayerEntity p) {
        return p.isSpectator() ? true : client.interactionManager != null && client.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR;
    }

    public static List<String> queue() {
        return queue;
    }

    @Nullable
    public static String current() {
        return current;
    }

    public static int position() {
        if (current == null) {
            return -1;
        } else {
            for (int i = 0; i < queue.size(); i++) {
                if (queue.get(i).equalsIgnoreCase(current)) {
                    return i;
                }
            }

            return -1;
        }
    }

    public static boolean finished() {
        return finished;
    }

    public static boolean currentLeft() {
        return current != null && position() < 0;
    }

    public static boolean justRestored() {
        return System.currentTimeMillis() - restoredAt < 2500L;
    }

    private static void say(MinecraftClient client, String text, Formatting color) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Слежка] ").formatted(Formatting.GOLD).append(Text.literal(text).formatted(color)), false);
        }
    }
}
