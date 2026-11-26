package dev.freedomadminpanel.config;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class Perms {
    private static final Set<Role> ALL = EnumSet.allOf(Role.class);
    private static final Set<Role> MOD_HELP = EnumSet.of(Role.MODER, Role.HELPER);
    private static final Set<Role> MOD = EnumSet.of(Role.MODER);
    private static final Map<String, Set<Role>> RULES = new HashMap<>();

    private Perms() {
    }

    public static boolean allowed(String key) {
        Role role = FreedomAdminPanelConfig.get().role();
        return role == null || RULES.getOrDefault(key, ALL).contains(role);
    }

    static {
        for (String k : new String[]{
            "tp",
            "tphere",
            "spawn",
            "call",
            "msg",
            "profile",
            "seen",
            "alts",
            "checkaccount",
            "iphistory",
            "history",
            "jailhistory",
            "mute",
            "unmute",
            "jail",
            "unjail",
            "warn",
            "unwarn",
            "kick"
        }) {
            RULES.put(k, ALL);
        }

        for (String k : new String[]{"alert", "alert_remove", "ahhistory", "warnings"}) {
            RULES.put(k, MOD_HELP);
        }

        for (String k : new String[]{"homes", "invsee", "enderchest", "ban", "unban"}) {
            RULES.put(k, MOD);
        }

        for (String k : new String[]{"reports", "adminchat", "tpsbar", "grim", "ipreport", "event", "survival", "bc", "gm0", "gm1"}) {
            RULES.put(k, ALL);
        }

        RULES.put("gm3", EnumSet.of(Role.MODER, Role.EVENTOR));

        for (String k : new String[]{"alert_list", "review_delete"}) {
            RULES.put(k, MOD_HELP);
        }

        RULES.put("vanish", MOD);
        RULES.put("spectate", MOD);
    }
}
