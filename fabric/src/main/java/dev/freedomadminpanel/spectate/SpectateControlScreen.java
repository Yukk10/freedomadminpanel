package dev.freedomadminpanel.spectate;

import dev.freedomadminpanel.FreedomAdminPanelClient;
import dev.freedomadminpanel.gui.FlatButton;
import dev.freedomadminpanel.gui.PlayerActionsScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class SpectateControlScreen extends Screen {
    private FlatButton prev;
    private FlatButton profile;
    private FlatButton next;

    public SpectateControlScreen() {
        super(Text.literal("Слежка"));
    }

    @Override
    protected void init() {
        SpectateHud.Layout l = SpectateHud.layout();
        int h = 16;
        this.prev = this.addDrawableChild(
            new FlatButton(l.prevX(), l.btnY(), l.prevW(), h, Text.literal("← Пред."), -13712202, b -> SpectateMode.prev(this.client))
        );
        this.profile = this.addDrawableChild(new FlatButton(l.profileX(), l.btnY(), l.profileW(), h, Text.literal("Профиль"), -10777345, b -> this.openProfile()));
        this.next = this.addDrawableChild(
            new FlatButton(l.nextX(), l.btnY(), l.nextW(), h, Text.literal("След. →"), -13712202, b -> SpectateMode.next(this.client))
        );
        this.addDrawableChild(new FlatButton(l.stopX(), l.btnY(), l.stopW(), h, Text.literal("✕"), -45730, b -> {
            SpectateMode.stop(this.client, "Режим слежки выключен");
            this.close();
        }));
        this.updateButtons();
    }

    private void openProfile() {
        String name = SpectateMode.current();
        if (name != null) {
            SpectateMode.altSuppressed = true;
            this.client.setScreen(new PlayerActionsScreen(null, name));
        }
    }

    private void updateButtons() {
        this.prev.active = SpectateMode.prevIndex() >= 0;
        this.next.active = SpectateMode.remaining() > 0 || SpectateMode.current() != null && !SpectateMode.finished();
        this.profile.active = SpectateMode.current() != null && !SpectateMode.finished();
    }

    @Override
    public void tick() {
        if (SpectateMode.active() && FreedomAdminPanelClient.spectateMouseHeld(this.client)) {
            this.updateButtons();
        } else {
            this.close();
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (FreedomAdminPanelClient.spectateNextKey.matchesKey(input)) {
            SpectateMode.next(this.client);
            return true;
        } else if (FreedomAdminPanelClient.spectatePrevKey.matchesKey(input)) {
            SpectateMode.prev(this.client);
            return true;
        } else if (input.key() == 256) {
            SpectateMode.altSuppressed = true;
            this.close();
            return true;
        } else {
            return super.keyPressed(input);
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SpectateHud.draw(ctx, true);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
