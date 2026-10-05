package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 法术召唤物 / 环绕物的实体注册表（目前只有一颗：环绕雷球）。
 *
 * <p>为什么不塞进 {@code npc.TNNpcs}：那是**剧情 NPC** 的注册表（人形、有对话、有皮肤），
 * 这里是**法术产物**（无 AI、位置由机制层控制）✗ —— 两码事，别混。
 *
 * <p>没有刷怪蛋 ✗：这个实体必须绑主人（{@code TNThunderOrbEntity.bind}）才有意义，
 * 手动放一个出来会立刻自己 discard（没有主人 + 没有 buff）✓。
 */
public final class TNOrbEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TNMod.MODID);

    public static final RegistryObject<EntityType<com.tnc.tnc.magic.water.TNWaterSpellEntity>> WATER_SPELL =
            ENTITY_TYPES.register("water_spell", () -> EntityType.Builder.of(com.tnc.tnc.magic.water.TNWaterSpellEntity::new, MobCategory.MISC)
                    .sized(.1F,.1F).clientTrackingRange(12).updateInterval(1).build("tnc:water_spell"));
    public static final RegistryObject<EntityType<com.tnc.tnc.magic.water.TNWaterBoltEntity>> WATER_BOLT =
            ENTITY_TYPES.register("water_bolt", () -> EntityType.Builder.of(com.tnc.tnc.magic.water.TNWaterBoltEntity::new, MobCategory.MISC)
                    .sized(.3F,.3F).clientTrackingRange(8).updateInterval(1).build("tnc:water_bolt"));
    public static final RegistryObject<EntityType<com.tnc.tnc.magic.water.TNWaterFieldEntity>> WATER_FIELD =
            ENTITY_TYPES.register("water_field", () -> EntityType.Builder.of(com.tnc.tnc.magic.water.TNWaterFieldEntity::new, MobCategory.MISC)
                    .sized(.1F,.1F).clientTrackingRange(12).updateInterval(1).build("tnc:water_field"));

    /**
     * 火球链的自有投射物。
     *
     * <p>{@code updateInterval(1)}：火球飞得快，位置要每 tick 同步，不然客户端会看到它一跳一跳。
     * 碰撞判定在服务端做（见 {@code TNFireBoltEntity.tick}），客户端只跟位置 + 撒粒子。
     */
    public static final RegistryObject<EntityType<com.tnc.tnc.magic.fire.TNFireBoltEntity>> FIRE_BOLT =
            ENTITY_TYPES.register("fire_bolt", () -> EntityType.Builder.of(com.tnc.tnc.magic.fire.TNFireBoltEntity::new, MobCategory.MISC)
                    .sized(.3F,.3F).clientTrackingRange(10).updateInterval(1).build("tnc:fire_bolt"));

    /**
     * 熔岩地（火系火球链 t3 起）：贴在落点下方的一块持续灼烧区域。
     *
     * <p>{@code updateInterval(10)}：它自己是静止的、只撒粒子，位置同步慢一点无所谓
     * （省带宽）；判定完全在服务端做。
     */
    public static final RegistryObject<EntityType<com.tnc.tnc.magic.fire.TNLavaFieldEntity>> LAVA_FIELD =
            ENTITY_TYPES.register("lava_field", () -> EntityType.Builder.of(com.tnc.tnc.magic.fire.TNLavaFieldEntity::new, MobCategory.MISC)
                    .sized(.1F,.1F).clientTrackingRange(12).updateInterval(10).build("tnc:lava_field"));

    /**
     * 环绕雷球。
     *
     * <p>{@code updateInterval(2)}：位置每 2 tick 同步一次 —— 环绕是持续运动的，
     * 默认的 3 tick 会让它在客户端看起来一顿一顿的 ✓。
     */
    /**
     * 环绕雷球。
     *
     * <p><b>`updateInterval(1)`：位置每 tick 同步一次</b> ✗ 原来是 2 —— 那时它 80 tick 转一圈还行，
     * 后来作者要求"转速快一倍"（40 tick 一圈）＋"绕得更远"（6 格），2 tick 一次的插值就明显**卡顿**了
     * （作者 2026-09-22："环绕的帧率低还是卡啊，我感觉转的不流畅"）→ 改成每 tick ✓。
     */
    public static final RegistryObject<EntityType<TNThunderOrbEntity>> THUNDER_ORB =
            ENTITY_TYPES.register("thunder_orb", () -> EntityType.Builder
                    .of(TNThunderOrbEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("tnc:thunder_orb"));

    /**
     * 地上的魔法阵（主链雷法 5 档 ＋ 雷球链 t3/t4/t5 施法时留下）✓
     *
     * <p>尺寸给 0.1：它只是个贴地面片、没有碰撞、不可选中 ✓（碰撞箱小一点，免得挡路）。
     *
     * <p><b>`updateInterval(1)`</b>：原来 10 ✗ —— 半径/时长是走 {@code SynchedEntityData} 同步的，
     * 10 tick 一次意味着<b>客户端可能先收到"实体已生成"、过最多 0.5 秒才收到尺寸</b>，
     * 而渲染器在尺寸为 0 时直接 return → 表现就是"晚半拍、然后啪地冒出来" ✗
     * （作者 2026-09-27："魔法阵出现的不是很流畅……释放出魔法的那一刻就出现"）→ 改成每 tick ✓。
     */
    public static final RegistryObject<EntityType<TNMagicCircleEntity>> MAGIC_CIRCLE =
            ENTITY_TYPES.register("magic_circle", () -> EntityType.Builder
                    .of(TNMagicCircleEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("tnc:magic_circle"));

    /**
     * 爆炸冲击波 —— 由法术 JSON 的 {@code SPAWN} 动作在爆炸点生成 ✓
     * （引擎的 SPAWN 只能指定实体类型，所以"冲击波"必须是独立类型，见 {@link TNShockwaveEntity}）。
     */
    public static final RegistryObject<EntityType<TNShockwaveEntity>> SHOCKWAVE =
            ENTITY_TYPES.register("shockwave", () -> EntityType.Builder
                    .of(TNShockwaveEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("tnc:shockwave"));

    /**
     * A single falling-bolt visual (primary chain: field / storm / strike).
     *
     * <p>Author 2026-09-27: these three must strike with the flash model he made, the same
     * way heavenly thunder does - not with particle arcs. Damage is dealt by the mechanic
     * layer; this entity only draws the author's bolt model for a few ticks.
     */
    public static final RegistryObject<EntityType<TNLightningStrikeEntity>> LIGHTNING_STRIKE =
            ENTITY_TYPES.register("lightning_strike", () -> EntityType.Builder
                    .of(TNLightningStrikeEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("tnc:lightning_strike"));

    /**
     * ★ 光系第二条链 t3/t4/t5 召唤的**光天使** ✓（作者 2026-10-01 做的 Bedrock 模型 ✓）。
     *
     * <p>它是 {@link com.tnc.tnc.light.TNAngelEntity}（GeckoLib 生物 ✓，纯雕像：无敌/无 AI/不推动别人 ✓）。
     * 尺寸由法术定（2 / 3 / 5 格高 ✓）；碰撞箱给一个正常玩家大小即可（它不可选中 ✓）。
     */
    public static final RegistryObject<EntityType<com.tnc.tnc.light.TNAngelEntity>> ANGEL =
            ENTITY_TYPES.register("angel", () -> EntityType.Builder
                    .of(com.tnc.tnc.light.TNAngelEntity::new, MobCategory.MISC)
                    .sized(0.7F, 2.5F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("tnc:angel"));

    /**
     * ★ 光系第三条链「光线」的**实体光柱** ✓（作者 2026-10-01："光线我想要实体的" ✗）。
     *
     * <p>它是 {@link com.tnc.tnc.light.TNLightBeamEntity}（纯表现 + 自己判伤 ✓）：
     * 实体只有一个点，**光柱是渲染器画出来的**（本地 +Z 方向长 {@code length} 格 ✓）。
     * 所以 {@code updateInterval(1)} 很关键 ✗ —— 不然客户端会"先收到实体、过一会才知道多粗多长"，
     * 表现就是那根柱子晚半拍才冒出来 ✗（魔法阵那轮踩过同样的坑 ✓）。
     * {@code clientTrackingRange(16)} 给得大一些：远处的大光柱也要看得见 ✓。
     */
    public static final RegistryObject<EntityType<com.tnc.tnc.light.TNLightBeamEntity>> LIGHT_BEAM =
            ENTITY_TYPES.register("light_beam", () -> EntityType.Builder
                    .of(com.tnc.tnc.light.TNLightBeamEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .updateInterval(1)
                    .build("tnc:light_beam"));

    /**
     * ★ 光法第 4 条链「召唤天使」的**战斗天使** ✓（作者 2026-10-02："我制作了一个 fightingangel，你先把他做成怪" ✓）。
     *
     * <p>和上面那位"雕像天使"（{@link #ANGEL}）是两个东西 ✗：那个是第二条链法阵中心的无敌雕像，
     * 这个是**会飞、跟着你、替你打怪**的召唤物 ✓（见 {@link com.tnc.tnc.light.TNFightingAngelEntity} ✓）。
     *
     * <p>档位由法术写入（t1..t5 ✓），渲染器按档位放大 0.70 → 1.40 ✓；
     * 碰撞箱按模型原尺寸给（0.9 × 2.2 格 ✓ —— 视觉缩放只影响画面，不影响打不打得到 ✓）。
     */
    public static final RegistryObject<EntityType<com.tnc.tnc.light.TNFightingAngelEntity>> FIGHTING_ANGEL =
            ENTITY_TYPES.register("fighting_angel", () -> EntityType.Builder
                    .of(com.tnc.tnc.light.TNFightingAngelEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 2.2F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("tnc:fighting_angel"));

    /**
     * ★ 光系第五条链「光龙」<b>扔出去的那条龙</b> ✓（作者 2026-10-02："新增加一条光龙链" ✓）。
     *
     * <p>★ 它是 {@link com.tnc.tnc.light.TNDragonEntity} —— **和雷球同一类东西** ✓：
     * 基类是 {@code Entity} ✗ 不是生物 ✗（作者同日："我要的龙不是怪，你把他怪给我删了，
     * 我要的是跟雷球一样，扔出去，一条龙冲出去" ✓）⇒ 所以这里是
     * <b>{@code MobCategory.MISC}</b>（和雷球/光柱/魔法阵一个分类 ✓），
     * 而且 {@code TNNpcAttributes} 里**没有它**（非生物不注册属性 ✓）。
     *
     * <p>碰撞箱只给身体那一小段（2.5 × 2 格 ✓）—— 23 格全进碰撞箱会卡墙 ✗；
     * 渲染缩放由法术写进实体（t3 0.30 / t4 0.90 / t5 3.00 ✓），剔除由实体自己按体长撑 ✓；
     * {@code updateInterval(1)} —— 它飞得快，隔久了客户端会一卡一卡 ✗（光柱那条同理 ✓）。
     */
    public static final RegistryObject<EntityType<com.tnc.tnc.light.TNDragonEntity>> LIGHT_DRAGON =
            ENTITY_TYPES.register("light_dragon", () -> EntityType.Builder
                    .of(com.tnc.tnc.light.TNDragonEntity::new, MobCategory.MISC)
                    .sized(2.5F, 2.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("tnc:light_dragon"));

    /**
     * ★★ <b>龙真正的实体（2026-10-04 起）</b> —— 原版 {@link net.minecraft.world.entity.Display.BlockDisplay} ✓。
     *
     * <p>作者："<b>龙释放还是异常，都没法出现啊大哥，你把他当成block来使用好不好</b>" ✓
     * ⇒ 不再走 GeckoLib（那条路上"非生物实体 yaw 恒为 0 / 大模型被视锥剔掉 / 出生点被推到
     * 几十格外"三件事叠起来，实机就是"根本没法出现"✗），
     * 改成**方块模型 + 原版 Display** ✓（见 {@code light/TNDragonDisplayEntity} ✓）。
     *
     * <p>光龙 / 暗龙**共用这一个实体类型** ✓ —— 差别只有"挂哪个载体方块" ✓
     * （{@code tnc:dragon_display_light} / {@code _dark} ✓），
     * 法术生成时用 {@code setCarrier(...)} 指定 ✓。
     */
    public static final RegistryObject<EntityType<com.tnc.tnc.light.TNDragonDisplayEntity>> DRAGON =
            ENTITY_TYPES.register("dragon", () -> EntityType.Builder
                    .of(com.tnc.tnc.light.TNDragonDisplayEntity::new, MobCategory.MISC)
                    .sized(2.5F, 2.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("tnc:dragon"));

    /**
     * ★ 暗系链「暗龙」<b>扔出去的那条暗龙</b> ✓（作者 2026-10-02：<b>"复制一下光龙，生成一个暗龙"</b> ✓）。
     *
     * <p>它就是上面那条光龙的**暗色镜像** ✓ —— 实体类**完全一样**
     * （{@link com.tnc.tnc.light.TNDragonEntity} ✓，冲/环绕/撞伤那套逻辑一个字没改 ✗），
     * 差别只有两处：
     * <ul>
     *   <li><b>实体类型不同</b>（{@code tnc:dark_dragon} ✓）⇒ 客户端走另一个渲染器 + 另一张贴图
     *       （{@code dark/client/TNDarkDragonGeoModel} ✓）；</li>
     *   <li>法术生成时调一次 {@code entity.setDark(true)} ✓ ⇒ 粒子换成灵魂火 + 黑烟 ✓。</li>
     * </ul>
     * 同样是投射物（{@code MobCategory.MISC} ✓ 不是生物 ✗、**不注册属性** ✓）。
     */
    public static final RegistryObject<EntityType<com.tnc.tnc.light.TNDragonEntity>> DARK_DRAGON =
            ENTITY_TYPES.register("dark_dragon", () -> EntityType.Builder
                    .of(com.tnc.tnc.light.TNDragonEntity::new, MobCategory.MISC)
                    .sized(2.5F, 2.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("tnc:dark_dragon"));

    // ------------------------------------------------------------------
    //  ★ 暗系第三条链「召唤」的五个召唤物 ✓（作者 2026-10-04："暗魔法的召唤流没实装吗" ✓）
    //    五个模型差得远（5/8/10/11/18 骨骼 ✗）⇒ 注册成**五个实体类型** ✓：
    //    这样一个类（{@code dark/TNDarkSummonEntity}）就能按类型读出档位 ✓，
    //    每档还能有自己的名字（小恶魔 / 暗卫 / … ✓）和自己的属性表 ✓。
    // ------------------------------------------------------------------

    /** t1 小恶魔（0.69 格，飘着的一团）✓ */
    public static final RegistryObject<EntityType<com.tnc.tnc.dark.TNDarkSummonEntity>> DARK_IMP =
            summonType("dark_imp", 0.7F, 0.9F);
    /** t2 暗卫（1.88 格，单刀）✓ */
    public static final RegistryObject<EntityType<com.tnc.tnc.dark.TNDarkSummonEntity>> DARK_GUARD =
            summonType("dark_guard", 0.8F, 1.9F);
    /** t3 暗之统领（1.88 格，双刀 + 披风）✓ */
    public static final RegistryObject<EntityType<com.tnc.tnc.dark.TNDarkSummonEntity>> DARK_LORD =
            summonType("dark_lord", 0.8F, 1.9F);
    /** t4 暗之国王（2.0 格，王冠）✓ */
    public static final RegistryObject<EntityType<com.tnc.tnc.dark.TNDarkSummonEntity>> DARK_KING =
            summonType("dark_king", 0.9F, 2.0F);
    /** t5 邪神（2.38 格，六臂 + 光环）✓ */
    public static final RegistryObject<EntityType<com.tnc.tnc.dark.TNDarkSummonEntity>> EVIL_GOD =
            summonType("evil_god", 1.1F, 2.4F);

    private static RegistryObject<EntityType<com.tnc.tnc.dark.TNDarkSummonEntity>> summonType(
            String id, float width, float height) {
        return ENTITY_TYPES.register(id, () -> EntityType.Builder
                .of(com.tnc.tnc.dark.TNDarkSummonEntity::new, MobCategory.CREATURE)
                .sized(width, height)
                .clientTrackingRange(10)
                .build("tnc:" + id));
    }

    /**
     * ★ <b>黑雾</b>（暗系第四条链：黑雾 / 领域）✓（作者 2026-10-09："你全做吧"）。
     *
     * <p>它就是引擎 {@code SpellCloud} 的<b>子类</b> ✓ —— 法术 JSON 写
     * {@code "entity_type_id": "tnc:fog"} 之后，{@code SpellHelper.placeCloud} 会
     * {@code create(level)} 出这个类（随后强转成 {@code SpellCloud}）✓，
     * 于是那五个法术的雾就能<b>跟着施法者走</b>（t3 起）✓、并托管一张跟着走的
     * <b>边界法阵</b> ✓（见 {@link DarkFogCloudEntity}）。
     *
     * <p>尺寸给 <b>9 × 6</b>：它不是"实心方块"，而是要画成一大团低矮的穹顶云 ✓ ——
     * 包围盒偏小会让客户端的可见性剔除把雾<b>提前剪掉</b> ✗（10 格高的天使踩过这个坑：
     * 抬头看天使整尊消失，见 {@code TNAngelEntity.getBoundingBoxForCulling}）；
     * {@code clientTrackingRange} 也要够大，否则站在雾里的玩家收不到这片雾 ✓。
     *
     * <p><b>{@code updateInterval(1)}</b>：t3 起雾的位置每 tick 都在变，
     * 2 tick 同步一次就会看起来一顿一顿 ✗（环绕雷球当初就是这么被作者抓到的 ✓）。
     */
    // SpellCloud is supplied by the optional engine; isolated tests have no engine.
    public static final RegistryObject<EntityType<DarkFogCloudEntity>> FOG =
            net.minecraftforge.fml.ModList.get().isLoaded("spell_engine")
            ? ENTITY_TYPES.register("fog", () -> EntityType.Builder
                    .of(DarkFogCloudEntity::new, MobCategory.MISC)
                    .sized(9.0F, 6.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("tnc:fog")) : null;

    private TNOrbEntities() {
    }

    /** 挂到 mod 事件总线上（由 {@code TNMod} 构造函数调用，必须在注册事件之前 ✓）。 */
    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
