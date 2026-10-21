package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.RouteNodeEntity;
import net.minecraft.nbt.CompoundTag;
/** Typed credits are always a subset of the node's real mana balance. */
public final class ArcanaEnergy {
    public static String source(net.minecraft.world.level.block.entity.BlockEntity be){
        String id=be==null?"":net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock()).getPath();
        if(id.matches(".*(thermal|pepper|steam).*"))return "fire";if(id.matches(".*(dew|lotus|rain|frost).*"))return "water";
        if(id.matches(".*(wind|dance|sail).*"))return "wind";if(id.contains("storm"))return "lightning";
        if(id.contains("shadow"))return "dark";if(id.matches(".*(dawn|prism|star).*"))return "light";return "earth";
    }
    public static String animal(com.tnc.tnc.life.pasture.PastureAnimal a){if(a.getPersistentData().getFloat("ArcanaScale")>=3)return "heavy";String id=a.speciesId();if(id.matches(".*(forge|ember).*"))return "fire";if(id.matches(".*(whale|otter|mist).*"))return "water";if(id.matches(".*(ray|moth).*"))return "wind";if(id.contains("wire"))return "lightning";if(id.contains("dusk"))return "dark";return "earth";}
    public static final String[] TYPES={"solar","lunar","astral","fire","water","earth","wind","lightning","dark","light","heavy"};
    private static CompoundTag tag(RouteNodeEntity n){var t=n.getPersistentData();if(!t.contains("ElementMana",10))t.put("ElementMana",new CompoundTag());return t.getCompound("ElementMana");}
    public static void credit(RouteNodeEntity n,String type,int amount){if(amount<=0)return;var t=tag(n);int total=0;for(String k:TYPES)total+=Math.max(0,t.getInt(k));int credit=Math.max(0,Math.min(amount,n.mana-total));t.putInt(type,Math.max(0,t.getInt(type))+credit);n.setChanged();}
    public static void debit(RouteNodeEntity n,int amount){var t=tag(n);for(String k:TYPES){int used=Math.min(amount,Math.max(0,t.getInt(k)));t.putInt(k,Math.max(0,t.getInt(k)-used));amount-=used;if(amount<=0)return;}}
    public static void transfer(RouteNodeEntity from,RouteNodeEntity to,int amount){var t=tag(from);for(String k:TYPES){int part=Math.min(amount,Math.max(0,t.getInt(k)));credit(to,k,part);amount-=part;if(amount<=0)return;}}
    public static int amount(RouteNodeEntity n,String type){return Math.min(n.mana,Math.max(0,tag(n).getInt(type)));}
    public static boolean spend(RouteNodeEntity n,String type,int amount){if(amount(n,type)<amount)return false;var t=tag(n);t.putInt(type,t.getInt(type)-amount);n.mana-=amount;n.setChanged();return true;}
}
