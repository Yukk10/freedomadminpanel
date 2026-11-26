package dev.freedomadminpanel.config;

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
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

public class FreedomAdminPanelConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("freedomadminpanel.json");
    private static final Path OLD_PATH = FabricLoader.getInstance().getConfigDir().resolve("playermenu.json");
    private static final Map<String, List<String>> OLD_DEFAULTS = Map.of(
        "jail",
        List.of("jail {player} {time} jail{jail} : {reason}", "jail {player} {time} jail {jail} : {reason}"),
        "unmute",
        List.of("unmute {player}"),
        "unban",
        List.of("unban {player}")
    );
    private static FreedomAdminPanelConfig instance = new FreedomAdminPanelConfig();
    public Map<String, String> commands = defaultCommands();
    public int jailCount = 5;
    public List<String> timePresets = new ArrayList<>(List.of("10m", "30m", "1h", "3h", "1d", "7d", "30d"));
    public List<String> reasonPresets = new ArrayList<>(List.of("Оскорбление", "Флуд", "Спам", "Реклама", "Читы", "Гриферство", "Неадекват", "Обход наказания"));
    public List<String> messagePresets = new ArrayList<>(List.of("Привет!", "Можешь подойти?", "Подожди минуту"));
    public List<String> revokePresets = new ArrayList<>(List.of("Ошибочное наказание", "Апелляция одобрена", "Решение старшего состава", "Досрочно"));
    public boolean hideSelf = false;
    public String silentFlag = "-s";
    public String role = null;
    public String spectateServer = "Выживание";
    public boolean spectateGamemode = true;

    private static Map<String, String> defaultCommands() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("tp", "tp {player}");
        m.put("tphere", "tphere {player}");
        m.put("call", "админ {player}");
        m.put("invsee", "invsee {player}");
        m.put("enderchest", "enderchest {player}");
        m.put("profile", "profile {player}");
        m.put("seen", "seen {player}");
        m.put("alts", "alts {player}");
        m.put("alert_remove", "alert remove {player}");
        m.put("msg", "msg {player} {message}");
        m.put("alert", "alert add {player} {reason}");
        m.put("mute", "mute {player} {time} {reason}");
        m.put("jail", "jail {player} {time} jail {jail} r: {reason}");
        m.put("ban", "ban {player} {time} {reason}");
        m.put("warn", "warn {player} {reason}");
        m.put("kick", "kick {player} {reason}");
        m.put("spawn", "spawn {player}");
        m.put("homes", "homes {player}");
        m.put("checkaccount", "checkaccount {player}");
        m.put("iphistory", "iphistory {player}");
        m.put("ahhistory", "ahadmin history {player}");
        m.put("warnings", "warnings {player}");
        m.put("history", "history {player}");
        m.put("jailhistory", "jailhistory {player}");
        m.put("unmute", "unmute {player} {reason}");
        m.put("unjail", "unjail {player}");
        m.put("unban", "unban {player} {reason}");
        m.put("unwarn", "unwarn {player} {reason}");
        m.put("reports", "reports");
        m.put("vanish", "vanish");
        m.put("adminchat", "чат-админ");
        m.put("tpsbar", "tpsbar");
        m.put("grim", "grim alerts");
        m.put("alert_list", "alert list");
        m.put("ipreport", "ipreport");
        m.put("event", "event");
        m.put("survival", "survival");
        m.put("gm0", "gm 0");
        m.put("gm1", "gm 1");
        m.put("gm3", "gm 3");
        m.put("bc", "bc {message}");
        m.put("review_delete", "reviewsadmin delete {id}");
        return m;
    }

    public static FreedomAdminPanelConfig get() {
        return instance;
    }

    @Nullable
    public Role role() {
        if (this.role == null) {
            return null;
        } else {
            try {
                return Role.valueOf(this.role);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public void setRole(Role newRole) {
        this.role = newRole == null ? null : newRole.name();
        save();
    }

    public String silentFlag() {
        return this.silentFlag != null && !this.silentFlag.isBlank() ? this.silentFlag.trim() : "-s";
    }

    public String command(String key) {
        String value = this.commands.get(key);
        return value != null ? value : defaultCommands().getOrDefault(key, key + " {player}");
    }

    public static void load() {
        if (!Files.exists(PATH) && Files.exists(OLD_PATH)) {
            try {
                Files.move(OLD_PATH, PATH);
            } catch (Exception e) {
                System.err.println("[freedomadminpanel] Не удалось перенести старый конфиг: " + e);
            }
        }

        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                FreedomAdminPanelConfig loaded = (FreedomAdminPanelConfig)GSON.fromJson(reader, FreedomAdminPanelConfig.class);
                if (loaded != null) {
                    Map<String, String> merged = defaultCommands();
                    if (loaded.commands != null) {
                        merged.putAll(loaded.commands);
                    }

                    Map<String, String> defaults = defaultCommands();
                    OLD_DEFAULTS.forEach((key, olds) -> {
                        if (olds.contains(merged.get(key))) {
                            merged.put(key, defaults.get(key));
                        }
                    });
                    loaded.commands = merged;
                    if (loaded.timePresets == null) {
                        loaded.timePresets = new ArrayList<>();
                    }

                    if (loaded.reasonPresets == null) {
                        loaded.reasonPresets = new ArrayList<>();
                    }

                    if (loaded.messagePresets == null) {
                        loaded.messagePresets = new ArrayList<>();
                    }

                    if (loaded.revokePresets == null) {
                        loaded.revokePresets = new ArrayList<>();
                    }

                    loaded.jailCount = Math.max(1, Math.min(loaded.jailCount, 20));
                    instance = loaded;
                }
            } catch (Exception e) {
                System.err.println("[freedomadminpanel] Не удалось прочитать конфиг, используются значения по умолчанию: " + e);
            }
        }

        save();
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception e) {
            System.err.println("[freedomadminpanel] Не удалось сохранить конфиг: " + e);
        }
    }
}
