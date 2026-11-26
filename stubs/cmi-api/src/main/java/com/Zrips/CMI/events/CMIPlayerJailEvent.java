package com.Zrips.CMI.events;

import com.Zrips.CMI.Containers.CMIUser;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Заглушка CMI API (только для компиляции, в jar не попадает). */
public class CMIPlayerJailEvent extends Event {
    public CMIUser getUser() { throw new UnsupportedOperationException(); }

    @Override
    public HandlerList getHandlers() { throw new UnsupportedOperationException(); }

    public static HandlerList getHandlerList() { throw new UnsupportedOperationException(); }
}
