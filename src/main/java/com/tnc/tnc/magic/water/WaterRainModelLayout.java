package com.tnc.tnc.magic.water;

import net.minecraft.world.phys.Vec3;

/** Deterministic model instances on one field entity; no extra server entities per drop. */
public final class WaterRainModelLayout {
    private WaterRainModelLayout() {}
    public static int count(int tier,boolean nearby){return nearby?16+tier*6:12+tier*2;}
    public static Vec3 drop(int index,double radius,double height,double age) {
        double angle=index*2.399963229728653;
        double radial=Math.sqrt(((index*37)%101+.5)/101.0)*radius*.94;
        double phase=(index*.61803398875+age*.10/Math.max(1,height))%1;
        return new Vec3(Math.cos(angle)*radial,.25+(1-phase)*height,Math.sin(angle)*radial);
    }
}
