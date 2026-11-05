package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.WarriorAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

final class WarriorMartialClips {
    private WarriorMartialClips() {}
    static WarriorComboAnimations.Clip clip(WarriorAnimation animation,ItemStack stack) {
        var id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        return WarriorComboAnimations.clip(animation,id==null?"":id.toString());
    }
}
