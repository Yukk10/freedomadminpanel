package dev.freedomadminpanel.config;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

public enum Role {
    MODER("Модератор", "Контроль чата, игроков и порядка", -11692289, Items.DIAMOND_SWORD),
    HELPER("Хелпер", "Помощь игрокам и поддержание порядка", -12723068, Items.TOTEM_OF_UNDYING),
    EVENTOR("Ивентор", "Проведение и контроль ивентов", -29630, Items.FIREWORK_ROCKET);

    public final String title;
    public final String description;
    public final int color;
    public final Item icon;

    private Role(String title, String description, int color, Item icon) {
        this.title = title;
        this.description = description;
        this.color = color;
        this.icon = icon;
    }
}
