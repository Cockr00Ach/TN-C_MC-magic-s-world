package com.tnc.tnc.life.pasture;

import com.tnc.tnc.home.TownProtection;
import com.tnc.tnc.life.botanical.BotanicalPlantEntity;
import com.tnc.tnc.production.energy.EnergyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import java.util.*;

/** Original meat, food and harvested resources with actual consumers. */
public final class PastureProductItem extends Item {
    private final String id;
    public PastureProductItem(String id){super(properties(id));this.id=id;}
    public String productId(){return id;}
    private static Properties properties(String id){
        var p=new Properties();
        if(id.endsWith("_meat"))p.food(new FoodProperties.Builder().nutrition(3).saturationMod(.25F).build());
        int n=switch(id){case"stonebarrow_hotpot","honeydew_casserole"->10;case"emberback_stew"->12;case"tideback_chowder","frostwarm_skewer","starfelt_travel_roll"->8;default->0;};
        if(n>0)p.food(new FoodProperties.Builder().nutrition(n).saturationMod(.8F).alwaysEat().build());
        if(Set.of("stonebarrow_hotpot","emberback_stew","tideback_chowder","honeydew_casserole").contains(id))p.craftRemainder(Items.BOWL);
        if(id.equals("pasture_scraper"))p.durability(128);
        else if(Set.of("soft_lantern","quiet_felt","calming_bell","climbing_cord","waterproof_seed_wrap","beast_saddle").contains(id))p.stacksTo(1);
        return p;
    }
    @Override public ItemStack finishUsingItem(ItemStack held,Level level,LivingEntity who){
        ItemStack result=super.finishUsingItem(held,level,who);
        if(who instanceof ServerPlayer p){
            long now=level.getGameTime();var t=p.getPersistentData();
            switch(id){
                case"stonebarrow_hotpot"->t.putLong("TncGuardMealUntil",now+1200);
                case"emberback_stew"->{t.putLong("TncWarmUntil",now+1200);p.setTicksFrozen(0);}
                case"frostwarm_skewer"->{t.putLong("TncWarmUntil",now+600);p.setTicksFrozen(0);}
                case"tideback_chowder"->p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE,900,0));
                case"honeydew_casserole"->{t.putLong("TncRestMealUntil",now+400);t.putLong("TncRestMealStarted",now);t.putLong("TncRestMealNext",now+100);t.putInt("TncRestHeals",4);}
                case"starfelt_travel_roll"->{t.putLong("TncTravelMealUntil",now+1200);t.putLong("TncTravelMealNext",now+100);t.putInt("TncTravelMana",12);}
            }
            if(!p.isCreative()&&Set.of("stonebarrow_hotpot","emberback_stew","tideback_chowder","honeydew_casserole").contains(id)){if(result.isEmpty())return new ItemStack(Items.BOWL);give(p,new ItemStack(Items.BOWL));}
        }
        return result;
    }
    private static void give(Player player,ItemStack result){if(!player.getInventory().add(result))player.drop(result,false);}
    private static void eatOne(ServerPlayer p,ItemStack held){if(!p.isCreative())held.shrink(1);p.getInventory().setChanged();}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack held=player.getItemInHand(hand);
        if(Set.of("air_plume","wind_bottle","warm_breath","quiet_felt","calming_bell").contains(id)){
            if(player instanceof ServerPlayer p){
                if(id.equals("air_plume")||id.equals("wind_bottle")){p.setDeltaMovement(p.getDeltaMovement().add(0,.5,0));p.hurtMarked=true;p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,120,0));eatOne(p,held);}
                if(id.equals("warm_breath")){p.getPersistentData().putLong("TncWarmUntil",level.getGameTime()+1200);p.setTicksFrozen(0);eatOne(p,held);if(!p.isCreative()){var jar=new ItemStack(PastureRegistry.item("empty_breath_jar"));if(held.isEmpty())return InteractionResultHolder.consume(jar);give(p,jar);}}
                if(id.equals("quiet_felt")){p.getPersistentData().putLong("TncQuietUntil",level.getGameTime()+600);eatOne(p,held);}
                if(id.equals("calming_bell")){int count=0;for(var a:level.getEntitiesOfClass(PastureAnimal.class,p.getBoundingBox().inflate(12),a->a.mayCareFor(p))){a.calmFor(100);count++;}p.displayClientMessage(Component.literal("安畜铃响过，附近 "+count+" 只牲畜安静停步五秒。"),true);}
                p.getCooldowns().addCooldown(this,80);
            }
            return InteractionResultHolder.sidedSuccess(held,level.isClientSide);
        }
        if(Set.of("lantern_antler","lamp_wax").contains(id)&&player.getOffhandItem().getItem() instanceof PastureProductItem item&&item.id.equals("soft_lantern")){
            if(player instanceof ServerPlayer p){ItemStack lantern=p.getOffhandItem();int duration=id.equals("lantern_antler")?14400:4800;lantern.getOrCreateTag().putInt("LampTicks",Math.min(28800,lantern.getOrCreateTag().getInt("LampTicks")+duration));eatOne(p,held);}
            return InteractionResultHolder.sidedSuccess(held,level.isClientSide);
        }
        if(id.equals("soft_lantern")){if(player instanceof ServerPlayer p)p.displayClientMessage(Component.literal("提灯燃料 "+held.getOrCreateTag().getInt("LampTicks")/1200+" 分钟；另一手持落角或灯蜡可添燃料。"),true);return InteractionResultHolder.sidedSuccess(held,level.isClientSide);}
        return super.use(level,player,hand);
    }
    @Override public InteractionResult useOn(UseOnContext c){
        if(!(c.getPlayer() instanceof ServerPlayer p))return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
        BlockPos at=c.getClickedPos();var level=p.serverLevel();ItemStack held=c.getItemInHand();
        if(TownProtection.denied(p,at))return InteractionResult.FAIL;
        if(id.equals("climbing_cord"))return com.tnc.tnc.life.wonders.WonderRopes.use(c,12);
        if(id.equals("storm_crystal")&&level.getBlockEntity(at) instanceof EnergyBlockEntity node&&node.mayUse(p)){
            int reserve=held.hasTag()&&held.getTag().contains("CrystalFE")?held.getTag().getInt("CrystalFE"):50;
            reserve=Math.max(0,Math.min(50,reserve));int accepted=node.addEnergy(reserve);
            if(accepted>0){int left=reserve-accepted;if(left==0)eatOne(p,held);else if(held.getCount()==1)held.getOrCreateTag().putInt("CrystalFE",left);else{held.shrink(1);ItemStack remainder=new ItemStack(this);remainder.getOrCreateTag().putInt("CrystalFE",left);give(p,remainder);}}
            p.displayClientMessage(Component.literal("雷余晶送入 "+accepted+" FE。剩余电量随晶体保存。"),true);return InteractionResult.CONSUME;
        }
        if(id.equals("loam_pebble")||id.equals("spring_concentrate")){
            int capacity=id.equals("spring_concentrate")?4:1;
            int left=held.hasTag()&&held.getTag().contains("WaterUses")?held.getTag().getInt("WaterUses"):capacity;
            if(left<=0)return InteractionResult.FAIL;
            left=Math.min(capacity,left);
            BlockPos crop=level.getBlockEntity(at) instanceof BotanicalPlantEntity?at:at.above();
            boolean applied=false;
            if(level.getBlockEntity(crop) instanceof BotanicalPlantEntity plant&&!TownProtection.denied(p,crop)){plant.water(12000);applied=true;}
            BlockPos soil=level.getBlockState(at).is(Blocks.FARMLAND)?at:at.below();
            if(level.getBlockState(soil).is(Blocks.FARMLAND)&&!TownProtection.denied(p,soil)){PastureMoistureData.get(level).apply(soil,p.getUUID(),level.getGameTime()+12000);applied=true;}
            if(!applied)return InteractionResult.FAIL;
            left--;
            if(left<=0){eatOne(p,held);if(id.equals("spring_concentrate"))give(p,new ItemStack(Items.GLASS_BOTTLE));}
            else if(held.getCount()==1)held.getOrCreateTag().putInt("WaterUses",left);
            else{held.shrink(1);var remainder=new ItemStack(this);remainder.getOrCreateTag().putInt("WaterUses",left);give(p,remainder);}
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
    @Override public void inventoryTick(ItemStack stack,Level level,Entity holder,int slot,boolean selected){
        if(id.equals("soft_lantern")&&holder instanceof ServerPlayer p&&(selected||p.getOffhandItem()==stack)&&level.getGameTime()%20==0){
            int fuel=stack.getOrCreateTag().getInt("LampTicks");if(fuel<=0)return;
            BlockPos position=p.blockPosition().above();if(TownProtection.denied(p,position))return;
            if(PastureEffectEvents.localLight(p,position)){stack.getOrCreateTag().putInt("LampTicks",Math.max(0,fuel-20));}
        }
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> text,TooltipFlag flags){
        text.add(Component.literal(switch(id){case"storm_crystal"->"右键蓄能设备转入最多50FE；只消耗实际接收的电量。";case"spring_concentrate"->"四次浇灌植物/耕地，每次保湿十分钟，用尽返瓶。";case"loam_pebble"->"一次保湿十分钟，不凭空造水或矿。";case"soft_lantern"->"真实移动光；落角供12分钟、灯蜡供4分钟。";case"air_plume","wind_bottle"->"一阵向上的气流，缓降6秒；一次消耗。";case"warm_breath"->"保暖一分钟；使用后返空暖息罐。";case"quiet_felt"->"脚步振动静30秒；攻击即结束。";case"climbing_cord"->"右键布12格真实攀绳，潜行右键收回；不拆别人方块。";default->"原创牧养产物，用于料理、牧场工具、工坊或商行供货。";}));
    }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack){return Set.of("warm_breath","spring_concentrate","honeydew").contains(id)||super.hasCraftingRemainingItem(stack);}
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack){return switch(id){case"warm_breath"->new ItemStack(PastureRegistry.item("empty_breath_jar"));case"spring_concentrate","honeydew"->new ItemStack(Items.GLASS_BOTTLE);default->super.getCraftingRemainingItem(stack);};}
}
