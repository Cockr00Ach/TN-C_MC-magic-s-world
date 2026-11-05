package com.tnc.tnc.warrior.forge;

import java.util.UUID;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc")
public final class ForgeWeaponData {
    public static final String KEY="TncForgeCraft";
    private ForgeWeaponData() {}
    public static void finish(ItemStack out, ItemStack billet, ForgeSession s) {
        var data=ForgeJobData.save(s);
        var source=billet.getOrCreateTag();
        data.putUUID("WeaponId",source.hasUUID("WorkId")?source.getUUID("WorkId"):UUID.randomUUID());
        if(source.hasUUID("Maker"))data.putUUID("Maker",source.getUUID("Maker"));
        data.putString("MakerName",source.getString("MakerName"));data.putInt("Quality",s.quality().ordinal());
        data.putBoolean("Named",false);data.putBoolean("DivineRecorded",s.quality()==ForgeQuality.PRIMORDIAL);
        out.getOrCreateTag().put(KEY,data);
        out.setHoverName(Component.literal(s.quality().title+"·"+s.pattern.title).withStyle(Style.EMPTY.withColor(s.quality().color)));
    }
    public static ForgeQuality quality(ItemStack s) {
        if(!s.hasTag()||!s.getTag().contains(KEY,10))return null;
        int i=s.getTag().getCompound(KEY).getInt("Quality");
        return i<0||i>=ForgeQuality.values().length?null:ForgeQuality.values()[i];
    }
    public static boolean needsName(ItemStack s) { return quality(s)==ForgeQuality.PRIMORDIAL&&!s.getTag().getCompound(KEY).getBoolean("Named"); }
    public static boolean name(ServerPlayer p, ItemStack stack, UUID id, String raw) {
        if(!needsName(stack))return false;
        var t=stack.getTag().getCompound(KEY);
        if(!t.hasUUID("WeaponId")||!t.getUUID("WeaponId").equals(id)||!t.hasUUID("Maker")||!t.getUUID("Maker").equals(p.getUUID()))return false;
        String name=raw.strip();
        if(name.codePointCount(0,name.length())<2||name.codePointCount(0,name.length())>16
                ||name.codePoints().anyMatch(c->Character.isISOControl(c)||c==167))return false;
        t.putString("DivineName",name);t.putBoolean("Named",true);
        stack.setHoverName(Component.literal("原初·神铸「"+name+"」").withStyle(Style.EMPTY.withColor(ForgeQuality.PRIMORDIAL.color)));
        p.getInventory().setChanged();return true;
    }
    @SubscribeEvent public static void attributes(ItemAttributeModifierEvent e) {
        var q=quality(e.getItemStack());if(q==null||q.attack==0||e.getSlotType()!=EquipmentSlot.MAINHAND)return;
        var original=java.util.List.copyOf(e.getModifiers().get(Attributes.ATTACK_DAMAGE));
        for(var modifier:original)if(modifier.getOperation()==AttributeModifier.Operation.ADDITION){
            e.removeModifier(Attributes.ATTACK_DAMAGE,modifier);
            e.addModifier(Attributes.ATTACK_DAMAGE,new AttributeModifier(modifier.getId(),modifier.getName(),modifier.getAmount()*(1+q.attack),modifier.getOperation()));
        }
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent e) {
        var q=quality(e.getItemStack());if(q==null)return;
        var t=e.getItemStack().getTag().getCompound(KEY);
        e.getToolTip().add(Component.literal("手工品质 · "+q.title+"  铸造者 · "+t.getString("MakerName")).withStyle(Style.EMPTY.withColor(q.color)));
        e.getToolTip().add(Component.literal(String.format(java.util.Locale.ROOT,"火候%.0f  锻打%.0f  定形%.0f  淬火%.0f  装配%.0f",t.getDouble("H"),t.getDouble("P"),t.getDouble("S"),t.getDouble("Q"),t.getDouble("A"))).withStyle(net.minecraft.ChatFormatting.GRAY));
        if(needsName(e.getItemStack()))e.getToolTip().add(Component.literal("主手持握，对空右键为神铸兵器命名").withStyle(net.minecraft.ChatFormatting.GOLD));
    }
    @SubscribeEvent public static void nameAgain(PlayerInteractEvent.RightClickItem e) {
        if(e.getEntity() instanceof ServerPlayer p&&e.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND&&needsName(e.getItemStack()))ForgeNamingNetwork.open(p,e.getItemStack());
    }
}
