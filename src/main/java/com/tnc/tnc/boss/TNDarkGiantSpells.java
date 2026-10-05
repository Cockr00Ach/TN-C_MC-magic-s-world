package com.tnc.tnc.boss;

import com.tnc.tnc.dark.TNDarkSummonChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_power.api.SpellPower;

import java.util.Locale;

/**
 * <b>巨兽人领主的招式表</b> ✓ —— 作者 2026-10-03："这个 boss 会放<b>暗龙的 t5</b> 和
 * <b>召唤的 t4t5</b> 和<b>手的 t3t4</b>" ✓；
 * ★ 2026-10-04 作者："<b>把龙法术都删了吧，包括光龙和暗龙</b>" ✗ ⇒ 暗龙那一招已删 ✓（现在四招 ✓）。
 *
 * <h2>两条链分别怎么放（关键区别 ✗）</h2>
 * <ul>
 *   <li><b>召唤</b>：本模组自己写的链 ✓，入口就是
 *       {@link TNDarkSummonChain#onSpellCast}，
 *       施法者直接传**这只 boss** ✓（和阿波罗放光系那两条链一模一样 ✓）——
 *       玩家放 t5 长什么样，boss 放就长什么样 ✓（同一份代码、同一张数值表 ✓）；</li>
 *   <li><b>手</b>：这几招是**引擎的纯数据法术** ✗（{@code release.target.type = PROJECTILE} ＋
 *       {@code client_data.model = tnc:projectile/dark_hand} ✓，没有本模组的链代码 ✓），
 *       而引擎的施法入口 {@code performSpell/attemptCasting} **只认玩家** ✗（{@code Player} 参数 ✓）。
 *       ⇒ 这里走引擎**自己的发射函数** {@link SpellHelper#shootProjectile} ✓：
 *       它自己造 {@code SpellProjectile}、自己按 json 里的 {@code launch_properties} 发射 ✓，
 *       而且第一个参数就是 {@code LivingEntity} ✓（怪物也能用 ✓）。
 *       也就是说：手 t3/t4 的飞行、追踪（{@code homing_angle}）、命中伤害/致盲
 *       **全部照 json 里的原样走** ✓，我没有另写一套 ✗。</li>
 * </ul>
 *
 * <p>★ 档位是按法术 json 里的 {@code learn.tier} 核过的 ✓（不是按模型大小猜的 ✗）：
 * 手 t3 = {@code night_embrace} ✓、手 t4 = {@code black_ruin} ✓
 * （{@code slay_light} 才是 t5 ✗ 别搞混 ✓）。
 */
public final class TNDarkGiantSpells {

    private static final int CHAIN_SUMMON = 0;
    private static final int CHAIN_HAND = 1;

    /**
     * 一招 ✓。
     *
     * @param path      法术 id 的 path（{@code tnc:<path>} ✓）
     * @param tier      作者的档位要求 ✓（测试会拿法术 json 里的 {@code learn.tier} 核对 ✓）
     * @param chain     走哪条链（上面三个常数 ✓）
     * @param castTicks 起手到生效多少 tick ✓（= 作者那段施法动画的长度附近 ✓，动画播完才出招 ✓）
     * @param weight    抽签权重 ✓（越大越常放 ✓）
     * @param name      日志/摘要用的名字 ✓
     */
    public record Move(String path, int tier, int chain, int castTicks, int weight, String name) {
    }

    /**
     * ★ 作者 2026-10-03 点名的招 ✓（召唤 t4/t5 · 手 t3/t4 ✓）。
     *
     * <p>★ 2026-10-04 作者："<b>把龙法术都删了吧，包括光龙和暗龙</b>" ✗ ⇒
     * 原来这里还有一招 {@code dark_dragon_descend}（暗龙 t5 ✓）—— **已删** ✓，龙法术整套没了 ✓。
     */
    private static final Move[] MOVES = {
            new Move("dark_king", 4, CHAIN_SUMMON, 50, 2, "暗之国王（召唤 t4）"),
            new Move("evil_god", 5, CHAIN_SUMMON, 56, 2, "邪神（召唤 t5）"),
            new Move("night_embrace", 3, CHAIN_HAND, 40, 3, "夜之拥抱（手 t3）"),
            new Move("black_ruin", 4, CHAIN_HAND, 44, 3, "黑蚀（手 t4）"),
    };

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/boss");

