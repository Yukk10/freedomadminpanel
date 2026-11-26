package dev.freedomadminpanel.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class InputScreen extends BaseMenuScreen {
    private static final int FIELD_H = 18;
    private static final int CHIP_H = 14;
    private final ActionTile.Icon icon;
    private final int color;
    private final String subtitle;
    private final String label;
    private final String placeholder;
    private final int maxLength;
    private final Predicate<String> valid;
    private final String submitText;
    @Nullable
    private final Function<String, String> preview;
    @Nullable
    private final Function<String, List<String>> suggestions;
    private final Consumer<String> onSubmit;
    private String value = "";
    private TextFieldWidget field;
    private FlatButton submit;
    private int labelY;
    private int previewY = -1;
    private int chipsY = -1;
    private int chipsX;
    private int chipsTop;
    private int chipsW;
    private final List<FlatButton> chipButtons = new ArrayList<>();

    public InputScreen(
        @Nullable Screen parent,
        String title,
        String subtitle,
        ActionTile.Icon icon,
        int color,
        String label,
        String placeholder,
        int maxLength,
        Predicate<String> valid,
        String submitText,
        @Nullable Function<String, String> preview,
        @Nullable Function<String, List<String>> suggestions,
        Consumer<String> onSubmit
    ) {
        super(Text.literal(title), parent);
        this.subtitle = subtitle;
        this.icon = icon;
        this.color = color;
        this.label = label;
        this.placeholder = placeholder;
        this.maxLength = maxLength;
        this.valid = valid;
        this.submitText = submitText;
        this.preview = preview;
        this.suggestions = suggestions;
        this.onSubmit = onSubmit;
    }

    @Override
    protected int accent() {
        return this.color;
    }

    @Override
    protected void init() {
        int wanted = 300;
        int chipsH = this.suggestions == null ? 0 : 36;
        int h = 88 + chipsH + (this.preview != null ? 26 : 0) + 20 + 12;
        this.layoutPanel(wanted, h);
        int x = this.px + 12;
        int w = this.pw - 24;
        int y = this.py + 52;
        this.labelY = y;
        y += 12;
        this.field = new TextFieldWidget(this.textRenderer, x, y, w, 18, Text.literal(this.label));
        this.field.setMaxLength(this.maxLength);
        this.field.setText(this.value);
        this.field.setPlaceholder(Text.literal(this.placeholder).withColor(-10854288));
        this.field.setChangedListener(s -> {
            // слушатель вызывается и при движении курсора — пересобираем подсказки только при смене текста
            if (s.equals(this.value)) {
                return;
            }

            this.value = s;
            this.buildChips();
            this.updateState();
        });
        this.addDrawableChild(this.field);
        this.setInitialFocus(this.field);
        y += 24;
        this.chipsX = x;
        this.chipsTop = y;
        this.chipsW = w;
        this.chipButtons.clear();
        this.buildChips();
        y += chipsH;
        if (this.preview != null) {
            this.previewY = y;
            y += 26;
        }

        int half = (w - 6) / 2;
        this.addDrawableChild(new FlatButton(x, y, half, 20, Text.literal("← Назад"), -10787720, b -> this.close()));
        this.submit = this.addDrawableChild(
            new FlatButton(x + half + 6, y, w - half - 6, 20, Text.literal("✔ " + this.submitText), this.color, b -> this.submit())
        );
        this.updateState();
    }

    /**
     * Подсказки пересобираются на месте, без clearAndInit: тот сбрасывал курсор в конец поля, а после стрелок
     * (клавиатурная навигация) ещё и уводил фокус на первую кнопку.
     */
    private void buildChips() {
        for (FlatButton b : this.chipButtons) {
            this.remove(b);
        }

        this.chipButtons.clear();
        if (this.suggestions == null) {
            this.chipsY = -1;
            return;
        }

        List<String> chips = this.suggestions.apply(this.value.trim());
        this.chipsY = chips.isEmpty() ? this.chipsTop : -1;
        int x = this.chipsX;
        int w = this.chipsW;
        int cx = x;
        int cy = this.chipsTop;

        for (String chip : chips) {
            int cw = Math.min(w, this.textRenderer.getWidth(chip) + 10);
            if (cx > x && cx + cw > x + w) {
                cx = x;
                cy += 17;
                if (cy >= this.chipsTop + 34) {
                    break;
                }
            }

            this.chipButtons.add(this.addDrawableChild(new FlatButton(cx, cy, cw, 14, Text.literal(chip), this.color, b -> {
                this.field.setText(chip);
                this.setFocused(this.field);
            })));
            cx += cw + 3;
        }
    }

    private boolean isValid() {
        return this.valid.test(this.value.trim());
    }

    private void updateState() {
        if (this.submit != null) {
            this.submit.active = this.isValid();
        }
    }

    private void submit() {
        if (this.isValid()) {
            this.onSubmit.accept(this.value.trim());
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if ((key == 257 || key == 335) && !(this.getFocused() instanceof PressableWidget)) {
            this.submit();
            return true;
        } else {
            return super.keyPressed(input);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawIconHeader(ctx, this.icon, this.getTitle(), Text.literal(this.subtitle));
        int x = this.px + 12;
        ctx.drawText(this.textRenderer, this.label, x, this.labelY, -1512204, false);
        if (this.maxLength > 32) {
            String counter = this.value.length() + "/" + this.maxLength;
            ctx.drawText(this.textRenderer, counter, this.px + this.pw - 12 - this.textRenderer.getWidth(counter), this.labelY, -10854288, false);
        }

        if (this.suggestions != null && this.chipsY >= 0) {
            ctx.drawText(
                this.textRenderer,
                Theme.ellipsize(this.textRenderer, "Нет совпадений онлайн — введите ник полностью", this.pw - 24),
                x,
                this.chipsY + 3,
                -10854288,
                false
            );
        }

        if (this.previewY >= 0 && this.preview != null) {
            Theme.roundRect(ctx, x, this.previewY, this.pw - 24, 20, -15723750);
            ctx.fill(x, this.previewY + 2, x + 2, this.previewY + 18, this.color);
            String text = this.preview.apply(this.value.trim());
            ctx.drawText(
                this.textRenderer,
                Theme.ellipsize(this.textRenderer, text, this.pw - 24 - 14),
                x + 8,
                this.previewY + 6,
                this.isValid() ? -1512204 : -10854288,
                false
            );
        }
    }
}
