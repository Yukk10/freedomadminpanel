package dev.freedomadminpanel.velocity;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

final class RecordService {
    private static final long TTL = 15000L;
    private final Logger logger;
    private final Map<String, RecordService.Cached> cache = new ConcurrentHashMap<>();
    private volatile boolean jailWarned;

    RecordService(Logger logger) {
        this.logger = logger;
    }

    void clear() {
        this.cache.clear();
        this.jailWarned = false;
    }

    @Nullable
    RecordService.Record get(PlayerDirectory directory, PluginConfig config, String name) {
        BanSource source = directory.source();
        if (source == null) {
            return null;
        } else {
            String key = name.toLowerCase(Locale.ROOT);
            long now = System.currentTimeMillis();
            RecordService.Cached cached = this.cache.get(key);
            if (cached != null && now - cached.at() < 15000L) {
                return cached.record();
            } else {
                UUID uuid = uuidOf(directory, name);
                long since = now - config.recidivism.windowDays * 86400000L;

                BanSource.Counts counts;
                int activeWarns;
                try {
                    counts = source.counts(uuid, since);
                    activeWarns = source.activeWarnings(uuid);
                } catch (Exception e) {
                    this.logger.warn("Не удалось посчитать рецидив {}: {}", name, e.getMessage());
                    return null;
                }

                int jails = 0;
                boolean tracked = true;

                try {
                    jails = source.jailCount(config.jailTable, uuid, name, since);
                } catch (Exception e) {
                    tracked = false;
                    if (!this.jailWarned) {
                        this.jailWarned = true;
                        this.logger.warn("История тюрьмы недоступна (таблица {}): {}", config.jailTable, e.getMessage());
                    }
                }

                RecordService.Record record = new RecordService.Record(counts, jails, tracked, activeWarns);
                this.cache.put(key, new RecordService.Cached(record, now));
                return record;
            }
        }
    }

    String jailStatus(PlayerDirectory directory, PluginConfig config) {
        BanSource source = directory.source();
        if (source == null) {
            return "нет подключения к базе";
        } else {
            try {
                source.jailTotal(config.jailTable, new UUID(0L, 0L), "");
                return "таблица " + config.jailTable + " — OK";
            } catch (Exception e) {
                return "таблица " + config.jailTable + " не найдена — поставьте плагин freedomadminpanel на сервера с CMI (" + e.getMessage() + ")";
            }
        }
    }

    static UUID uuidOf(PlayerDirectory directory, String name) {
        UUID uuid = directory.uuidOf(name);
        if (uuid != null) {
            return uuid;
        } else {
            BanSource source = directory.source();
            if (source != null) {
                try {
                    uuid = source.uuidByName(name);
                } catch (Exception e) {
                }
            }

            return uuid != null ? uuid : SkinService.offlineUuid(name);
        }
    }

    private record Cached(RecordService.Record record, long at) {
    }

    record Record(BanSource.Counts counts, int jails, boolean jailTracked, int activeWarns) {
    }
}
