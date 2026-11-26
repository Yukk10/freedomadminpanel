package dev.freedomadminpanel.velocity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

final class PlayerDirectory {
    private static final Gson GSON = new Gson();
    private final ProxyServer proxy;
    private final Logger logger;
    private final PluginConfig config;
    private final Path file;
    private final Executor background;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final Map<String, PlayerDirectory.Known> own = new ConcurrentHashMap<>();
    private volatile boolean dirty;
    @Nullable
    private final BanSource source;
    private volatile List<PlayerDirectory.Known> history = List.of();
    private volatile Set<UUID> banned = Set.of();
    private volatile Set<UUID> muted = Set.of();
    private volatile long cachedAt;
    private volatile boolean liteBansFailed;
    private volatile String lastError = "";

    PlayerDirectory(ProxyServer proxy, Logger logger, PluginConfig config, Path dataDir, Executor background) {
        this.proxy = proxy;
        this.background = background;
        this.logger = logger;
        this.config = config;
        this.file = dataDir.resolve("players.json");
        this.load();
        this.source = this.createSource();
    }

    @Nullable
    private BanSource createSource() {
        if (this.config.liteBansDatabase.enabled) {
            return new LiteBansSql(this.config.liteBansDatabase);
        } else if (this.config.useLiteBans && !this.proxy.getPluginManager().getPlugin("litebans").isEmpty()) {
            try {
                return new LiteBansBridge();
            } catch (Throwable e) {
                this.logger.warn("LiteBans найден, но его API недоступно — список будет собираться самим плагином", e);
                return null;
            }
        } else {
            return null;
        }
    }

    @Nullable
    BanSource source() {
        return this.source;
    }

    boolean usesLiteBans() {
        return this.source != null && !this.liteBansFailed;
    }

    String liteBansStatus() {
        if (this.source == null) {
            return "не подключён (список собирается самим плагином)";
        } else if (this.liteBansFailed) {
            return this.source.describe() + " — ОШИБКА: " + this.lastError;
        } else {
            return this.cachedAt == 0L
                ? this.source.describe() + " — ещё не опрашивался"
                : this.source.describe() + " — OK: в истории " + this.history.size() + ", банов " + this.banned.size() + ", мутов " + this.muted.size();
        }
    }

    void selfCheck() {
        if (this.source != null) {
            this.invalidate();
            this.refreshLiteBans();
            if (this.liteBansFailed) {
                this.logger.error("LiteBans ({}): не удалось прочитать данные — {}", this.source.describe(), this.lastError);
            } else {
                this.logger
                    .info(
                        "LiteBans ({}): игроков в истории {}, активных банов {}, мутов {}",
                        this.source.describe(),
                        this.history.size(),
                        this.banned.size(),
                        this.muted.size()
                    );
            }
        }
    }

    int knownCount() {
        return Math.max(this.own.size(), this.history.size());
    }

    void seen(Player player) {
        this.own
            .put(player.getUsername().toLowerCase(Locale.ROOT), new PlayerDirectory.Known(player.getUsername(), player.getUniqueId(), System.currentTimeMillis()));
        this.dirty = true;
    }

    @Nullable
    UUID uuidOf(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        PlayerDirectory.Known k = this.own.get(key);
        if (k != null) {
            return k.uuid();
        } else {
            for (PlayerDirectory.Known h : this.history) {
                if (h.name().equalsIgnoreCase(name)) {
                    return h.uuid();
                }
            }

            return null;
        }
    }

    private synchronized void refreshLiteBans() {
        if (this.source != null) {
            long now = System.currentTimeMillis();
            if (now - this.cachedAt >= this.config.cacheSeconds * 1000L) {
                try {
                    this.history = this.source.history();
                    this.banned = this.source.activeBans();
                    this.muted = this.source.activeMutes();
                    if (this.liteBansFailed) {
                        this.logger.info("Связь с LiteBans ({}) восстановлена", this.source.describe());
                    }

                    this.liteBansFailed = false;
                    this.lastError = "";
                } catch (Throwable e) {
                    this.lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    if (!this.liteBansFailed) {
                        this.logger.warn("Не удалось прочитать LiteBans ({}) — временно используется только свой учёт: {}", this.source.describe(), this.lastError);
                    }

                    this.liteBansFailed = true;
                }

                this.cachedAt = now;
            }
        }
    }

