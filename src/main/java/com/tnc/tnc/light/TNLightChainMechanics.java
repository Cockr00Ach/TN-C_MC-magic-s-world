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
 * <b>光系两条链</b>的全部机制 ✓ —— 光耀（治疗/减伤/天使）＋ 光翼（飞行）都在这一个文件里，
 * 因为两条链共用一个入口（{@code TnSpellMechanics} 的 SPELL_CAST 钩子 ✓）。
 *
 * <h2>光耀（第二条链）—— 作者 2026-10-01 定的五档</h2>
 * <table border="1">
 *   <tr><th>档</th><th>法术</th><th>范围</th><th>治疗</th><th>减伤</th><th>额外</th></tr>
 *   <tr><td>t1</td><td>光芒照耀</td><td>8</td><td>20</td><td>25%</td><td>地面法阵（半径＝范围）</td></tr>
 *   <tr><td>t2</td><td>圣光</td><td>10</td><td>30</td><td>50%</td><td>法阵</td></tr>
 *   <tr><td>t3</td><td>神光</td><td>12</td><td>30</td><td>50%</td><td>法阵中心召唤 4 格高天使（白/淡黄粒子）＋ <b>回归晴天</b></td></tr>
 *   <tr><td>t4</td><td>天使降临</td><td>14</td><td>30</td><td>70%</td><td>天使变 6 格高</td></tr>
 *   <tr><td>t5</td><td>天使的悲悯</td><td>16</td><td>30</td><td>70%</td><td>天使 10 格高 ＋ 范围内怪物<b>停手 5 秒</b></td></tr>
 * </table>
 *
 * <h2>光翼（第一条链）—— 作者 2026-10-01：使用期间展开光翼并可飞</h2>
 * <ul>
 *   <li>三档（t1 飞行 / t2 极速飞行 / t3 光翼展开）都在 {@link #WINGS} 表里 ✓；</li>
 *   <li>释放时就在这里把 buff 挂上（不靠 JSON 的 STATUS_EFFECT ✗，见下）＋ 立刻给
 *       {@code mayfly} ✓，并挂 {@link TNEffects#LIGHT_WINGS} 标记给客户端画翅膀 ✓；</li>
 *   <li>buff 到点后由 {@link #tickWings} 收回飞行 ✓（"能飞"和"翅膀"永远同时存在/同时消失 ✓）。</li>
 * </ul>
 *
 * <h2>★ 2026-10-01 事故：飞行链"既飞不了也看不到翅膀"的根因</h2>
 * 原来 {@code TNLightWingsEvents} 把逻辑挂在 {@code TickEvent.PlayerTickEvent} 上 ✗ ——
 * 而<b>这个事件在本仓库里是死的</b>（{@code TnSpellMechanics} 的注释里早有实机记录：
 * "PlayerTickEvent 在我们的 mod 上一个世界整局都没进来过" ✗）⇒
 * 光翼标记永远没人挂、{@code mayfly} 永远没人给 ⇒ 法术放得出来（引擎那边一切正常 ✓）却什么也不发生 ✗。
 * 现在改挂到**已证活着的**服务端 tick 路径上（{@code TnSpellMechanics.tickPlayer} ✓），
 * 并且在释放的那一刻就把状态给足 ✓（两条路互为保险 ✓）。
 *
 * <h2>设计要点（为什么这么做）</h2>
 * <ul>
 *   <li><b>法术 JSON 只负责表演</b>（起手粒子/动作/音效 ＋ 给施法者挂一个 buff ✓）✗；
 *       真正的"范围治疗 + 队友 buff + 法阵 + 天使 + 转晴 + 飞行"都在这里 ✓ ——
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

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/light");

    /** 每个档位的全部数值（作者给的表 ✓ —— 想调手感只改这里 ✓）。 */
    private record Spell(String path, int tier, double radius, int heal, double heightBlocks,
                         RegistryObject<MobEffect> buff, int buffSeconds, boolean clearWeather,
                         boolean calmMonsters) {
    }

    private static final Spell[] SPELLS = {
            // ★ 2026-10-01 作者："天使模型的高度变成现在的两倍" ✓ ⇒ 2/3/5 格 → 4/6/10 格 ✓
            //   （只动这一列就行 ✓ —— 渲染缩放、粒子范围都跟着 this 自动变大 ✓）
            new Spell("light_radiance", 1, 8.0D, 20, 0.0D, TNEffects.LIGHT_RADIANCE, 12, false, false),
            new Spell("holy_light", 2, 10.0D, 30, 0.0D, TNEffects.LIGHT_HOLY, 14, false, false),
            new Spell("divine_light", 3, 12.0D, 30, 4.0D, TNEffects.LIGHT_DIVINE, 16, true, false),
            new Spell("angel_descent", 4, 14.0D, 30, 6.0D, TNEffects.LIGHT_DESCENT, 18, false, false),
            new Spell("angel_mercy", 5, 16.0D, 30, 10.0D, TNEffects.LIGHT_MERCY, 20, false, true),
    };

    /** 法阵存在时长（tick）：光耀的阵留得久一点（作者要给玩家看清 ✓）。 */
    private static final int CIRCLE_LIFE = 220;
    /** 天使存在时长（tick）：15 秒 ✓（和 buff 时长接近，观感统一 ✓）。 */
    private static final int ANGEL_LIFE = 300;
    /** t5 怪物停手时长（tick）：作者指定 **5 秒** ✓。 */
    private static final int CALM_TICKS = 100;

    // ------------------------------------------------------------------
    //  光翼链（第一条链）—— 飞行 ✓
    // ------------------------------------------------------------------

    /**
     * 光翼链的一档：法术名 / 该挂的 buff / 时长（秒） / 聊天里叫什么 ✓。
     *
     * <p>时长＝作者设计文档给的建议值（t1 30s / t2 20s / t3 12s ✓），
     * <b>必须和法术 JSON 里的 {@code cooldown_duration} 配套</b> ✓（改这里也要改那边 ✓）。
     */
    private record Wing(String path, RegistryObject<MobEffect> buff, int seconds, String name) {
    }

    private static final Wing[] WINGS = {
            new Wing("light_flight", TNEffects.LIGHT_FLIGHT, 30, "飞行"),
            new Wing("light_swift_flight", TNEffects.LIGHT_SWIFT_FLIGHT, 20, "极速飞行"),
            new Wing("light_wingspan", TNEffects.LIGHT_WINGSPAN, 12, "光翼展开"),
    };

    private TNLightChainMechanics() {
    }

    /** 光翼标记的续期（tick）：比 buff 多留一点点，避免"buff 还在、翅膀先掉"✓。 */
    private static final int WING_MARKER_EXTRA = 20;

    private static Wing findWing(String path) {
        for (Wing wing : WINGS) {
            if (wing.path().equals(path)) {
                return wing;
            }
        }
        return null;
    }

    /** 这个法术是不是本文件的（两条链都算 ✓）。 */
    public static boolean isLightChainSpell(String path) {
        return find(path) != null || findWing(path) != null;
    }

    /** 玩家身上有没有光翼链的 buff（任一档 ✓）。 */
    private static boolean winged(ServerPlayer player) {
        return has(player, TNEffects.LIGHT_FLIGHT)
                || has(player, TNEffects.LIGHT_SWIFT_FLIGHT)
                || has(player, TNEffects.LIGHT_WINGSPAN);
    }

    private static boolean has(ServerPlayer player, RegistryObject<MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
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
        // ① 光翼链（飞行）另一条路 ✓ —— 先看它，别掉进光耀那张表 ✗
        Wing wing = findWing(path);
        if (wing != null) {
            castWings(caster, wing);
            return;
        }
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

    // ------------------------------------------------------------------
    //  光翼链：释放 + 每 tick 维持（两条链共用同一个入口文件 ✓）
    // ------------------------------------------------------------------

    /**
     * 光翼链释放：挂 buff + 挂光翼标记 + <b>立刻给飞行</b> ✓。
     *
     * <p>为什么不靠法术 JSON 里的 {@code STATUS_EFFECT}：那是引擎那条链 ✓（这里也留着，不冲突 ✓），
     * 但"能不能飞"必须由**我们自己的代码**保证 —— 作者实测的那次事故里，
     * 引擎把 buff 挂上了，而负责发飞行的事件根本没跑 ✗（见类注释的事故记录）。
     * 所以这里<b>自己挂一遍</b>：{@code apply_mode = SET} 的语义用 {@code addEffect} 覆盖即可 ✓。
     */
    private static void castWings(ServerPlayer caster, Wing wing) {
        if (!wing.buff().isPresent()) {
            LOGGER.warn("TN-C/light: 光翼 buff 没注册，{} 只给了飞行", wing.path());
        } else {
            caster.addEffect(new MobEffectInstance(wing.buff().get(),
                    wing.seconds() * 20, 0, false, true, true));
        }
        // 光翼标记（客户端拿它画翅膀 ✓）：比 buff 多留 1 秒，避免"buff 还在、翅膀先掉"✗
        if (TNEffects.LIGHT_WINGS.isPresent()) {
            caster.addEffect(new MobEffectInstance(TNEffects.LIGHT_WINGS.get(),
                    wing.seconds() * 20 + WING_MARKER_EXTRA, 0, false, false, false));
        }
        giveFlight(caster, true);
        caster.displayClientMessage(Component.literal("§b[TN-C] §r" + wing.name()
                + " §7（" + wing.seconds() + " 秒内可飞行：双击空格起飞）"), true);
        LOGGER.info("TN-C/light: 光翼 {} ticks={} mayfly=true", wing.path(), wing.seconds() * 20);
    }

    /**
     * 每 tick 维持光翼链的状态 ✓（由 {@code TnSpellMechanics.tickPlayer} 调用 —— <b>那条路已证实活着</b> ✓）。
     *
     * <ul>
     *   <li>有光翼 buff ⇒ 续标记 ＋ 保证 {@code mayfly} ✓；</li>
     *   <li>buff 掉了 ⇒ 收标记 ＋ 收回飞行 ✓（创造/旁观不碰 ✗ —— 那不是我们给的 ✓）。</li>
     * </ul>
     */
    public static void tickWings(ServerPlayer player) {
        boolean on = winged(player);
        if (on) {
            if (TNEffects.LIGHT_WINGS.isPresent()) {
                player.addEffect(new MobEffectInstance(TNEffects.LIGHT_WINGS.get(),
                        WING_MARKER_EXTRA + 20, 0, false, false, false));
            }
            giveFlight(player, true);
        } else {
            if (TNEffects.LIGHT_WINGS.isPresent() && player.hasEffect(TNEffects.LIGHT_WINGS.get())) {
                player.removeEffect(TNEffects.LIGHT_WINGS.get());
            }
            giveFlight(player, false);
        }
    }

    /** 给/收飞行 ✓（只在状态真的变了才发同步包 ✗ —— 每 tick 刷包会把客户端刷爆 ✗）。 */
    private static void giveFlight(ServerPlayer player, boolean fly) {
        if (fly == player.getAbilities().mayfly) {
            return;
        }
        if (!fly && (player.isCreative() || player.isSpectator())) {
            return;                                     // 创造/旁观的飞行不是我们给的 ✓
        }
        player.getAbilities().mayfly = fly;
        if (!fly) {
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
        LOGGER.info("TN-C/light: 光翼飞行 {}", fly ? "给上" : "收回");
    }
}
