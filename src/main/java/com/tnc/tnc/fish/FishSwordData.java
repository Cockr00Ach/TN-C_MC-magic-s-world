package com.tnc.tnc.fish;

import net.minecraft.nbt.CompoundTag;

/** Copies the complete serialized stack so foreign tags/capabilities survive metalization. */
public final class FishSwordData {
    private FishSwordData() {}
    public static CompoundTag metalized(CompoundTag original, int oldMax, int newMax) {
        CompoundTag result = original.copy();
        result.putString("id", "tnc:metal_fish_sword");
        CompoundTag tag = result.getCompound("tag");
        tag.remove(FishSwordEvents.PENDING);
        tag.putInt("Damage", FishSwordRules.convertedDamage(tag.getInt("Damage"), oldMax, newMax));
        result.put("tag", tag);
        return result;
    }
}
