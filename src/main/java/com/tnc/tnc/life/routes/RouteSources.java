package com.tnc.tnc.life.routes;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.tnc.tnc.life.botanical.*;
/** One shared per-root balance, saved with the root, so collectors cannot multiply output. */
public final class RouteSources {
    public static int draw(ServerLevel l,BlockPos p,UUID owner,int maximum){var raw=l.getBlockEntity(p);
        if(raw instanceof RoutePlantEntity plant){if(!owner.equals(plant.owner)||!MagicSoilBlock.growing(l,p)||plant.growth<18000)return 0;var data=plant.getPersistentData();long sec=l.getGameTime()/20;if(data.getLong("DrawSecond")!=sec){data.putLong("DrawSecond",sec);data.putInt("DrawUsed",0);}int limit=plant.kind()==NewPlantKind.SHADOW||plant.kind()==NewPlantKind.PAGE?4:(int)Math.ceil(plant.kind().rate);int got=plant.draw(Math.min(maximum,Math.max(0,limit-data.getInt("DrawUsed"))));data.putInt("DrawUsed",data.getInt("DrawUsed")+got);return got;}
        if(raw instanceof com.tnc.tnc.life.ecology.VerdantVeinBlockEntity plant){return owner.equals(plant.manaOwner())&&MagicSoilBlock.growing(l,p)?plant.drawMana(maximum):0;}
        if(raw instanceof BotanicalPlantEntity plant){if(!owner.equals(plant.owner)||plant.growth<plant.species().firstTicks||!plant.reason(l).isEmpty())return 0;var t=plant.getPersistentData();long sec=l.getGameTime()/20;if(t.getLong("ManaSecond")!=sec){t.putLong("ManaSecond",sec);double generated=t.getInt("ManaBuffer")>=120?0:switch(plant.species()){
                case DAWN_DISK->l.isDay()&&l.canSeeSky(p)?1:0;
                case DANCE_BELL->{var player=l.getServer().getPlayerList().getPlayer(owner);boolean moving=false;if(player!=null&&player.serverLevel()==l){double dx=player.getX()-t.getDouble("DancePlayerX"),dz=player.getZ()-t.getDouble("DancePlayerZ");double distance=dx*dx+dz*dz;moving=t.getBoolean("DanceTracked")&&distance>.01&&distance<=64&&player.onGround()&&player.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5)<64;t.putBoolean("DanceTracked",true);t.putDouble("DancePlayerX",player.getX());t.putDouble("DancePlayerZ",player.getZ());}int active=t.getInt("DanceActive"),rest=t.getInt("DanceRest");if(rest>0){t.putInt("DanceRest",rest-1);yield 0;}if(moving){if(++active>=30){t.putInt("DanceRest",10);active=0;}t.putInt("DanceActive",active);yield .8;}yield 0;}
                case WIND_SAIL->{boolean open=l.canSeeSky(p);int wind=(int)Math.floorMod(l.getGameTime()/600+l.getSeed(),4);net.minecraft.core.Direction direction=net.minecraft.core.Direction.from2DDataValue(wind);var neighbour=p.relative(direction);var state=l.getBlockState(neighbour);boolean shaded=state.is(BotanicalContent.BLOCKS.get("wind_sail"))||!state.getCollisionShape(l,neighbour).isEmpty();yield open&&!shaded?(l.isRaining()?3:1):0;}
                case ECHO_BEAN->{int seconds=t.getInt("BeanSeconds");if(seconds>0){t.putInt("BeanSeconds",seconds-1);yield 2;}yield 0;}
                case HONEY_CLUSTER->{int seconds=t.getInt("HoneySeconds");if(seconds>0){t.putInt("HoneySeconds",seconds-1);yield .6;}yield 0;}
                default->0;
            };double remainder=t.getDouble("ManaFraction")+generated;int whole=(int)remainder;t.putDouble("ManaFraction",remainder-whole);t.putInt("ManaBuffer",Math.min(120,t.getInt("ManaBuffer")+whole));t.putInt("ManaDraw",0);plant.setChanged();}
            int output=switch(plant.species()){case WIND_SAIL->3;case DAWN_DISK,DANCE_BELL,HONEY_CLUSTER->1;case ECHO_BEAN->2;default->0;};int got=Math.min(maximum,Math.min(t.getInt("ManaBuffer"),Math.max(0,output-t.getInt("ManaDraw"))));t.putInt("ManaBuffer",t.getInt("ManaBuffer")-got);t.putInt("ManaDraw",t.getInt("ManaDraw")+got);plant.setChanged();return got;
        }
        if(raw instanceof com.tnc.tnc.life.wonders.SkyVineRootEntity vine)return vine.drawMana(owner,maximum);
        return 0;
    }
    public static boolean feedBotanical(BotanicalPlantEntity plant,net.minecraft.server.level.ServerPlayer player,net.minecraft.world.item.ItemStack held){var data=plant.getPersistentData();if(!Objects.equals(plant.owner,player.getUUID()))return false;
        if(plant.species()==BotanicalSpecies.ECHO_BEAN&&held.is(BotanicalContent.PRODUCTS.get("echo_bean_pod"))&&Integer.bitCount(data.getInt("DistinctNotes"))>=3&&data.getInt("BeanSeconds")==0){if(!player.isCreative())held.shrink(1);data.putInt("DistinctNotes",0);data.putInt("BeanSeconds",6);plant.setChanged();return true;}
        if(plant.species()==BotanicalSpecies.HONEY_CLUSTER&&plant.pollinations>=3&&(held.is(net.minecraft.world.item.Items.SUGAR)||held.is(BotanicalContent.PRODUCTS.get("honey_powder")))&&data.getInt("HoneySeconds")==0){if(!player.isCreative())held.shrink(1);data.putInt("HoneySeconds",30);plant.pollinations=0;plant.setChanged();return true;}return false;}
}
