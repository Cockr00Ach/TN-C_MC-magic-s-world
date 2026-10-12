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
    public static int charge(int tier) { return tier == 5 ? 24 : tier == 4 ? 2 : 10; }
    public static int duration(int tier) { return tier == 5 ? 240 : tier == 4 ? 60 : tier == 3 ? 200 : 64; }
    public static double boltRadius(int tier) { return tier == 2 ? .85 : .32; }
    public static final int SLASH_CHANNEL_TICKS = 20;
    public static final int SLASH_VOLLEY_INTERVAL = 1;
    /** 龙滅（水球链 t5）的**水柱 / 破坏半径**＝ 15 格 ✓ —— 多条测试与作者都钉着这个数 ✗。 */
    public static final double DRAGON_RUIN_RADIUS = 15;
    /**
     * 龙滅（水球链 t5）的**施法法阵半径** ✓ —— 作者 2026-10-10："t5 的施法法阵的半径
     * 应该是水柱半径的一倍"，问清后确认是**加大一倍** ⇒ 法阵 = 水柱 ×2 = **30 格** ✓。
     *
     * <p>⚠️ <b>只放大法阵</b> ✗：{@link #radius} 仍是 15 ✓ —— 阵放大<b>不改判定、不改伤害、
     * 不改钻掘半径</b> ✗（否则一次龙滅会挖掉 60 格宽的山 ✗，那不是作者要的 ✗）。
     */
    public static final double DRAGON_RUIN_SIGIL_RADIUS = DRAGON_RUIN_RADIUS * 2;
    /**
     * 雨滴链（第四条链 · kind==3）的**布局常量** ✓ —— 第九批重做时抽出来的唯一来源 ✗。
     *
     * <p>为什么必须共享：天空法阵、落水柱那一圈、雨幕半宽这三样**客户端几何与服务端粒子各画一次** ✗
     * （{@code WaterRainVisuals} 与 {@code WaterFx.rainFx} / {@code skySeal} ✗）——
     * 两个地方各写一个魔法数字，改一处就会错位 ✗（水板那批已经踩过一次"环半径没跟着板走"✗）。
     */
    public static final int RAIN_SKY_SEAL_LIFT = 4;
    /** 落水柱所在的那一圈（占领域半径的比例 ✗）：t5 十二道水帘**正好挂在上层天空阵的边上** ✓
        （上层阵半径 = `.80r` ✗ ⇒ 天洪读作"天空大阵往下倒十二道水" ✓，而不是十二道柱子悬在阵外面 ✗）。 */
    public static final double RAIN_CURTAIN_RING = .80;
    /** 雨幕粒子的**半宽**（占领域半径的比例 ✗）：盒角 `.70*√2 ≈ 半径` ⇒ 雨正好落在水面里 ✓，不撒到域外 ✗。 */
    public static final double RAIN_VOLUME_HALF = .70;
    /** 天空法阵的高度 ✓（t5 领域高 28 ⇒ 阵在 32 格高 ✗，档位文案写的就是"28格高双层天空法阵"✓）。 */
    public static double rainSkySealHeight(int tier) { return fieldHeight(3, tier) + RAIN_SKY_SEAL_LIFT; }
    /** 雨链 t3 水莲的**半径比例** ✓（客户端瓣面与服务端瓣尖粒子共用 ✗）。 */
    public static final double RAIN_LOTUS_SCALE = .92;
    /** 水莲张开度（0 合拢 → 1 全开 ✓）：由年龄驱动 ⇒ 莲是"活"的 ✗；两端共用同一条曲线 ✓。 */
    public static double rainLotusOpen(int tier, int age) {
        return Math.min(1, age / Math.max(1.0, fieldLife(3, tier) * .30)) * (.94 + .06 * Math.sin(age * .02));
    }
    /** 花瓣张开的横向比例 ✓（与 `WaterGeometry.petalPoint` 里那条 spread 同一条公式 ✗）。 */
    public static double rainLotusSpread(double open) { return .55 + .45 * open; }
    /** 瓣尖高度 ✓ —— 客户端瓣缘、发光星、服务端瓣尖粒子**三处共用** ✗（各写一遍必然错位 ✗）。 */
    public static double rainLotusTipHeight(double lotusRadius, double open) {
        return Math.sin(.58 * Math.PI) * lotusRadius * .40 * open + .40 - .32 * lotusRadius * .55 * open;
    }
    public static double radius(int tier) { return tier == 5 ? DRAGON_RUIN_RADIUS : .8; }
    public static double range(int tier) { return tier == 5 ? 96 : tier == 1 ? 40 : 48; }
    public static double circleRadius(int tier) { return tier == 5 ? DRAGON_RUIN_SIGIL_RADIUS : tier == 4 ? 3.5 : 4; }
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
    /** Twenty individually rolled fronts, one tick apart, all aimed forward. */
    public static final int SLASH_COUNT = 20;
    public static final float SLASH_ROLL_STEP = 18F;
    public static final float SLASH_ROLL_SPIN = 0F;
    public static float slashRoll(int index) { return Math.floorMod(index,SLASH_COUNT)*SLASH_ROLL_STEP; }
    public static double slashRollRadians(float rollDegrees, double age) { return Math.toRadians(rollDegrees); }
    public static Vec3 slashArc(Vec3 direction,double roll,double angle,double radius) {
        Vec3 forward=direction.normalize(),right=right(forward),up=right.cross(forward).normalize();
        Vec3 across=right.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
        return across.scale(Math.sin(angle)*radius).add(forward.scale(Math.cos(angle)*radius*.30));
    }
    public static java.util.List<Vec3> slashDirections(float yaw) {
        return java.util.List.of(Vec3.directionFromRotation(0,yaw));
    }
    public static boolean canRestrain(double height) { return height>0 && height<4; }
    public static final float RAIN_SEAL_OPACITY = .25F;
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
        return powerMultiplier(value);
    }
    public static float powerMultiplier(double value) {
        // SpellPower attributes start at ZERO in the real pack; zero is no gear bonus,
        // not permission to turn every base-damage spell into a harmless knockback.
        return Double.isFinite(value) ? (float)Math.max(1, Math.min(10000, value)) : 1;
    }
}
