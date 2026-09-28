package com.tnc.tnc.magic.water;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Shared server/client geometry and first-pass tuning, independent of the lightning spells. */
public final class WaterSpellRules {
    private WaterSpellRules() {}
    public static int tier(String id) {
        return switch(id) {
            case "water_ball" -> 1; case "water_cannon" -> 2; case "dragon_roar" -> 3;
            case "dragon_howl" -> 4; case "dragon_ruin" -> 5; default -> 0;
        };
    }
    public static int charge(int tier) { return tier == 5 ? 30 : tier == 4 ? 20 : 10; }
    public static int duration(int tier) { return tier == 5 ? 240 : tier == 4 ? 60 : 64; }
    public static double radius(int tier) { return tier == 5 ? circleRadius(tier) : .8; }
    public static double range(int tier) { return tier == 5 ? 96 : tier == 1 ? 40 : 48; }
    public static double circleRadius(int tier) { return tier == 5 ? 15 : tier == 4 ? 3.5 : 4; }
    public static double fieldRadius(int kind,int tier) {
        return kind==1?(tier==5?OceanWaveRules.RADIUS:tier==4?8:tier==3?4:3):kind==2?new double[]{5,7,12,18,26}[tier-1]:new double[]{8,12,16,22,32}[tier-1];
    }
    public static int fieldLife(int kind,int tier) {
        return kind==1?(tier==5?OceanWaveRules.LIFE:tier==4?48:28):kind==2?new int[]{160,240,360,480,SeaGodSwordRules.FIELD_LIFE}[tier-1]:200+tier*100;
    }
    public static double fieldHeight(int kind,int tier) {
        return kind==1?(tier==5?OceanWaveRules.HEIGHT:tier==4?8:tier==3?4:3):kind==2?Math.min(24,3+fieldRadius(kind,tier)*.8):Math.min(28,4+fieldRadius(kind,tier)*.75);
    }
    public static net.minecraft.world.phys.AABB uprightArea(Vec3 center,double radius,double height) {
        return new net.minecraft.world.phys.AABB(center.add(-radius,-.25,-radius),center.add(radius,height,radius));
    }
    /** 26 directions, three latitude rings plus both poles; no duplicate vectors. */
    public static java.util.List<Vec3> slashDirections(float yaw) {
        var result=new java.util.ArrayList<Vec3>();
        for(float pitch:new float[]{0,-45,45})for(int i=0;i<8;i++)result.add(Vec3.directionFromRotation(pitch,yaw+i*45));
        result.add(new Vec3(0,1,0));result.add(new Vec3(0,-1,0));return java.util.List.copyOf(result);
    }
    public static float damage(int tier) { return switch(tier) { case 2 -> 8; case 4 -> 6; case 5 -> 14; default -> 4; }; }
    public static Vec3 right(Vec3 direction) {
        Vec3 reference = Math.abs(direction.y) > .95 ? new Vec3(0,0,1) : new Vec3(0,1,0);
        return direction.cross(reference).normalize();
    }
    public static double segmentDistanceSquared(Vec3 point, Vec3 start, Vec3 direction, double length) {
        double t = Math.max(0, Math.min(length, point.subtract(start).dot(direction)));
        return point.distanceToSqr(start.add(direction.scale(t)));
    }
    /** Swept thin circular cross-section, not a radius-sized spherical endcap. */
    static boolean slashIntersects(net.minecraft.world.phys.AABB box,Vec3 previous,Vec3 current,Vec3 dir,double radius) {
        Vec3 half=new Vec3(box.getXsize()/2,box.getYsize()/2,box.getZsize()/2);
        Vec3 delta=box.getCenter().subtract(previous);
        double axial=delta.dot(dir),extent=projectExtent(half,dir);
        if(axial+extent<-.3 || axial-extent>previous.distanceTo(current)+.3)return false;
        Vec3 right=right(dir),up=right.cross(dir).normalize();
        double x=Math.max(0,Math.abs(delta.dot(right))-projectExtent(half,right));
        double y=Math.max(0,Math.abs(delta.dot(up))-projectExtent(half,up));
        return x*x+y*y<=radius*radius;
    }
    private static double projectExtent(Vec3 half,Vec3 axis) {
        return Math.abs(axis.x)*half.x+Math.abs(axis.y)*half.y+Math.abs(axis.z)*half.z;
    }
    public static boolean enemy(LivingEntity caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isAlliedTo(caster) || caster.isAlliedTo(target)
                || target instanceof Player || target instanceof TamableAnimal pet && pet.isTame()) return false;
        return target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == caster;
    }
    public static float power(LivingEntity caster) {
        var attribute = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("spell_power", "water"));
        if (attribute == null || caster.getAttribute(attribute) == null) return 1;
        double value = caster.getAttributeValue(attribute);
        return Double.isFinite(value) ? (float)Math.max(0, Math.min(10000, value)) : 1;
    }
}
