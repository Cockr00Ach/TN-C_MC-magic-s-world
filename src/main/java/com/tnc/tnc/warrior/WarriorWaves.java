package com.tnc.tnc.warrior;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 剑气 / 剑波：连招收尾之后射出去的一道能量。
 *
 * <p>服务端模拟移动伤害体积，客户端根据一次发射快照渲染连续光刃：
 * 每 tick 往前推一段、对路过的实体结算一次伤害；飞行表现不使用粒子。所以存档里不会留东西，
 * 也不会因为实体没加载而失效。
 *
 * <p>两种形态：
 * <ul>
 *   <li><b>AIR</b> 剑气：竖直的月牙，跟着角色朝向飞出去 —— 长剑的破锋式/燕返、长枪的破军刺。</li>
 *   <li><b>GROUND</b> 贴地剑波：贴着地面横着扫出去的新月，宽而低、慢而重 —— 重剑的山崩/裂地、
 *       战锤的震地/崩山。用户画的例图就是这一种：外圈暗、内芯亮。</li>
 * </ul>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class WarriorWaves {

    private WarriorWaves() {
    }

    public enum Kind {
        /** 竖直月牙，飞在半空。 */
        AIR,
        /** 贴地新月，横扫地面。 */
        GROUND
    }

    /**
     * 一招的剑气规格。
     *
     * @param kind     形态
     * @param range    飞行距离（格）
     * @param speed    每 tick 前进的格数
     * @param damage   伤害倍率（乘玩家攻击力）
     * @param halfWidth 判定半宽（格）：路过时离中心线多近算被打到
     * @param core     内芯颜色（亮）
     * @param edge     外圈颜色（暗）
     */
    public record Spec(Kind kind, double range, double speed, float damage, double halfWidth,
                       float[] core, float[] edge) {
    }

    private static final float[] SWORD_CORE = {0.78f, 0.94f, 1.0f};
    private static final float[] SWORD_EDGE = {0.22f, 0.46f, 0.86f};
    private static final float[] GREAT_CORE = {1.0f, 0.72f, 0.24f};
    private static final float[] GREAT_EDGE = {0.30f, 0.20f, 0.14f};
    private static final float[] HAMMER_CORE = {1.0f, 0.86f, 0.46f};
    private static final float[] HAMMER_EDGE = {0.42f, 0.30f, 0.16f};
    private static final float[] SPEAR_CORE = {0.80f, 1.0f, 0.92f};
    private static final float[] SPEAR_EDGE = {0.16f, 0.62f, 0.52f};

    private static final Map<String, Spec> SPECS = Map.ofEntries(
            // 长剑：细月牙剑气
            Map.entry("sword_combo_1", new Spec(Kind.AIR, 5.0, 0.95, 0.55f, 1.0, SWORD_CORE, SWORD_EDGE)),
            Map.entry("sword_combo_2", new Spec(Kind.AIR, 4.2, 1.05, 0.45f, 1.3, SWORD_CORE, SWORD_EDGE)),
            Map.entry("sword_combo_3", new Spec(Kind.AIR, 6.0, 1.10, 0.60f, 1.1, SWORD_CORE, SWORD_EDGE)),
            Map.entry("sword_combo_6", new Spec(Kind.AIR, 7.0, 1.20, 0.70f, 1.2, SWORD_CORE, SWORD_EDGE)),
            // 重剑：贴地剑波（例图那一张）
            Map.entry("greatsword_combo_1", new Spec(Kind.GROUND, 7.0, 0.55, 0.80f, 1.8, GREAT_CORE, GREAT_EDGE)),
            Map.entry("greatsword_combo_2", new Spec(Kind.GROUND, 7.0, 0.50, 0.90f, 1.9, GREAT_CORE, GREAT_EDGE)),
            Map.entry("greatsword_combo_5", new Spec(Kind.GROUND, 9.0, 0.60, 1.00f, 2.2, GREAT_CORE, GREAT_EDGE)),
            Map.entry("greatsword_combo_6", new Spec(Kind.GROUND, 6.0, 0.60, 0.70f, 1.8, GREAT_CORE, GREAT_EDGE)),
            // 战锤：贴地冲击波
            Map.entry("hammer_combo_2", new Spec(Kind.GROUND, 7.0, 0.55, 0.85f, 2.0, HAMMER_CORE, HAMMER_EDGE)),
            Map.entry("hammer_combo_5", new Spec(Kind.GROUND, 8.0, 0.55, 0.95f, 2.1, HAMMER_CORE, HAMMER_EDGE)),
            // 长枪：又细又快的枪气
            Map.entry("spear_combo_1", new Spec(Kind.AIR, 7.0, 1.35, 0.60f, 0.7, SPEAR_CORE, SPEAR_EDGE)),
            Map.entry("spear_combo_2", new Spec(Kind.AIR, 8.0, 1.45, 0.55f, 0.7, SPEAR_CORE, SPEAR_EDGE)));

    private static final class Wave {
        final Kind kind;
        final ResourceKey<Level> dimension;
        final Vec3 origin;
        final Vec3 dir;
        final Spec spec;
        final float damage;
        final UUID owner;
        final long startedAt;
        /** 磨砺虚影倍率：飞得更远（与近战判定同步变长）。 */
        final double reach;
        final Set<Integer> hit = new HashSet<>();

        Wave(Kind kind, ResourceKey<Level> dimension, Vec3 origin, Vec3 dir, Spec spec, float damage,
             UUID owner, long startedAt, double reach) {
            this.kind = kind;
            this.dimension = dimension;
            this.origin = origin;
            this.dir = dir;
            this.spec = spec;
            this.damage = damage;
            this.owner = owner;
            this.startedAt = startedAt;
            this.reach = reach;
        }
    }

    private static final List<Wave> ACTIVE = new ArrayList<>();
    private static boolean warned;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** 这一招要不要带剑气/剑波；没有就不发。 */
    static void onFinisher(ServerPlayer player, WarriorSkill skill, double reach) {
        Spec spec = specFor(skill);
        if (spec == null || player == null) {
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        Vec3 dir = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).normalize();
        Vec3 origin = player.position().add(0, skill.family() == WarriorWeaponFamily.BOW ? 1.2 : 0.35, 0)
                .add(dir.scale(0.8));
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ACTIVE.add(new Wave(spec.kind(), player.level().dimension(), origin, dir, spec, base * spec.damage(),
                player.getUUID(), player.level().getGameTime(), Math.max(1.0, reach)));
        var stack=player.getMainHandItem();
        var key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        String element=stack.hasTag()?stack.getTag().getString(WindInfusion.ELEMENT_KEY):"";
        // ★ 2026-11-08 领域法术：剑波也跟着领域换色（雷＝紫电、暗＝近黑）✓
        String domain=com.tnc.tnc.magic.domain.DomainSpells.activeElementId(player);
        if(domain!=null)element=domain;
        var appearance=WarriorTrailAppearance.of(key==null?"":key.getPath(),skill.family(),element);
        com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.NEAR.with(() ->
                        new net.minecraftforge.network.PacketDistributor.TargetPoint(origin.x, origin.y, origin.z,
                                96, player.level().dimension())),
                new WarriorWavePacket(player.level().dimension().location(), origin, dir,
                        spec.kind() == Kind.GROUND, spec.speed(), spec.range() * Math.max(1.0, reach),
                        spec.halfWidth(), appearance.core(), appearance.shell(), player.level().getGameTime()));
        player.serverLevel().playSound(null, player.blockPosition(),
                spec.kind() == Kind.GROUND ? SoundEvents.GENERIC_EXPLODE : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, spec.kind() == Kind.GROUND ? 0.45f : 0.8f,
                spec.kind() == Kind.GROUND ? 0.85f : 1.35f);
    }

    /** Own level hook: removing arrow particle code must never remove moving damage simulation. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void levelTick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if(event.phase==net.minecraftforge.event.TickEvent.Phase.END && event.level instanceof ServerLevel level)
            tick(level,level.getGameTime());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){ACTIVE.clear();warned=false;}
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void unloaded(net.minecraftforge.event.level.LevelEvent.Unload event){
        if(event.getLevel() instanceof ServerLevel level)ACTIVE.removeIf(w->w.dimension.equals(level.dimension()));
    }
    public static Spec specFor(WarriorSkill skill) {
        if(skill==null || skill.ranged() || !skill.family().melee() || skill.slot()==1 || skill.slot()==2 || skill.backsteps())return null;
        Spec original=SPECS.get(skill.id());if(original!=null)return original;
        return switch(skill.family()) {
            case LONGSWORD->SPECS.get("sword_combo_1");
            case GREATSWORD->SPECS.get("greatsword_combo_1");
            case HAMMER->SPECS.get("hammer_combo_2");
            case SPEAR->SPECS.get("spear_combo_1");
            default->null;
        };
    }
    /** Advance exactly once per server level tick. */
    static void tick(ServerLevel level, long now) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        var it = ACTIVE.iterator();
        while (it.hasNext()) {
            Wave wave = it.next();
            if (!wave.dimension.equals(level.dimension())) {
                continue;
            }
            long age = now - wave.startedAt;
            double travelled = wave.spec.speed() * age;
            if (age > 1 && travelled > wave.spec.range() * wave.reach) {
                it.remove();
                continue;
            }
            Vec3 center = wave.origin.add(wave.dir.scale(travelled));
            Vec3 previous=wave.origin.add(wave.dir.scale(Math.max(0,travelled-wave.spec.speed())));
            if(level.clip(new net.minecraft.world.level.ClipContext(previous,center,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,null)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS) {
                it.remove();continue;
            }
            Vec3 side = new Vec3(-wave.dir.z, 0, wave.dir.x);
            try {
                damage(level, wave, center, side);
            } catch (RuntimeException error) {
                // 一道波出错不能把整个服务器 tick 带崩（那会直接把人踢下线）
                it.remove();
                if (!warned) {
                    warned = true;
                    LOGGER.error("[tnc] 剑气结算出错，已丢弃这一道波：", error);
                }
            }
        }
    }

    private static int color(float[] rgb) {
        return ((int)(rgb[0] * 255) << 16) | ((int)(rgb[1] * 255) << 8) | (int)(rgb[2] * 255);
    }

    private static void damage(ServerLevel level, Wave wave, Vec3 center, Vec3 side) {
        if (!(level.getEntity(wave.owner) instanceof ServerPlayer player)) {
            return;
        }
        double reach = Math.max(1.2, wave.spec.halfWidth() + 0.6);
        AABB box = new AABB(center.x - reach, center.y - 1.0, center.z - reach,
                center.x + reach, center.y + (wave.kind == Kind.GROUND ? 1.0 : 1.9), center.z + reach);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> WarriorComboService.validTarget(player,e) && player.hasLineOfSight(e))) {
            if (!wave.hit.add(target.getId())) {
                continue;
            }
            Vec3 offset = target.position().subtract(center);
            double along = offset.dot(wave.dir);
            double lateral = Math.abs(offset.dot(side));
            if (along < -0.8 || along > 1.4 || lateral > reach) {
                wave.hit.remove(target.getId());
                continue;
            }
            if (target.hurt(player.damageSources().playerAttack(player), wave.damage)) {
                Vec3 push = wave.dir.scale(wave.kind == Kind.GROUND ? 0.75 : 0.45).add(0, 0.18, 0);
                target.push(push.x, push.y, push.z);
                WarriorCombatEffects.hitSpark(player, target, false);
            }
        }
    }

    /** 当前有多少道剑气在飞（GameTest/调试用）。 */
    public static int activeCount() {
        return ACTIVE.size();
    }
    static long activeCount(UUID owner){return ACTIVE.stream().filter(w->w.owner.equals(owner)).count();}

}
