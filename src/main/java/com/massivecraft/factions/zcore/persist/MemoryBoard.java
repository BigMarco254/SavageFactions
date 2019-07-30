package com.massivecraft.factions.zcore.persist;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.massivecraft.factions.*;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.struct.Relation;
import com.massivecraft.factions.util.AsciiCompass;
import com.massivecraft.factions.util.LazyLocation;
import com.massivecraft.factions.zcore.util.TagReplacer;
import com.massivecraft.factions.zcore.util.TagUtil;
import mkremins.fanciful.FancyMessage;
import org.bukkit.ChatColor;
import org.bukkit.World;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;


public abstract class MemoryBoard extends Board {

    public MemoryBoardMap flocationIds = new MemoryBoardMap();

    //----------------------------------------------//
    // Get and Set
    //----------------------------------------------//
    public String getIdAt(FLocation flocation) {
        if (!flocationIds.containsKey(flocation)) {
            return "0";
        }

        return flocationIds.get(flocation);
    }

    public Faction getFactionAt(FLocation flocation) {
        return Factions.getInstance().getFactionById(getIdAt(flocation));
    }

    public void setIdAt(String id, FLocation flocation) {
        clearOwnershipAt(flocation);

        if (id.equals("0")) {
            removeAt(flocation);
        }

        flocationIds.put(flocation, id);
    }

    public void setFactionAt(Faction faction, FLocation flocation) {
        setIdAt(faction.getId(), flocation);
    }

    public void removeAt(FLocation flocation) {
        Faction faction = getFactionAt(flocation);
        Iterator<LazyLocation> it = faction.getWarps().values().iterator();
        while (it.hasNext()) {
            if (flocation.isInChunk(it.next().getLocation())) {
                it.remove();
            }
        }
        clearOwnershipAt(flocation);
        flocationIds.remove(flocation);
    }

    public Set<FLocation> getAllClaims(String factionId) {
        Set<FLocation> locs = new HashSet<>();
        for (Entry<FLocation, String> entry : flocationIds.entrySet()) {
            if (entry.getValue().equals(factionId)) {
                locs.add(entry.getKey());
            }
        }
        return locs;
    }

    public Set<FLocation> getAllClaims(Faction faction) {
        return getAllClaims(faction.getId());
    }

    // not to be confused with claims, ownership referring to further member-specific ownership of a claim
    public void clearOwnershipAt(FLocation flocation) {
        Faction faction = getFactionAt(flocation);
        if (faction != null && faction.isNormal()) {
            faction.clearClaimOwnership(flocation);
        }
    }

    public void unclaimAll(String factionId) {
        Faction faction = Factions.getInstance().getFactionById(factionId);
        if (faction != null && faction.isNormal()) {
            faction.clearAllClaimOwnership();
            faction.clearWarps();
        }
        clean(factionId);
    }

    public void unclaimAllInWorld(String factionId, World world) {
        for (FLocation loc : getAllClaims(factionId)) {
            if (loc.getWorldName().equals(world.getName())) {
                removeAt(loc);
            }
        }
    }

    public void clean(String factionId) {
        flocationIds.removeFaction(factionId);
    }

    // Is this coord NOT completely surrounded by coords claimed by the same faction?
    // Simpler: Is there any nearby coord with a faction other than the faction here?
    public boolean isBorderLocation(FLocation flocation) {
        Faction faction = getFactionAt(flocation);
        FLocation a = flocation.getRelative(1, 0);
        FLocation b = flocation.getRelative(-1, 0);
        FLocation c = flocation.getRelative(0, 1);
        FLocation d = flocation.getRelative(0, -1);
        return faction != getFactionAt(a) || faction != getFactionAt(b) || faction != getFactionAt(c) || faction != getFactionAt(d);
    }

    // Is this coord connected to any coord claimed by the specified faction?
    public boolean isConnectedLocation(FLocation flocation, Faction faction) {
        FLocation a = flocation.getRelative(1, 0);
        FLocation b = flocation.getRelative(-1, 0);
        FLocation c = flocation.getRelative(0, 1);
        FLocation d = flocation.getRelative(0, -1);
        return faction == getFactionAt(a) || faction == getFactionAt(b) || faction == getFactionAt(c) || faction == getFactionAt(d);
    }

    /**
     * Checks if there is another faction within a given radius other than Wilderness. Used for HCF feature that
     * requires a 'buffer' between factions.
     *
     * @param flocation - center location.
     * @param faction   - faction checking for.
     * @param radius    - chunk radius to check.
     * @return true if another Faction is within the radius, otherwise false.
     */
    public boolean hasFactionWithin(FLocation flocation, Faction faction, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }

                FLocation relative = flocation.getRelative(x, z);
                Faction other = getFactionAt(relative);

