package dev.freedomadminpanel.gui;

import java.util.Locale;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public final class Theme {
    public static final int PANEL_BG = -15394526;
    public static final int PANEL_BORDER = -13748922;
    public static final int LIST_BG = -15723750;
    public static final int ROW_ALT = -15526112;
    public static final int ROW_HOVER = -14472646;
    public static final int SEPARATOR = -14275526;
    public static final int SHADOW = 1711276032;
    public static final int TEXT = -1512204;
    public static final int MUTED = -7695450;
    public static final int DISABLED = -10854288;
    public static final int ACCENT = -10777345;
    public static final int TEAL = -13712202;
    public static final int GREEN = -12723068;
    public static final int BLUE = -11692289;
    public static final int YELLOW = -739006;
    public static final int ORANGE = -29630;
    public static final int RED = -45730;
    public static final int DARK_RED = -4186054;
    public static final int PURPLE = -6595099;
    public static final int PINK = -36939;
    public static final int GRAY = -10787720;

    private Theme() {
    }

    public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + 1, color);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void sectionTitle(DrawContext ctx, TextRenderer tr, String title, int x, int y, int right, int accent) {
        ctx.fill(x, y + 1, x + 2, y + 8, accent);
        ctx.drawText(tr, title, x + 6, y + 1, -7695450, false);
        int lineX = x + 6 + tr.getWidth(title) + 6;
        if (lineX < right) {
            ctx.fill(lineX, y + 4, right, y + 5, -14275526);
        }
    }

    public static void panel(DrawContext ctx, int x, int y, int w, int h, int accent) {
        roundRect(ctx, x + 3, y + 3, w, h, 1711276032);
        roundRect(ctx, x, y, w, h, -13748922);
        roundRect(ctx, x + 1, y + 1, w - 2, h - 2, -15394526);
        ctx.fill(x + 2, y + 1, x + w - 2, y + 3, accent);
    }

    public static void separator(DrawContext ctx, int x1, int x2, int y) {
        ctx.fill(x1, y, x2, y + 1, -14275526);
    }

    public static int mix(int a, int b, float t) {
        int r = (int)((a >> 16 & 0xFF) * (1.0F - t) + (b >> 16 & 0xFF) * t);
        int g = (int)((a >> 8 & 0xFF) * (1.0F - t) + (b >> 8 & 0xFF) * t);
        int bl = (int)((a & 0xFF) * (1.0F - t) + (b & 0xFF) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    public static int pingColor(int ping) {
        if (ping < 100) {
            return -12723068;
        } else if (ping < 200) {
            return -739006;
        } else {
            return ping < 400 ? -29630 : -45730;
        }
    }

    public static String ago(long millis) {
        if (millis <= 0L) {
            return "давно";
        } else {
            long s = Math.max(0L, (System.currentTimeMillis() - millis) / 1000L);
            if (s < 60L) {
                return "только что";
            } else {
                long m = s / 60L;
                if (m < 60L) {
                    return m + " мин назад";
                } else {
                    long h = m / 60L;
                    if (h < 24L) {
                        return h + " ч назад";
                    } else {
                        long d = h / 24L;
                        if (d < 30L) {
                            return d + " д назад";
                        } else {
                            return d < 365L ? d / 30L + " мес назад" : d / 365L + " г назад";
                        }
                    }
                }
            }
        }
    }

    public static int pill(DrawContext ctx, TextRenderer tr, String text, int right, int y, int color) {
        int w = tr.getWidth(text) + 6;
        int x = right - w;
        roundRect(ctx, x, y, w, 11, mix(-15394526, color, 0.3F));
        ctx.drawText(tr, text, x + 3, y + 2, mix(color, -1, 0.35F), false);
        return x;
    }

    public static int pillLeft(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color, boolean strong) {
        int w = tr.getWidth(text) + 6;
        roundRect(ctx, x, y, w, 11, mix(-15394526, color, strong ? 0.55F : 0.22F));
        if (strong) {
            outline(ctx, x, y, w, 11, color);
        }

        ctx.drawText(tr, text, x + 3, y + 2, strong ? -1 : mix(color, -1, 0.35F), false);
        return x + w;
    }

    public static int serverColor(String server) {
        switch (server.toLowerCase(Locale.ROOT)) {
            case "лобби":
            case "lobby":
                return -11692289;
            case "выживание":
            case "survival":
                return -12723068;
            case "ивент":
            case "event":
                return -29630;
            case "столбы":
            case "pillars":
                return -6595099;
            default:
                int[] colors = new int[]{-13712202, -36939, -739006, -45730};
                return colors[Math.floorMod(server.hashCode(), colors.length)];
        }
    }

    public static String ellipsize(TextRenderer tr, String s, int maxWidth) {
        return tr.getWidth(s) <= maxWidth ? s : tr.trimToWidth(s, Math.max(0, maxWidth - tr.getWidth("…"))) + "…";
    }
}
