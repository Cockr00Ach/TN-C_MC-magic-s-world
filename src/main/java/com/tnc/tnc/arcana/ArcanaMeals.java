package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.BlockHitResult;
public final class ArcanaMeals {
    public enum Taste {FIRE,WATER,WIND,EARTH,NEUTRAL}
    public static Taste taste(ItemStack s){String id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath();
        if(id.contains("pudding")||id.contains("honey")||id.contains("cake"))return Taste.NEUTRAL;
        if(id.contains("pepper")||id.contains("spicy")||id.contains("blaze")||id.contains("warm"))return Taste.FIRE;
        if(id.contains("fish")||id.contains("cod")||id.contains("salmon")||id.contains("frost")||id.contains("kelp")||id.contains("tide"))return Taste.WATER;
        if(id.contains("feather")||id.contains("fowl")||id.contains("chicken")||id.contains("wind")||id.contains("mutton"))return Taste.WIND;
        if(id.contains("potato")||id.contains("carrot")||id.contains("beef")||id.contains("mushroom")||id.contains("stone"))return Taste.EARTH;
        return Taste.NEUTRAL;
    }
    public static boolean clash(Taste a,Taste b){return a==Taste.FIRE&&b==Taste.WATER||a==Taste.WATER&&b==Taste.FIRE;}
    public static boolean conflict(RouteNodeEntity n){for(int i=0;i<9;i++){if(n.inventory.getItem(i).isEmpty())continue;for(int j:new int[]{i%3<2?i+1:-1,i<6?i+3:-1})if(j>=0&&!n.inventory.getItem(j).isEmpty()&&clash(taste(n.inventory.getItem(i)),taste(n.inventory.getItem(j))))return true;}return false;}
    public static boolean active(RouteNodeEntity n){if(conflict(n))return false;var seen=java.util.EnumSet.noneOf(Taste.class);for(int i=0;i<9;i++){if(n.inventory.getItem(i).isEmpty())return false;seen.add(taste(n.inventory.getItem(i)));}return seen.size()==5;}
    public static String state(RouteNodeEntity n){return conflict(n)?"相克冲突：火与水之间隔一份中和料理":active(n)?"五味成阵 · 空手右键享用，15分钟微型太阳":"摆满九格；集齐火、水、风、土与中和";}
    public static void interact(ServerPlayer p,RouteNodeEntity n,InteractionHand hand,BlockHitResult hit){var held=p.getItemInHand(hand);var at=hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(n.getBlockPos()));int x=Math.max(0,Math.min(2,(int)(at.x*3))),z=Math.max(0,Math.min(2,(int)(at.z*3))),slot=z*3+x;
        if(p.isShiftKeyDown()&&held.isEmpty()){var s=n.inventory.removeItemNoUpdate(slot);if(!s.isEmpty()&&!p.getInventory().add(s))p.drop(s,false);}
        else if(held.isEdible()&&n.inventory.getItem(slot).isEmpty())n.inventory.setItem(slot,held.split(1));
        else if(held.isEmpty()){
            boolean full=true;for(int i=0;i<9;i++)full&=!n.inventory.getItem(i).isEmpty();if(full){boolean bad=conflict(n),good=active(n);
                for(int i=0;i<9;i++){var old=n.inventory.getItem(i);var remainder=old.getCraftingRemainingItem();n.inventory.setItem(i,ItemStack.EMPTY);if(!remainder.isEmpty()&&!p.getInventory().add(remainder))p.drop(remainder,false);}
                if(bad){p.hurt(p.damageSources().magic(),4);p.push(0,.65,0);p.hurtMarked=true;p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION,160,0));}
                else{p.getFoodData().eat(20,1);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION,good?18000:1200,0));if(good){ArcanaPhysiology.data(p).putLong("MealSunUntil",ArcanaPhysiology.now(p)+18000);com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();}}
            }
        }
        n.setChanged();p.serverLevel().sendBlockUpdated(n.getBlockPos(),n.getBlockState(),n.getBlockState(),3);p.displayClientMessage(net.minecraft.network.chat.Component.literal(state(n)),true);
    }
    private ArcanaMeals(){}
}
