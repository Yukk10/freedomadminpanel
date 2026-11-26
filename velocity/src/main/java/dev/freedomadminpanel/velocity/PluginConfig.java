package dev.freedomadminpanel.velocity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;

final class PluginConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    String permission = "freedomadminpanel.use";
    List<String> allowedPlayers = new ArrayList<>();
    boolean useLiteBans = true;
    PluginConfig.Database liteBansDatabase = new PluginConfig.Database();
    Map<String, String> serverNames = defaultServerNames();
    int cacheSeconds = 60;
    String jailTable = "freedomadminpanel_jails";
    PluginConfig.Recidivism recidivism = new PluginConfig.Recidivism();
    PluginConfig.WarnAutoBan warnAutoBan = new PluginConfig.WarnAutoBan();

    private static Map<String, String> defaultServerNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("lobby", "Лобби");
        names.put("survival", "Выживание");
        names.put("event", "Ивент");
        names.put("pillars", "Столбы");
        return names;
    }

    static PluginConfig load(Path dataDir, Logger logger) {
        Path file = dataDir.resolve("config.json");
        PluginConfig config = new PluginConfig();

        try {
            Files.createDirectories(dataDir);
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    PluginConfig loaded = GSON.fromJson(reader, PluginConfig.class);
                    if (loaded != null) {
                        config = loaded;
                    }
                }
            }

            if (config.allowedPlayers == null) {
                config.allowedPlayers = new ArrayList<>();
            }

            if (config.serverNames == null) {
                config.serverNames = new LinkedHashMap<>();
            }

            defaultServerNames().forEach(config.serverNames::putIfAbsent);
            if (config.liteBansDatabase == null) {
                config.liteBansDatabase = new PluginConfig.Database();
            }

            if (config.recidivism == null) {
                config.recidivism = new PluginConfig.Recidivism();
            }

            if (config.warnAutoBan == null) {
                config.warnAutoBan = new PluginConfig.WarnAutoBan();
            }

            if (config.warnAutoBan.warnings <= 0) {
                config.warnAutoBan.warnings = 3;
            }

            if (config.warnAutoBan.duration == null || config.warnAutoBan.duration.isBlank()) {
                config.warnAutoBan.duration = "3d";
            }

            config.recidivism.fillDefaults();
            if (config.jailTable == null || config.jailTable.isBlank()) {
                config.jailTable = "freedomadminpanel_jails";
            }

            config.jailTable = config.jailTable.replaceAll("[^A-Za-z0-9_]", "");
            if (config.permission == null || config.permission.isBlank()) {
                config.permission = "freedomadminpanel.use";
            }

            config.cacheSeconds = Math.max(10, config.cacheSeconds);

            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            logger.error("Не удалось прочитать config.json, используются настройки по умолчанию", e);
        }

        return config;
    }

    boolean isAllowedName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return this.allowedPlayers.stream().anyMatch(n -> n.toLowerCase(Locale.ROOT).equals(lower));
    }

    String displayServer(String velocityName) {
        return this.serverNames.getOrDefault(velocityName, velocityName);
    }

    static final class Database {
        boolean enabled = false;
        String address = "localhost:3306";
        String database = "litebans";
        String username = "litebans";
        String password = "";
        String tablePrefix = "litebans_";
        String options = "allowPublicKeyRetrieval=true";
    }

    static final class Formula {
        String defaultBase;
        String step;
        String max;

        static PluginConfig.Formula of(String base, String step, String max) {
            PluginConfig.Formula f = new PluginConfig.Formula();
            f.defaultBase = base;
            f.step = step;
            f.max = max;
            return f;
        }

        static PluginConfig.Formula fill(PluginConfig.Formula f, String base, String step, String max) {
            if (f == null) {
                return of(base, step, max);
            } else {
                if (f.defaultBase == null || f.defaultBase.isBlank()) {
                    f.defaultBase = base;
                }

                if (f.step == null || f.step.isBlank()) {
                    f.step = step;
                }

                if (f.max == null || f.max.isBlank()) {
                    f.max = max;
                }

                return f;
            }
        }
    }

    static final class Recidivism {
        int windowDays = 60;
        PluginConfig.Formula mute = PluginConfig.Formula.of("30m", "10m", "7d");
        PluginConfig.Formula jail = PluginConfig.Formula.of("30m", "10m", "12h");
        PluginConfig.Formula ban = PluginConfig.Formula.of("1d", "12h", "90d");

        void fillDefaults() {
            if (this.windowDays <= 0) {
                this.windowDays = 60;
            }

            this.mute = PluginConfig.Formula.fill(this.mute, "30m", "10m", "7d");
            this.jail = PluginConfig.Formula.fill(this.jail, "30m", "10m", "12h");
            this.ban = PluginConfig.Formula.fill(this.ban, "1d", "12h", "90d");
        }
    }

    static final class WarnAutoBan {
        boolean enabled = true;
        int warnings = 3;
        String duration = "3d";
    }
}
