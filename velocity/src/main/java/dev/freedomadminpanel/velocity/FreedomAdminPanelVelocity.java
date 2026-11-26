package dev.freedomadminpanel.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

@Plugin(
    id = "freedomadminpanel",
    name = "freedomadminpanel",
    version = BuildConstants.VERSION,
    authors = {"yukio"},
    description = "Список всех игроков для мода freedomadminpanel (онлайн и офлайн, баны и муты из LiteBans)",
    dependencies = {@Dependency(
        id = "litebans",
        optional = true
    ), @Dependency(
        id = "skinsrestorer",
        optional = true
    )}
)
public final class FreedomAdminPanelVelocity {
    static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("freedomadminpanel:main");
    private static final String ADMIN_PERMISSION = "freedomadminpanel.admin";
    private static final long LIST_COOLDOWN_MS = 1000L;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private final Map<UUID, Long> lastListRequest = new ConcurrentHashMap<>();
    private volatile PluginConfig config;
    private volatile PlayerDirectory directory;
    private SkinService skins;
    private final RecordService records;

    @Inject
    public FreedomAdminPanelVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
        this.records = new RecordService(logger);
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        this.config = PluginConfig.load(this.dataDir, this.logger);
        this.directory = new PlayerDirectory(this.proxy, this.logger, this.config, this.dataDir);
        this.skins = new SkinService(this.proxy, this.logger);
        this.proxy.getChannelRegistrar().register(new ChannelIdentifier[]{CHANNEL});
        this.proxy.getScheduler().buildTask(this, () -> this.directory.saveIfDirty()).repeat(1L, TimeUnit.MINUTES).schedule();
        CommandMeta meta = this.proxy.getCommandManager().metaBuilder("fapanel").aliases(new String[]{"freedomadminpanel"}).plugin(this).build();
        this.proxy.getCommandManager().register(meta, new FreedomAdminPanelVelocity.AdminCommand());
        this.logger.info("freedomadminpanel запущен. SkinsRestorer: {}", this.skins.usesSkinsRestorer() ? "да" : "нет");
        PlayerDirectory dir = this.directory;
        this.proxy.getScheduler().buildTask(this, () -> {
            if (dir.usesLiteBans()) {
                dir.selfCheck();
                this.logger.info("История тюрьмы: {}", this.records.jailStatus(dir, this.config));
            } else {
                this.logger.info("LiteBans не подключён — список игроков собирается самим плагином, рецидив недоступен");
            }
        }).schedule();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (this.directory != null) {
            this.directory.save();
        }
    }

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        this.directory.seen(event.getPlayer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.directory.seen(event.getPlayer());
        this.lastListRequest.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (CHANNEL.equals(event.getIdentifier())) {
            event.setResult(ForwardResult.handled());
            if (event.getSource() instanceof Player player) {
                byte[] data = event.getData();
                this.proxy.getScheduler().buildTask(this, () -> this.handle(player, data)).schedule();
            }
        }
    }

    private boolean allowed(Player player) {
        return player.hasPermission(this.config.permission) || this.config.isAllowedName(player.getUsername());
    }

    private void handle(Player player, byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            byte type = in.readByte();
            if (!this.allowed(player)) {
                player.sendPluginMessage(CHANNEL, Protocol.encodeDenied("Нет права " + this.config.permission));
            } else {
        switch (type) {
                    case 1:
                        int version = in.readInt();
                        int requestId = in.readInt();
                        if (version != Protocol.VERSION) {
                            player.sendPluginMessage(CHANNEL, Protocol.encodeDenied("Версия мода не совпадает с плагином — обновите мод"));
                            return;
                        } else {
                            long now = System.currentTimeMillis();
                            Long last = this.lastListRequest.put(player.getUniqueId(), now);
                            if (last != null && now - last < 1000L) {
                                return;
                            }

                            for (byte[] chunk : Protocol.encodeList(requestId, this.directory.snapshot())) {
                                player.sendPluginMessage(CHANNEL, chunk);
                            }

                            return;
                        }
                    case 2:
                        int count = Math.min(in.readUnsignedShort(), 48);
                        List<String> names = new ArrayList<>(count);
                        List<SkinService.Skin> found = new ArrayList<>(count);

                        for (int i = 0; i < count; i++) {
                            String name = in.readUTF();
                            if (!name.isEmpty() && name.length() <= 32) {
                                names.add(name);
                                found.add(this.skins.lookup(name, this.directory.uuidOf(name)));
                            }
                        }

                        if (!names.isEmpty()) {
                            player.sendPluginMessage(CHANNEL, Protocol.encodeSkins(names, found));
                        }

                        return;
                    case 3:
                        String name = in.readUTF();
                        if (!name.isEmpty() && name.length() <= 32) {
                            RecordService.Record record = this.records.get(this.directory, this.config, name);
                            player.sendPluginMessage(CHANNEL, Protocol.encodeRecord(name, record, this.config.recidivism, this.config.warnAutoBan));
                            return;
                        }

                        return;
                }
            }
        } catch (Exception e) {
            this.logger.warn("Ошибка обработки запроса от {}", player.getUsername(), e);
        }
    }

    private final class AdminCommand implements SimpleCommand {
        public void execute(Invocation invocation) {
            String[] args = (String[])invocation.arguments();
            if (args.length > 1 && args[0].equalsIgnoreCase("check")) {
                String name = args[1];
                FreedomAdminPanelVelocity.this.proxy
                    .getScheduler()
                    .buildTask(
                        FreedomAdminPanelVelocity.this,
                        () -> {
                            FreedomAdminPanelVelocity.this.records.clear();
                            RecordService.Record r = FreedomAdminPanelVelocity.this.records
                                .get(FreedomAdminPanelVelocity.this.directory, FreedomAdminPanelVelocity.this.config, name);
                            if (r == null) {
                                invocation.source()
                                    .sendMessage(Component.text("Рецидив " + name + ": нет данных (нет подключения к базе LiteBans)", NamedTextColor.RED));
                            } else {
                                invocation.source()
                                    .sendMessage(
                                        Component.text(
                                            "Рецидив "
                                                + name
                                                + " за "
                                                + FreedomAdminPanelVelocity.this.config.recidivism.windowDays
                                                + " дн. (без снятых): мутов "
                                                + r.counts().mute()
                                                + ", тюрем "
                                                + (r.jailTracked() ? r.jails() : "?")
                                                + ", банов "
                                                + r.counts().ban()
                                                + ", варнов "
                                                + r.counts().warn()
                                                + " (активных "
                                                + r.activeWarns()
                                                + "), киков "
                                                + r.counts().kick(),
                                            NamedTextColor.YELLOW
                                        )
                                    );
                            }
                        }
                    )
                    .schedule();
            } else if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                FreedomAdminPanelVelocity.this.config = PluginConfig.load(FreedomAdminPanelVelocity.this.dataDir, FreedomAdminPanelVelocity.this.logger);
                FreedomAdminPanelVelocity.this.directory.save();
                PlayerDirectory dir = new PlayerDirectory(
                    FreedomAdminPanelVelocity.this.proxy,
                    FreedomAdminPanelVelocity.this.logger,
                    FreedomAdminPanelVelocity.this.config,
                    FreedomAdminPanelVelocity.this.dataDir
                );
                FreedomAdminPanelVelocity.this.directory = dir;
                FreedomAdminPanelVelocity.this.records.clear();
                invocation.source().sendMessage(Component.text("freedomadminpanel: конфиг перезагружен, проверяю LiteBans...", NamedTextColor.GREEN));
                FreedomAdminPanelVelocity.this.proxy
                    .getScheduler()
                    .buildTask(
                        FreedomAdminPanelVelocity.this,
                        () -> {
                            dir.selfCheck();
                            invocation.source().sendMessage(Component.text("LiteBans: " + dir.liteBansStatus(), NamedTextColor.YELLOW));
                            invocation.source()
                                .sendMessage(
                                    Component.text(
                                        "История тюрьмы: " + FreedomAdminPanelVelocity.this.records.jailStatus(dir, FreedomAdminPanelVelocity.this.config),
                                        NamedTextColor.YELLOW
                                    )
                                );
                        }
                    )
                    .schedule();
            } else {
                PlayerDirectory dir = FreedomAdminPanelVelocity.this.directory;
                FreedomAdminPanelVelocity.this.proxy
                    .getScheduler()
                    .buildTask(
                        FreedomAdminPanelVelocity.this,
                        () -> invocation.source()
                            .sendMessage(
                                Component.text(
                                    "freedomadminpanel: LiteBans — "
                                        + dir.liteBansStatus()
                                        + "; история тюрьмы — "
                                        + FreedomAdminPanelVelocity.this.records.jailStatus(dir, FreedomAdminPanelVelocity.this.config)
                                        + "; SkinsRestorer — "
                                        + (FreedomAdminPanelVelocity.this.skins.usesSkinsRestorer() ? "да" : "нет")
                                        + ", известно игроков: "
                                        + dir.knownCount()
                                        + ", право доступа: "
                                        + FreedomAdminPanelVelocity.this.config.permission,
                                    NamedTextColor.YELLOW
                                )
                            )
                    )
                    .schedule();
            }
        }

        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("freedomadminpanel.admin");
        }

        public List<String> suggest(Invocation invocation) {
            return List.of("reload", "status", "check");
        }
    }
}
