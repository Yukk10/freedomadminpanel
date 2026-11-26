package dev.freedomadminpanel.gui;

import dev.freedomadminpanel.Commands;
import dev.freedomadminpanel.config.FreedomAdminPanelConfig;
import dev.freedomadminpanel.net.PanelNet;
import dev.freedomadminpanel.net.PlayerRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class CommandFormScreen extends BaseMenuScreen {
    private static final Pattern TIME = Pattern.compile("^(\\d+[a-zA-Z]{1,3})+$");
    private static final int FIELD_H = 18;
    private static final int CHIP_H = 14;
    private static final int LABEL_H = 12;
    private static final int REC_H = 34;
    private static final ActionType[] REC_ORDER = new ActionType[]{ActionType.MUTE, ActionType.JAIL, ActionType.BAN, ActionType.WARN, ActionType.KICK};
    private static int lastJail = 1;
    private final String name;
    private final ActionType type;
    private String timeValue = "";
    private String textValue = "";
    private int jail = lastJail;
    private TextFieldWidget timeField;
    private TextFieldWidget textField;
    private FlatButton executeButton;
    private FlatButton silentButton;
    private final List<FlatButton> jailButtons = new ArrayList<>();
    private int timeLabelY = -1;
    private int jailLabelY = -1;
    private int textLabelY;
    private int previewY;
    private boolean showTimeChips;
    private boolean showTextChips;
    private int recY = -1;
    @Nullable
    private FlatButton applyButton;
    @Nullable
    private String appliedValue;
    private long appliedBase = -1L;
    private long autoBanArmedAt;

    public CommandFormScreen(Screen parent, String name, ActionType type) {
        super(Text.literal(type.title + ": " + name), parent);
        this.name = name;
        this.type = type;
    }

    @Override
    protected int accent() {
        return this.type.color;
    }

    @Override
    protected void init() {
        this.jailButtons.clear();
        this.recY = -1;
        this.applyButton = null;
        if (this.type.isPunishment()) {
            PanelNet.requestRecord(this.name);
        }

        int wanted = 340;
        int innerW = Math.min(wanted, this.width - 16) - 24;
        this.showTimeChips = this.showTextChips = true;
        int contentH = this.layout(0, 0, innerW, false) + 12;
        if (contentH > this.height - 16) {
            this.showTextChips = false;
            contentH = this.layout(0, 0, innerW, false) + 12;
        }

        if (contentH > this.height - 16) {
            this.showTimeChips = false;
            contentH = this.layout(0, 0, innerW, false) + 12;
        }

        this.layoutPanel(wanted, contentH);
        this.layout(this.px + 12, this.py, this.pw - 24, true);
        this.updateState();
        this.setInitialFocus(this.timeField != null ? this.timeField : this.textField);
    }

    private int layout(int x, int top, int w, boolean create) {
        FreedomAdminPanelConfig cfg = FreedomAdminPanelConfig.get();
        int y = top + 52;
        if (this.showRecidivism()) {
            if (create) {
                this.recY = y;
                if (this.type.time != ActionType.TimeMode.NONE) {
                    this.applyButton = this.addDrawableChild(new FlatButton(x + w - 60, y + 14, 60, 14, Text.empty(), this.type.color, bx -> this.applySuggestion()));
                    this.applyButton.setTooltip(Tooltip.of(Text.literal("Подставить рекомендуемый срок в поле «Время»")));
                    this.applyButton.visible = false;
                }
            }

            y += 34;
        }

        if (this.type.time != ActionType.TimeMode.NONE) {
            if (create) {
                this.timeLabelY = y;
            }

            y += 12;
            if (create) {
                this.timeField = new TextFieldWidget(this.textRenderer, x, y, w, 18, Text.literal("Время"));
                this.timeField.setMaxLength(16);
                this.timeField.setText(this.timeValue);
                this.timeField
                    .setPlaceholder(Text.literal(this.type.time == ActionType.TimeMode.OPTIONAL ? "пусто = навсегда" : "например 30m, 1h, 7d").withColor(-10854288));
                this.timeField.setChangedListener(s -> {
                    this.timeValue = s;
                    this.updateState();
                });
                this.addDrawableChild(this.timeField);
            }

            y += 22;
            List<String> presets = new ArrayList<>(cfg.timePresets);
            if (this.type.time == ActionType.TimeMode.OPTIONAL) {
                presets.add("Навсегда");
            }

            if (this.showTimeChips) {
                y += this.chips(presets, x, y, w, create, value -> this.timeField.setText(value.equals("Навсегда") ? "" : value), this.type.color);
            }

            y += 4;
        }

        if (this.type.jail) {
            if (create) {
                this.jailLabelY = y;
            }

            y += 12;
            int count = Math.max(1, cfg.jailCount);
            if (this.jail > count) {
                this.jail = 1;
            }

            int gap = 3;
            int bw = (w - (count - 1) * gap) / count;
            if (create) {
                for (int i = 1; i <= count; i++) {
                    int number = i;
                    FlatButton b = new FlatButton(x + (i - 1) * (bw + gap), y, bw, 16, Text.literal(String.valueOf(i)), this.type.color, btn -> {
                        this.jail = number;
                        lastJail = number;
                        this.updateState();
                    });
                    this.jailButtons.add(this.addDrawableChild(b));
                }
            }

            y += 22;
        }

        if (create) {
            this.textLabelY = y;
        }

        y += 12;
        if (create) {
            this.textField = new TextFieldWidget(this.textRenderer, x, y, w, 18, Text.literal(this.type.textLabel));
            this.textField.setMaxLength(240);
            this.textField.setText(this.textValue);
            this.textField.setPlaceholder(Text.literal(this.type.placeholder).withColor(-10854288));
            this.textField.setChangedListener(s -> {
                this.textValue = s;
                this.updateState();
            });
            this.addDrawableChild(this.textField);
        }

        y += 22;
        List<String> textPresets = this.type.isMessage() ? cfg.messagePresets : (this.type.isRevoke() ? cfg.revokePresets : cfg.reasonPresets);
        if (this.showTextChips && !textPresets.isEmpty()) {
            y += this.chips(textPresets, x, y, w, create, value -> this.textField.setText(value), -10787720);
        }

        y += 6;
        if (create) {
            this.previewY = y;
        }

        y += 26;
        if (create) {
            if (this.type.silentText != null) {
                int third = (w - 8) / 3;
                this.addDrawableChild(new FlatButton(x, y, third, 20, Text.literal("← Назад"), -10787720, bx -> this.close()));
                this.executeButton = this.addDrawableChild(
                    new FlatButton(x + third + 4, y, third, 20, Text.literal("✔ " + this.executeText()), this.type.color, bx -> this.execute(false))
                );
                this.silentButton = this.addDrawableChild(
                    new FlatButton(
                        x + 2 * (third + 4),
                        y,
                        w - 2 * (third + 4),
                        20,
                        Text.literal(this.type.silentText),
                        Theme.mix(this.type.color, -10787720, 0.55F),
                        bx -> this.execute(true)
                    )
                );
                this.silentButton.setTooltip(Tooltip.of(Text.literal("Без оповещения в чате (флаг " + FreedomAdminPanelConfig.get().silentFlag() + ")")));
            } else {
                int half = (w - 6) / 2;
                this.addDrawableChild(new FlatButton(x, y, half, 20, Text.literal("← Назад"), -10787720, bx -> this.close()));
                this.executeButton = this.addDrawableChild(
                    new FlatButton(x + half + 6, y, w - half - 6, 20, Text.literal("✔ " + this.executeText()), this.type.color, bx -> this.execute(false))
                );
            }
        }

        y += 20;
        return y - top;
    }

    private int chips(List<String> values, int x, int y, int w, boolean create, Consumer<String> onPick, int color) {
        int cx = x;
        int rows = 1;

        for (String value : values) {
            int cw = Math.min(w, this.textRenderer.getWidth(value) + 10);
            if (cx > x && cx + cw > x + w) {
                cx = x;
                rows++;
            }

            if (create) {
                int cy = y + (rows - 1) * 17;
                this.addDrawableChild(new FlatButton(cx, cy, cw, 14, Text.literal(value), color, b -> onPick.accept(value)));
            }

            cx += cw + 3;
        }

        return rows * 17;
    }

    private boolean showRecidivism() {
        return this.type.isPunishment() && PanelNet.available();
    }

    @Nullable
    private Recidivism.Suggestion currentSuggestion() {
        PlayerRecord record = PanelNet.record(this.name);
        if (record != null && this.type.time != ActionType.TimeMode.NONE) {
            long base = this.applied() ? this.appliedBase : Recidivism.parse(this.timeValue);
            return Recidivism.suggest(record, this.type, base);
        } else {
            return null;
        }
    }

    private boolean applied() {
        return this.appliedValue != null && this.appliedValue.equals(this.timeValue.trim());
    }

    private void applySuggestion() {
        Recidivism.Suggestion s = this.currentSuggestion();
        if (s != null && this.timeField != null) {
            String value = Recidivism.toCommand(s.total());
            this.appliedBase = s.base();
            this.appliedValue = value;
            this.timeField.setText(value);
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        if (this.recY >= 0) {
            this.drawRecidivism(ctx, this.px + 12, this.recY, this.pw - 24);
        }
    }

    @Override
    public void tick() {
        if (this.autoBanArmedAt > 0L && System.currentTimeMillis() - this.autoBanArmedAt > 3000L) {
            this.autoBanArmedAt = 0L;
            if (this.executeButton != null) {
                this.executeButton.setMessage(Text.literal("✔ " + this.executeText()));
            }
        }

        if (this.applyButton != null) {
            Recidivism.Suggestion s = this.currentSuggestion();
            boolean show = s != null && s.addition() > 0L && !this.applied();
            this.applyButton.visible = show;
            if (show) {
                String label = "→ " + Recidivism.toCommand(s.total());
                int bw = this.textRenderer.getWidth(label) + 10;
                this.applyButton.setMessage(Text.literal(label));
                this.applyButton.setDimensions(bw, 14);
                this.applyButton.setX(this.px + this.pw - 12 - 4 - bw);
            }
        }
    }

    private void drawRecidivism(DrawContext ctx, int x, int y, int w) {
        Theme.roundRect(ctx, x, y - 2, w, 30, -15723750);
        ctx.fill(x, y, x + 2, y + 34 - 8, this.type.color);
        y++;
        PlayerRecord r = PanelNet.record(this.name);
        int tx = x + 7;
        if (r == null) {
            String msg = PanelNet.recordMissing(this.name) ? "Рецидив: нет данных — плагин не подключён к базе LiteBans" : "Рецидив: загрузка…";
            ctx.drawText(this.textRenderer, Theme.ellipsize(this.textRenderer, msg, w - 14), tx, y + 8, -7695450, false);
        } else {
            ctx.drawText(this.textRenderer, "За " + r.windowDays() + " дн.:", tx, y + 2, -7695450, false);
            int px1 = tx + this.textRenderer.getWidth("За " + r.windowDays() + " дн.:") + 5;

            for (ActionType t : REC_ORDER) {
                int n = Recidivism.same(r, t);
                String label = t.title.toLowerCase(Locale.ROOT).replace("предупреждение", "варн")
                    + " "
                    + (t == ActionType.JAIL && !r.jailTracked() ? "?" : String.valueOf(n));
                int color = n <= 0 && t != this.type ? -10787720 : t.color;
                px1 = Theme.pillLeft(ctx, this.textRenderer, label, px1, y, color, t == this.type) + 3;
            }

            int ly = y + 16;
            int maxW = w - 14 - (this.applyButton != null && this.applyButton.visible ? this.applyButton.getWidth() + 8 : 0);
            int color = -1512204;
            String line;
            switch (this.type) {
                case WARN:
                    if (r.autoBanKnown()) {
                        int next = r.activeWarns() + 1;
                        String ban = Recidivism.human(Recidivism.parse(r.autoBanDuration()));
                        if (next == r.autoBanAt()) {
                            line = "⚠ " + next + "-й активный варн — автобан на " + ban + "!";
                            color = -45730;
                        } else if (next < r.autoBanAt()) {
                            line = "Будет " + next + "-й из " + r.autoBanAt() + " варнов — на " + r.autoBanAt() + "-м автобан на " + ban;
                            color = next == r.autoBanAt() - 1 ? -29630 : -1512204;
                        } else {
                            line = "Будет " + next + "-й активный варн (автобан уже был на " + r.autoBanAt() + "-м)";
                            color = -7695450;
                        }
                    } else {
                        line = "Будет " + (r.warn() + 1) + "-й варн за " + r.windowDays() + " дн.";
                    }
                    break;
                case KICK:
                    line = "Будет " + (r.kick() + 1) + "-й кик за " + r.windowDays() + " дн.";
                    break;
                default:
                    Recidivism.Suggestion s = this.currentSuggestion();
                    if (s == null) {
                        line = "";
                    } else if (s.addition() <= 0L) {
                        line = s.level() == 0.0 ? "Рецидива нет — обычный срок " + Recidivism.human(s.base()) : "Срок уже не меньше рекомендуемого";
                        color = -7695450;
                    } else {
                        boolean done = this.applied();
                        line = (done ? "✔ С учётом рецидива: " : "Рекомендуем: ")
                            + Recidivism.human(s.base())
                            + " + "
                            + Recidivism.human(s.addition())
                            + " = "
                            + Recidivism.human(s.total())
                            + (s.capped() ? " (макс.)" : "")
                            + " · ур. "
                            + Recidivism.formatLevel(s.level());
                        color = done ? -12723068 : -1512204;
                    }
            }

            ctx.drawText(this.textRenderer, Theme.ellipsize(this.textRenderer, line, maxW), tx, ly, color, false);
        }
    }

    private String executeText() {
        return switch (this.type) {
            case MSG -> "Отправить";
            case ALERT -> "Добавить";
            default -> this.type.buttonText;
        };
    }

    private boolean timeValid() {
        String t = this.timeValue.trim();

        return switch (this.type.time) {
            case NONE -> true;
            case REQUIRED -> TIME.matcher(t).matches();
            case OPTIONAL -> t.isEmpty() || TIME.matcher(t).matches();
        };
    }

    private boolean isValid() {
        return this.timeValid() && !this.textValue.isBlank();
    }

    private void updateState() {
        if (this.timeField != null) {
            this.timeField.setEditableColor(!this.timeValue.isBlank() && !this.timeValid() ? -45730 : -1512204);
        }

        for (int i = 0; i < this.jailButtons.size(); i++) {
            this.jailButtons.get(i).setSelected(i + 1 == this.jail);
        }

        if (this.executeButton != null) {
            this.executeButton.active = this.isValid();
        }

        if (this.silentButton != null) {
            this.silentButton.active = this.isValid();
        }
    }

    private String buildCommand(boolean silent) {
        Map<String, String> vars = new HashMap<>();
        vars.put("player", this.name);
        vars.put("time", this.type.time == ActionType.TimeMode.NONE ? "" : this.timeValue.trim());
        vars.put("jail", String.valueOf(this.jail));
        vars.put("reason", this.textValue.trim());
        vars.put("message", this.textValue.trim());
        String command = Commands.build(this.type.key, vars);
        return silent ? command + " " + FreedomAdminPanelConfig.get().silentFlag() : command;
    }

    private void execute(boolean silent) {
        if (this.isValid()) {
            PlayerRecord record = PanelNet.record(this.name);
            if (this.type == ActionType.WARN && record != null && record.nextWarnBans() && System.currentTimeMillis() - this.autoBanArmedAt > 3000L) {
                this.autoBanArmedAt = System.currentTimeMillis();
                if (this.executeButton != null) {
                    this.executeButton.setMessage(Text.literal("Точно? Будет автобан"));
                }
            } else {
                if (Commands.send(this.buildCommand(silent))) {
                    this.closeAll();
                }
            }
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key != 257 && key != 335) {
            return super.keyPressed(input);
        } else {
            this.execute(false);
            return true;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawPlayerHeader(ctx, this.name, Text.literal(this.type.title).withColor(this.type.color));
        int x = this.px + 12;
        if (this.timeLabelY >= 0) {
            ctx.drawText(this.textRenderer, "Время", x, this.timeLabelY, -1512204, false);
            int hintColor = -7695450;
            String hint;
            if (!this.timeValue.isBlank() && !this.timeValid()) {
                hint = "неверный формат (1s, 5m, 2h, 7d)";
                hintColor = -45730;
            } else if (this.type.time == ActionType.TimeMode.OPTIONAL && this.timeValue.isBlank()) {
                hint = "навсегда (перманентно)";
                hintColor = -45730;
            } else {
                hint = "s — сек, m — мин, h — час, d — дни";
            }

            ctx.drawText(this.textRenderer, hint, this.px + this.pw - 12 - this.textRenderer.getWidth(hint), this.timeLabelY, hintColor, false);
        }

        if (this.jailLabelY >= 0) {
            ctx.drawText(this.textRenderer, "Номер тюрьмы", x, this.jailLabelY, -1512204, false);
        }

        ctx.drawText(this.textRenderer, this.type.textLabel, x, this.textLabelY, -1512204, false);
        String counter = this.textValue.length() + "/240";
        ctx.drawText(this.textRenderer, counter, this.px + this.pw - 12 - this.textRenderer.getWidth(counter), this.textLabelY, -10854288, false);
        Theme.roundRect(ctx, x, this.previewY, this.pw - 24, 20, -15723750);
        ctx.fill(x, this.previewY + 2, x + 2, this.previewY + 18, this.type.color);
        boolean silentHovered = this.silentButton != null && this.silentButton.isHovered();
        String preview = "/" + this.buildCommand(silentHovered);
        int previewColor = this.isValid() ? -1512204 : -10854288;
        ctx.drawText(this.textRenderer, Theme.ellipsize(this.textRenderer, preview, this.pw - 24 - 14), x + 8, this.previewY + 6, previewColor, false);
    }
}
