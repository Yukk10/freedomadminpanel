package dev.freedomadminpanel.gui;

import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.text.Text;

public class FlatButton extends PressableWidget {
    private final int accent;
    private final Consumer<FlatButton> action;
    private boolean selected;

    public FlatButton(int x, int y, int width, int height, Text message, int accent, Consumer<FlatButton> action) {
        super(x, y, width, height, message);
        this.accent = accent;
        this.action = action;
    }

    public FlatButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    public void onPress(AbstractInput input) {
        this.action.accept(this);
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hot = this.active && (this.isHovered() || this.isFocused());
        int bg;
        if (!this.active) {
            bg = -14539217;
        } else if (this.selected) {
            bg = Theme.mix(this.accent, -16777216, 0.25F);
        } else if (hot) {
            bg = Theme.mix(-15394526, this.accent, 0.45F);
        } else {
            bg = Theme.mix(-15394526, this.accent, 0.22F);
        }

        Theme.roundRect(ctx, x, y, w, h, bg);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, this.active ? this.accent : -10854288);
        if (this.selected) {
            ctx.fill(x + 1, y, x + w - 1, y + 1, -1);
        }

        int color = this.active ? (!hot && !this.selected ? -1512204 : -1) : -10854288;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String label = Theme.ellipsize(tr, this.getMessage().getString(), w - 6);
        ctx.drawCenteredTextWithShadow(tr, label, x + w / 2, y + (h - 8) / 2, color);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
