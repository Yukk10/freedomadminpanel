package dev.freedomadminpanel.gui;

import dev.freedomadminpanel.net.PlayerRecord;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public final class Recidivism {
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(mo|s|m|h|d|w|y)", Pattern.CASE_INSENSITIVE);
    private static final long MIN = 60000L;
    private static final long HOUR = 3600000L;
    private static final long DAY = 86400000L;

    private Recidivism() {
    }

    public static int same(PlayerRecord r, ActionType type) {
        return switch (type) {
            case MUTE -> r.mute();
            case JAIL -> r.jail();
            case BAN -> r.ban();
            case WARN -> r.warn();
            case KICK -> r.kick();
            default -> 0;
        };
    }

    public static double level(PlayerRecord r, ActionType type) {
        return same(r, type);
    }

    @Nullable
    public static PlayerRecord.Formula formula(PlayerRecord r, ActionType type) {
        return switch (type) {
            case MUTE -> r.muteFormula();
            case JAIL -> r.jailFormula();
            case BAN -> r.banFormula();
            default -> null;
        };
    }

    @Nullable
    public static Recidivism.Suggestion suggest(PlayerRecord r, ActionType type, long baseMs) {
        PlayerRecord.Formula f = formula(r, type);
        if (f == null) {
            return null;
        } else {
            long base = baseMs > 0L ? baseMs : parse(f.base());
            long step = Math.max(0L, parse(f.step()));
            long max = parse(f.max());
            if (base <= 0L) {
                return null;
            } else {
                double lvl = level(r, type);
                long addition = Math.round(step * lvl * (lvl + 1.0) / 2.0);
                long total = round(base + addition);
                boolean capped = false;
                if (max > 0L && total > max) {
                    total = Math.max(base, max);
                    capped = true;
                }

                return new Recidivism.Suggestion(base, total - base, total, lvl, capped);
            }
        }
    }

    public static long parse(String s) {
        if (s == null) {
            return -1L;
        } else {
            String t = s.trim();
            if (t.isEmpty()) {
                return -1L;
            } else {
                Matcher m = PART.matcher(t);
                long total = 0L;

                int end;
                for (end = 0; m.find(); end = m.end()) {
                    if (m.start() != end && !t.substring(end, m.start()).isBlank()) {
                        return -1L;
                    }

                    String unit = m.group(2).toLowerCase(Locale.ROOT);
                    long unitMs = switch (unit) {
                        case "y" -> 365L * 86400000L;
                        case "mo" -> 30L * 86400000L;
                        case "w" -> 604800000L;
                        case "d" -> 86400000L;
                        case "h" -> 3600000L;
                        case "m" -> 60000L;
                        default -> 1000L;
                    };

                    try {
                        total = Math.addExact(total, Math.multiplyExact(Long.parseLong(m.group(1)), unitMs));
                    } catch (ArithmeticException | NumberFormatException e) {
                        return -1L;
                    }
                }

                return end == t.length() && total > 0L ? total : -1L;
            }
        }
    }

    private static long round(long ms) {
        if (ms < 21600000L) {
            return Math.max(300000L, Math.round(ms / 300000.0) * 5L * 60000L);
        } else {
            return ms < 259200000L ? Math.round(ms / 3600000.0) * 3600000L : Math.round(ms / 8.64E7) * 86400000L;
        }
    }

    public static String toCommand(long ms) {
        if (ms % 86400000L == 0L) {
            return ms / 86400000L + "d";
        } else {
            return ms % 3600000L == 0L ? ms / 3600000L + "h" : Math.max(1L, Math.round(ms / 60000.0)) + "m";
        }
    }

    public static String human(long ms) {
        long minutes = Math.max(0L, Math.round(ms / 60000.0));
        long d = minutes / 1440L;
        long h = minutes % 1440L / 60L;
        long m = minutes % 60L;
        StringBuilder sb = new StringBuilder();
        if (d > 0L) {
            sb.append(d).append("д ");
        }

        if (h > 0L) {
            sb.append(h).append("ч ");
        }

        if (m > 0L && d == 0L) {
            sb.append(m).append("м");
        }

        String out = sb.toString().trim();
        return out.isEmpty() ? "0м" : out;
    }

    public static String plural(int n, String one, String few, String many) {
        int n100 = n % 100;
        int n10 = n % 10;
        if (n100 >= 11 && n100 <= 14) {
            return n + " " + many;
        } else if (n10 == 1) {
            return n + " " + one;
        } else {
            return n10 >= 2 && n10 <= 4 ? n + " " + few : n + " " + many;
        }
    }

    public static String formatLevel(double level) {
        return level == Math.floor(level) ? String.valueOf((long)level) : String.format(Locale.ROOT, "%.1f", level);
    }

    public record Suggestion(long base, long addition, long total, double level, boolean capped) {
    }
}
