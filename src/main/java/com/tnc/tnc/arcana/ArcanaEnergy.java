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
    public static int color(RouteNodeEntity n){String best="";int max=0;for(String type:TYPES){int amount=amount(n,type);if(amount>max){max=amount;best=type;}}return switch(best){case "fire"->0xff794e;case "water"->0x6adfff;case "wind"->0x7df5b8;case "earth"->0xe9bb65;case "lightning"->0xc69cff;case "light","solar"->0xffe4a0;case "dark"->0x8565c9;case "lunar"->0xc5eaff;case "astral"->0xe6b5ff;default->0x63e9ef;};}
    private static CompoundTag tag(RouteNodeEntity n){var t=n.getPersistentData();if(!t.contains("ElementMana",10))t.put("ElementMana",new CompoundTag());return t.getCompound("ElementMana");}
    public static void credit(RouteNodeEntity n,String type,int amount){if(amount<=0)return;var t=tag(n);long total=0;for(String k:TYPES)total+=Math.max(0,t.getInt(k));int credit=(int)Math.max(0,Math.min(amount,(long)n.mana-total));t.putInt(type,Math.max(0,t.getInt(type))+credit);n.setChanged();}
    public static void debit(RouteNodeEntity n,int amount){var t=tag(n);for(String k:TYPES){int used=Math.min(amount,Math.max(0,t.getInt(k)));t.putInt(k,Math.max(0,t.getInt(k)-used));amount-=used;if(amount<=0)return;}}
    public static void transfer(RouteNodeEntity from,RouteNodeEntity to,int amount){var t=tag(from);for(String k:TYPES){int part=Math.min(amount,Math.max(0,t.getInt(k)));credit(to,k,part);amount-=part;if(amount<=0)return;}}
    public static int amount(RouteNodeEntity n,String type){return Math.min(n.mana,Math.max(0,tag(n).getInt(type)));}
    /** Selective endpoints take only real matching typed credits. The source is debited here exactly once. */
    public static int move(RouteNodeEntity from,RouteNodeEntity to,int offered){
        offered=Math.min(offered,Math.max(0,from.mana-ArcanaBeacon.reserved(from)));int room=Math.max(0,Math.min(offered,to.kind().capacity-to.mana));String wanted=to.kind()==com.tnc.tnc.life.routes.RouteKind.NURTURE?ArcanaDevices.wandElement(to):"";
        boolean selective=to.kind()==com.tnc.tnc.life.routes.RouteKind.NURTURE||to.kind()==com.tnc.tnc.life.routes.RouteKind.PHASE_SHIFTER||from.kind()==com.tnc.tnc.life.routes.RouteKind.PHASE_SHIFTER;
        if(!selective){int got=to.receive(room);transfer(from,to,got);from.extract(got);return got;}
        int total=0;for(String type:TYPES){boolean celestial=type.equals("solar")||type.equals("lunar");if(to.kind()==com.tnc.tnc.life.routes.RouteKind.NURTURE&&!type.equals(wanted)||to.kind()==com.tnc.tnc.life.routes.RouteKind.PHASE_SHIFTER&&!celestial||from.kind()==com.tnc.tnc.life.routes.RouteKind.PHASE_SHIFTER&&(celestial||type.equals("astral")))continue;
            int part=Math.min(room-total,amount(from,type));if(part<=0)continue;int got=to.receiveTyped(part,type);if(got>0){spend(from,type,got);credit(to,type,got);total+=got;}if(total>=room)break;
        }
        if(total==0&&to.kind()==com.tnc.tnc.life.routes.RouteKind.NURTURE)to.getPersistentData().putLong("RejectedUntil",to.getLevel().getGameTime()+25);return total;
    }
    public static void retype(RouteNodeEntity n,String from,String to,int amount){int actual=Math.min(Math.max(0,amount),amount(n,from));if(actual==0||from.equals(to))return;var t=tag(n);t.putInt(from,t.getInt(from)-actual);t.putInt(to,t.getInt(to)+actual);n.setChanged();}
    public static boolean spend(RouteNodeEntity n,String type,int amount){if(amount<0||n.mana-ArcanaBeacon.reserved(n)<amount||amount(n,type)<amount)return false;var t=tag(n);t.putInt(type,t.getInt(type)-amount);n.mana-=amount;n.setChanged();return true;}
}