    void invalidate() {
        this.cachedAt = 0L;
    }

    void close() {
        if (this.source != null) {
            this.source.close();
        }
    }

    /**
     * Первый раз читает LiteBans сразу, дальше — в фоне: запрос списка не ждёт тяжёлый GROUP BY по истории,
     * а отдаёт последние прочитанные данные.
     */
    private void refreshIfStale() {
        if (this.source == null) {
            return;
        }

        if (this.cachedAt == 0L) {
            this.refreshLiteBans();
        } else if (System.currentTimeMillis() - this.cachedAt >= this.config.cacheSeconds * 1000L && this.refreshing.compareAndSet(false, true)) {
            try {
                this.background.execute(() -> {
                    try {
                        this.refreshLiteBans();
                    } finally {
                        this.refreshing.set(false);
                    }
                });
            } catch (RuntimeException e) {
                this.refreshing.set(false);
                throw e;
            }
        }
    }

    List<PlayerDirectory.Row> snapshot() {
        this.refreshIfStale();
        long now = System.currentTimeMillis();
        Map<String, PlayerDirectory.Known> merged = new HashMap<>();

        for (PlayerDirectory.Known k : this.history) {
            merge(merged, k);
        }

        for (PlayerDirectory.Known k : this.own.values()) {
            merge(merged, k);
        }

        Map<String, Player> online = new HashMap<>();

        for (Player p : this.proxy.getAllPlayers()) {
            online.put(p.getUsername().toLowerCase(Locale.ROOT), p);
            merge(merged, new PlayerDirectory.Known(p.getUsername(), p.getUniqueId(), now));
        }

        Set<UUID> bans = this.banned;
        Set<UUID> mutes = this.muted;
        List<PlayerDirectory.Row> rows = new ArrayList<>(merged.size());

        for (Entry<String, PlayerDirectory.Known> e : merged.entrySet()) {
            PlayerDirectory.Known k = e.getValue();
            Player p = online.get(e.getKey());
            String server = p == null ? "" : p.getCurrentServer().map(s -> this.config.displayServer(s.getServerInfo().getName())).orElse("");
            rows.add(new PlayerDirectory.Row(k.name(), p != null ? now : k.lastSeen(), p != null, server, bans.contains(k.uuid()), mutes.contains(k.uuid())));
        }

        return rows;
    }

    private static void merge(Map<String, PlayerDirectory.Known> into, PlayerDirectory.Known k) {
        into.merge(k.name().toLowerCase(Locale.ROOT), k, (a, b) -> (PlayerDirectory.Known)(a.lastSeen() >= b.lastSeen() ? a : b));
    }

    private void load() {
        if (Files.exists(this.file)) {
            try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
                List<PlayerDirectory.Known> list = GSON.fromJson(reader, (new TypeToken<List<PlayerDirectory.Known>>() {}).getType());
                if (list != null) {
                    for (PlayerDirectory.Known k : list) {
                        if (k != null && k.name() != null) {
                            this.own.put(k.name().toLowerCase(Locale.ROOT), k);
                        }
                    }
                }
            } catch (Exception e) {
                this.logger.error("Не удалось прочитать players.json", e);
            }
        }
    }

    void saveIfDirty() {
        if (this.dirty) {
            this.save();
        }
    }

    synchronized void save() {
        this.dirty = false;

        try {
            Path tmp = this.file.resolveSibling("players.json.tmp");

            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(this.own.values()), writer);
            }

            Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            this.dirty = true;
            this.logger.error("Не удалось сохранить players.json", e);
        }
    }

    record Known(String name, UUID uuid, long lastSeen) {
    }

    record Row(String name, long lastSeen, boolean online, String server, boolean banned, boolean muted) {
    }
}
