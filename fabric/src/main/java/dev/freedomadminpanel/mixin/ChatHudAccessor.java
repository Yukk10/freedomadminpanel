package dev.freedomadminpanel.mixin;

import java.util.List;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к строкам чата для выделения текста мышью (см. ChatSelection). */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    /** Видимые (уже перенесённые) строки, индекс 0 — самая новая. */
    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> freedomadminpanel$visibleMessages();

    /** Исходные сообщения целиком, индекс 0 — самое новое. */
    @Accessor("messages")
    List<ChatHudLine> freedomadminpanel$messages();

    @Accessor("scrolledLines")
    int freedomadminpanel$scrolledLines();
}
