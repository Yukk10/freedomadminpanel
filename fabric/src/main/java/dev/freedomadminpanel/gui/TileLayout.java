package dev.freedomadminpanel.gui;

import dev.freedomadminpanel.config.Perms;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.Nullable;

public final class TileLayout {
    public static final int GAP = 4;
    public static final int TITLE_H = 13;
    public static final int SECTION_GAP = 6;

    private TileLayout() {
    }

    public static int height(List<TileLayout.Section> sections, int tileH) {
        int h = 0;
        int count = 0;

        for (TileLayout.Section s : sections) {
            int sh = s.height(tileH);
            if (sh != 0) {
                h += sh;
                count++;
            }
        }

        return h + Math.max(0, count - 1) * 6;
    }

    public static void place(List<TileLayout.Section> sections, int x, int y, int w, int tileH, Consumer<ActionTile> add) {
        for (TileLayout.Section s : sections) {
            List<TileLayout.Item> items = s.visible();
            if (!items.isEmpty()) {
                int th = s.tileH > 0 ? s.tileH : tileH;
                if (s.title != null) {
                    s.titleY = y;
                    y += 13;
                }

                int rows = s.rows();
                int base = items.size() / rows;
                int extra = items.size() % rows;
                int index = 0;

                for (int r = 0; r < rows; r++) {
                    int count = base + (r < extra ? 1 : 0);
                    placeRow(items.subList(index, index + count), x, y, w, th, add);
                    index += count;
                    y += th + (r < rows - 1 ? 4 : 0);
                }

                y += 6;
            }
        }
    }

    private static void placeRow(List<TileLayout.Item> row, int x, int y, int w, int h, Consumer<ActionTile> add) {
        float sum = 0.0F;

        for (TileLayout.Item i : row) {
            sum += i.weight();
        }

        int free = w - (row.size() - 1) * 4;
        int tx = x;

        for (int i = 0; i < row.size(); i++) {
            int tw = i == row.size() - 1 ? x + w - tx : Math.round(free * row.get(i).weight() / sum);
            ActionTile tile = row.get(i).tile();
            tile.setPosition(tx, y);
            tile.setDimensions(tw, h);
            add.accept(tile);
            tx += tw + 4;
        }
    }

    public static void drawTitles(DrawContext ctx, TextRenderer tr, List<TileLayout.Section> sections, int x, int right) {
        for (TileLayout.Section s : sections) {
            if (s.title != null && !s.visible().isEmpty()) {
                Theme.sectionTitle(ctx, tr, s.title, x, s.titleY, right, s.color);
            }
        }
    }

    public record Item(String perm, ActionTile tile, float weight) {
        public Item(String perm, ActionTile tile) {
            this(perm, tile, 1.0F);
        }
    }

    public static final class Section {
        @Nullable
        final String title;
        final int color;
        final int maxCols;
        final int tileH;
        final List<TileLayout.Item> items = new ArrayList<>();
        int titleY;

        public Section(@Nullable String title, int color, int maxCols, int tileH) {
            this.title = title;
            this.color = color;
            this.maxCols = maxCols;
            this.tileH = tileH;
        }

        public TileLayout.Section add(String perm, ActionTile tile) {
            this.items.add(new TileLayout.Item(perm, tile));
            return this;
        }

        public TileLayout.Section add(String perm, ActionTile tile, float weight) {
            this.items.add(new TileLayout.Item(perm, tile, weight));
            return this;
        }

        List<TileLayout.Item> visible() {
            return this.items.stream().filter(i -> Perms.allowed(i.perm())).toList();
        }

        int rows() {
            int n = this.visible().size();
            return (n + this.maxCols - 1) / this.maxCols;
        }

        int height(int defaultTileH) {
            int rows = this.rows();
            if (rows == 0) {
                return 0;
            } else {
                int th = this.tileH > 0 ? this.tileH : defaultTileH;
                return (this.title != null ? 13 : 0) + rows * th + (rows - 1) * 4;
            }
        }
    }
}
