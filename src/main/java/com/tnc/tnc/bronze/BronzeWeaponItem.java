package com.tnc.tnc.bronze;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class BronzeWeaponItem extends SwordItem {
    public static final String UPGRADE="TncBronzeUpgrade", FLIGHT="TncBronzeFlightUntil",FLIGHT_ID="TncBronzeFlightId";
    public final BronzeProfile profile;
    public BronzeWeaponItem(BronzeProfile profile){super(Tiers.IRON,0,profile.speed-4,new Item.Properties().durability(640));this.profile=profile;}
    public static int upgrade(ItemStack stack){return stack.hasTag()?Math.max(0,Math.min(10,stack.getTag().getInt(UPGRADE))):0;}
    public static boolean inFlight(ItemStack stack,Level level){return stack.hasTag()&&stack.getTag().getLong(FLIGHT)>level.getGameTime();}
    @Override public Multimap<Attribute,AttributeModifier> getAttributeModifiers(EquipmentSlot slot,ItemStack stack){
        if(slot!=EquipmentSlot.MAINHAND)return ImmutableMultimap.of();
        return ImmutableMultimap.of(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Bronze damage",profile.upgraded(upgrade(stack)),AttributeModifier.Operation.ADDITION),
            Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Bronze speed",profile.speed-4,AttributeModifier.Operation.ADDITION));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND||inFlight(stack,level)||!com.tnc.tnc.warrior.WarriorBronzeBridge.canThrow(player))return InteractionResultHolder.fail(stack);
        if(profile==BronzeProfile.HALBERD&&player.isShiftKeyDown())return new InteractionResultHolder<>(level.isClientSide?InteractionResult.SUCCESS:BronzeSummons.summon((net.minecraft.server.level.ServerPlayer)player),stack);
        if(profile!=BronzeProfile.DAGGER&&profile!=BronzeProfile.HALBERD)return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.SPEAR;}
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
        if(!level.isClientSide&&entity instanceof net.minecraft.server.level.ServerPlayer player&&(profile==BronzeProfile.DAGGER||profile==BronzeProfile.HALBERD)&&!inFlight(stack,level)&&com.tnc.tnc.warrior.WarriorBronzeBridge.canThrow(player))
            BronzeDaggerEntity.launch(player,stack,getUseDuration(stack)-remaining);
    }
    @Override public void inventoryTick(ItemStack stack,Level level,Entity owner,int slot,boolean selected){
        super.inventoryTick(stack,level,owner,slot,selected);
        if(!level.isClientSide&&stack.hasTag()&&stack.getTag().contains(FLIGHT)&&(!inFlight(stack,level)||stack.getTag().getLong(FLIGHT)>level.getGameTime()+60)){stack.getTag().remove(FLIGHT);stack.getTag().remove(FLIGHT_ID);}
    }
    @Override public boolean isValidRepairItem(ItemStack a,ItemStack b){return b.is(Items.COPPER_INGOT)||super.isValidRepairItem(a,b);}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.tnc.bronze_upgrade",upgrade(stack),upgrade(stack)*10).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.tnc.bronze_combo").withStyle(ChatFormatting.GRAY));
        if(profile==BronzeProfile.DAGGER||profile==BronzeProfile.HALBERD)lines.add(Component.translatable("tooltip.tnc.bronze_throw").withStyle(ChatFormatting.GRAY));
        if(profile==BronzeProfile.HALBERD)lines.add(Component.translatable("tooltip.tnc.bronze_summon").withStyle(ChatFormatting.DARK_AQUA));
        if(level!=null&&inFlight(stack,level))lines.add(stack.getHoverName().copy().append(" ↩").withStyle(ChatFormatting.DARK_AQUA));
    }
}
