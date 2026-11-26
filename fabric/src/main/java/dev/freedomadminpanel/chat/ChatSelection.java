package dev.freedomadminpanel.chat;

import dev.freedomadminpanel.mixin.ChatHudAccessor;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Выделение текста в чате мышью: зажать ЛКМ и протянуть по строкам, затем Ctrl+C или ПКМ — копировать.
 * Двойной клик выделяет слово. Геометрия строк повторяет ChatHud#render (1.21.10).
 */
public final class ChatSelection {
    private static final int HIGHLIGHT = 0x803D7DFF;
    private static final long DOUBLE_CLICK_MS = 300L;
    private static final long COPIED_SHOW_MS = 1500L;
    private static final long AUTOSCROLL_MS = 60L;

    @Nullable
    private static ChatHudLine.Visible anchorLine;
    private static int anchorChar;
    @Nullable
    private static ChatHudLine.Visible headLine;
    private static int headChar;
    private static boolean dragging;
    private static boolean moved;
    private static long lastClickAt;
    @Nullable
    private static ChatHudLine.Visible lastClickLine;
    private static int lastClickChar = -1;
    private static long copiedAt;
    private static long lastAutoScroll;

    private ChatSelection() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof ChatScreen) {
                attach(screen);
            }
        });
    }

    private static void attach(Screen screen) {
        clear();
        ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
            Pos pos = posAt(click.x(), click.y(), false);
            if (click.button() == 1 && hasSelection() && pos != null && isInside(pos)) {
                copy();
                return false;
            }

            if (click.button() != 0) {
                return true;
            }

            if (pos == null) {
                clear();
                return true;
            }

            long now = System.currentTimeMillis();
            boolean doubled = now - lastClickAt < DOUBLE_CLICK_MS && lastClickLine == pos.line && Math.abs(lastClickChar - pos.ch) <= 1;
            lastClickAt = now;
            lastClickLine = pos.line;
            lastClickChar = pos.ch;
            if (doubled) {
                selectWord(pos);
                dragging = false;
                return false;
            }

            anchorLine = headLine = pos.line;
            anchorChar = headChar = pos.ch;
            dragging = true;
            moved = false;
            return true;
        });
        ScreenMouseEvents.allowMouseDrag(screen).register((s, click, dx, dy) -> {
            if (!dragging || click.button() != 0) {
                return true;
            }

            autoScroll(click.y());
            Pos pos = posAt(click.x(), click.y(), true);
            if (pos != null && (pos.line != headLine || pos.ch != headChar)) {
                headLine = pos.line;
                headChar = pos.ch;
                moved = true;
            }

            return false;
        });
        ScreenMouseEvents.allowMouseRelease(screen).register((s, click) -> {
            if (dragging && click.button() == 0) {
                dragging = false;
                if (!moved) {
                    clear();
                }
            }

            return true;
        });
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, input) -> {
            if (input.isCopy() && hasSelection() && !fieldHasSelection(s)) {
                copy();
                return false;
            }

            return true;
        });
        ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> render(ctx));
        ScreenEvents.remove(screen).register(s -> clear());
    }

    private static boolean fieldHasSelection(Screen screen) {
        return screen.getFocused() instanceof TextFieldWidget field && !field.getSelectedText().isEmpty();
    }

    public static void clear() {
        anchorLine = headLine = null;
        anchorChar = headChar = 0;
        dragging = false;
        moved = false;
    }

    private static boolean hasSelection() {
        Range r = range();
        return r != null && (r.startIdx != r.endIdx || r.startCh != r.endCh);
    }

    // ---------------------------------------------------------------- геометрия

    private static ChatHud hud() {
        return MinecraftClient.getInstance().inGameHud.getChatHud();
    }

    private static List<ChatHudLine.Visible> lines() {
        return ((ChatHudAccessor)hud()).freedomadminpanel$visibleMessages();
    }

    private static int scrolled() {
        return ((ChatHudAccessor)hud()).freedomadminpanel$scrolledLines();
    }

    private static int lineHeight() {
        return (int)(9.0 * (MinecraftClient.getInstance().options.getChatLineSpacing().getValue() + 1.0));
    }

    private static float scale() {
        return (float)hud().getChatScale();
    }

    /** Нижняя граница строки 0 в координатах чата (до масштабирования). */
    private static int base() {
        return MathHelper.floor((MinecraftClient.getInstance().getWindow().getScaledHeight() - 40) / scale());
    }

    private static int rows() {
        return Math.max(0, Math.min(hud().getVisibleLineCount(), lines().size() - scrolled()));
    }

    private static boolean chatHidden() {
        return MinecraftClient.getInstance().options.getChatVisibility().getValue() == ChatVisibility.HIDDEN;
    }

    private static int indexOf(@Nullable ChatHudLine.Visible line) {
        if (line != null) {
            List<ChatHudLine.Visible> list = lines();

            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) == line) {
                    return i;
                }
            }
        }

        return -1;
    }

    /** Позиция под курсором. clamp — при протягивании прижимать к ближайшей строке/символу. */
    @Nullable
    private static Pos posAt(double mouseX, double mouseY, boolean clamp) {
        if (chatHidden() || lines().isEmpty()) {
            return null;
        }

        float s = scale();
        double x = mouseX / s - 4.0;
        double y = mouseY / s;
        int rows = rows();
        if (rows <= 0) {
            return null;
        }

        int row = MathHelper.floor((base() - y) / lineHeight());
        if (!clamp) {
            if (row < 0 || row >= rows || x < -4.0 || x > MathHelper.floor(hud().getWidth() / s)) {
                return null;
            }
        } else {
            row = MathHelper.clamp(row, 0, rows - 1);
        }

        ChatHudLine.Visible line = lines().get(row + scrolled());
        return new Pos(line, charAt(line, x));
    }

    private static int charAt(ChatHudLine.Visible line, double x) {
        List<Glyph> glyphs = glyphs(line);
        float cur = 0.0F;

        for (int i = 0; i < glyphs.size(); i++) {
            float w = glyphs.get(i).width;
            if (x < cur + w / 2.0F) {
                return i;
            }

            cur += w;
        }

        return glyphs.size();
    }

    private static List<Glyph> glyphs(ChatHudLine.Visible line) {
        TextHandler handler = MinecraftClient.getInstance().textRenderer.getTextHandler();
        List<Glyph> out = new ArrayList<>();
        line.content().accept((index, style, codePoint) -> {
            out.add(new Glyph(codePoint, handler.getWidth(OrderedText.styled(codePoint, style))));
            return true;
        });
        return out;
    }

    private static float widthTo(List<Glyph> glyphs, int ch) {
        float w = 0.0F;

        for (int i = 0; i < Math.min(ch, glyphs.size()); i++) {
            w += glyphs.get(i).width;
        }

        return w;
    }

    private static String text(List<Glyph> glyphs, int from, int to) {
        StringBuilder sb = new StringBuilder();

        for (int i = Math.max(0, from); i < Math.min(to, glyphs.size()); i++) {
            sb.appendCodePoint(glyphs.get(i).codePoint);
        }

        return sb.toString();
    }

    /** Упорядоченное выделение: start — раньше по тексту (строка с большим индексом — старее). */
    @Nullable
    private static Range range() {
        int a = indexOf(anchorLine);
        int h = indexOf(headLine);
        if (a < 0 || h < 0) {
            return null;
        } else if (a > h || a == h && anchorChar <= headChar) {
            return new Range(a, anchorChar, h, headChar);
        } else {
            return new Range(h, headChar, a, anchorChar);
        }
    }

    private static boolean isInside(Pos pos) {
        Range r = range();
        int i = indexOf(pos.line);
        if (r == null || i < 0 || i > r.startIdx || i < r.endIdx) {
            return false;
        } else if (i == r.startIdx && pos.ch < r.startCh) {
            return false;
        } else {
            return i != r.endIdx || pos.ch <= r.endCh;
        }
    }

    private static void selectWord(Pos pos) {
        List<Glyph> glyphs = glyphs(pos.line);
        int from = Math.min(pos.ch, glyphs.size());
        int to = from;
        if (from < glyphs.size() && isWordChar(glyphs.get(from).codePoint) || from > 0 && isWordChar(glyphs.get(from - 1).codePoint)) {
            while (from > 0 && isWordChar(glyphs.get(from - 1).codePoint)) {
                from--;
            }

            while (to < glyphs.size() && isWordChar(glyphs.get(to).codePoint)) {
                to++;
            }
        }

        anchorLine = headLine = pos.line;
        anchorChar = from;
        headChar = to;
    }

    private static boolean isWordChar(int cp) {
        return !Character.isWhitespace(cp) && cp != ',' && cp != ';' && cp != '"' && cp != '(' && cp != ')' && cp != '[' && cp != ']';
    }

    private static void autoScroll(double mouseY) {
        long now = System.currentTimeMillis();
        if (now - lastAutoScroll < AUTOSCROLL_MS) {
            return;
        }

        float s = scale();
        double y = mouseY / s;
        int rows = rows();
        int top = base() - rows * lineHeight();
        if (y < top && scrolled() + rows < lines().size()) {
            hud().scroll(1);
            lastAutoScroll = now;
        } else if (y > base() && scrolled() > 0) {
            hud().scroll(-1);
            lastAutoScroll = now;
        }
    }

    // ---------------------------------------------------------------- копирование

    private static void copy() {
        String text = selectedText();
        if (!text.isEmpty()) {
            MinecraftClient.getInstance().keyboard.setClipboard(text);
            copiedAt = System.currentTimeMillis();
        }
    }

    private static String selectedText() {
        Range r = range();
        if (r == null) {
            return "";
        }

        List<ChatHudLine.Visible> list = lines();
        StringBuilder sb = new StringBuilder();

        for (int i = r.startIdx; i >= r.endIdx; i--) {
            ChatHudLine.Visible line = list.get(i);
            List<Glyph> glyphs = glyphs(line);
            int from = i == r.startIdx ? r.startCh : 0;
            int to = i == r.endIdx ? r.endCh : glyphs.size();
            boolean continuation = isContinuation(list, i);
            if (continuation && from == 0 && !glyphs.isEmpty() && glyphs.get(0).codePoint == ' ') {
                from = 1;
            }

            if (i != r.startIdx) {
                sb.append(continuation ? joiner(list, i) : "\n");
            }

            sb.append(text(glyphs, from, to));
        }

        return sb.toString();
    }

    /** Строка — перенос предыдущей (ванильный чат добавляет ей ведущий пробел). */
    private static boolean isContinuation(List<ChatHudLine.Visible> list, int i) {
        return i + 1 < list.size() && !list.get(i + 1).endOfEntry();
    }

    /**
     * Чем склеить перенесённую строку с предыдущей: при переносе по пробелу ванилла его съедает, а при переносе
     * длинного слова (ссылки) — нет. Узнаём это, сверяясь с исходным текстом сообщения.
     */
    private static String joiner(List<ChatHudLine.Visible> list, int index) {
        int start = index;
        while (start + 1 < list.size() && !list.get(start + 1).endOfEntry()) {
            start++;
        }

        int end = index;
        while (end > 0 && !list.get(end).endOfEntry()) {
            end--;
        }

        String full = messageText(list, end);
        if (full == null) {
            return " ";
        }

        int p = 0;

        for (int i = start; i >= end; i--) {
            List<Glyph> glyphs = glyphs(list.get(i));
            String part = text(glyphs, i != start && !glyphs.isEmpty() && glyphs.get(0).codePoint == ' ' ? 1 : 0, glyphs.size());
            String sep;
            if (i == start) {
                if (!full.startsWith(part)) {
                    return " ";
                }

                sep = "";
            } else if (full.startsWith(part, p)) {
                sep = "";
            } else if (p < full.length() && Character.isWhitespace(full.charAt(p)) && full.startsWith(part, p + 1)) {
                sep = full.substring(p, p + 1);
            } else {
                return " ";
            }

            p += sep.length();
            if (i == index) {
                return sep;
            }

            p += part.length();
        }

        return " ";
    }

    /** Исходный текст сообщения, к которому относится последняя строка с индексом lastLine. */
    @Nullable
    private static String messageText(List<ChatHudLine.Visible> list, int lastLine) {
        int message = 0;

        for (int i = 0; i < lastLine; i++) {
            if (list.get(i).endOfEntry()) {
                message++;
            }
        }

        List<ChatHudLine> messages = ((ChatHudAccessor)hud()).freedomadminpanel$messages();
        if (message >= messages.size()) {
            return null;
        }

        String s = messages.get(message).content().getString();
        return MinecraftClient.getInstance().options.getChatColors().getValue() ? s : Formatting.strip(s);
    }

    // ---------------------------------------------------------------- отрисовка

    private static void render(DrawContext ctx) {
        Range r = range();
        if (r == null && (anchorLine != null || headLine != null)) {
            clear();
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        boolean selection = hasSelection();
        if (selection && !chatHidden()) {
            float s = scale();
            int lh = lineHeight();
            int base = base();
            int scrolled = scrolled();
            int rows = rows();
            List<ChatHudLine.Visible> list = lines();
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().scale(s, s);
            ctx.getMatrices().translate(4.0F, 0.0F);

            for (int i = r.startIdx; i >= r.endIdx; i--) {
                int row = i - scrolled;
                if (row < 0 || row >= rows) {
                    continue;
                }

                List<Glyph> glyphs = glyphs(list.get(i));
                int from = i == r.startIdx ? r.startCh : 0;
                int to = i == r.endIdx ? r.endCh : glyphs.size();
                int x1 = MathHelper.floor(widthTo(glyphs, from));
                int x2 = MathHelper.ceil(widthTo(glyphs, to));
                if (x2 <= x1 && i != r.endIdx) {
                    x2 = x1 + 3;
                }

                int bottom = base - row * lh;
                if (x2 > x1) {
                    ctx.fill(x1, bottom - lh, x2, bottom, HIGHLIGHT);
                }
            }

            ctx.getMatrices().popMatrix();
        }

        String hint = null;
        int color = 0xFFB8C7E0;
        if (System.currentTimeMillis() - copiedAt < COPIED_SHOW_MS) {
            hint = "✔ Скопировано";
            color = 0xFF3FD07A;
        } else if (selection && !dragging) {
            hint = "Ctrl+C или ПКМ — копировать";
        }

        if (hint != null) {
            int w = tr.getWidth(hint);
            int x = ctx.getScaledWindowWidth() - w - 6;
            int y = ctx.getScaledWindowHeight() - 28;
            ctx.fill(x - 4, y - 3, x + w + 4, y + 11, 0xC0101418);
            ctx.drawTextWithShadow(tr, hint, x, y, color);
        }
    }

    private record Glyph(int codePoint, float width) {
    }

    private record Pos(ChatHudLine.Visible line, int ch) {
    }

    private record Range(int startIdx, int startCh, int endIdx, int endCh) {
    }
}
