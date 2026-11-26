package dev.freedomadminpanel.gui;

public enum ActionType {
    MSG("msg", "Написать сообщение", "Личное сообщение", -11692289, ActionType.TimeMode.NONE, false, "Сообщение", "Введите текст сообщения...", null),
    ALERT("alert", "Добавить оповещение", "Добавить оповещение", -739006, ActionType.TimeMode.NONE, false, "Причина", "За что оповещение...", null),
    MUTE("mute", "Замутить", "Мут", -29630, ActionType.TimeMode.REQUIRED, false, "Причина", "Причина мута...", "Тихий мут"),
    JAIL("jail", "Посадить в тюрьму", "Тюрьма", -6595099, ActionType.TimeMode.REQUIRED, true, "Причина", "Причина заключения...", null),
    BAN("ban", "Забанить", "Бан", -4186054, ActionType.TimeMode.OPTIONAL, false, "Причина", "Причина бана...", "Тихий бан"),
    WARN("warn", "Дать предупреждение", "Предупреждение", -739006, ActionType.TimeMode.NONE, false, "Причина", "Причина предупреждения...", null),
    KICK("kick", "Кикнуть", "Кик", -45730, ActionType.TimeMode.NONE, false, "Причина", "Причина кика...", "Тихий кик"),
    UNMUTE("unmute", "Размутить", "Снятие мута", -12723068, ActionType.TimeMode.NONE, false, "Причина", "Почему снимаете мут...", null),
    UNBAN("unban", "Разбанить", "Снятие бана", -12723068, ActionType.TimeMode.NONE, false, "Причина", "Почему снимаете бан...", null),
    UNWARN("unwarn", "Снять варн", "Снятие предупреждения", -12723068, ActionType.TimeMode.NONE, false, "Причина", "Почему снимаете предупреждение...", null);

    public final String key;
    public final String buttonText;
    public final String title;
    public final int color;
    public final ActionType.TimeMode time;
    public final boolean jail;
    public final String textLabel;
    public final String placeholder;
    public final String silentText;

    private ActionType(
        String key, String buttonText, String title, int color, ActionType.TimeMode time, boolean jail, String textLabel, String placeholder, String silentText
    ) {
        this.key = key;
        this.buttonText = buttonText;
        this.title = title;
        this.color = color;
        this.time = time;
        this.jail = jail;
        this.textLabel = textLabel;
        this.placeholder = placeholder;
        this.silentText = silentText;
    }

    public boolean isMessage() {
        return this == MSG;
    }

    public boolean isPunishment() {
        return this == MUTE || this == JAIL || this == BAN || this == WARN || this == KICK;
    }

    public boolean isRevoke() {
        return this == UNMUTE || this == UNBAN || this == UNWARN;
    }

    public static enum TimeMode {
        NONE,
        REQUIRED,
        OPTIONAL;
    }
}
