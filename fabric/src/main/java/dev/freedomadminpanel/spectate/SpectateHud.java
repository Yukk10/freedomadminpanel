package dev.freedomadminpanel.spectate;

import dev.freedomadminpanel.FreedomAdminPanelClient;
import dev.freedomadminpanel.gui.PlayerHeads;
import dev.freedomadminpanel.gui.Theme;
import dev.freedomadminpanel.net.PanelNet;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class SpectateHud {
    public static final int W = 236;
    public static final int H = 64;
    public static final int BTN_H = 16;
    private static final int PREV_W = 56;
    private static final int NEXT_W = 56;
    private static final int STOP_W = 18;
    private static final int GAP = 3;

    private SpectateHud() {
    }

    public static SpectateHud.Layout layout() {
        int x = 6;
        int y = 6;
        int prevX = x + 6;
        int profileW = 85;
        int profileX = prevX + 56 + 3;
        int nextX = profileX + profileW + 3;
        int stopX = nextX + 56 + 3;
        return new SpectateHud.Layout(x, y, y + 33, prevX, profileX, profileW, nextX, stopX);
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.of("freedomadminpanel", "spectate"), (ctx, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (SpectateMode.active() && !client.options.hudHidden) {
                if (!(client.currentScreen instanceof SpectateControlScreen)) {
                    draw(ctx, false);
                }
            }
        });
    }

    public static void draw(DrawContext ctx, boolean interactive) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        SpectateHud.Layout l = layout();
        Theme.panel(ctx, l.x(), l.y(), 236, 64, -13712202);
        String name = SpectateMode.current();
        int count = SpectateMode.queue().size();
        int pos = SpectateMode.position();
        int tx = l.x() + 32;
        int right = l.x() + 236 - 8;
        boolean finished = SpectateMode.finished();
        int remaining = SpectateMode.remaining();
        String counter = (pos >= 0 ? pos + 1 : "—") + " / " + count;
        int counterW = tr.getWidth(counter);
        ctx.drawText(tr, counter, right - counterW, l.y() + 8, -7695450, false);
        if (finished) {
            ctx.drawTextWithShadow(tr, Text.literal("Все просмотрены ✔").styled(s -> s.withBold(true)), l.x() + 8, l.y() + 8, -12723068);
        } else if (name != null) {
            Theme.roundRect(ctx, l.x() + 7, l.y() + 6, 22, 22, -13748922);
            PlayerSkinDrawer.draw(ctx, PlayerHeads.skin(name), l.x() + 8, l.y() + 7, 20);
            ctx.drawTextWithShadow(tr, Text.literal(Theme.ellipsize(tr, name, right - counterW - 6 - tx)).styled(s -> s.withBold(true)), tx, l.y() + 8, -1);
        } else {
            ctx.drawText(tr, count == 0 ? "Никого нет" : "Слежка", l.x() + 8, l.y() + 8, -1512204, true);
        }

        int statusColor = -7695450;
        String status;
        if (finished) {
            status = remaining > 0 ? "новых: " + remaining + " — нажмите «След.»" : "новые игроки встанут в конец";
            if (remaining > 0) {
                statusColor = -29630;
            }
        } else if (name == null) {
            status = count == 0 ? "на сервере пусто — ждём игроков" : "нажмите «След.» для телепорта";
        } else if (SpectateMode.currentLeft()) {
            status = "вышел с сервера";
            statusColor = -45730;
        } else {
            PanelNet.NetPlayer p = PanelNet.find(name);
            int ping = ping(client, name);
            status = (p != null && !p.server().isEmpty() ? p.server() : "в сети")
                + (ping >= 0 ? " · " + ping + " мс" : "")
                + (remaining == 0 ? " · последний" : "");
        }

        int sx = name != null && !finished ? tx : l.x() + 8;
        ClientPlayerEntity self = client.player;
        String speed;
        int speedColor;
        if (self != null && self.isSpectator()) {
            float mult = self.getAbilities().getFlySpeed() / 0.05F;
            speed = (SpectateMode.justRestored() ? "↺ " : "") + "полёт ×" + String.format(Locale.ROOT, "%.1f", mult);
            speedColor = SpectateMode.justRestored() ? -12723068 : -7695450;
        } else {
            speed = "вы не в GM 3";
            speedColor = -29630;
        }

        int speedW = tr.getWidth(speed);
        ctx.drawText(tr, speed, right - speedW, l.y() + 19, speedColor, false);
        ctx.drawText(tr, Theme.ellipsize(tr, status, right - speedW - 6 - sx), sx, l.y() + 19, statusColor, false);
        if (!interactive) {
            chip(ctx, tr, l.prevX(), l.btnY(), 56, "← Пред.", -13712202, SpectateMode.prevIndex() >= 0);
            chip(ctx, tr, l.profileX(), l.btnY(), l.profileW(), "Профиль", -10777345, name != null && !finished);
            chip(ctx, tr, l.nextX(), l.btnY(), 56, "След. →", -13712202, remaining > 0 || name != null && !finished);
            chip(ctx, tr, l.stopX(), l.btnY(), 18, "✕", -45730, true);
        }

        String hint = interactive
            ? "Отпустите " + keyName(FreedomAdminPanelClient.spectateMouseKey) + " — вернуться к полёту"
            : keyName(FreedomAdminPanelClient.spectateMouseKey)
                + " — мышь · "
                + keyName(FreedomAdminPanelClient.spectatePrevKey)
                + " / "
                + keyName(FreedomAdminPanelClient.spectateNextKey)
                + " — листать";
        ctx.drawText(tr, Theme.ellipsize(tr, hint, 220), l.x() + 8, l.y() + 53, -10854288, false);
    }

    private static void chip(DrawContext ctx, TextRenderer tr, int x, int y, int w, String label, int color, boolean enabled) {
        Theme.roundRect(ctx, x, y, w, 16, enabled ? Theme.mix(-15394526, color, 0.22F) : -14539217);
        ctx.fill(x + 1, y + 16 - 1, x + w - 1, y + 16, enabled ? color : -10854288);
        ctx.drawCenteredTextWithShadow(tr, label, x + w / 2, y + 4, enabled ? -1512204 : -10854288);
    }

    private static int ping(MinecraftClient client, String name) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return -1;
        } else {
            PlayerListEntry entry = handler.getPlayerListEntry(name);
            return entry == null ? -1 : entry.getLatency();
        }
    }

    private static String keyName(KeyBinding key) {
        if (key == null) {
            return "?";
        } else {
            Key bound = KeyBindingHelper.getBoundKeyOf(key);
            if (bound != null && bound.getCategory() == Type.KEYSYM) {
        switch (bound.getCode()) {
                    case 262:
                        return "→";
                    case 263:
                        return "←";
                    case 264:
                        return "↓";
                    case 265:
                        return "↑";
                    case 340:
                    case 344:
                        return "Shift";
                    case 341:
                    case 345:
                        return "Ctrl";
                    case 342:
                    case 346:
                        return "Alt";
                }
            }

            return key.getBoundKeyLocalizedText().getString();
        }
    }

    public record Layout(int x, int y, int btnY, int prevX, int profileX, int profileW, int nextX, int stopX) {
        public int prevW() {
            return 56;
        }

        public int nextW() {
            return 56;
        }

        public int stopW() {
            return 18;
        }
    }
}
