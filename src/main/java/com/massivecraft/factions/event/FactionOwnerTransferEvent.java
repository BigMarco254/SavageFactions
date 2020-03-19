package com.massivecraft.factions.event;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import org.bukkit.event.Cancellable;

public class FactionOwnerTransferEvent extends FactionEvent implements Cancellable {

    final FPlayer oldOwner;
    private FPlayer newOwner;
    private boolean isCanceled = false;

    public FactionOwnerTransferEvent(Faction faction, FPlayer oldOwner, FPlayer newOwner) {
        super(faction);
        this.oldOwner = oldOwner;
        this.newOwner = newOwner;
    }

    public void setNewOwner(FPlayer fPlayer) {
        this.newOwner = fPlayer;
    }

    public FPlayer getNewOwner() {
        return this.newOwner;
    }

    public FPlayer getOldOwner() {
        return this.oldOwner;
    }

    @Override
    public boolean isCancelled() {
        return this.isCanceled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.isCanceled = cancel;
    }
}
