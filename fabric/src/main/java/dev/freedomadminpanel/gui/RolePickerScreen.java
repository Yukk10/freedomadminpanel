package dev.freedomadminpanel.gui;

import dev.freedomadminpanel.config.FreedomAdminPanelConfig;
import dev.freedomadminpanel.config.Role;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class RolePickerScreen extends BaseMenuScreen {
    private static final int CARD_H = 104;

    public RolePickerScreen(@Nullable Screen parent) {
        super(Text.literal("Выбор должности"), parent);
    }

    @Override
    protected int accent() {
        Role role = FreedomAdminPanelConfig.get().role();
        return role == null ? -10777345 : role.color;
    }

    @Override
    protected void init() {
        this.layoutPanel(360, 198);
        int x = this.px + 12;
        int w = this.pw - 24;
        int gap = 6;
        int cw = (w - 2 * gap) / 3;
        int y = this.py + 52;
        Role[] roles = Role.values();

        for (int i = 0; i < roles.length; i++) {
            int cx = x + i * (cw + gap);
            this.addDrawableChild(new RolePickerScreen.RoleCard(cx, y, i == roles.length - 1 ? x + w - cx : cw, 104, roles[i]));
        }

        this.addDrawableChild(new FlatButton(x, y + 104 + 10, w, 20, Text.literal("← Назад"), -10787720, b -> this.close()));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawIconHeader(ctx, ActionTile.Icon.item(Items.NAME_TAG), Text.literal("Выбор должности"), Text.literal("Кнопки без прав будут скрыты"));
    }

    private class RoleCard extends PressableWidget {
        private final Role role;
        private final ItemStack icon;

        RoleCard(int x, int y, int w, int h, Role role) {
            super(x, y, w, h, Text.literal(role.title));
            this.role = role;
            this.icon = new ItemStack(role.icon);
        }

        @Override
        public void onPress(AbstractInput input) {
            FreedomAdminPanelConfig.get().setRole(this.role);
            RolePickerScreen.this.close();
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            boolean current = FreedomAdminPanelConfig.get().role() == this.role;
            boolean hot = this.isHovered() || this.isFocused();
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            float tint = current ? 0.3F : (hot ? 0.24F : 0.1F);
            Theme.roundRect(ctx, x, y, w, h, Theme.mix(-15394526, this.role.color, tint));
            if (!current && !hot) {
                ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, Theme.mix(-15394526, this.role.color, 0.55F));
            } else {
                Theme.outline(ctx, x, y, w, h, this.role.color);
            }

            int iy = y + 8 - (hot ? 1 : 0);
            BaseMenuScreen.drawScaledIcon(ctx, (c, ix, iy2) -> c.drawItem(this.icon, ix, iy2), x + (w - 32) / 2, iy, 2.0F);
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            Text title = Text.literal(this.role.title).styled(s -> s.withBold(true));
            ctx.drawCenteredTextWithShadow(tr, title, x + w / 2, y + 44, -1);
            List<OrderedText> lines = tr.wrapLines(Text.literal(this.role.description), w - 10);
            int ly = y + 57;

            for (int i = 0; i < Math.min(lines.size(), 3); i++) {
                ctx.drawCenteredTextWithShadow(tr, lines.get(i), x + w / 2, ly, -7695450);
                ly += 10;
            }

            if (current) {
                ctx.drawCenteredTextWithShadow(tr, Text.literal("✔ выбрано").withColor(this.role.color), x + w / 2, y + h - 12, -1);
            }
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            this.appendDefaultNarrations(builder);
        }
    }
}
