package com.massivecraft.factions.event;

import com.massivecraft.factions.Faction;

public class FactionPostCreateEvent extends FactionEvent {

    public FactionPostCreateEvent(Faction faction) {
        super(faction);
    }

}
