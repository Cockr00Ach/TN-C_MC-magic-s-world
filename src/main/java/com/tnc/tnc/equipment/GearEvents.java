package com.tnc.tnc.equipment;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.*;
import com.tnc.tnc.magic.*;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class GearEvents {
    @SubscribeEvent public static void equipment(LivingEquipmentChangeEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p)||e.getSlot().getType()!=EquipmentSlot.Type.ARMOR)return;
        refresh(p);
    }
    public static void refresh(ServerPlayer p){
        var m=MagicStone.getOrNull(p);if(m!=null){MagicStone.refreshMaxMana(p,m);MagicStoneNetwork.syncTo(p);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent e){
        if(e.isCanceled()||!(e.getEntity() instanceof ServerPlayer p)||e.getAmount()<p.getHealth()
                ||e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)||com.tnc.tnc.combat.DownedCombat.isDowned(p))return;
        var body=MageGear.worn(p,EquipmentSlot.CHEST);var hat=MageGear.worn(p,EquipmentSlot.HEAD);
        if(body==null||hat==null||!body.divine()||!hat.divine())return;
        var store=AdventureSavedData.get(p.server);var a=AdventureService.profile(p);if(store.activeTicks<a.divineReadyAt)return;
        a.divineReadyAt=store.activeTicks+8*60*20L;store.setDirty();e.setCanceled(true);p.setHealth(1);
        var m=MagicStone.getOrNull(p);if(m!=null){m.addMana((int)Math.floor(m.getMaxMana()*.2));MagicStoneNetwork.syncTo(p);}
        p.sendSystemMessage(Component.literal("归墟 · 残星回响：护住最后一线生命，回复20%魔力。"));
    }
}