    private TNDarkGiantSpells() {
    }

    public static int count() {
        return MOVES.length;
    }

    public static Move move(int index) {
        return MOVES[Math.max(0, Math.min(MOVES.length - 1, index))];
    }

    /** 按权重随机挑一招 ✓。 */
    public static int pick(net.minecraft.util.RandomSource random) {
        int total = 0;
        for (Move m : MOVES) {
            total += m.weight();
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < MOVES.length; i++) {
            roll -= MOVES[i].weight();
            if (roll < 0) {
                return i;
            }
        }
        return 0;
    }

    /** 让 boss 放第 {@code index} 招 ✓（服务端 ✓）。 */
    public static boolean cast(LivingEntity caster, int index) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return false;
        }
        Move move = move(index);
        boolean ok = switch (move.chain()) {
            case CHAIN_SUMMON -> {
                TNDarkSummonChain.onSpellCast(caster, move.path());
                yield true;
            }
            default -> castHand(level, caster, move.path());
        };
        LOGGER.info("TN-C/boss: 巨兽人领主出招 {} path={} 成功={} phase={}",
                move.name(), move.path(), ok,
                caster instanceof TNDarkGiantEntity giant ? giant.phase() : -1);
        return ok;
    }

    /**
     * ★ 手（引擎的纯数据投射物法术 ✓）：走引擎自己的 {@link SpellHelper#shootProjectile} ✓
     * —— 它会自己造投射物、按 json 发射、并且把**施法者**（这只 boss ✓）记成 owner ✓，
     * 所以命中伤害算的是 boss 自己的 {@code spell_power:soul} ✓。
     */
    private static boolean castHand(ServerLevel level, LivingEntity caster, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("tnc", path);
        Spell spell = SpellRegistry.getSpell(id);
        if (spell == null) {
            LOGGER.warn("TN-C/boss: 引擎里没有法术 {} ⇒ 这一招跳过 ✗", id);
            return false;
        }
        if (spell.release == null || spell.release.target == null
                || spell.release.target.projectile == null) {
            LOGGER.warn("TN-C/boss: 法术 {} 不是投射物法术 ⇒ 这一招跳过 ✗", id);
            return false;
        }
        // 施法者自己的法强（soul 系 ✓）—— 引擎判伤害时用的就是这个 ✓
        SpellPower.Result power = SpellPower.getSpellPower(spell.school, caster);
        Vec3 at = caster.getEyePosition();
        SpellHelper.ImpactContext context = new SpellHelper.ImpactContext()
                .position(at)
                .power(power);
        // 手会追踪（json 里 homing_angle=0.5 ✓）⇒ 把 boss 当前的目标交给引擎让它追 ✓
        LivingEntity target = caster instanceof Mob mob ? mob.getTarget() : null;
        SpellHelper.shootProjectile(level, caster, target, new SpellInfo(spell, id), context, 0);
        return true;
    }

    /** 供命令/日志看的摘要 ✓。 */
    public static String describe() {
        StringBuilder sb = new StringBuilder("巨兽人领主招式：");
        for (Move m : MOVES) {
            sb.append(String.format(Locale.ROOT, "%n  %s 起手%dt 权重%d", m.name(), m.castTicks(), m.weight()));
        }
        return sb.toString();
    }

    /** 给测试用：五招的 (path, tier) ✓（拿法术 json 对账 ✓）。 */
    public static String[] paths() {
        String[] out = new String[MOVES.length];
        for (int i = 0; i < MOVES.length; i++) {
            out[i] = MOVES[i].path();
        }
        return out;
    }

    public static int tierOf(String path) {
        for (Move m : MOVES) {
            if (m.path().equals(path)) {
                return m.tier();
            }
        }
        return -1;
    }
}
