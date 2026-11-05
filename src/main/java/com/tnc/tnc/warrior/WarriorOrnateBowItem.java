package com.tnc.tnc.warrior;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.entity.projectile.AbstractArrow;

/** Ordinary right-click shots also use the matching block-built arrow. */
public final class WarriorOrnateBowItem extends BowItem {
    private final int style;
    public WarriorOrnateBowItem(int style,int durability){super(new Properties().durability(durability));this.style=style;}
    @Override public AbstractArrow customArrow(AbstractArrow original){
        // Preserve tipped / spectral / third-party arrows rather than dropping their effects.
        if(original.getClass()!=net.minecraft.world.entity.projectile.Arrow.class
                || !(original.getOwner() instanceof net.minecraft.world.entity.LivingEntity owner))return original;
        var tag=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        if(tag.contains("Potion") || tag.contains("CustomPotionEffects"))return original;
        var arrow=new WarriorArrowEntity(original.level(),owner,style);
        arrow.load(tag);arrow.setStyle(style);
        return arrow;
    }
}
