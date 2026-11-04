package com.tnc.tnc.warrior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class WarriorArrowItem extends ArrowItem {
    private final int style;
    public WarriorArrowItem(int style){super(new Properties());this.style=style;}
    public int style(){return style;}
    @Override public AbstractArrow createArrow(Level level,ItemStack stack,LivingEntity owner){return new WarriorArrowEntity(level,owner,style);}
}
