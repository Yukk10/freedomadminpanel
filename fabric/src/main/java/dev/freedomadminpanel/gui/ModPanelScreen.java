package dev.freedomadminpanel.gui;

import dev.freedomadminpanel.Commands;
import dev.freedomadminpanel.net.PanelNet;
import dev.freedomadminpanel.spectate.SpectateMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class ModPanelScreen extends BaseMenuScreen {
    private static final int HERO_H = 36;
    private static final int TILE_H = 24;
    private final List<TileLayout.Section> sections = new ArrayList<>();
    private ActionTile playersTile;
    private ActionTile offlineTile;
    private int x;
    private int w;
    private int ticks;
    private boolean builtWithNet;
    private int netVersion;

    public ModPanelScreen() {
        super(Text.literal("Панель модерации"), null);
    }

    @Override
    protected boolean closeOnOpenKey() {
        return true;
    }

    @Override
    protected void init() {
        this.builtWithNet = PanelNet.available();
        this.netVersion = PanelNet.version();
        PanelNet.requestList();
        this.buildSections();
        this.layoutPanel(460, 52 + TileLayout.height(this.sections, 24) + 6 + 14);
        this.x = this.px + 12;
        this.w = this.pw - 24;
        TileLayout.place(this.sections, this.x, this.py + 52, this.w, 24, this::addDrawableChild);
        this.addDrawableChild(new RoleChip(this.px + this.pw - 12, this.py + 15, () -> this.client.setScreen(new RolePickerScreen(this))));
    }

    private void buildSections() {
        this.sections.clear();
        this.offlineTile = null;
        TileLayout.Section hero = new TileLayout.Section(null, -10777345, 4, 36);
        if (this.builtWithNet) {
            this.playersTile = this.tile(
                    this.onlineLabel(), -12723068, this::drawHeadsIcon, t -> this.client.setScreen(new PlayerListScreen(this, PlayerListScreen.Tab.ONLINE))
                )
                .hint("Игроки онлайн на всех серверах сети", null);
            this.offlineTile = this.tile(
                    this.offlineLabel(),
                    -10787720,
                    ActionTile.Icon.item(Items.CLOCK),
                    t -> this.client.setScreen(new PlayerListScreen(this, PlayerListScreen.Tab.OFFLINE))
                )
                .hint("Все, кто когда-либо заходил: когда был, баны и муты", null);
            hero.add("players", this.playersTile, 1.15F).add("players", this.offlineTile, 1.15F);
        } else {
            this.playersTile = this.tile(this.onlineLabel(), -10777345, this::drawHeadsIcon, t -> this.client.setScreen(new PlayerListScreen(this)))
                .hint("Список игроков онлайн: поиск, сортировка, действия", null);
            hero.add("players", this.playersTile, 1.3F)
                .add(
                    "lookup",
                    this.tile("Найти по нику", -6595099, ActionTile.Icon.item(Items.NAME_TAG), t -> this.openLookup())
                        .hint("Открыть меню игрока по нику — работает и для тех, кто не в сети (разбан и т.п.)", null),
                    1.0F
                );
        }

        hero.add(
            "bc",
            this.tile("Объявление", -739006, ActionTile.Icon.item(Items.GOAT_HORN), t -> this.openBroadcast()).hint("Объявление всему серверу", "/bc <текст>"),
            1.0F
        );
        hero.add("spectate", this.tile(SpectateMode.active() ? "Слежка · вкл" : "Слежка", -13712202, ActionTile.Icon.item(Items.SPYGLASS), t -> {
            this.closeAll();
            SpectateMode.toggle(this.client);
        }).hint(SpectateMode.active() ? "Выключить режим слежки" : "Телепорт по очереди ко всем игрокам на выживании (только для модераторов)", null), 1.0F);
        this.sections.add(hero);
        this.sections
            .add(
                new TileLayout.Section("Инструменты", -13712202, 4, 0)
                    .add("reports", this.run("Репорты", -13712202, Items.BOOK, "reports", "Просмотр репортов"))
                    .add("vanish", this.run("Ваниш", -13712202, Items.GLASS, "vanish", "Включить / выключить невидимость"))
                    .add("adminchat", this.run("Админ-чат", -13712202, Items.OAK_SIGN, "adminchat", "Переключить чат админ-состава"))
                    .add("tpsbar", this.run("TPS-бар", -13712202, Items.COMPARATOR, "tpsbar", "Показать / скрыть TPS, MSPT и пинг"))
                    .add("grim", this.run("Grim-алерты", -13712202, Items.SCULK_SENSOR, "grim", "Получать оповещения античита"))
                    .add("alert_list", this.run("Оповещения", -13712202, Items.BELL, "alert_list", "Список оповещений"))
                    .add("ipreport", this.run("Скан твинков", -13712202, Items.RECOVERY_COMPASS, "ipreport", "Сканирование твинков на сервере"))
                    .add(
                        "review_delete",
                        this.tile("Удалить отзыв", -45730, ActionTile.Icon.item(Items.WRITTEN_BOOK), t -> this.openReviewDelete())
                            .hint("Удалить отзыв по ID", "/reviewsadmin delete <ID>")
                    )
            );
        this.sections
            .add(
                new TileLayout.Section("Миры и режим игры", -29630, 5, 0)
                    .add("event", this.run("Ивент", -29630, Items.FIREWORK_ROCKET, "event", "Телепорт в ивент-мир"))
                    .add("survival", this.run("Выживание", -29630, Items.GRASS_BLOCK, "survival", "Телепорт в мир выживания"))
                    .add("gm0", this.run("GM 0", -29630, Items.IRON_SWORD, "gm0", "Режим выживания (только в ивент-мире)"))
                    .add("gm1", this.run("GM 1", -29630, Items.NETHER_STAR, "gm1", "Творческий режим (только в ивент-мире)"))
                    .add("gm3", this.run("GM 3", -29630, Items.ENDER_EYE, "gm3", "Режим наблюдателя (только в ивент-мире)"))
            );
    }

    private ActionTile tile(String label, int color, ActionTile.Icon icon, Consumer<ActionTile> action) {
        return new ActionTile(0, 0, 0, 0, Text.literal(label), color, icon, action);
    }

    private ActionTile run(String label, int color, Item icon, String key, String description) {
        String command = Commands.build(key, Map.of());
        return this.tile(label, color, ActionTile.Icon.item(icon), t -> {
            Commands.send(command);
            this.closeAll();
        }).hint(description, "/" + command);
    }

    private void openLookup() {
        this.client
            .setScreen(
                new InputScreen(
                    this,
                    "Найти игрока",
                    "Работает и для игроков не в сети",
                    ActionTile.Icon.item(Items.NAME_TAG),
                    -6595099,
                    "Ник игрока",
                    "Введите ник...",
                    32,
                    s -> !s.isEmpty() && !s.contains(" "),
                    "Открыть",
                    null,
                    this::matchingPlayers,
                    nick -> this.client.setScreen(new PlayerActionsScreen(this, nick))
                )
            );
    }

    private void openBroadcast() {
        this.client
            .setScreen(
                new InputScreen(
                    this,
                    "Объявление",
                    "Сообщение увидят все игроки",
                    ActionTile.Icon.item(Items.GOAT_HORN),
                    -739006,
                    "Текст объявления",
                    "Введите текст...",
                    200,
                    s -> !s.isEmpty(),
                    "Отправить",
                    s -> "/" + Commands.build("bc", Map.of("message", s)),
                    null,
                    s -> {
                        Commands.send(Commands.build("bc", Map.of("message", s)));
                        this.closeAll();
                    }
                )
            );
    }

    private void openReviewDelete() {
        this.client
            .setScreen(
                new InputScreen(
                    this,
                    "Удалить отзыв",
                    "Отзыв будет удалён без возможности вернуть",
                    ActionTile.Icon.item(Items.WRITTEN_BOOK),
                    -45730,
                    "ID отзыва",
                    "например 128",
                    16,
                    s -> !s.isEmpty() && !s.contains(" "),
                    "Удалить",
                    s -> "/" + Commands.build("review_delete", Map.of("id", s)),
                    null,
                    s -> {
                        Commands.send(Commands.build("review_delete", Map.of("id", s)));
                        this.closeAll();
                    }
                )
            );
    }

    private List<String> matchingPlayers(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        Stream<String> names = PanelNet.available() && !q.isEmpty()
            ? PanelNet.players().stream().map(PanelNet.NetPlayer::name)
            : this.onlinePlayers().stream().map(e -> e.getProfile().name());
        return names.filter(n -> n.toLowerCase(Locale.ROOT).contains(q) && !n.equalsIgnoreCase(query)).sorted(String.CASE_INSENSITIVE_ORDER).limit(12L).toList();
    }

    private Collection<PlayerListEntry> onlinePlayers() {
        ClientPlayNetworkHandler handler = this.client == null ? null : this.client.getNetworkHandler();
        return (Collection<PlayerListEntry>)(handler == null ? List.of() : handler.getListedPlayerListEntries());
    }

    private int onlineCount() {
        return this.builtWithNet ? Math.max(PanelNet.onlineCount(), this.onlinePlayers().size()) : this.onlinePlayers().size();
    }

    private String onlineLabel() {
        return (this.builtWithNet ? "Онлайн · " : "Игроки · ") + this.onlineCount();
    }

    private String offlineLabel() {
        return "Офлайн · " + PanelNet.offlineCount();
    }

    private void drawHeadsIcon(DrawContext ctx, int ix, int iy) {
        List<PlayerListEntry> heads = this.onlinePlayers().stream().limit(3L).toList();
        if (heads.isEmpty()) {
            ctx.drawItem(new ItemStack(Items.PLAYER_HEAD), ix, iy);
        } else {
            int step = 9;
            int start = ix - (heads.size() - 1) * step / 2;

            for (int i = heads.size() - 1; i >= 0; i--) {
                int hx = start + i * step;
                ctx.fill(hx - 1, iy - 1, hx + 17, iy + 17, -15723750);
                PlayerSkinDrawer.draw(ctx, heads.get(i).getSkinTextures(), hx, iy, 16);
            }
        }
    }

    @Override
    public void tick() {
        if (PanelNet.version() != this.netVersion) {
            this.netVersion = PanelNet.version();
            if (PanelNet.available() != this.builtWithNet) {
                this.clearAndInit();
                return;
            }
        }

        if (++this.ticks % 20 == 0 || this.ticks == 1) {
            if (this.playersTile != null) {
                this.playersTile.setMessage(Text.literal(this.onlineLabel()));
            }

            if (this.offlineTile != null) {
                this.offlineTile.setMessage(Text.literal(this.offlineLabel()));
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        String self = this.client.getSession().getUsername();
        PlayerListEntry me = this.findEntry(self);
        ActionTile.Icon selfHead = me != null
            ? (c, ix, iy) -> PlayerSkinDrawer.draw(c, me.getSkinTextures(), ix, iy, 16)
            : ActionTile.Icon.item(Items.PLAYER_HEAD);
        this.drawIconHeader(ctx, selfHead, Text.literal("Панель модерации"), Text.literal(self + " · онлайн: " + this.onlineCount()));
        TileLayout.drawTitles(ctx, this.textRenderer, this.sections, this.x, this.x + this.w);
        ctx.drawCenteredTextWithShadow(
            this.textRenderer, "Esc — закрыть · наведи на кнопку, чтобы увидеть команду", this.px + this.pw / 2, this.py + this.ph - 14, -10854288
        );
    }
}
