package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.BlockHitResult;
public final class ArcanaMeals {
    public enum Taste {FIRE,WATER,WIND,EARTH,NEUTRAL}
    public static Taste taste(ItemStack s){if(s.isEmpty())return null;var key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());if(key==null||!key.getNamespace().equals("tnc"))return null;return switch(key.getPath()){
        case "hearth_pepper_fruit","salt_ink","foldleaf_film"->Taste.FIRE;
        case "mirror_dew","frost_dew","rainletter_berry","dewgrass_thread","steam_lotus_heart"->Taste.WATER;
        case "echo_bean_pod","sail_fiber","dance_petal","rainproof_pod","mist_cotton_fiber","page_fern_fiber","storm_crown_tip"->Taste.WIND;
        case "stone_pattern_leaf","vine_sinew","paper_bark","sleep_leaf","dream_humus","cycle_sandgrain","ore_dream_flake","mana_root_core"->Taste.EARTH;
        case "dawn_honey","farlight_fruit","shadow_silk","honey_powder","star_dew_fruit","star_rest_dew","homeward_flower_petal","prism_crown_petal","shadow_hour_petal"->Taste.NEUTRAL;
        default->null;};}
    public static int color(Taste t){return t==null?0x808080:switch(t){case FIRE->0xff7151;case WATER->0x68cfff;case WIND->0x7ff2c6;case EARTH->0xd7ad65;case NEUTRAL->0xffe7a5;};}
    public static boolean allowed(ItemStack s){return taste(s)!=null;}
    public static boolean clash(Taste a,Taste b){return a==Taste.FIRE&&b==Taste.WATER||a==Taste.WATER&&b==Taste.FIRE;}
    public static boolean conflict(RouteNodeEntity n){for(int i=0;i<9;i++){if(n.inventory.getItem(i).isEmpty())continue;for(int j:new int[]{i%3<2?i+1:-1,i<6?i+3:-1})if(j>=0&&!n.inventory.getItem(j).isEmpty()&&clash(taste(n.inventory.getItem(i)),taste(n.inventory.getItem(j))))return true;}return false;}
    public static boolean active(RouteNodeEntity n){if(conflict(n))return false;var seen=java.util.EnumSet.noneOf(Taste.class);for(int i=0;i<9;i++){if(!allowed(n.inventory.getItem(i)))return false;seen.add(taste(n.inventory.getItem(i)));}return seen.size()==5;}
    public static String state(RouteNodeEntity n){return conflict(n)?"红蓝直接相邻会冲突，金色植萃可中和":active(n)?"植萃成阵 · 空手吸收，15分钟微型太阳":"仅放自制魔植产物；看各格微粒颜色搭配";}
    public static void interact(ServerPlayer p,RouteNodeEntity n,InteractionHand hand,BlockHitResult hit){var held=p.getItemInHand(hand);var at=hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(n.getBlockPos()));int x=Math.max(0,Math.min(2,(int)(at.x*3))),z=Math.max(0,Math.min(2,(int)(at.z*3))),slot=z*3+x;
        if(p.isShiftKeyDown()&&held.isEmpty()){var s=n.inventory.removeItemNoUpdate(slot);if(!s.isEmpty()&&!p.getInventory().add(s))p.drop(s,false);}
        else if(allowed(held)&&n.inventory.getItem(slot).isEmpty())n.inventory.setItem(slot,held.split(1));
        else if(held.isEmpty()){
            boolean full=true;for(int i=0;i<9;i++)full&=allowed(n.inventory.getItem(i));if(full){boolean bad=conflict(n),good=active(n);
                for(int i=0;i<9;i++){var old=n.inventory.getItem(i);var remainder=old.getCraftingRemainingItem();n.inventory.setItem(i,ItemStack.EMPTY);if(!remainder.isEmpty()&&!p.getInventory().add(remainder))p.drop(remainder,false);}
                if(bad){p.hurt(p.damageSources().magic(),4);p.push(0,.65,0);p.hurtMarked=true;p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION,160,0));}
                else{p.getFoodData().eat(20,1);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION,good?18000:1200,0));if(good){ArcanaPhysiology.data(p).putLong("MealSunUntil",ArcanaPhysiology.now(p)+18000);com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();}}
            }
        }
        n.setChanged();p.serverLevel().sendBlockUpdated(n.getBlockPos(),n.getBlockState(),n.getBlockState(),3);p.displayClientMessage(net.minecraft.network.chat.Component.literal(state(n)),true);
    }
    private ArcanaMeals(){}
}
