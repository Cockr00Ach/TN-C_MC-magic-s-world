package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.npc.compat.GeoSelfSupport;
import com.tnc.tnc.npc.compat.MaidNpcSupport;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * TN-C 自己的 NPC 实体注册表。
 *
 * <p><b>为什么我们自己注册，而不是用原作者那套</b>：原作者的 {@code ysjxmodel} 是闭源
 * （All Rights Reserved），而他的皮肤 NPC（{@code PlayerSkinNpcEntity}）把每个 NPC 的贴图
 * <b>硬编码在静态字段里</b> —— 既改不了 jar，也没法加人。所以 TN-C 的 NPC 一律自己注册。
 *
 * <p>这套写法就是后面 cava（铁匠）/ riggen（黑市商人）的模板：加一个实体 = 这里加一行。
 */
public final class TNNpcs {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TNMod.MODID);

    /**
     * A. Self —— 酒馆老板（中立派系）。
     *
     * <p>参数说明（Forge 1.20.1）：
     * <ul>
     *   <li>{@code sized(0.6F, 1.8F)} —— 玩家尺寸（宽 0.6 / 高 1.8），皮肤模型才不会被拉伸；</li>
     *   <li>{@code clientTrackingRange(10)} —— 10 个区块内客户端会收到它（要看得见人）；</li>
     *   <li>{@code MobCategory.MISC} —— 不参与任何刷怪规则。他是剧情 NPC，只能被我们放出来，
     *       不会自然生成，也不会因为玩家走远而消失（要不要消失由实体类里
     *       {@code removeWhenFarAway} 决定，我们让它不消失）。</li>
     * </ul>
     *
     * <p><b>模型分流（2026-09-22，动画系统MMM 加）</b>：与庄鹊让同一套写法 ——
     * 装了 GeckoLib（整合包一定有）→ 实体是 {@link SelfBedrockNpcEntity}，
     * 客户端用 GeckoLib 画 {@code self.geo.json} ✓（**能播 Blockbench 关键帧**，剧情演出靠它）；
     * 没装 → 就是 {@link SelfNpcEntity}，原版人形 + 64×64 皮肤 ✓。
     * 分流点 {@link GeoSelfSupport}（那里才引用 GeckoLib 的类型，避免整个 mod 加载不了 ✗）✓。
     *
     * <p>⚠️ 与庄鹊让那边的**关键区别**：这里的实体类型字段**保持**
     * {@code RegistryObject<EntityType<SelfNpcEntity>>}（不改成通配符）——
     * 因为 {@code TNNpcAttributes} / {@code NpcCommand} / {@code NpcPlacementDefaults} 都在按
     * {@code SelfNpcEntity} 用这个字段，改成通配符会牵动一串别人的文件 ✗；
     * 而两种实体类型都满足这个签名（一个是父类，一个是它的子类）✓。
     */
    public static final RegistryObject<EntityType<SelfNpcEntity>> SELF = registerSelf();

    private static RegistryObject<EntityType<SelfNpcEntity>> registerSelf() {
        if (GeoSelfSupport.available()) {
            return ENTITY_TYPES.register("self", () -> EntityType.Builder
                    .of(GeoSelfSupport::create, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("tnc:self"));
        }
        return ENTITY_TYPES.register("self", () -> EntityType.Builder
                .of(SelfNpcEntity::new, MobCategory.MISC)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(10)
                .build("tnc:self"));
    }

    /**
     * B. cava —— 铁匠老板（中立派系），槐的父亲。
     *
     * <p>剧情第一段第三场「一面没打完的盾」在他铺子里。参数与 Self 完全一致
     * （同样尺寸、同样不参与刷怪）。<b>加一个 NPC 就是复制这一段</b>。
     */
    public static final RegistryObject<EntityType<CavaNpcEntity>> CAVA =
            ENTITY_TYPES.register("cava", () -> EntityType.Builder
                    .of(CavaNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("tnc:cava"));

    /** 槐—— cava 的儿子，小队里的战士。参数同前两个。 */
    public static final RegistryObject<EntityType<HuaiNpcEntity>> HUAI =
            ENTITY_TYPES.register("huai", () -> EntityType.Builder
                    .of(HuaiNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("tnc:huai"));

    /**
     * 庄鹊让 —— 卷五《代》的角色（作者 2026-09-22："先当 NPC 做"，且"保持女仆模型"）。
     *
     * <p>参数与前面三个完全一致（玩家尺寸 / 不参与刷怪 / 10 区块追踪）。
     *
     * <p><b>模型分流</b>：装了 GeckoLib（整合包一定有）→ 实体是
     * {@code ZhuangquerangMaidNpcEntity}，客户端用 GeckoLib 画<b>女仆模型</b> ✓；
     * 没装 → 父类 {@code ZhuangquerangNpcEntity}，原版人形 + 64×64 降级皮肤 ✓。
     * 分流点见 {@code com.tnc.tnc.npc.compat.MaidNpcSupport}（那里才引用 GeckoLib 的类型，
     * 避免"没装 GeckoLib 就整个 mod 加载不了" ✗）。
     *
     * <p>注意 <b>id 就是 {@code zhuangquerang}</b> —— {@code /tnc npc} 的 id 是
     * <b>按注册表查实体类型</b>解析的（见 {@code NpcCommand}），所以这里注册完，
     * {@code /tnc npc here zhuangquerang} 立刻可用 ✓，不用再改命令代码。
     * 唯一要同步的地方是 {@code purge all} 的名单 ✗（那里是硬编码数组）。
     */
    public static final RegistryObject<EntityType<ZhuangquerangNpcEntity>> ZHUANGQUERANG = registerZhuangquerang();

    private static RegistryObject<EntityType<ZhuangquerangNpcEntity>> registerZhuangquerang() {
        if (MaidNpcSupport.available()) {
            return ENTITY_TYPES.register("zhuangquerang", () -> EntityType.Builder
                    .of(MaidNpcSupport::create, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("tnc:zhuangquerang"));
        }
        return ENTITY_TYPES.register("zhuangquerang", () -> EntityType.Builder
                .of(ZhuangquerangNpcEntity::new, MobCategory.MISC)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(10)
                .build("tnc:zhuangquerang"));
    }

    private TNNpcs() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
