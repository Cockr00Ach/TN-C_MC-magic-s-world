package com.tnc.tnc.adventure;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class AdventureSavedData extends SavedData {
    public long activeTicks;
    public CompoundTag housing = new CompoundTag();
    final Map<UUID, AdventureProfile> players = new LinkedHashMap<>();
    public static AdventureSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AdventureSavedData::load,AdventureSavedData::new,"tnc_adventure_v1");
    }
    public static AdventureSavedData load(CompoundTag root) {
        var data = new AdventureSavedData(); data.activeTicks = Math.max(0,root.getLong("ActiveTicks"));
        data.housing=root.getCompound("Housing").copy();
        for (Tag entry : root.getList("Players",Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) entry;
            if (!t.hasUUID("UUID")) continue;
            data.players.put(t.getUUID("UUID"),readProfile(t));
        }
        return data;
    }
    public static AdventureProfile readProfile(CompoundTag t) {
        var p = new AdventureProfile();
        p.xp = Math.max(0,Math.min(AdventureRules.xpAtLevel(100),t.getLong("XP")));
        p.coins = Math.max(0,Math.min(AdventureRules.MAX_COINS,t.getLong("Coins")));
        p.reputation = Math.max(0,t.getInt("Reputation")); p.rank = Math.max(0,Math.min(1,t.getInt("Rank")));
        p.learningBase = Math.max(4,t.getInt("LearningBase"));
        p.registered=t.getBoolean("Registered"); p.crafted=t.getBoolean("Crafted");
        p.armorCrafted=t.getBoolean("ArmorCrafted");
        p.divineReadyAt=Math.max(0,t.getLong("DivineReadyAt"));
        p.bank.load(t.getCompound("Bank"));
        p.smithReady=t.contains("SmithReady")?t.getLong("SmithReady"):-1; p.smithFree=t.getBoolean("SmithFree");
        p.smithDesign=t.getString("SmithDesign");p.nativeBounties=Math.max(0,t.getInt("NativeBounties"));p.adventureKills=Math.max(0,t.getInt("AdventureKills"));p.bossKills=Math.max(0,t.getInt("BossKills"));
        p.completedEpoch=t.contains("CompletedEpoch")?t.getLong("CompletedEpoch"):-1;
        for (Tag v:t.getList("Milestones",Tag.TAG_STRING)) p.milestones.add(v.getAsString());
        for (Tag v:t.getList("PaidOffers",Tag.TAG_STRING)) p.paidOffers.add(v.getAsString());
        for (Tag v:t.getList("Ledger",Tag.TAG_STRING)) { if(p.ledger.size()<5)p.ledger.addLast(v.getAsString()); }
        for (Tag v:t.getList("Contracts",Tag.TAG_COMPOUND)) {
            var c=(CompoundTag)v; String id=c.getString("ID"); var def=ContractCatalog.find(id);
            if(def!=null && p.contracts.size()<3) p.contracts.put(id,new AdventureProfile.ContractProgress(id,c.getLong("Epoch"),Math.max(0,Math.min(def.kills(),c.getInt("Kills")))));
        }
        return p;
    }
    public static CompoundTag writeProfile(AdventureProfile p) {
        var t=new CompoundTag(); t.putLong("XP",p.xp);t.putLong("Coins",p.coins);t.putInt("Reputation",p.reputation);
        t.putInt("Rank",p.rank);t.putInt("LearningBase",p.learningBase);t.putBoolean("Registered",p.registered);
        t.putBoolean("Crafted",p.crafted);t.putLong("SmithReady",p.smithReady);t.putBoolean("SmithFree",p.smithFree);
        t.putString("SmithDesign",p.smithDesign);t.putInt("NativeBounties",p.nativeBounties);
        t.putBoolean("ArmorCrafted",p.armorCrafted);
        t.putLong("DivineReadyAt",p.divineReadyAt);
        t.put("Bank",p.bank.save());
        t.putInt("AdventureKills",p.adventureKills);t.putInt("BossKills",p.bossKills);
        t.putLong("CompletedEpoch",p.completedEpoch);t.put("Milestones",strings(p.milestones));t.put("PaidOffers",strings(p.paidOffers));t.put("Ledger",strings(p.ledger));
        var list=new ListTag();p.contracts.values().forEach(c->{var v=new CompoundTag();v.putString("ID",c.id);v.putLong("Epoch",c.epoch);v.putInt("Kills",c.kills);list.add(v);});
        t.put("Contracts",list);return t;
    }
    private static ListTag strings(Collection<String> values) {var list=new ListTag();values.forEach(v->list.add(StringTag.valueOf(v)));return list;}
    @Override public CompoundTag save(CompoundTag root) {
        root.putLong("ActiveTicks",activeTicks);root.put("Housing",housing.copy());var list=new ListTag();
        players.forEach((id,p)->{var t=writeProfile(p);t.putUUID("UUID",id);list.add(t);});root.put("Players",list);return root;
    }
}
