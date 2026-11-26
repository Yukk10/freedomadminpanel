package dev.freedomadminpanel.paper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

final class JailHistoryCommand implements TabExecutor {
    private static final int PAGE_SIZE = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final FreedomAdminPanelPaper plugin;

    JailHistoryCommand(FreedomAdminPanelPaper plugin) {
        this.plugin = plugin;
    }

    static boolean allowed(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("freedomadminpanel.use") || sender.hasPermission("cmi.command.jail");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!allowed(sender)) {
            sender.sendMessage(Component.text("Нет прав на просмотр истории тюрьмы.", NamedTextColor.RED));
            return true;
        } else if (args.length == 0) {
            sender.sendMessage(Component.text("Использование: /" + label + " <ник> [страница]", NamedTextColor.RED));
            return true;
        } else {
            String name = args[0];
            int page = 1;
            if (args.length > 1) {
                try {
                    page = Math.max(1, Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                }
            }

            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            UUID uuid = cached != null ? cached.getUniqueId() : null;
            int requested = page;
            this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> this.show(sender, name, uuid, requested));
            return true;
        }
    }

    private void show(CommandSender sender, String name, UUID uuid, int page) {
        JailDatabase db = this.plugin.db();

        int total;
        List<JailDatabase.Entry> entries;
        try {
            total = db.total(uuid, name);
            int pages = Math.max(1, (total + 8 - 1) / 8);
            page = Math.min(page, pages);
            entries = db.history(uuid, name, 8, (page - 1) * 8);
        } catch (Exception e) {
            sender.sendMessage(Component.text("История тюрьмы недоступна: " + e.getMessage(), NamedTextColor.RED));
            return;
        }

        if (total == 0) {
            sender.sendMessage(Component.text("У игрока " + name + " нет записей в истории тюрьмы.", NamedTextColor.YELLOW));
        } else {
            int pages = Math.max(1, (total + 8 - 1) / 8);
            sender.sendMessage(
                ((TextComponent)Component.text("История тюрьмы ", NamedTextColor.GOLD).append(Component.text(name, NamedTextColor.WHITE)))
                    .append(Component.text(" — записей: " + total + " (стр. " + page + "/" + pages + ")", NamedTextColor.GOLD))
            );
            long now = System.currentTimeMillis();

            for (JailDatabase.Entry e : entries) {
                String term = e.until() > 0L ? human(e.until() - e.time()) : "без срока";
                String place = (e.jail() != null && !e.jail().isBlank() ? " · " + e.jail() + (e.cell() == null ? "" : " " + e.cell()) : "")
                    + (e.server() != null && !e.server().isBlank() ? " · " + e.server() : "");
                sender.sendMessage(
                    ((TextComponent)Component.text(" " + DATE.format(Instant.ofEpochMilli(e.time())), NamedTextColor.GRAY)
                            .append(Component.text(" · " + term, NamedTextColor.WHITE)))
                        .append(Component.text(place, NamedTextColor.GRAY))
                );
                Component status;
                if (e.released() > 0L && e.early()) {
                    status = Component.text("вышел досрочно " + DATE.format(Instant.ofEpochMilli(e.released())), NamedTextColor.GREEN);
                } else if (e.released() > 0L) {
                    status = Component.text("отсидел", NamedTextColor.DARK_GRAY);
                } else if (e.until() > 0L && e.until() < now) {
                    status = Component.text("срок истёк", NamedTextColor.DARK_GRAY);
                } else {
                    status = Component.text("сидит сейчас", NamedTextColor.RED);
                }

                sender.sendMessage(
                    ((TextComponent)Component.text("   " + (e.reason() != null && !e.reason().isBlank() ? e.reason() : "без причины"), NamedTextColor.YELLOW)
                            .append(Component.text(" · выдал: " + (e.jailedBy() == null ? "?" : e.jailedBy()) + " · ", NamedTextColor.GRAY)))
                        .append(status)
                );
            }

            if (page < pages) {
                String next = "/jailhistory " + name + " " + (page + 1);
                sender.sendMessage(
                    ((TextComponent)Component.text(" » Следующая страница", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand(next)))
                        .hoverEvent(HoverEvent.showText((Component)Component.text(next)))
                );
            }
        }
    }

    static String human(long ms) {
        long minutes = Math.max(1L, Math.round(ms / 60000.0));
        long d = minutes / 1440L;
        long h = minutes % 1440L / 60L;
        long m = minutes % 60L;
        StringBuilder sb = new StringBuilder();
        if (d > 0L) {
            sb.append(d).append("д ");
        }

        if (h > 0L) {
            sb.append(h).append("ч ");
        }

        if (m > 0L && d == 0L) {
            sb.append(m).append("м");
        }

        return sb.toString().trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && allowed(sender)) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers()
                .stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        } else {
            return List.of();
        }
    }
}
