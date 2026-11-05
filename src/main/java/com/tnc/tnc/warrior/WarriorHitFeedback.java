package com.tnc.tnc.warrior;

/** Render-only recoil, in degrees. Size uses the actual target bounding box. */
public final class WarriorHitFeedback {
    private WarriorHitFeedback() {}
    public static double strength(double size, boolean heavy) {
        if (!Double.isFinite(size) || size <= 0) return 0;
        return Math.min(2.2, (.18 + .29 * Math.log1p(size)) * (heavy ? 1.45 : 1));
    }
    public static double duration(double size) {
        return Double.isFinite(size) ? Math.min(6.5, 3.2 + Math.max(0, size) * .2) : 3.2;
    }
    public static double envelope(double age, double duration) {
        if (age < 0 || age >= duration) return 0;
        double t = age / duration;
        return Math.min(1, age / .25) * (1-t) * (1-t);
    }
    public static net.minecraft.world.phys.Vec3 contact(net.minecraft.world.phys.AABB box,net.minecraft.world.phys.Vec3 source) {
        var center=anchor(box,source);var outward=normal(box,source);
        double radius=Math.sqrt(box.getXsize()*box.getXsize()+box.getYsize()*box.getYsize()+box.getZsize()*box.getZsize())+1;
        return box.clip(center.add(outward.scale(radius)),center).orElse(center).add(outward.scale(.035));
    }
    private static net.minecraft.world.phys.Vec3 anchor(net.minecraft.world.phys.AABB box,net.minecraft.world.phys.Vec3 source) {
        var c=box.getCenter();double margin=Math.min(.15,box.getYsize()*.15);
        return new net.minecraft.world.phys.Vec3(c.x,Math.max(box.minY+margin,Math.min(box.maxY-margin,source.y)),c.z);
    }
    public static net.minecraft.world.phys.Vec3 normal(net.minecraft.world.phys.AABB box,net.minecraft.world.phys.Vec3 source) {
        var n=source.subtract(anchor(box,source)).normalize();
        return n.lengthSqr()<.5?new net.minecraft.world.phys.Vec3(0,0,1):n;
    }

}
