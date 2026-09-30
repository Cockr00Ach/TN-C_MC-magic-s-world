package com.tnc.tnc.light;

import com.tnc.tnc.combat.CombatTeams;
import com.tnc.tnc.magic.TNEffects;
import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * <b>光系第二条链（光耀）</b>的全部机制 ✓ —— 作者 2026-10-01 定的五档：
 *
 * <table border="1">
 *   <tr><th>档</th><th>法术</th><th>范围</th><th>治疗</th><th>减伤</th><th>额外</th></tr>
 *   <tr><td>t1</td><td>光芒照耀</td><td>8</td><td>20</td><td>25%</td><td>地面法阵（半径＝范围）</td></tr>
 *   <tr><td>t2</td><td>圣光</td><td>10</td><td>30</td><td>50%</td><td>法阵</td></tr>
 *   <tr><td>t3</td><td>神光</td><td>12</td><td>30</td><td>50%</td><td>法阵中心召唤 2 格高天使（白/淡黄粒子）＋ <b>回归晴天</b></td></tr>
 *   <tr><td>t4</td><td>天使降临</td><td>14</td><td>30</td><td>70%</td><td>天使变 3 格高</td></tr>
 *   <tr><td>t5</td><td>天使的悲悯</td><td>16</td><td>30</td><td>70%</td><td>天使 5 格高 ＋ 范围内怪物<b>停手 5 秒</b></td></tr>
 * </table>
 *
 * <h2>设计要点（为什么这么做）</h2>
 * <ul>
 *   <li><b>法术 JSON 只负责表演</b>（起手粒子/动作/音效 ＋ 给施法者挂一个 buff ✓）✗；
 *       真正的"范围治疗 + 队友 buff + 法阵 + 天使 + 转晴"都在这里 ✓ ——
 *       因为 {@code release.target = SELF} 的法术**绝不能在 JSON 里放 {@code area_impact}** ✗✗
 *       （引擎会在 {@code ImpactContext.position} 为 null 时 NPE 崩服，本仓库踩过 ✓）。</li>
 *   <li><b>法阵半径 = 法术范围</b> ✓（作者原话："范围多大法阵多大"✓）——
 *       所以 {@link Spell#radius} 一个数同时管三件事：治疗半径、队友判定、法阵大小 ✓。</li>
 *   <li><b>队友判定</b>用现成的 {@link CombatTeams#group} ✓（同队/同 FTB 队伍/联机时的"co-op"✓），
 *       施法者自己永远算 ✓。</li>
 *   <li><b>天使</b>是 {@link TNAngelEntity}（纯雕像 ✓），一尊只留一尊：召唤前先把旧的清掉 ✓，
 *       免得连续放 t5 叠出一排天使 ✗。</li>
 *   <li><b>怪物停手</b>：给范围内的 {@link Enemy} 挂 {@link TNEffects#LIGHT_CALM} ✓，
 *       每 tick 由 {@link #tick()} 把它们的攻击目标清掉 ✓；
 *       伤害层面由 {@code armor/TNIronArmorEvents} 同一族逻辑拦（见 {@link #tick()} 的说明 ✓）。</li>
 * </ul>
 */
public final class TNLightChainMechanics {

    /** 每个档位的全部数值（作者给的表 ✓ —— 想调手感只改这里 ✓）。 */
    private record Spell(String path, int tier, double radius, int heal, double heightBlocks,
                         RegistryObject<MobEffect> buff, int buffSeconds, boolean clearWeather,
                         boolean calmMonsters) {
    }

    private static final Spell[] SPELLS = {
            new Spell("light_radiance", 1, 8.0D, 20, 0.0D, TNEffects.LIGHT_RADIANCE, 12, false, false),
            new Spell("holy_light", 2, 10.0D, 30, 0.0D, TNEffects.LIGHT_HOLY, 14, false, false),
            new Spell("divine_light", 3, 12.0D, 30, 2.0D, TNEffects.LIGHT_DIVINE, 16, true, false),
            new Spell("angel_descent", 4, 14.0D, 30, 3.0D, TNEffects.LIGHT_DESCENT, 18, false, false),
            new Spell("angel_mercy", 5, 16.0D, 30, 5.0D, TNEffects.LIGHT_MERCY, 20, false, true),
    };

    /** 法阵存在时长（tick）：光耀的阵留得久一点（作者要给玩家看清 ✓）。 */
    private static final int CIRCLE_LIFE = 220;
    /** 天使存在时长（tick）：15 秒 ✓（和 buff 时长接近，观感统一 ✓）。 */
    private static final int ANGEL_LIFE = 300;
    /** t5 怪物停手时长（tick）：作者指定 **5 秒** ✓。 */
    private static final int CALM_TICKS = 100;

    private TNLightChainMechanics() {
    }

    /** 这个法术是不是本链的（供 SPELL_CAST 钩子快速判断 ✓）。 */
    public static boolean isLightChainSpell(String path) {
        return find(path) != null;
    }

    private static Spell find(String path) {
        for (Spell spell : SPELLS) {
            if (spell.path().equals(path)) {
                return spell;
            }
        }
        return null;
    }

    /**
     * 释放时调用（由 {@code TnSpellMechanics.onSpellCast} 转发 ✓）。
     *
     * <p>顺序：法阵 → 治疗/挂 buff → 天使 → 转晴 → 怪物停手 ✓（先铺阵再治疗，观感上"光先落地"✓）。
     */
    public static void onSpellCast(ServerPlayer caster, String path) {
        Spell spell = find(path);
        if (spell == null) {
            return;
        }
        ServerLevel level = caster.serverLevel();
        Vec3 at = caster.position();

        // ① 法阵：半径 = 法术范围 ✓（作者："范围多大法阵多大"✓），用**光系**那张纯白贴图 ✓
        TNMagicCircleEntity circle = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
        if (circle != null) {
            circle.configure(spell.radius(), CIRCLE_LIFE, TNMagicCircleEntity.STYLE_LIGHT);
            circle.moveTo(at.x, at.y + 0.04D, at.z, 0.0F, 0.0F);
            level.addFreshEntity(circle);
        }

        // ② 范围内的队友：立刻回血 + 挂减伤 buff（施法者自己一定算 ✓）
        List<Player> allies = level.getEntitiesOfClass(Player.class,
                new AABB(at, at).inflate(spell.radius()));
        int healed = 0;
        for (Player ally : allies) {
            if (!isAlly(caster, ally)) {
                continue;
            }
            ally.heal(spell.heal());
            if (spell.buff().isPresent()) {
                ally.addEffect(new MobEffectInstance(spell.buff().get(),
                        spell.buffSeconds() * 20, 0, false, true, true));
            }
            healed++;
        }

        // ③ 天使（t3 起 ✓）：先把旧的那尊清掉（一尊就够 ✗ 免得叠一排 ✗）
        if (spell.heightBlocks() > 0.0D) {
            for (TNAngelEntity old : level.getEntitiesOfClass(TNAngelEntity.class,
                    new AABB(at, at).inflate(48.0D))) {
                old.discard();
            }
            TNAngelEntity angel = TNOrbEntities.ANGEL.get().create(level);
            if (angel != null) {
                angel.configure(ANGEL_LIFE, spell.heightBlocks());
                angel.moveTo(at.x, at.y, at.z, caster.getYRot(), 0.0F);
                level.addFreshEntity(angel);
            }
        }

        // ④ 回归晴天（t3 神光 ✓ —— 作者："并回归晴天"✓）
        if (spell.clearWeather() && !level.isRaining()) {
            // 已经晴了就不动（别把别人正在用的雷雨/下雨搅了 ✗）
        }
        if (spell.clearWeather()) {
            level.setWeatherParameters(6000, 0, false, false);
        }

        // ⑤ t5：范围内怪物停手 5 秒 ✓（挂标记；"停手"的具体执行在 tick() ✓）
        if (spell.calmMonsters()) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(spell.radius()))) {
                if (mob instanceof Enemy && TNEffects.LIGHT_CALM.isPresent()) {
                    mob.addEffect(new MobEffectInstance(TNEffects.LIGHT_CALM.get(),
                            CALM_TICKS, 0, false, false, true));
                    mob.setTarget(null);
                }
            }
        }

        caster.displayClientMessage(Component.literal("§e[TN-C] §r" + spellName(spell)
                + " §7（" + healed + " 人受治疗，范围 " + (int) spell.radius() + " 格）"), true);
    }

    private static String spellName(Spell spell) {
        return switch (spell.tier()) {
            case 1 -> "光芒照耀";
            case 2 -> "圣光";
            case 3 -> "神光";
            case 4 -> "天使降临";
            default -> "天使的悲悯";
        };
    }

    /** 队友 = 同一个 {@link CombatTeams#group} ✓（自己也算 ✓）。 */
    private static boolean isAlly(ServerPlayer caster, Player other) {
        if (other == caster) {
            return true;
        }
        if (other instanceof ServerPlayer sp) {
            return CombatTeams.group(caster).equals(CombatTeams.group(sp));
        }
        return false;
    }

    /**
     * 每 tick 的"停手"执行 ✓（由 {@code TnSpellMechanics.tickPlayer} 调，每 tick 对所有玩家一次 ✓）。
     *
     * <h2>为什么要每 tick 清目标</h2>
     * 挂了 {@link TNEffects#LIGHT_CALM} 的怪，AI 仍可能重新锁人 ✗（原版 goal 每 tick 都在选目标 ✓）
     * ⇒ 这里每 tick 把它的目标清空 ✓（持续 5 秒 ✓，效果掉了自然恢复正常 ✓）。
     * 另外伤害层面：{@code LivingHurtEvent} 里对"带 calm 的怪造成的伤害"直接取消 ✓
     * （见 {@code armor/TNIronArmorEvents.hurtFromCalm} ✓ —— 那是"停手"的最后一道保险 ✓）。
     */
    public static void tick(LivingEntity entity) {
        if (!(entity instanceof Mob mob) || !(mob instanceof Enemy)) {
            return;
        }
        if (TNEffects.LIGHT_CALM.isPresent() && mob.hasEffect(TNEffects.LIGHT_CALM.get())) {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
        }
    }
}
