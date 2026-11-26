package dev.freedomadminpanel.gui;

import dev.freedomadminpanel.FreedomAdminPanelClient;
import dev.freedomadminpanel.net.PanelNet;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public abstract class BaseMenuScreen extends Screen {
    @Nullable
    protected final Screen parent;
    protected int px;
    protected int py;
    protected int pw;
    protected int ph;

    protected BaseMenuScreen(Text title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected void layoutPanel(int w, int h) {
        this.pw = Math.min(w, this.width - 16);
        this.ph = Math.min(h, this.height - 16);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;
    }

    protected int accent() {
        return -10777345;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        Theme.panel(ctx, this.px, this.py, this.pw, this.ph, this.accent());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    protected void closeAll() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    protected boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Nullable
    protected PlayerListEntry findEntry(String name) {
        if (this.client == null) {
            return null;
        } else {
            ClientPlayNetworkHandler handler = this.client.getNetworkHandler();
            if (handler == null) {
                return null;
            } else {
                for (PlayerListEntry e : handler.getPlayerList()) {
                    if (e.getProfile().name().equalsIgnoreCase(name)) {
                        return e;
                    }
                }

                return null;
            }
        }
    }

    protected void drawPlayerHeader(DrawContext ctx, String name, Text subtitle) {
        PlayerListEntry entry = this.findEntry(name);
        PanelNet.NetPlayer net = PanelNet.find(name);
        int hx = this.px + 12;
        int hy = this.py + 12;
        Theme.roundRect(ctx, hx - 2, hy - 2, 28, 28, -13748922);
        PlayerSkinDrawer.draw(ctx, entry != null ? entry.getSkinTextures() : PlayerHeads.skin(name), hx, hy, 24);
        Text status;
        if (entry != null) {
            int ping = entry.getLatency();
            status = Text.literal("● " + ping + " мс").withColor(Theme.pingColor(ping));
        } else if (net != null && net.online()) {
            status = Text.literal("● в сети" + (net.server().isEmpty() ? "" : " · " + net.server())).withColor(-12723068);
        } else if (net != null) {
            status = Text.literal("● был " + Theme.ago(net.lastSeen())).withColor(-7695450);
        } else {
            status = Text.literal("● не в сети").withColor(-45730);
        }

        int statusW = this.textRenderer.getWidth(status);
        ctx.drawText(this.textRenderer, status, this.px + this.pw - 12 - statusW, hy + 2, -1512204, false);
        if (net != null) {
            int right = this.px + this.pw - 12;
            if (net.muted()) {
                right = Theme.pill(ctx, this.textRenderer, "МУТ", right, hy + 13, -29630) - 3;
            }

            if (net.banned()) {
                Theme.pill(ctx, this.textRenderer, "БАН", right, hy + 13, -45730);
            }
        }

        int nameMax = this.pw - 12 - statusW - 8 - (hx + 32 - this.px);
        ctx.drawTextWithShadow(
            this.textRenderer, Text.literal(Theme.ellipsize(this.textRenderer, name, nameMax)).styled(s -> s.withBold(true)), hx + 32, hy + 2, -1512204
        );
        ctx.drawText(this.textRenderer, subtitle, hx + 32, hy + 14, -7695450, false);
        Theme.separator(ctx, this.px + 8, this.px + this.pw - 8, this.py + 44);
    }

    protected void drawIconHeader(DrawContext ctx, ActionTile.Icon icon, Text title, Text subtitle) {
        int hx = this.px + 12;
        int hy = this.py + 12;
        Theme.roundRect(ctx, hx - 2, hy - 2, 28, 28, -13748922);
        drawScaledIcon(ctx, icon, hx, hy, 1.5F);
        ctx.drawTextWithShadow(this.textRenderer, title.copy().styled(s -> s.withBold(true)), hx + 32, hy + 2, -1512204);
        ctx.drawText(this.textRenderer, subtitle, hx + 32, hy + 14, -7695450, false);
        Theme.separator(ctx, this.px + 8, this.px + this.pw - 8, this.py + 44);
    }

    protected static void drawScaledIcon(DrawContext ctx, ActionTile.Icon icon, int x, int y, float scale) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        icon.draw(ctx, 0, 0);
        ctx.getMatrices().popMatrix();
    }

    protected int contentTop() {
        return this.py + 52;
    }

    protected boolean closeOnOpenKey() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (this.closeOnOpenKey() && FreedomAdminPanelClient.openKey.matchesKey(input)) {
            this.closeAll();
            return true;
        } else {
            return super.keyPressed(input);
        }
    }
}
