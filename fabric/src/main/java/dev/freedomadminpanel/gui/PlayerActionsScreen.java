package dev.freedomadminpanel.gui;

import dev.freedomadminpanel.Commands;
import dev.freedomadminpanel.net.PanelNet;
import dev.freedomadminpanel.net.PlayerRecord;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class PlayerActionsScreen extends BaseMenuScreen {
    private static final int TABS_H = 18;
    private static final int BOTTOM_H = 20;
    private static PlayerActionsScreen.Tab lastTab = PlayerActionsScreen.Tab.MAIN;
    private final String name;
    private final Map<PlayerActionsScreen.Tab, List<TileLayout.Section>> tabs = new EnumMap<>(PlayerActionsScreen.Tab.class);
    private int x;
    private int w;

    public PlayerActionsScreen(@Nullable Screen parent, String name) {
        super(Text.literal("Действия: " + name), parent);
        this.name = name;
    }

    @Override
    protected boolean closeOnOpenKey() {
        return true;
    }

    @Override
    protected int accent() {
        return lastTab.color;
    }

    @Override
    protected void init() {
        PanelNet.requestRecord(this.name);
        this.buildTabs();
        List<PlayerActionsScreen.Tab> visibleTabs = this.tabs.keySet().stream().filter(t -> TileLayout.height(this.tabs.get(t), 30) > 0).toList();
        if (!visibleTabs.contains(lastTab) && !visibleTabs.isEmpty()) {
            lastTab = visibleTabs.get(0);
        }

        int fixed = 112;
        int tileH = 24;

        for (int candidate = 34; candidate >= 30; candidate--) {
            if (fixed + this.maxContentHeight(candidate) <= this.height - 16) {
                tileH = candidate;
                break;
            }
        }

        this.layoutPanel(320, fixed + this.maxContentHeight(tileH));
        this.x = this.px + 12;
        this.w = this.pw - 24;
        int ty = this.py + 50;
        int tw = (this.w - (visibleTabs.size() - 1) * 3) / Math.max(1, visibleTabs.size());

        for (int i = 0; i < visibleTabs.size(); i++) {
            PlayerActionsScreen.Tab tab = visibleTabs.get(i);
            int bx = this.x + i * (tw + 3);
            int bw = i == visibleTabs.size() - 1 ? this.x + this.w - bx : tw;
            this.addDrawableChild(new FlatButton(bx, ty, bw, 18, Text.literal(tab.title), tab.color, b -> {
                lastTab = tab;
                this.clearAndInit();
            }).setSelected(tab == lastTab));
        }

        int contentY = ty + 18 + 8;
        TileLayout.place(this.tabs.get(lastTab), this.x, contentY, this.w, tileH, this::addDrawableChild);
        this.addDrawableChild(new FlatButton(this.x, this.py + this.ph - 10 - 20, this.w, 20, Text.literal("← Назад"), -10787720, b -> this.close()));
    }

    private int maxContentHeight(int tileH) {
        int max = 0;

        for (List<TileLayout.Section> sections : this.tabs.values()) {
            max = Math.max(max, TileLayout.height(sections, tileH));
        }

        return max;
    }

    private void buildTabs() {
        this.tabs.clear();
        TileLayout.Section move = new TileLayout.Section("Перемещение", -13712202, 3, 0)
            .add("tp", this.run("Тп к игроку", -13712202, Items.ENDER_PEARL, "tp", "Телепортироваться к игроку"))
            .add("tphere", this.run("Тп к себе", -13712202, Items.LEAD, "tphere", "Телепортировать игрока к себе"))
            .add("spawn", this.run("На спавн", -13712202, Items.COMPASS, "spawn", "Телепортировать игрока на спавн"))
            .add("homes", this.run("Список Домов", -13712202, Items.RED_BED, "homes", "Точки дома игрока / телепорт на них"))
            .add("call", this.run("Позвать игрока", -13712202, Items.GOAT_HORN, "call", "Позвать игрока (вывести из АФК)"));
        TileLayout.Section talk = new TileLayout.Section("Связь и оповещения", -11692289, 3, 0)
            .add(
                "msg",
                this.form("Написать", -11692289, Items.WRITABLE_BOOK, ActionType.MSG, "Написать личное сообщение", "/msg " + this.name + " <сообщение>"),
                0.8F
            )
            .add("alert", this.form("+ Оповещение", -739006, Items.BELL, ActionType.ALERT, "Добавить оповещение", "/alert add " + this.name + " <причина>"), 1.1F)
            .add("alert_remove", this.run("− Оповещение", -739006, Items.BELL, "alert_remove", "Удалить оповещения игрока (нажмите дважды)").confirm(), 1.1F);
        this.tabs.put(PlayerActionsScreen.Tab.MAIN, List.of(move, talk));
        TileLayout.Section check = new TileLayout.Section("Проверка игрока", -6595099, 3, 0)
            .add("profile", this.tile("Профиль", -6595099, this::drawHeadIcon, this.send("profile")).hint("Открыть профиль игрока", this.cmd("profile")))
            .add("invsee", this.run("Инвентарь", -6595099, Items.CHEST, "invsee", "Открыть инвентарь игрока"))
            .add("enderchest", this.run("Эндерчест", -6595099, Items.ENDER_CHEST, "enderchest", "Открыть эндерчест игрока"))
            .add("seen", this.run("Последний заход", -6595099, Items.CLOCK, "seen", "Когда игрок заходил последний раз"))
            .add("alts", this.run("Твинки", -6595099, Items.SPYGLASS, "alts", "Проверка IP / твинков"))
            .add("checkaccount", this.run("Твинки №2", -6595099, Items.NAME_TAG, "checkaccount", "Проверка IP / твинков (второй способ)"))
            .add("iphistory", this.run("IP-история", -6595099, Items.MAP, "iphistory", "Список IP игрока"))
            .add("ahhistory", this.run("Лог аукциона", -6595099, Items.LECTERN, "ahhistory", "История действий игрока на аукционе"))
            .add("warnings", this.run("Варны", -6595099, Items.PAPER, "warnings", "Список варнов игрока").badge("!", -739006));
        this.tabs.put(PlayerActionsScreen.Tab.CHECK, List.of(check));
        TileLayout.Section give = new TileLayout.Section("Выдать наказание", -45730, 3, 0)
            .add("mute", this.punish(ActionType.MUTE, Items.NOTE_BLOCK).badge("×", -45730))
            .add("jail", this.punish(ActionType.JAIL, Items.IRON_BARS))
            .add("ban", this.punish(ActionType.BAN, Items.BARRIER))
            .add("warn", this.punish(ActionType.WARN, Items.PAPER).badge("!", -739006))
            .add("kick", this.punish(ActionType.KICK, Items.LEATHER_BOOTS))
            .add("history", this.run("История", -10787720, Items.BOOK, "history", "История наказаний игрока"))
            .add("jailhistory", this.run("История тюрьмы", -10787720, Items.IRON_BARS, "jailhistory", "История тюрьмы игрока"));
        TileLayout.Section revoke = new TileLayout.Section("Снять наказание", -12723068, 2, 24)
            .add("unmute", this.revoke(ActionType.UNMUTE, "Размутить", Items.NOTE_BLOCK), 0.85F)
            .add("unjail", this.run("Выпустить с тюрьмы", -12723068, Items.IRON_DOOR, "unjail", "Выпустить из тюрьмы (нажмите дважды)").confirm(), 1.15F)
            .add("unban", this.revoke(ActionType.UNBAN, "Разбанить", Items.TOTEM_OF_UNDYING), 0.85F)
            .add("unwarn", this.revoke(ActionType.UNWARN, "Снять предупреждение", Items.PAPER), 1.15F);
        this.tabs.put(PlayerActionsScreen.Tab.PUNISH, List.of(give, revoke));
    }

    private ActionTile tile(String label, int color, ActionTile.Icon icon, Consumer<ActionTile> action) {
        return new ActionTile(0, 0, 0, 0, Text.literal(label), color, icon, action);
    }

    private ActionTile run(String label, int color, Item icon, String key, String description) {
        return this.tile(label, color, ActionTile.Icon.item(icon), this.send(key)).hint(description, this.cmd(key));
    }

    private ActionTile form(String label, int color, Item icon, ActionType type, String description, String preview) {
        return this.tile(label, color, ActionTile.Icon.item(icon), t -> this.client.setScreen(new CommandFormScreen(this, this.name, type)))
            .hint(description, preview);
    }

    private ActionTile punish(ActionType type, Item icon) {
        String preview = "/" + Commands.build(type.key, Map.of("player", this.name, "time", "<время>", "jail", "<номер>", "reason", "<причина>"));
        return this.tile(type.title, type.color, ActionTile.Icon.item(icon), t -> this.client.setScreen(new CommandFormScreen(this, this.name, type)))
            .hint(type.buttonText, preview)
            .counterText(() -> {
                PlayerRecord r = PanelNet.record(this.name);
                if (r == null) {
                    return null;
                } else if (type == ActionType.WARN && r.autoBanKnown()) {
                    return r.activeWarns() + "/" + r.autoBanAt();
                } else {
                    int n = Recidivism.same(r, type);
                    return n > 0 ? "×" + n : null;
                }
            });
    }

    private ActionTile revoke(ActionType type, String label, Item icon) {
        String preview = "/" + Commands.build(type.key, Map.of("player", this.name, "reason", "<причина>"));
        return this.tile(label, -12723068, ActionTile.Icon.item(icon), t -> this.client.setScreen(new CommandFormScreen(this, this.name, type)))
            .hint(type.title, preview);
    }

    private Text subtitle() {
        PlayerRecord r = PanelNet.record(this.name);
        if (lastTab != PlayerActionsScreen.Tab.PUNISH || r == null) {
            return Text.literal("Выберите действие");
        } else {
            return r.total() == 0
                ? Text.literal("Чист: наказаний за " + r.windowDays() + " дн. нет").withColor(-12723068)
                : Text.literal("Рецидив за " + r.windowDays() + " дн. — на каждой плитке").withColor(-29630);
        }
    }

    private void drawHeadIcon(DrawContext ctx, int ix, int iy) {
        PlayerListEntry entry = this.findEntry(this.name);
        PlayerSkinDrawer.draw(ctx, entry != null ? entry.getSkinTextures() : PlayerHeads.skin(this.name), ix, iy, 16);
    }

    private String cmd(String key) {
        return "/" + Commands.build(key, Map.of("player", this.name));
    }

    private Consumer<ActionTile> send(String key) {
        return t -> {
            Commands.send(Commands.build(key, Map.of("player", this.name)));
            this.closeAll();
        };
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawPlayerHeader(ctx, this.name, this.subtitle());
        TileLayout.drawTitles(ctx, this.textRenderer, this.tabs.getOrDefault(lastTab, new ArrayList<>()), this.x, this.x + this.w);
    }

    private static enum Tab {
        MAIN("Основное", -13712202),
        CHECK("Проверка", -6595099),
        PUNISH("Наказания", -45730);

        final String title;
        final int color;

        private Tab(String title, int color) {
            this.title = title;
            this.color = color;
        }
    }
}
