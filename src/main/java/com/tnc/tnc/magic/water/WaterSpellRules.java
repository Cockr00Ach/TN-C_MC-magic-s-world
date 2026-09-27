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
    public static int duration(int tier) { return tier == 5 ? 160 : tier == 4 ? 60 : 64; }
    public static double radius(int tier) { return tier == 5 ? 4 : .8; }
    public static double range(int tier) { return tier == 5 ? 64 : tier == 1 ? 40 : 48; }
    public static double circleRadius(int tier) { return tier == 5 ? 9 : tier == 4 ? 3.5 : 4; }
    public static float damage(int tier) { return switch(tier) { case 2 -> 8; case 4 -> 6; case 5 -> 14; default -> 4; }; }
    public static Vec3 right(Vec3 direction) {
        Vec3 reference = Math.abs(direction.y) > .95 ? new Vec3(0,0,1) : new Vec3(0,1,0);
        return direction.cross(reference).normalize();
    }
    public static double segmentDistanceSquared(Vec3 point, Vec3 start, Vec3 direction, double length) {
        double t = Math.max(0, Math.min(length, point.subtract(start).dot(direction)));
        return point.distanceToSqr(start.add(direction.scale(t)));
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
