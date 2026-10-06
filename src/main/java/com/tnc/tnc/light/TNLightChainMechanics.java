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
    //  ★ 飞行已经并进光耀链（作者 2026-10-02："把光魔法的飞行链删去，加入到光耀里，
    //    释放光耀法术就获得飞行" ✓）—— 原来那条"光翼链"（light_flight /
    //    light_swift_flight / light_wingspan）整个删掉了 ✓
    //    ⇒ 现在判"能不能飞"靠 {@link #FLIGHT_UNTIL} 那张**显式记账**表 ✓，
    //      飞行时长 = 那一次法术的 buff 时长（12 / 14 / 16 / 18 / 20 秒 ✓），
    //      顺带把玩家背后的光翼也一起点亮（标记 LIGHT_WINGS ✓ 客户端画翅膀那套没变 ✓）
    //   ★ 2026-10-10（作者："在释放光耀的时候，在范围内的玩家都可以获得翅膀，t4 开始"）：
    //      范围内的**其他玩家**在 **t4（angel_descent）/ t5（angel_mercy）** 也会被点名 ✓，
    //      t1~t3 的队友只吃回血 + 减伤 buff、不飞 ✓。
    // ------------------------------------------------------------------

    /** 光翼标记的续期（tick）：比 buff 多留一点点，避免"buff 还在、翅膀先掉"✓。 */
    private static final int WING_MARKER_EXTRA = 20;

    // ------------------------------------------------------------------
    //  召唤天使链（第四条链，作者 2026-10-02）—— 会飞的战斗天使 ✓
    // ------------------------------------------------------------------

    /**
     * 召唤天使链的一档：法术 / 档位 / 召唤几只 / **个头** / 血 / 伤害 / 活多久（秒） / 名字 ✓。
     *
     * <p>★ 作者 2026-10-02 改的第二版：<b>"t1 一只小小的，t2 三只，t3 五只现在这样的，
     * t4 十五只，t5 三只大只的"</b> ✓ ⇒ 只数 <b>1 / 3 / 5 / 15 / 3</b> ✓，
     * 并且**个头不再等于档位** ✗（t4 是十五只小的、t5 才是大的 ✓）⇒ 这里单独写一列 ✓。
     */
    private record Summon(String path, int tier, int count, double scale, double health,
                          double damage, int seconds, String name) {
    }

    /**
     * 数值表 ✓ —— 要调就调这一张表（一档一行）✓。
     *
     * <p>个头（{@link Summon#scale}，1.0 = 模型原尺寸 2.44 格 ✓）：
     * t1 <b>0.45</b>（小小 ✓）/ t2 0.70 / t3 <b>1.05</b>（= "现在这样的" ✓）/
     * t4 <b>0.75</b>（十五只 ⇒ 做成一群小的，不然满屏都是三格高的天使 ✗，作者要改就改这个数 ✓）/
     * t5 <b>1.80</b>（大只的 ✓）。
     */
    private static final Summon[] SUMMONS = {
            new Summon("summon_angel", 1, 1, 0.45D, 50.0D, 7.0D, 30, "召唤天使"),
            new Summon("angel_twins", 2, 3, 0.70D, 70.0D, 10.0D, 30, "天使卫队"),
            new Summon("angel_legion", 3, 5, 1.05D, 120.0D, 14.0D, 40, "天使军团"),
            new Summon("seraph_descent", 4, 15, 0.75D, 60.0D, 8.0D, 45, "炽天使降临"),
            new Summon("archangel", 5, 3, 1.80D, 260.0D, 26.0D, 60, "大天使长"),
    };

    /** 同一个人身上最多留几只天使 ✓（t4 一次就是十五只 ⇒ 上限必须 ≥ 15 ✓，超了送走最老的 ✓）。 */
    private static final int SUMMON_CAP = 20;
    /** 召唤出来的天使落在主人周围多大半径的圈上 ✓。 */
    private static final double SUMMON_RING = 2.4D;
    /** 一圈最多挤几只 ✓ —— 满了就往外再来一圈 ✓（t4 十五只要两圈 ✓）。 */
    private static final int SUMMON_PER_RING = 8;
    /** 招呼唤物时那一下白光的亮度 ✓。 */
    private static final int SUMMON_FLASH_PARTICLES = 60;

    private static Summon findSummon(String path) {
        for (Summon summon : SUMMONS) {
            if (summon.path().equals(path)) {
                return summon;
            }
        }
        return null;
    }

    private TNLightChainMechanics() {
    }

    /** 这个法术是不是本文件的（光耀 / 召唤天使 ✓）—— ★ 光龙链 2026-10-04 已按作者要求删除 ✗。 */
    public static boolean isLightChainSpell(String path) {
        return find(path) != null || findSummon(path) != null;
    }

    // ★ 2026-10-10：原来这里有一个 `graceBuffed(player)`（"身上有光耀链任一条 buff 就算能飞"）
    //   —— **已删除** ✗。因为光耀是**范围法术**，范围内的队友也会拿到那个 buff，
    //   用它当判据会让队友**自己给自己发飞行**，绕开"t4 才开始给队友翅膀"这条规则 ✗。
    //   现在唯一判据是 {@link #FLIGHT_UNTIL}（只有 {@link #grantGraceFlight} 点过名的才有）✓。

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
     * 释放时调用（玩家走 {@code TnSpellMechanics} 的 SPELL_CAST 钩子 ✓）。
     *
     * <p>顺序：法阵 → 治疗/挂 buff → 天使 → 转晴 → 怪物停手 ✓（先铺阵再治疗，观感上"光先落地"✓）。
     *
     * <p>★ 作者 2026-10-01："新做了一个阿波罗 boss…就跟公孙衍迷失一样，能放玩家的法术" ✓
     * ⇒ 施法者放宽到 {@link LivingEntity} ✓（阿波罗在自己 {@code aiStep} 里直接调 ✓）；
     * ★ 作者 2026-10-02："释放光耀法术就获得飞行" ✓ ⇒ 光耀链**每一档**都会给玩家飞行 ✓
     * （见中段那次 {@code grantGraceFlight} ✓；怪物施法当然不给 ✗）。
     */
    public static void onSpellCast(LivingEntity caster, String path) {
        Spell spell = find(path);
        if (spell == null) {
            // ★ 召唤天使链（第四条链 ✓）：和光耀不一样 —— 它不治疗、不铺大阵，
            //   只把"会飞的战斗天使"放到主人身边 ✓（见 castSummon ✓）
            Summon summon = findSummon(path);
            if (summon != null) {
                castSummon(caster, summon);
                return;
            }
            // ★ 光龙链 2026-10-04 按作者要求**整条删了** ✗（"把龙法术都删了吧，包括光龙和暗龙" ✓）
            //   ⇒ 这里不再派发 ✓（法术 json / 法杖池条目也都没了 ✓，
            //      Java 那条链的代码还留着但**没有任何入口** ✓ —— 要不要连代码+模型+贴图一起删，等作者点头 ✓）
            return;
        }
        ServerLevel level = (ServerLevel) caster.level();
        Vec3 at = caster.position();
        boolean playerCaster = caster instanceof ServerPlayer;

        // ① 法阵：半径 = 法术范围 ✓（作者："范围多大法阵多大"✓），用**光系**那张纯白贴图 ✓
        TNMagicCircleEntity circle = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
        if (circle != null) {
            circle.configure(spell.radius(), CIRCLE_LIFE, TNMagicCircleEntity.STYLE_LIGHT);
            circle.moveTo(at.x, at.y + 0.04D, at.z, 0.0F, 0.0F);
            level.addFreshEntity(circle);
        }

        // ② 队友：立刻回血 + 挂减伤 buff（施法者自己一定算 ✓）
        //    ★ 作者 2026-10-01："新做了一个阿波罗 boss…就跟公孙衍迷失一样，能放玩家的法术" ✓
        //    ⇒ 施法者不再限定玩家 ✗：**怪物施法时就只有它自己吃到**（它没有队伍 ✗），
        //       玩家施法时照旧连队友一起 ✓。
        int healed = 0;
        caster.heal(spell.heal());
        if (spell.buff().isPresent()) {
            caster.addEffect(new MobEffectInstance(spell.buff().get(),
                    spell.buffSeconds() * 20, 0, false, true, true));
        }
        healed++;
        // ★ 作者 2026-10-02："释放光耀法术就获得飞行" ✓ —— 光耀**每一档**都给玩家飞行 ✓
        //   （时长 = 这一次的 buff 时长 ✓；随后每 tick 由 tickGraceFlight 续/收 ✓）
        if (caster instanceof ServerPlayer self) {
            grantGraceFlight(self, spell.buffSeconds());
        }
        if (playerCaster) {
            List<Player> allies = level.getEntitiesOfClass(Player.class,
                    new AABB(at, at).inflate(spell.radius()));
            for (Player ally : allies) {
                if (ally == caster || !isAlly(caster, ally)) {
                    continue;
                }
                ally.heal(spell.heal());
                if (spell.buff().isPresent()) {
                    ally.addEffect(new MobEffectInstance(spell.buff().get(),
                            spell.buffSeconds() * 20, 0, false, true, true));
                }
                // ★ 队友也一起飞 ✓ —— **但只从 t4 开始** ✓
                //   作者 2026-10-10："在释放光耀的时候，在范围内的玩家都可以获得翅膀，**t4 开始**"
                //   （原来是不分档位地给 ✗）。t1~t3 的队友只吃回血 + 减伤 buff，不飞 ✓。
                if (spell.tier() >= ALLY_FLIGHT_FROM_TIER && ally instanceof ServerPlayer mate) {
                    grantGraceFlight(mate, spell.buffSeconds());
                }
                healed++;
            }
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
        //    ★ 只对**玩家施法**生效 ✓ —— Boss 放这招时把它自己的小怪也"劝停"很怪 ✗
        if (spell.calmMonsters() && playerCaster) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(spell.radius()))) {
                if (mob instanceof Enemy && TNEffects.LIGHT_CALM.isPresent()) {
                    mob.addEffect(new MobEffectInstance(TNEffects.LIGHT_CALM.get(),
                            CALM_TICKS, 0, false, false, true));
                    mob.setTarget(null);
                }
            }
        }

        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§e[TN-C] §r" + spellName(spell)
                    + " §7（" + healed + " 人受治疗，范围 " + (int) spell.radius()
                    + " 格 · " + spell.buffSeconds() + " 秒内可飞行 ✓）"), true);
        }
        LOGGER.info("TN-C/light: 光耀 {} radius={} heal={} caster={}",
                spell.path(), spell.radius(), healed, caster.getName().getString());
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

    /**
     * <b>召唤天使链（光系第四条链）释放</b> ✓ —— 主人身边一圈白光里落下 N 只<b>会飞的</b>战斗天使 ✓。
     *
     * <h2>为什么天使不用 JSON 的 SPAWN 动作生成 ✗</h2>
     * 引擎的 {@code SPAWN} 只能指定实体类型 ✗ —— 而天使的<b>档位 / 主人 / 寿命 / 血伤</b>都要现写 ✗。
     * 所以和其它三条光链一样：JSON 只管表演，实体由这里生成 ✓（见 {@link #summonOne} ✓）。
     *
     * <h2>叠放规则（作者没规定，我定的 —— 只改这几个常量就行 ✓）</h2>
     * <ul>
     *   <li><b>同档覆盖</b>：再放一次 t3 ⇒ 先把上一批 t3 撤掉 ✓（不然连点两下变 6 只 ✗）；</li>
     *   <li><b>总数上限</b> {@link #SUMMON_CAP} 只 ✓：位置不够就把<b>最老的</b>送走 ✓（按实体 id 排 ✓）；</li>
     *   <li>不同档的天使可以共存 ✓（放完 t5 再放 t1，两只一起飞 ✓ —— 这正是"链"该有的样子 ✓）。</li>
     * </ul>
     */
    private static void castSummon(LivingEntity caster, Summon summon) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = caster.position();
        List<TNFightingAngelEntity> mine = level.getEntitiesOfClass(TNFightingAngelEntity.class,
                new AABB(at, at).inflate(64.0D),
                a -> caster.getUUID().equals(a.owner()));

        // ① 同档覆盖 ✓
        for (TNFightingAngelEntity old : mine) {
            if (old.tier() == summon.tier()) {
                old.discard();
            }
        }
        // ② 总数上限 ✓：不够位置就把最老的（实体 id 最小 = 先出生的 ✓）送走 ✓
        List<TNFightingAngelEntity> others = new java.util.ArrayList<>();
        for (TNFightingAngelEntity old : mine) {
            if (old.isAlive() && old.tier() != summon.tier()) {
                others.add(old);
            }
        }
        others.sort(java.util.Comparator.comparingInt(TNFightingAngelEntity::getId));
        int free = SUMMON_CAP - others.size();
        int index = 0;
        while (free < summon.count() && index < others.size()) {
            others.get(index++).discard();
            free++;
        }
        int toSpawn = Math.min(summon.count(), Math.max(0, free));

        // ③ 在主人周围一圈放下来 ✓（角度跟着朝向转，天使落在你面前而不是背后 ✓）
        //    ★ t4 一次十五只 ⇒ 一圈八个放不下 ✗：满八个就往外再铺一圈 ✓（见 ring 那两行 ✓）
        for (int i = 0; i < toSpawn; i++) {
            int ring = i / SUMMON_PER_RING;
            int inRing = Math.min(SUMMON_PER_RING, toSpawn - ring * SUMMON_PER_RING);
            int indexInRing = i - ring * SUMMON_PER_RING;
            double radius = SUMMON_RING * (1.0D + ring * 0.9D);
            double angle = Math.toRadians(caster.getYRot())
                    + (Math.PI * 2.0D * indexInRing) / Math.max(1, inRing);
            summonOne(level, caster, summon,
                    caster.getX() + Math.cos(angle) * radius,
                    caster.getY() + 1.0D,
                    caster.getZ() + Math.sin(angle) * radius);
        }

        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§e[TN-C] §r" + summon.name()
                    + " §7（" + toSpawn + " 只 · " + summon.seconds() + " 秒 · "
                    + (int) summon.health() + " 血 / " + (int) summon.damage() + " 伤 · 会轮流放光线 ✓）"), true);
        }
        LOGGER.info("TN-C/light: 召唤天使 {} count={} tier={} caster={}",
                summon.path(), toSpawn, summon.tier(), caster.getName().getString());
    }

    /** 放下一只天使 ✓：档位 / 主人 / 寿命 / 血伤全在这里写进去 ✓。 */
    private static void summonOne(ServerLevel level, LivingEntity caster, Summon summon,
                                  double x, double y, double z) {
        TNFightingAngelEntity angel = TNOrbEntities.FIGHTING_ANGEL.get().create(level);
        if (angel == null) {
            return;
        }
        angel.setTier(summon.tier());
        angel.setScale(summon.scale());            // ★ 个头由法术定 ✓（不再等于档位 ✗）
        angel.setOwner(caster.getUUID());
        angel.setLifetime(summon.seconds() * 20);
        angel.moveTo(x, y, z, caster.getYRot(), 0.0F);
        // 档位 → 血量/伤害 ✓（实体自身的基准值只是兜底 ✓；血要先 setBaseValue 再 setHealth ✗，
        // 反过来会被钳到旧上限 ✗）
        if (angel.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null) {
            angel.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                    .setBaseValue(summon.health());
            angel.setHealth((float) summon.health());
        }
        if (angel.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null) {
            angel.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    .setBaseValue(summon.damage());
        }
        level.addFreshEntity(angel);
        // 出场白光 ✓（金 + 白，和光耀链一个色系 ✓）
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                x, y + 1.0D, z, SUMMON_FLASH_PARTICLES, 0.4D, 0.7D, 0.4D, 0.05D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                x, y + 1.0D, z, 24, 0.3D, 0.5D, 0.3D, 0.08D);
    }

    /** 队友 = 同一个 {@link CombatTeams#group} ✓（自己也算 ✓）；怪物施法没有队伍 ⇒ 只算自己 ✓。 */
    private static boolean isAlly(LivingEntity caster, Player other) {
        if (other == caster) {
            return true;
        }
        if (caster instanceof ServerPlayer sp && other instanceof ServerPlayer osp) {
            return CombatTeams.group(sp).equals(CombatTeams.group(osp));
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
    //  ★ 光耀链自带的飞行（原来那条"光翼链"删掉了 ✓，作者 2026-10-02）
    // ------------------------------------------------------------------

    /**
     * ★★ <b>"谁有资格飞"到什么时候</b>（tick）—— 光耀的赐飞是**限时**的 ✓。
     *
     * <p>为什么不能靠"身上有没有光耀 buff"来判断 ✗：光耀是**范围法术**，
     * 范围内的队友**也会拿到那个 buff** ✓ ⇒ 如果 {@code tickGraceFlight} 用 buff 判断，
     * 队友就会**自己给自己发飞行**，与"t4 才开始给队友翅膀"这条规则打架 ✗。
     * 所以资格改成**显式记账**：只有 {@link #grantGraceFlight} 点过名的玩家才有 ✓，
     * 到点自动收回 ✓。
     */
    private static final java.util.Map<java.util.UUID, Long> FLIGHT_UNTIL = new java.util.HashMap<>();

    /**
     * 从第几档开始，**范围内的其他玩家**也一起获得翅膀 ✓。
     *
     * <p>作者 2026-10-10：「在释放光耀的时候，**在范围内的玩家都可以获得翅膀，t4 开始**」✓
     */
    private static final int ALLY_FLIGHT_FROM_TIER = 4;

    /**
     * 光耀**释放那一刻**就给飞行 + 点亮光翼 ✓。
     *
     * <p>为什么不靠法术 JSON 里的 {@code STATUS_EFFECT}：那是引擎那条链 ✓（这里也留着，不冲突 ✓），
     * 但"能不能飞"必须由**我们自己的代码**保证 —— 作者实测的那次事故里，
     * 引擎把 buff 挂上了，而负责发飞行的事件根本没跑 ✗（见类注释的事故记录）。
     * 所以这里<b>自己给一遍</b>：{@code mayfly = true} + 光翼标记 + 记下资格到期时间 ✓。
     *
     * @param seconds 这一次的 buff 时长（飞行也跟着它走 ✓）
     */
    private static void grantGraceFlight(ServerPlayer player, int seconds) {
        FLIGHT_UNTIL.put(player.getUUID(), player.serverLevel().getGameTime() + seconds * 20L);
        if (TNEffects.LIGHT_WINGS.isPresent()) {
            // 光翼标记（客户端拿它画翅膀 ✓）：比 buff 多留 1 秒，避免"buff 还在、翅膀先掉"✗
            player.addEffect(new MobEffectInstance(TNEffects.LIGHT_WINGS.get(),
                    seconds * 20 + WING_MARKER_EXTRA, 0, false, false, false));
        }
        giveFlight(player, true);
        LOGGER.info("TN-C/light: 光耀给飞行 {} 秒", seconds);
    }

    /**
     * 每 tick 维持光耀给的飞行 ✓（由 {@code TnSpellMechanics.tickPlayer} 调用 —— <b>那条路已证实活着</b> ✓）。
     *
     * <ul>
     *   <li>{@link #FLIGHT_UNTIL} 还没到点 ⇒ 续光翼标记（快到期才续 ✗ 免得每 tick 发包）＋ 保证 {@code mayfly} ✓；</li>
     *   <li>到点了 ⇒ 收标记 ＋ 收回飞行 ✓（创造/旁观不碰 ✗ —— 那不是我们给的 ✓）。</li>
     * </ul>
     *
     * ★ 判据从"有没有光耀 buff"改成"**有没有被点过名**" ✓ ——
     *   否则范围内的队友会凭 buff 自己给自己发飞行，绕开"t4 开始"这条规则 ✗。
     */
    public static void tickGraceFlight(ServerPlayer player) {
        Long until = FLIGHT_UNTIL.get(player.getUUID());
        boolean entitled = until != null && player.serverLevel().getGameTime() < until;
        if (entitled) {
            if (TNEffects.LIGHT_WINGS.isPresent()) {
                // ★ 只在快到期时续（阈值 30 tick ✓）—— 每 tick 都 addEffect 会每 tick 发一次
                //   同步包 ✗（600 包/半分钟），纯浪费 ✓
                MobEffectInstance marker = player.getEffect(TNEffects.LIGHT_WINGS.get());
                if (marker == null || marker.getDuration() < 30) {
                    player.addEffect(new MobEffectInstance(TNEffects.LIGHT_WINGS.get(),
                            WING_MARKER_EXTRA + 20, 0, false, false, false));
                }
            }
            giveFlight(player, true);
        } else {
            if (until != null) {
                FLIGHT_UNTIL.remove(player.getUUID());      // 到点 ⇒ 清账 ✓
            }
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