                if (other.isNormal() && other != faction) {
                    return true;
                }
            }
        }
        return false;
    }

    public void clean() {
        Iterator<Entry<FLocation, String>> iter = flocationIds.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<FLocation, String> entry = iter.next();
            if (!Factions.getInstance().isValidFactionId(entry.getValue())) {
                SavageFactions.plugin.log("Board cleaner removed " + entry.getValue() + " from " + entry.getKey());
                iter.remove();
            }
        }
    }


    //----------------------------------------------//
    // Cleaner. Remove orphaned foreign keys
    //----------------------------------------------//

    public int getFactionCoordCount(String factionId) {
        return flocationIds.getOwnedLandCount(factionId);
    }

    //----------------------------------------------//
    // Coord count
    //----------------------------------------------//

    public int getFactionCoordCount(Faction faction) {
        return getFactionCoordCount(faction.getId());
    }

    public int getFactionCoordCountInWorld(Faction faction, String worldName) {
        String factionId = faction.getId();
        int ret = 0;
        for (Entry<FLocation, String> entry : flocationIds.entrySet()) {
            if (entry.getValue().equals(factionId) && entry.getKey().getWorldName().equals(worldName)) {
                ret += 1;
            }
        }
        return ret;
    }

    /**
     * The map is relative to a coord and a faction north is in the direction of decreasing x east is in the direction
     * of decreasing z
     */
    public ArrayList<FancyMessage> getMap(FPlayer fplayer, FLocation flocation, double inDegrees) {
        Faction faction = fplayer.getFaction();
        ArrayList<FancyMessage> ret = new ArrayList<>();
        Faction factionLoc = getFactionAt(flocation);
        ret.add(new FancyMessage(ChatColor.DARK_GRAY + SavageFactions.plugin.txt.titleize("(" + flocation.getCoordString() + ") " + factionLoc.getTag(fplayer))));
        int buffer = SavageFactions.plugin.getConfig().getInt("world-border.buffer", 0) - 1;


        // Get the compass
        ArrayList<String> asciiCompass = AsciiCompass.getAsciiCompass(inDegrees, ChatColor.DARK_GREEN, SavageFactions.plugin.txt.parse("<gray>"));

        int halfWidth = Conf.mapWidth / 2;
        // Use player's value for height
        int halfHeight = fplayer.getMapHeight() / 2;
        FLocation topLeft = flocation.getRelative(-halfWidth, -halfHeight);
        int width = halfWidth * 2 + 1;
        int height = halfHeight * 2 + 1;

        if (Conf.showMapFactionKey) {
            height--;
        }

        Map<String, String> fList = new HashMap<>();
        int chrIdx = 0;

        // For each row
        for (int dz = 0; dz < height; dz++) {
            // Draw and add that row
            FancyMessage row = new FancyMessage("");
            for (int dx = 6; dx < width; dx++) {
                if (dx == halfWidth && dz == halfHeight) {
                    row.then(Conf.mapYouAreHereIcon).color(ChatColor.AQUA).tooltip(TL.CLAIM_YOUAREHERE.toString());
                } else {
                    FLocation flocationHere = topLeft.getRelative(dx, dz);
                    Faction factionHere = getFactionAt(flocationHere);
                    Relation relation = fplayer.getRelationTo(factionHere);
                    if (flocationHere.isOutsideWorldBorder(buffer)) {
                        row.then(Conf.mapFlatIcon).color(ChatColor.BLACK).tooltip(TL.CLAIM_MAP_OUTSIDEBORDER.toString());
                    } else if (factionHere.isWilderness()) {
                        row.then(Conf.mapFlatIcon).color(Conf.colorWilderness);
                        // Lol someone didnt add the x and z making it claim the wrong position Can i copyright this xD
                        if (fplayer.getPlayer().hasPermission(Permission.CLAIMAT.node)) {
                            row.tooltip(TL.CLAIM_CLICK_TO_CLAIM.format(dx + topLeft.getX(), dz + topLeft.getZ()))
                                    .command(String.format("/f claimat %s %d %d", flocation.getWorldName(), dx + topLeft.getX(), dz + topLeft.getZ()));
                        }
                    } else if (factionHere.isSafeZone()) {
                        row.then(Conf.mapFlatIcon).color(Conf.colorSafezone).tooltip(oneLineToolTip(factionHere, fplayer));
                    } else if (factionHere.isWarZone()) {
                        row.then(Conf.mapFlatIcon).color(Conf.colorWar).tooltip(oneLineToolTip(factionHere, fplayer));
                    } else if (factionHere == faction || factionHere == factionLoc || relation.isAtLeast(Relation.ALLY) ||
                            (Conf.showNeutralFactionsOnMap && relation.equals(Relation.NEUTRAL)) ||
                            (Conf.showEnemyFactionsOnMap && relation.equals(Relation.ENEMY)) ||
                            (Conf.showTrucesFactionsOnMap && relation.equals(Relation.TRUCE))) {
                        if (!fList.containsKey(factionHere.getTag())) {
                            fList.put(factionHere.getTag(), Conf.mapKeyChrs.get(ThreadLocalRandom.current().nextInt(0, Conf.mapKeyChrs.size())));
                        }
                        String tag = fList.get(factionHere.getTag());

                        //row.then(String.valueOf(tag)).color(factionHere.getColorTo(faction)).tooltip(getToolTip(factionHere, fplayer));
                        //changed out with a performance friendly one line tooltip :D
                        row.then(String.valueOf(tag)).color(factionHere.getColorTo(faction)).tooltip(oneLineToolTip(factionHere, fplayer));
                    } else {
                        row.then(Conf.mapFlatIcon).color(ChatColor.GRAY);
                    }
                }
            }
            ret.add(row);
        }


        // Add the faction key
        if (Conf.showMapFactionKey) {
            FancyMessage fRow = new FancyMessage("");
            int count = 0;
            for (String key : fList.keySet()) {
                fRow.then(String.format("%s: %s ", fList.get(key), key)).color(ChatColor.GRAY);
            }
            ret.add(fRow);
        }

        return ret;
    }

    //----------------------------------------------//
    // Map generation
    //----------------------------------------------//

    private List<String> oneLineToolTip(Faction faction, FPlayer to) {
        return Arrays.asList(faction.describeTo(to));
    }

    @SuppressWarnings("unused")
    private List<String> getToolTip(Faction faction, FPlayer to) {
        List<String> ret = new ArrayList<>();
        List<String> show = SavageFactions.plugin.getConfig().getStringList("map");

        if (!faction.isNormal()) {
            String tag = faction.getTag(to);
            // send header and that's all
            String header = show.get(0);
            if (TagReplacer.HEADER.contains(header)) {
                ret.add(SavageFactions.plugin.txt.titleize(tag));
            } else {
                ret.add(SavageFactions.plugin.txt.parse(TagReplacer.FACTION.replace(header, tag)));
            }
            return ret; // we only show header for non-normal factions
        }

        for (String raw : show) {
            // Hack to get rid of the extra underscores in title normally used to center tag
            if (raw.contains("{header}")) {
                raw = raw.replace("{header}", faction.getTag(to));
            }

            String parsed = TagUtil.parsePlain(faction, to, raw); // use relations
            if (parsed == null) {
                continue; // Due to minimal f show.
            }

            if (TagUtil.hasFancy(parsed)) {
                List<FancyMessage> fancy = TagUtil.parseFancy(faction, to, parsed);
                if (fancy != null) {
                    for (FancyMessage msg : fancy) {
                        ret.add((SavageFactions.plugin.txt.parse(msg.toOldMessageFormat())));
                    }
                }
                continue;
            }

            if (!parsed.contains("{notFrozen}") && !parsed.contains("{notPermanent}")) {
                if (parsed.contains("{ig}")) {
                    // replaces all variables with no home TL
                    parsed = parsed.substring(0, parsed.indexOf("{ig}")) + TL.COMMAND_SHOW_NOHOME.toString();
                }
                if (parsed.contains("%")) {
                    parsed = parsed.replaceAll("%", ""); // Just in case it got in there before we disallowed it.
                }
                ret.add(SavageFactions.plugin.txt.parse(parsed));
            }
        }

        return ret;
    }

    public abstract void convertFrom(MemoryBoard old);

    public class MemoryBoardMap extends HashMap<FLocation, String> {
        private static final long serialVersionUID = -6689617828610585368L;

        Multimap<String, FLocation> factionToLandMap = HashMultimap.create();

        @Override
        public String put(FLocation floc, String factionId) {
            String previousValue = super.put(floc, factionId);
            if (previousValue != null) {
                factionToLandMap.remove(previousValue, floc);
            }

            factionToLandMap.put(factionId, floc);
            return previousValue;
        }

        @Override
        public String remove(Object key) {
            String result = super.remove(key);
            if (result != null) {
                FLocation floc = (FLocation) key;
                factionToLandMap.remove(result, floc);
            }

            return result;
        }

        @Override
        public void clear() {
            super.clear();
            factionToLandMap.clear();
        }

        public int getOwnedLandCount(String factionId) {
            return factionToLandMap.get(factionId).size();
        }

        public void removeFaction(String factionId) {
            Collection<FLocation> flocations = factionToLandMap.removeAll(factionId);
            for (FLocation floc : flocations) {
                super.remove(floc);
            }
        }
    }
}
