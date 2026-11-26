package com.Zrips.CMI.Containers;

import com.Zrips.CMI.Modules.Jail.CMIJailCell;
import java.util.UUID;

/** Заглушка CMI API (только для компиляции, в jar не попадает). */
public class CMIUser {
    public UUID getUniqueId() { throw new UnsupportedOperationException(); }
    public String getName() { throw new UnsupportedOperationException(); }
    public boolean isJailed() { throw new UnsupportedOperationException(); }
    public UUID getJailedBy() { throw new UnsupportedOperationException(); }
    public String getJailedReason() { throw new UnsupportedOperationException(); }
    public Long getJailedUntil() { throw new UnsupportedOperationException(); }
    public CMIJailCell getCell() { throw new UnsupportedOperationException(); }
}
