package dev.freedomadminpanel.paper;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreedomAdminPanelPaper extends JavaPlugin {
    private JailDatabase db;
    private String serverName;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.serverName = this.getConfig().getString("server", "");
        this.db = new JailDatabase(this.getConfig());
        this.async(() -> {
            this.db.createTable();
            this.getLogger().info("База для истории тюрьмы подключена: " + this.db.describe());
        });
        JailHistoryCommand history = new JailHistoryCommand(this);
        PluginCommand command = this.getCommand("jailhistory");
        if (command != null) {
            command.setExecutor(history);
            command.setTabCompleter(history);
        }

        if (this.getServer().getPluginManager().getPlugin("CMI") == null) {
            this.getLogger().warning("CMI не найден — история тюрьмы на этом сервере не записывается");
        } else {
            this.getServer().getPluginManager().registerEvents(new JailListener(this), this);
            this.getLogger().info("Запись истории тюрьмы CMI включена (сервер: " + this.serverName + ")");
        }
    }

    JailDatabase db() {
        return this.db;
    }

    String serverName() {
        return this.serverName;
    }

    void async(FreedomAdminPanelPaper.DbTask task) {
        this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                task.run();
            } catch (Exception e) {
                this.getLogger().warning("Ошибка базы истории тюрьмы (" + this.db.describe() + "): " + e.getMessage());
            }
        });
    }

    interface DbTask {
        void run() throws Exception;
    }
}
