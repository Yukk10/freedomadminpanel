package dev.freedomadminpanel.paper;

import com.Zrips.CMI.Containers.CMIUser;
import com.Zrips.CMI.Modules.Jail.CMIJailCell;
import com.Zrips.CMI.events.CMIPlayerJailEvent;
import com.Zrips.CMI.events.CMIPlayerUnjailEvent;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class JailListener implements Listener {
    private final FreedomAdminPanelPaper plugin;

    JailListener(FreedomAdminPanelPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onJail(CMIPlayerJailEvent event) {
        CMIUser user = event.getUser();
        if (user != null) {
            long time = System.currentTimeMillis();
            Bukkit.getScheduler()
                .runTaskLater(
                    this.plugin,
                    () -> {
                        if (user.isJailed()) {
                            UUID by = user.getJailedBy();
                            CMIJailCell cell = user.getCell();
                            String jail = cell != null && cell.getJail() != null ? cell.getJail().getName() : null;
                            Integer cellId = cell != null ? cell.getId() : null;
                            JailDatabase.JailRecord record = new JailDatabase.JailRecord(
                                user.getUniqueId(),
                                user.getName(),
                                by,
                                nameOf(by),
                                user.getJailedReason(),
                                jail,
                                cellId,
                                this.plugin.serverName(),
                                time,
                                untilOf(user.getJailedUntil(), time)
                            );
                            this.plugin.write(() -> this.plugin.db().insert(record));
                        }
                    },
                    2L
                );
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onUnjail(CMIPlayerUnjailEvent event) {
        CMIUser user = event.getUser();
        if (user != null) {
            UUID uuid = user.getUniqueId();
            long time = System.currentTimeMillis();
            // Посадка пишется через 2 тика (ждём, пока CMI заполнит данные) — выход ставим в ту же очередь позже,
            // иначе при быстром освобождении UPDATE выполнится раньше INSERT и запись навсегда останется «сидит»
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.plugin.write(() -> this.plugin.db().release(uuid, time)), 3L);
        }
    }

    private static long untilOf(Long value, long now) {
        if (value != null && value > 0L) {
            return value < 100000000000L ? now + value : value;
        } else {
            return -1L;
        }
    }

    private static String nameOf(UUID uuid) {
        if (uuid == null) {
            return "Консоль";
        } else {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            return player.getName() != null ? player.getName() : uuid.toString();
        }
    }
}
