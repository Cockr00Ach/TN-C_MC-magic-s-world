package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.effect.*;
import java.util.*;
/** Free two-to-nine ingredient cooking. Tags are the sole ingredient whitelist. */
public final class ArcanaMeals {
    public enum Taste {FIRE,WATER,WIND,EARTH,NEUTRAL}
    public static Taste taste(ItemStack s){if(s.isEmpty())return null;for(var t:Taste.values())if(s.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,net.minecraft.resources.ResourceLocation.parse("tnc:meal/"+t.name().toLowerCase(Locale.ROOT)))))return t;return null;}
    public static int color(Taste t){return t==null?0x808080:switch(t){case FIRE->0xff7151;case WATER->0x68cfff;case WIND->0x7ff2c6;case EARTH->0xd7ad65;case NEUTRAL->0xffe7a5;};}
    public static boolean allowed(ItemStack s){return taste(s)!=null;}
    public static boolean clash(Taste a,Taste b){return a!=null&&b!=null&&(a==Taste.FIRE&&b==Taste.WATER||a==Taste.WATER&&b==Taste.FIRE||a==Taste.WIND&&b==Taste.EARTH||a==Taste.EARTH&&b==Taste.WIND);}
    public static int generation(Taste a,Taste b){if(a==null||b==null)return -1;Taste[][] pairs={{Taste.WIND,Taste.FIRE},{Taste.FIRE,Taste.EARTH},{Taste.EARTH,Taste.NEUTRAL},{Taste.NEUTRAL,Taste.WATER},{Taste.WATER,Taste.WIND}};for(int i=0;i<5;i++)if(a==pairs[i][0]&&b==pairs[i][1]||a==pairs[i][1]&&b==pairs[i][0])return i;return -1;}
    public record Meal(int ingredients,int conflicts,int bridges,int edges,int[] levels,boolean sun){}
    public static Meal analyze(Taste[] grid){int count=0,bad=0,bridges=0,edges=0;int[] levels=new int[5];var types=EnumSet.noneOf(Taste.class);
        for(int i=0;i<9;i++){var a=grid[i];if(a==null)continue;count++;types.add(a);for(int j:new int[]{i%3<2?i+1:-1,i<6?i+3:-1})if(j>=0){if(clash(a,grid[j]))bad++;int g=generation(a,grid[j]);if(g>=0){levels[g]++;edges++;}}
            if(a==Taste.NEUTRAL){if(i%3==1&&clash(grid[i-1],grid[i+1]))bridges++;if(i/3==1&&clash(grid[i-3],grid[i+3]))bridges++;}
        }return new Meal(count,bad,bridges,edges,levels,count==9&&types.size()==5&&bad==0&&edges+bridges>=6);
    }
    public static Meal analyze(RouteNodeEntity n){var grid=new Taste[9];for(int i=0;i<9;i++)grid[i]=taste(n.inventory.getItem(i));return analyze(grid);}
    public static boolean conflict(RouteNodeEntity n){return analyze(n).conflicts()>0;}
    public static boolean active(RouteNodeEntity n){return analyze(n).sun();}
    public static String state(RouteNodeEntity n){var m=analyze(n);return m.conflicts()>0?"相克 "+m.conflicts()+" · 强食将反胃禁魔":m.sun()?"九曜烈阳 · 空手享用3分钟无限火力":m.ingredients()<2?"放2—9味食材，F8看五行生克":"相生 "+m.edges()+" · 甘味桥 "+m.bridges()+" · 空手享用";}
    public static void interact(ServerPlayer p,RouteNodeEntity n,InteractionHand hand,BlockHitResult hit){var held=p.getItemInHand(hand);var at=hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(n.getBlockPos()));int x=Math.max(0,Math.min(2,(int)(at.x*3))),z=Math.max(0,Math.min(2,(int)(at.z*3))),slot=z*3+x;
        if(p.isShiftKeyDown()&&held.isEmpty()){var s=n.inventory.removeItemNoUpdate(slot);if(!s.isEmpty()&&!p.getInventory().add(s))p.drop(s,false);}
        else if(allowed(held)&&n.inventory.getItem(slot).isEmpty())n.inventory.setItem(slot,held.split(1));
        else if(held.isEmpty()){var m=analyze(n);if(m.ingredients()>=2){
            for(int i=0;i<9;i++){var old=n.inventory.getItem(i);if(old.isEmpty())continue;var rem=old.getCraftingRemainingItem();n.inventory.setItem(i,ItemStack.EMPTY);if(!rem.isEmpty()&&!p.getInventory().add(rem))p.drop(rem,false);}
            if(m.conflicts()>0){p.hurt(p.damageSources().magic(),4);p.push(0,.65,0);p.hurtMarked=true;p.addEffect(new MobEffectInstance(MobEffects.CONFUSION,300));p.addEffect(new MobEffectInstance(ArcanaMealEffects.NO_MAGIC,300));}
            else{p.getFoodData().eat(6+m.ingredients(),.6f);for(int i=0;i<5;i++)if(m.levels()[i]>0)p.addEffect(new MobEffectInstance(ArcanaMealEffects.MEALS[i],6000,Math.min(1,m.levels()[i]-1)));
                if(m.levels()[3]>0)ArcanaPhysiology.detox(p,30);if(m.bridges()>0)p.addEffect(new MobEffectInstance(ArcanaMealEffects.HARMONY,12000));
                if(m.sun()){ArcanaPhysiology.data(p).putLong("MealSunUntil",ArcanaPhysiology.now(p)+3600);com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();}
            }
        }}
        n.setChanged();p.serverLevel().sendBlockUpdated(n.getBlockPos(),n.getBlockState(),n.getBlockState(),3);p.displayClientMessage(net.minecraft.network.chat.Component.literal(state(n)),true);
    }
    public static void radiate(ServerPlayer p){for(var pos:RouteNetwork.positions(p.serverLevel())){if(pos.distToCenterSqr(p.position())>=25||!p.serverLevel().hasChunkAt(pos))continue;var be=p.serverLevel().getBlockEntity(pos);if(be instanceof RouteNodeEntity n&&p.getUUID().equals(n.owner)){int got=n.receiveTyped(8,"solar");ArcanaEnergy.credit(n,"solar",got);n.getPersistentData().putLong("MealBoostUntil",p.level().getGameTime()+25);}else if(be instanceof com.tnc.tnc.production.MagicForgeBlockEntity f&&p.getUUID().equals(f.owner())&&f.formedForTransfer()){f.addCharge(8);f.getPersistentData().putLong("MealBoostUntil",p.level().getGameTime()+25);}}}
    private ArcanaMeals(){}
}
