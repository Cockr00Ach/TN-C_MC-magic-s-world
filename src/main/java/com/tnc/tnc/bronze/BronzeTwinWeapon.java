package com.tnc.tnc.bronze;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** The fused weapon is one inventory stack; its second blade is rendering only. */
public final class BronzeTwinWeapon {
    public static final String BLOCKED="TncBronzeTwinBlocked";
    private BronzeTwinWeapon(){}
    public static boolean isPair(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof BronzeWeaponItem item&&item.profile==BronzeProfile.HALBERD;}
    public static boolean permits(BronzeProfile profile,boolean mainHand,boolean offhandEmpty){return mainHand&&(profile!=BronzeProfile.HALBERD||offhandEmpty);}
    public static boolean blocked(LivingEntity owner){return isPair(owner.getMainHandItem())&&!permits(BronzeProfile.HALBERD,true,owner.getOffhandItem().isEmpty());}
    public static boolean active(LivingEntity owner){return isPair(owner.getMainHandItem())&&!blocked(owner)&&!BronzeWeaponItem.inFlight(owner.getMainHandItem(),owner.level());}
    public static boolean canUse(LivingEntity owner,ItemStack stack){return !isPair(stack)||permits(BronzeProfile.HALBERD,stack==owner.getMainHandItem(),owner.getOffhandItem().isEmpty());}
    public static void updateAttributes(LivingEntity owner){
        var stack=owner.getMainHandItem();if(!isPair(stack))return;
        if(blocked(owner)){if(!stack.getOrCreateTag().getBoolean(BLOCKED))stack.getOrCreateTag().putBoolean(BLOCKED,true);}
        else if(stack.hasTag())stack.getTag().remove(BLOCKED);
        if(blocked(owner)&&owner.isUsingItem()&&owner.getUseItem()==stack)owner.stopUsingItem();
    }
}
