package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 燃烧线（火系第 3 条链）的<b>效果</b>与<b>机制</b>。
 *
 * <h2>这条链 = 以伤换伤</h2>
 * 作者 2026-10-11 整条重做：「燎肤 → 灼脉 → 焚元 → 烬魂 → 殉命」，
 * 五档都是<b>给自己上 buff</b>：火系法术伤害提高，代价是<b>自身受到的一切伤害提高</b>。
 *
 * <h2>两半数值（本类顶部两张表就是全部）</h2>
 * <table border="1">
 *   <tr><th>档</th><th>效果 id</th><th>火系法术伤害</th><th>自身受到伤害</th></tr>
 *   <tr><td>1 燎肤</td><td>{@code fire_aspect}</td><td>+10%</td><td>+10%</td></tr>
 *   <tr><td>2 灼脉</td><td>{@code ember_burn}</td><td>+25%</td><td>+20%</td></tr>
 *   <tr><td>3 焚元</td><td>{@code blaze_burn}</td><td>+75%</td><td>+35%</td></tr>
 *   <tr><td>4 烬魂</td><td>{@code inferno_burn}</td><td>+150%</td><td>+55%</td></tr>
 *   <tr><td>5 殉命</td><td>{@code total_burn}</td><td>+200%</td><td>+80%</td></tr>
 * </table>
 *
 * <p>火伤走 {@code spell_power:fire} 属性（{@link TNEffects#fireScales} ——
 * 作者要求"加成只加火系" ⇒ 只挂这一个学派 ✓）；
 * 受伤<b>没有原版属性可用</b>（1.20.1 没有"受到伤害"这个 Attribute），
 * 所以在 {@link LivingHurtEvent} 里直接把最终伤害乘上去 ✓。
 *
 * <h2>旧的三套机制按作者要求<b>全部移除</b>（2026-10-11）</h2>
 * <ul>
 *   <li><b>燃血</b>（每秒扣最大生命 1/2/3%、永不致死）—— 代价已经改成"受伤 ↑"，燃血成了重复扣血 ✗</li>
 *   <li><b>原地复活</b>（中级 15s / 高级 20s 内死亡 ⇒ 取消死亡 + 回满血）——
 *       新描述 t4 写「稍有不慎便<b>魂飞魄散</b>」⇒ 这条链不该保命 ✗</li>
 *   <li><b>完全燃烧的血锁 1 + 无敌 + 结束回半血</b> —— 与 t5「自身受到伤害<b>极大幅</b>提高」直接打架 ✗</li>
 * </ul>
 * <p>⚠️ <b>效果 id 一个没改</b>（{@code tnc:fire_aspect} … {@code tnc:total_burn}）：
 * KubeJS 那 5 个法术 json 按 id 引用效果（`modpack/.../kubejs/data/tnc/spells/`），
 * 改 id 就得连数据包一起改 ✗。这次改的只有<b>显示名</b>（{@link SpellCatalog}）与<b>机制</b>。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNFireMechanics {

    public static final DeferredRegister<MobEffect> FIRE_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TNMod.MODID);

    // ---------------- 效果 ----------------

    /**
     * 火系燃烧线的五个效果 —— 加 {@code spell_power:fire} ✓。
     *
     * <p>实现搬到了 {@link TNEffects#fireScales}（原来这里是私有嵌套类）：暗系第二条链
     * 「以伤换伤 · 献祭」要用**同一套实现**加 {@code spell_power:soul} ✓
     * —— 两条链只差"加哪个学派"，代码不该写两份 ✗。
     * 效果 id 与火伤数值**一个字没变** ✓（`tnc:fire_aspect` 等五个）。
     */
    /** 燎肤 火伤 +10%。 */
    public static final RegistryObject<MobEffect> FIRE_ASPECT = TNEffects.fireScales("fire_aspect", 0.10D);
    /** 灼脉 火伤 +25%。 */
    public static final RegistryObject<MobEffect> EMBER_BURN = TNEffects.fireScales("ember_burn", 0.25D);
    /** 焚元 火伤 +75%。 */
    public static final RegistryObject<MobEffect> BLAZE_BURN = TNEffects.fireScales("blaze_burn", 0.75D);
    /** 烬魂 火伤 +150%。 */
    public static final RegistryObject<MobEffect> INFERNO_BURN = TNEffects.fireScales("inferno_burn", 1.50D);
    /** 殉命 火伤 +200%。 */
    public static final RegistryObject<MobEffect> TOTAL_BURN = TNEffects.fireScales("total_burn", 2.00D);

    private TNFireMechanics() {
    }

    /** 由 {@link TNEffects#register} 调用（模组构造期注册，不能晚）。 */
    public static void register(IEventBus modEventBus) {
        FIRE_EFFECTS.register(modEventBus);
    }

    // ---------------- 数值 ----------------

    /**
     * 自身<b>受到伤害</b>的加成，和上面的五个效果一一对应（下标 0 = 燎肤）。
     *
     * <p>"中度 / 大幅 / 极大 / 极大幅"这四档按作者给的定位落成 20% / 35% / 55% / 80% ✓。
     */
    private static final double[] TAKEN = {0.10D, 0.20D, 0.35D, 0.55D, 0.80D};

    /**
     * 这个玩家现在"受伤加成"多少（0 = 没挂任何一档）。
     *
     * <p>取<b>身上最高的那一档</b>，<b>不叠加</b> —— 和火伤那半边（属性修饰符按 base 加算）
     * 保持一致的直觉：两档同时挂着时，只算高的那档的代价 ✓。
     */
    static double takenPercent(ServerPlayer player) {
        if (has(player, TOTAL_BURN)) {
            return TAKEN[4];
        }
        if (has(player, INFERNO_BURN)) {
            return TAKEN[3];
        }
        if (has(player, BLAZE_BURN)) {
            return TAKEN[2];
        }
        if (has(player, EMBER_BURN)) {
            return TAKEN[1];
        }
        if (has(player, FIRE_ASPECT)) {
            return TAKEN[0];
        }
        return 0.0D;
    }

    /**
     * 代价那一半：挂着一档燃烧时，<b>挨的所有伤害都放大</b>。
     *
     * <p>为什么不用属性修饰符：1.20.1 没有"受到伤害"这个 Attribute（`generic.armor` 是护甲值、
     * `generic.armor_toughness` 是韧性，都不是乘区）⇒ 只能在伤害事件里乘 ✓。
     *
     * <p>{@link LivingHurtEvent} 拿到的 {@code amount} 已经是<b>过完护甲/抗性</b>的最终伤害，
     * 所以"受伤 +35%"对轻甲和重甲是同一个 35% ✓（这比挂在护甲前更符合"身体更脆"的直觉）。
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0F) {
            return;
        }
        double percent = takenPercent(player);
        if (percent <= 0.0D) {
            return;
        }
        event.setAmount(event.getAmount() * (float) (1.0D + percent));
    }

    private static boolean has(ServerPlayer player, RegistryObject<MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }

    /** 这个玩家现在"受到伤害 +多少"（命令/自检用）。 */
    public static double takenBonusFor(ServerPlayer player) {
        return takenPercent(player);
    }

    /** 效果是否在身（命令/调试用）。 */
    public static boolean hasEffect(ServerPlayer player, String path) {
        for (RegistryObject<MobEffect> effect : FIRE_EFFECTS.getEntries()) {
            if (effect.getId().getPath().equals(path) && has(player, effect)) {
                return true;
            }
        }
        return false;
    }

    /** 供自检引用：5 个效果都注册上了吗。 */
    public static int registeredCount() {
        return FIRE_EFFECTS.getEntries().size();
    }
}
