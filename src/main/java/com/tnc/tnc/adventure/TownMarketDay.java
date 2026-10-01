package com.tnc.tnc.adventure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** Real-time town-wide recommendation; monotonic stored day prevents clock rollback rerolls. */
public final class TownMarketDay {
    private TownMarketDay(){}
    static int index(long seed,long day){return new java.util.Random(seed^Long.rotateLeft(day*0x9e3779b97f4a7c15L,21)).nextInt(ShopCatalog.PRODUCE.size());}
    static CompoundTag state(MinecraftServer server,long now){
        var store=AdventureSavedData.get(server);if(!store.housing.contains("MarketDay",10))store.housing.put("MarketDay",new CompoundTag());var t=store.housing.getCompound("MarketDay");long day=Math.max(0,now/86_400_000L);
        if(!t.contains("Day",4)||day>t.getLong("Day")){t.putLong("Day",day);t.putString("Featured",ShopCatalog.PRODUCE.get(index(server.overworld().getSeed(),day)).id());store.setDirty();}return t;
    }
    public static String featured(MinecraftServer server){return state(server,System.currentTimeMillis()).getString("Featured");}
    public static int price(ShopCatalog.Goods goods,String featured){return goods.id().equals(featured)?goods.price()*3/2:goods.price();}
}
