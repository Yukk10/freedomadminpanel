package dev.freedomadminpanel.gui;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class ActionTile extends PressableWidget {
    private static final long CONFIRM_MS = 3000L;
    private final int accent;
    private final ActionTile.Icon icon;
    private final Consumer<ActionTile> action;
    @Nullable
    private String badge;
    private int badgeColor;
    private boolean confirm;
    @Nullable
    private Supplier<String> counter;
    private boolean armed;
    private long armedAt;

    public ActionTile(int x, int y, int width, int height, Text label, int accent, ActionTile.Icon icon, Consumer<ActionTile> action) {
        super(x, y, width, height, label);
        this.accent = accent;
        this.icon = icon;
        this.action = action;
    }

    public ActionTile badge(String badge, int color) {
        this.badge = badge;
        this.badgeColor = color;
        return this;
    }

    public ActionTile counter(IntSupplier counter) {
        return this.counterText(() -> {
            int n = counter.getAsInt();
            return n > 0 ? "×" + n : null;
        });
    }

    public ActionTile counterText(Supplier<String> counter) {
        this.counter = counter;
        return this;
    }

    public ActionTile confirm() {
        this.confirm = true;
        return this;
    }

    public ActionTile hint(String description, @Nullable String command) {
        Text text = Text.literal(description);
        if (command != null) {
            text = text.copy().append(Text.literal("\n" + command).withColor(-7695450));
        }

        this.setTooltip(Tooltip.of(text));
        this.setTooltipDelay(Duration.ofMillis(350L));
        return this;
    }

    @Override
    public void onPress(AbstractInput input) {
        if (this.confirm && !this.armed) {
            this.armed = true;
            this.armedAt = System.currentTimeMillis();
        } else {
            this.armed = false;
            this.action.accept(this);
        }
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (this.armed && System.currentTimeMillis() - this.armedAt > 3000L) {
            this.armed = false;
        }

        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hot = this.active && (this.isHovered() || this.isFocused());
        int color = this.armed ? -45730 : this.accent;
        int bg;
        if (this.armed) {
            bg = Theme.mix(-15394526, -45730, 0.3F);
        } else if (hot) {
            bg = Theme.mix(-15394526, color, 0.26F);
        } else {
            bg = Theme.mix(-15394526, color, 0.1F);
        }

        Theme.roundRect(ctx, x, y, w, h, bg);
        if (!hot && !this.armed) {
            ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, Theme.mix(-15394526, color, 0.55F));
        } else {
            Theme.outline(ctx, x, y, w, h, color);
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String label = this.armed ? "Точно? Ещё раз" : this.getMessage().getString();
        int textColor = !hot && !this.armed ? -1512204 : -1;
        if (h >= 30) {
            int top = y + (h - 1 - 27) / 2;
            int ix = x + (w - 16) / 2;
            int iy = top + (hot && top - y >= 2 ? -1 : 0);
            this.icon.draw(ctx, ix, iy);
            this.drawBadge(ctx, tr, ix, iy);
            String shown = Theme.ellipsize(tr, label, w - 6);
            ctx.drawCenteredTextWithShadow(tr, shown, x + w / 2, top + 19, textColor);
        } else {
            int badgeW = this.badge != null ? 5 : 0;
            int counterW = this.counterWidth(tr);
            String shown = Theme.ellipsize(tr, label, w - 28 - badgeW - counterW);
            int contentW = 21 + badgeW + tr.getWidth(shown);
            int cx = x + (w - counterW - contentW) / 2;
            int iy = y + (h - 16) / 2;
            this.icon.draw(ctx, cx, iy);
            this.drawBadge(ctx, tr, cx, iy);
            ctx.drawTextWithShadow(tr, shown, cx + 21 + badgeW, y + (h - 8) / 2, textColor);
        }

        this.drawCounter(ctx, tr, x, y, w, h);
    }

    private int counterWidth(TextRenderer tr) {
        String text = this.counter == null ? null : this.counter.get();
        return text != null && !text.isEmpty() ? tr.getWidth(text) + 6 + 5 : 0;
    }

    private void drawCounter(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h) {
        String text = this.counter == null ? null : this.counter.get();
        if (text != null && !text.isEmpty()) {
            int py = h >= 30 ? y + 3 : y + (h - 11) / 2;
            Theme.pill(ctx, tr, text, x + w - 3, py, this.accent);
        }
    }

    private void drawBadge(DrawContext ctx, TextRenderer tr, int ix, int iy) {
        if (this.badge != null) {
            ctx.drawTextWithShadow(tr, this.badge, ix + 15, iy + 9, this.badgeColor);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    @FunctionalInterface
    public interface Icon {
        void draw(DrawContext context, int x, int y);

        static ActionTile.Icon item(Item item) {
            ItemStack stack = new ItemStack(item);
            return (ctx, x, y) -> ctx.drawItem(stack, x, y);
        }
    }
}
