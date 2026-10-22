package com.tnc.tnc.arcana;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.network.chat.Component;
import java.util.List;
/** A portable meal retains its actual nine-slot recipe until it is eaten. */
public final class ArcanaMealItem extends Item {
 public static final String[] IDS={"sunpepper_roast","tide_herb_soup","cloudleaf_salad","earthroot_loaf","moon_honey_parfait","concord_banquet","ninefold_sun_feast","discord_mash"};
 public ArcanaMealItem(){super(new Properties().stacksTo(16).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(10).saturationMod(.65f).alwaysEat().build()).rarity(Rarity.UNCOMMON));}
 @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity eater){var recipe=stack.getOrCreateTag().copy();var result=super.finishUsingItem(stack,level,eater);if(eater instanceof ServerPlayer p){
  if(recipe.getInt("Conflicts")>0){p.hurt(p.damageSources().magic(),4);p.push(0,.65,0);p.hurtMarked=true;p.addEffect(new MobEffectInstance(MobEffects.CONFUSION,300));p.addEffect(new MobEffectInstance(ArcanaMealEffects.NO_MAGIC,300));}
  else{int[] levels=recipe.getIntArray("TasteLevels");for(int i=0;i<Math.min(5,levels.length);i++)if(levels[i]>0)p.addEffect(new MobEffectInstance(ArcanaMealEffects.MEALS[i],6000,Math.min(1,levels[i]-1)));if(levels.length>3&&levels[3]>0)ArcanaPhysiology.detox(p,30);if(recipe.getInt("Bridges")>0)p.addEffect(new MobEffectInstance(ArcanaMealEffects.HARMONY,12000));if(recipe.getBoolean("SunMeal")){ArcanaPhysiology.data(p).putLong("MealSunUntil",ArcanaPhysiology.now(p)+3600);com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();}}
 }return result;}
 @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flags){var t=stack.getTag();if(t==null)return;lines.add(Component.literal("餐盘成品 · "+t.getInt("Ingredients")+"味 · 食用后生效"));if(t.getInt("Conflicts")>0)lines.add(Component.literal("药性相冲：食用会反胃并短暂禁魔").withStyle(net.minecraft.ChatFormatting.RED));else lines.add(Component.literal(t.getBoolean("SunMeal")?"九曜相生 · 三分钟烈阳共鸣":"相生增益 · 甘味桥 "+t.getInt("Bridges")).withStyle(net.minecraft.ChatFormatting.AQUA));for(var raw:t.getList("MealIngredients",10)){var ingredient=ItemStack.of((net.minecraft.nbt.CompoundTag)raw);lines.add(Component.literal("· ").append(ingredient.getHoverName()).withStyle(net.minecraft.ChatFormatting.GRAY));}}
}
