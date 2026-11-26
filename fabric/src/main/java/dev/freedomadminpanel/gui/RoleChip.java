package dev.freedomadminpanel.gui;

import dev.freedomadminpanel.config.FreedomAdminPanelConfig;
import dev.freedomadminpanel.config.Role;
import java.time.Duration;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class RoleChip extends PressableWidget {
    public static final int HEIGHT = 18;
    private final Runnable onPress;

    public RoleChip(int rightX, int y, Runnable onPress) {
        super(0, y, 0, 18, Text.literal(label()));
        this.onPress = onPress;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int w = 22 + tr.getWidth(label()) + 4 + tr.getWidth("▼") + 5;
        this.setDimensions(w, 18);
        this.setX(rightX - w);
        this.setTooltip(Tooltip.of(Text.literal("Сменить должность")));
        this.setTooltipDelay(Duration.ofMillis(300L));
    }

    private static String label() {
        Role role = FreedomAdminPanelConfig.get().role();
        return role == null ? "Должность" : role.title;
    }

    @Override
    public void onPress(AbstractInput input) {
        this.onPress.run();
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Role role = FreedomAdminPanelConfig.get().role();
        int color = role == null ? -10787720 : role.color;
        boolean hot = this.isHovered() || this.isFocused();
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        Theme.roundRect(ctx, x, y, w, h, Theme.mix(-15394526, color, hot ? 0.4F : 0.2F));
        Theme.outline(ctx, x, y, w, h, Theme.mix(-15394526, color, hot ? 1.0F : 0.6F));
        ctx.drawItem(new ItemStack(role == null ? Items.PAPER : role.icon), x + 2, y + 1);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String text = label();
        ctx.drawTextWithShadow(tr, text, x + 22, y + 5, -1);
        ctx.drawText(tr, "▼", x + 22 + tr.getWidth(text) + 4, y + 5, Theme.mix(color, -1, 0.4F), false);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
