package dev.freedomadminpanel.gui;

import dev.freedomadminpanel.config.FreedomAdminPanelConfig;
import dev.freedomadminpanel.net.PanelNet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class PlayerListScreen extends BaseMenuScreen {
    private static final int ROW_H = 22;
    private static final int SCROLLBAR_W = 6;
    private static final int SCROLLBAR_HIT = 12;
    private static PlayerListScreen.Tab lastTab = PlayerListScreen.Tab.ONLINE;
    private static final Map<PlayerListScreen.Tab, PlayerListScreen.SortMode> sortModes = new EnumMap<>(
        Map.of(PlayerListScreen.Tab.ONLINE, PlayerListScreen.SortMode.NAME_ASC, PlayerListScreen.Tab.OFFLINE, PlayerListScreen.SortMode.RECENT)
    );
    private static String lastQuery = "";
    private PlayerListScreen.Tab tab;
    private TextFieldWidget search;
    private FlatButton sortButton;
    private FlatButton onlineTab;
    private FlatButton offlineTab;
    private final List<PlayerListScreen.Row> onlineRows = new ArrayList<>();
    private final List<PlayerListScreen.Row> offlineRows = new ArrayList<>();
    private final List<PlayerListScreen.Row> visible = new ArrayList<>();
    private double scroll;
    private boolean draggingScrollbar;
    private double dragGrab;
    private int lx;
    private int ly;
    private int lw;
    private int lh;
    private int ticks;
    private int netVersion;
    private boolean builtWithNet;

    public PlayerListScreen(@Nullable Screen parent) {
        this(parent, lastTab);
    }

    public PlayerListScreen(@Nullable Screen parent, PlayerListScreen.Tab tab) {
        super(Text.literal("Игроки"), parent);
        this.tab = tab;
    }

    @Override
    protected boolean closeOnOpenKey() {
        return true;
    }

    @Override
    protected void init() {
        this.builtWithNet = PanelNet.available();
        this.netVersion = PanelNet.version();
        this.onlineTab = this.offlineTab = null;
        if (!this.builtWithNet) {
            this.tab = PlayerListScreen.Tab.ONLINE;
        }

        lastTab = this.tab;
        this.layoutPanel(320, 380);
        int x = this.px + 10;
        int w = this.pw - 20;
        int y = this.py + 28;
        if (this.builtWithNet) {
            int half = (w - 4) / 2;
            this.onlineTab = this.addDrawableChild(
                new FlatButton(x, y, half, 18, Text.empty(), -12723068, b -> this.switchTab(PlayerListScreen.Tab.ONLINE))
                    .setSelected(this.tab == PlayerListScreen.Tab.ONLINE)
            );
            this.offlineTab = this.addDrawableChild(
                new FlatButton(x + half + 4, y, w - half - 4, 18, Text.empty(), -10787720, b -> this.switchTab(PlayerListScreen.Tab.OFFLINE))
                    .setSelected(this.tab == PlayerListScreen.Tab.OFFLINE)
            );
            y += 24;
        }

        int sortW = 96;
        this.search = new TextFieldWidget(this.textRenderer, x, y + 1, w - sortW - 4, 18, Text.literal("Поиск"));
        this.search.setPlaceholder(Text.literal("Поиск по нику...").withColor(-10854288));
        this.search.setMaxLength(32);
        this.search.setText(lastQuery);
        this.search.setChangedListener(s -> {
            lastQuery = s;
            this.scroll = 0.0;
            this.applyFilter();
        });
        this.addDrawableChild(this.search);
        this.setInitialFocus(this.search);
        this.sortButton = this.addDrawableChild(new FlatButton(x + w - sortW, y, sortW, 20, sortModes.get(this.tab).label(), -10777345, b -> {
            PlayerListScreen.SortMode next = sortModes.get(this.tab).next(this.tab, this.builtWithNet);
            sortModes.put(this.tab, next);
            b.setMessage(next.label());
            this.applyFilter();
        }));
        this.sortButton.setTooltip(Tooltip.of(Text.literal("Сортировка: нажми, чтобы сменить")));
        if (!sortModes.get(this.tab).fits(this.tab, this.builtWithNet)) {
            sortModes.put(this.tab, sortModes.get(this.tab).next(this.tab, this.builtWithNet));
            this.sortButton.setMessage(sortModes.get(this.tab).label());
        }

        y += 24;
        this.lx = x;
        this.ly = y;
        this.lw = w;
        this.lh = this.py + this.ph - 22 - this.ly;
        this.rebuild();
        PanelNet.requestList();
    }

    private void switchTab(PlayerListScreen.Tab newTab) {
        if (this.tab != newTab) {
            this.tab = newTab;
            this.scroll = 0.0;
            this.clearAndInit();
        }
    }

    private void rebuild() {
        this.onlineRows.clear();
        this.offlineRows.clear();
        ClientPlayNetworkHandler handler = this.client == null ? null : this.client.getNetworkHandler();
        Map<String, PlayerListEntry> local = new HashMap<>();
        if (handler != null) {
            for (PlayerListEntry e : handler.getListedPlayerListEntries()) {
                local.put(e.getProfile().name().toLowerCase(Locale.ROOT), e);
            }
        }

        long now = System.currentTimeMillis();
        if (this.builtWithNet) {
            for (PanelNet.NetPlayer p : PanelNet.players()) {
                PlayerListEntry e = local.remove(p.name().toLowerCase(Locale.ROOT));
                if (!p.online() && e == null) {
                    this.offlineRows.add(new PlayerListScreen.Row(p.name(), false, "", -1, p.lastSeen(), p.banned(), p.muted(), null));
                } else {
                    this.onlineRows.add(new PlayerListScreen.Row(p.name(), true, p.server(), e != null ? e.getLatency() : -1, now, p.banned(), p.muted(), e));
                }
            }
        }

        for (PlayerListEntry e : local.values()) {
            this.onlineRows.add(new PlayerListScreen.Row(e.getProfile().name(), true, "", e.getLatency(), now, false, false, e));
        }

        if (FreedomAdminPanelConfig.get().hideSelf && this.client != null) {
            String self = this.client.getSession().getUsername();
            this.onlineRows.removeIf(r -> r.name().equalsIgnoreCase(self));
        }

        if (this.onlineTab != null) {
            this.onlineTab.setMessage(Text.literal("● Онлайн · " + this.onlineRows.size()));
        }

        if (this.offlineTab != null) {
            this.offlineTab.setMessage(Text.literal("Офлайн · " + this.offlineRows.size()));
        }

        this.applyFilter();
    }

    private void applyFilter() {
        this.visible.clear();
        String query = this.search == null ? "" : this.search.getText().trim().toLowerCase(Locale.ROOT);

        for (PlayerListScreen.Row r : this.tab == PlayerListScreen.Tab.ONLINE ? this.onlineRows : this.offlineRows) {
            if (query.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(query)) {
                this.visible.add(r);
            }
        }

        this.visible.sort(sortModes.get(this.tab).comparator);
        this.clampScroll();
    }

    private void clampScroll() {
        int max = Math.max(0, this.visible.size() * 22 - this.lh);
        this.scroll = Math.max(0.0, Math.min(this.scroll, (double)max));
    }

    private boolean scrollable() {
        return this.visible.size() * 22 > this.lh;
    }

    private int thumbHeight() {
        return Math.max(16, this.lh * this.lh / Math.max(1, this.visible.size() * 22));
    }

    private int thumbY() {
        int content = this.visible.size() * 22;
        return content <= this.lh ? this.ly : this.ly + (int)(this.scroll * (this.lh - this.thumbHeight()) / (content - this.lh));
    }

    private boolean onScrollbar(double mx, double my) {
        return this.scrollable() && mx >= this.lx + this.lw - 12 && mx < this.lx + this.lw && my >= this.ly && my < this.ly + this.lh;
    }

    private void dragScrollbarTo(double mouseY) {
        int range = this.lh - this.thumbHeight();
        if (range > 0) {
            double top = mouseY - this.dragGrab - this.ly;
            this.scroll = top / range * (this.visible.size() * 22 - this.lh);
            this.clampScroll();
        }
    }

    @Override
    public void tick() {
        if (this.client != null && this.client.getNetworkHandler() != null) {
            this.ticks++;
            if (PanelNet.version() != this.netVersion) {
                this.netVersion = PanelNet.version();
                if (PanelNet.available() != this.builtWithNet) {
                    this.clearAndInit();
                    return;
                }

                this.rebuild();
            } else if (this.ticks % 20 == 0) {
                this.rebuild();
            }

            if (this.ticks % 200 == 0) {
                PanelNet.requestList();
            }
        } else {
            this.closeAll();
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        String title = this.builtWithNet ? "Игроки сервера" : "Игроки онлайн";
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(title).styled(s -> s.withBold(true)), this.px + 10, this.py + 11, -1512204);
        int total = this.tab == PlayerListScreen.Tab.ONLINE ? this.onlineRows.size() : this.offlineRows.size();
        String count = this.visible.size() == total ? "Всего: " + total : "Найдено: " + this.visible.size() + " из " + total;
        ctx.drawText(this.textRenderer, count, this.px + this.pw - 10 - this.textRenderer.getWidth(count), this.py + 11, -7695450, false);
        Theme.roundRect(ctx, this.lx, this.ly, this.lw, this.lh, -15723750);
        boolean scrollable = this.scrollable();
        int rowW = this.lw - (scrollable ? 9 : 0);
        String self = this.client.getSession().getUsername();
        boolean mouseInList = this.isInside(mouseX, mouseY, this.lx, this.ly, rowW, this.lh);
        ctx.enableScissor(this.lx, this.ly, this.lx + this.lw, this.ly + this.lh);
        int first = Math.max(0, (int)(this.scroll / 22.0));

        for (int i = first; i < this.visible.size(); i++) {
            int y = this.ly + i * 22 - (int)this.scroll;
            if (y >= this.ly + this.lh) {
                break;
            }

            this.drawRow(ctx, this.visible.get(i), i, y, rowW, mouseInList && mouseY >= y && mouseY < y + 22, self);
        }

        ctx.disableScissor();
        if (this.visible.isEmpty()) {
            String empty = total == 0 ? (this.tab == PlayerListScreen.Tab.ONLINE ? "Нет игроков онлайн" : "Нет данных") : "Никого не найдено";
            ctx.drawCenteredTextWithShadow(this.textRenderer, empty, this.lx + this.lw / 2, this.ly + this.lh / 2 - 4, -7695450);
        }

        if (scrollable) {
            int thumbH = this.thumbHeight();
            int thumbY = this.thumbY();
            int sx = this.lx + this.lw - 6 - 1;
            boolean hot = this.draggingScrollbar || this.onScrollbar(mouseX, mouseY);
            ctx.fill(sx, this.ly + 1, sx + 6, this.ly + this.lh - 1, hot ? -14538701 : -14999511);
            int thumbColor = this.draggingScrollbar ? -10777345 : (hot ? Theme.mix(-10787720, -1, 0.3F) : -10787720);
            Theme.roundRect(ctx, sx, thumbY, 6, thumbH, thumbColor);
        }

        int hintColor = -10854288;
        String hint;
        if (PanelNet.status() == PanelNet.Status.DENIED) {
            hint = "Плагин: " + PanelNet.deniedReason();
            hintColor = Theme.mix(-45730, -15394526, 0.3F);
        } else if (this.builtWithNet) {
            hint = "ЛКМ по нику — действия · Enter — первый в списке";
        } else {
            hint = "ЛКМ по нику — действия · Enter — первый";
        }

        ctx.drawCenteredTextWithShadow(
            this.textRenderer, Theme.ellipsize(this.textRenderer, hint, this.pw - 16), this.px + this.pw / 2, this.py + this.ph - 15, hintColor
        );
    }

    private void drawRow(DrawContext ctx, PlayerListScreen.Row row, int index, int y, int rowW, boolean hovered, String self) {
        if (hovered) {
            Theme.roundRect(ctx, this.lx + 2, y + 1, rowW - 4, 20, -14472646);
            ctx.fill(this.lx + 2, y + 3, this.lx + 4, y + 22 - 3, -10777345);
        } else if (index % 2 == 1) {
            ctx.fill(this.lx + 2, y + 1, this.lx + rowW - 2, y + 22 - 1, -15526112);
        }

        PlayerSkinDrawer.draw(ctx, row.entry() != null ? row.entry().getSkinTextures() : PlayerHeads.skin(row.name()), this.lx + 8, y + 3, 16);
        int right = this.lx + rowW - 8;
        if (row.online()) {
            if (row.ping() >= 0) {
                String ping = row.ping() + " мс";
                right -= this.textRenderer.getWidth(ping);
                ctx.drawText(this.textRenderer, ping, right, y + 7, Theme.pingColor(row.ping()), false);
                right -= 5;
            }

            if (!row.server().isEmpty()) {
                right = Theme.pill(ctx, this.textRenderer, row.server(), right, y + 5, Theme.serverColor(row.server())) - 4;
            }
        } else {
            String ago = Theme.ago(row.lastSeen());
            right -= this.textRenderer.getWidth(ago);
            ctx.drawText(this.textRenderer, ago, right, y + 7, -7695450, false);
            right -= 5;
        }

        if (row.muted()) {
            right = Theme.pill(ctx, this.textRenderer, "МУТ", right, y + 5, -29630) - 3;
        }

        if (row.banned()) {
            right = Theme.pill(ctx, this.textRenderer, "БАН", right, y + 5, -45730) - 3;
        }

        boolean isSelf = row.name().equalsIgnoreCase(self);
        String suffix = isSelf ? " (вы)" : "";
        int nameX = this.lx + 30;
        int nameMax = right - 4 - nameX - this.textRenderer.getWidth(suffix);
        String shown = Theme.ellipsize(this.textRenderer, row.name(), nameMax);
        int nameColor = row.online() ? (hovered ? -1 : -1512204) : Theme.mix(-1512204, -15394526, 0.25F);
        ctx.drawText(this.textRenderer, shown, nameX, y + 7, nameColor, true);
        if (isSelf) {
            ctx.drawText(this.textRenderer, suffix, nameX + this.textRenderer.getWidth(shown), y + 7, -10777345, false);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0 && this.onScrollbar(click.x(), click.y())) {
            int top = this.thumbY();
            int height = this.thumbHeight();
            if (click.y() >= top && click.y() < top + height) {
                this.dragGrab = click.y() - top;
            } else {
                this.dragGrab = height / 2.0;
                this.dragScrollbarTo(click.y());
            }

            this.draggingScrollbar = true;
            return true;
        } else if (super.mouseClicked(click, doubled)) {
            return true;
        } else {
            if (click.button() == 0 && this.isInside(click.x(), click.y(), this.lx, this.ly, this.lw, this.lh)) {
                int index = (int)((click.y() - this.ly + this.scroll) / 22.0);
                if (index >= 0 && index < this.visible.size()) {
                    this.openPlayer(this.visible.get(index));
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.draggingScrollbar) {
            this.dragScrollbarTo(click.y());
            return true;
        } else {
            return super.mouseDragged(click, offsetX, offsetY);
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.draggingScrollbar && click.button() == 0) {
            this.draggingScrollbar = false;
            return true;
        } else {
            return super.mouseReleased(click);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (this.isInside(mouseX, mouseY, this.lx, this.ly, this.lw, this.lh)) {
            this.scroll -= vertical * 22.0;
            this.clampScroll();
            return true;
        } else {
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if ((key == 257 || key == 335) && !this.visible.isEmpty()) {
            this.openPlayer(this.visible.get(0));
            return true;
        } else {
            return super.keyPressed(input);
        }
    }

    private void openPlayer(PlayerListScreen.Row row) {
        this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.client.setScreen(new PlayerActionsScreen(this, row.name()));
    }

    private record Row(String name, boolean online, String server, int ping, long lastSeen, boolean banned, boolean muted, @Nullable PlayerListEntry entry) {
    }

    private static enum SortMode {
        NAME_ASC("Ник: А → Я", true, true, false, Comparator.comparing(PlayerListScreen.Row::name, String.CASE_INSENSITIVE_ORDER)),
        NAME_DESC("Ник: Я → А", true, true, false, Comparator.comparing(PlayerListScreen.Row::name, String.CASE_INSENSITIVE_ORDER).reversed()),
        PING(
            "Пинг ↑",
            true,
            false,
            false,
            Comparator.<PlayerListScreen.Row>comparingInt(r -> r.ping() < 0 ? Integer.MAX_VALUE : r.ping())
                .thenComparing(PlayerListScreen.Row::name, String.CASE_INSENSITIVE_ORDER)
        ),
        SERVER(
            "Сервер",
            true,
            false,
            true,
            Comparator.comparing(PlayerListScreen.Row::server, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PlayerListScreen.Row::name, String.CASE_INSENSITIVE_ORDER)
        ),
        RECENT("Недавние", false, true, false, Comparator.comparingLong(PlayerListScreen.Row::lastSeen).reversed()),
        OLDEST("Давние", false, true, false, Comparator.comparingLong(PlayerListScreen.Row::lastSeen)),
        PUNISHED(
            "Наказанные",
            true,
            true,
            true,
            Comparator.<PlayerListScreen.Row>comparingInt(r -> (r.banned() ? 0 : 2) + (r.muted() ? 0 : 1))
                .thenComparing(Comparator.comparingLong(PlayerListScreen.Row::lastSeen).reversed())
        );

        private final String text;
        private final boolean forOnline;
        private final boolean forOffline;
        private final boolean needsNet;
        private final Comparator<PlayerListScreen.Row> comparator;

        private SortMode(String text, boolean forOnline, boolean forOffline, boolean needsNet, Comparator<PlayerListScreen.Row> comparator) {
            this.text = text;
            this.forOnline = forOnline;
            this.forOffline = forOffline;
            this.needsNet = needsNet;
            this.comparator = comparator;
        }

        boolean fits(PlayerListScreen.Tab tab, boolean net) {
            return (tab == PlayerListScreen.Tab.ONLINE ? this.forOnline : this.forOffline) && (net || !this.needsNet);
        }

        Text label() {
            return Text.literal("⇅ " + this.text);
        }

        PlayerListScreen.SortMode next(PlayerListScreen.Tab tab, boolean net) {
            PlayerListScreen.SortMode[] all = values();

            for (int i = 1; i <= all.length; i++) {
                PlayerListScreen.SortMode m = all[(this.ordinal() + i) % all.length];
                if (m.fits(tab, net)) {
                    return m;
                }
            }

            return this;
        }
    }

    public static enum Tab {
        ONLINE,
        OFFLINE;
    }
}
