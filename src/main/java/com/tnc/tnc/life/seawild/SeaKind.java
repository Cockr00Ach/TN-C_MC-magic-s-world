package com.tnc.tnc.life.seawild;

import net.minecraft.world.entity.MobCategory;

public enum SeaKind {
    REEF("reef_glass_school", MobCategory.WATER_AMBIENT, .72f, .50f),
    SILVER("silverwake_school", MobCategory.WATER_AMBIENT, .82f, .52f),
    JELLY("veilbell_jelly", MobCategory.WATER_CREATURE, .80f, 1.08f),
    SLUG("lantern_nudibranch", MobCategory.WATER_AMBIENT, .58f, .26f);

    public final String id;
    final MobCategory category;
    final float width, height;

    SeaKind(String id, MobCategory category, float width, float height) {
        this.id = id;
        this.category = category;
        this.width = width;
        this.height = height;
    }

    public boolean school() { return this == REEF || this == SILVER; }
}
