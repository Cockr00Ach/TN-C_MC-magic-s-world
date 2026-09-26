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

    /**
     * 环绕雷球。
     *
     * <p>{@code updateInterval(2)}：位置每 2 tick 同步一次 —— 环绕是持续运动的，
     * 默认的 3 tick 会让它在客户端看起来一顿一顿的 ✓。
     */
    public static final RegistryObject<EntityType<TNThunderOrbEntity>> THUNDER_ORB =
            ENTITY_TYPES.register("thunder_orb", () -> EntityType.Builder
                    .of(TNThunderOrbEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("tnc:thunder_orb"));

    /**
     * 地上的魔法阵（传说级/神级雷球施法时留下）✓
     *
     * <p>尺寸给 0.1：它只是个贴地面片、没有碰撞、不可选中 ✓（碰撞箱小一点，免得挡路）。
     */
    public static final RegistryObject<EntityType<TNMagicCircleEntity>> MAGIC_CIRCLE =
            ENTITY_TYPES.register("magic_circle", () -> EntityType.Builder
                    .of(TNMagicCircleEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(10)
                    .updateInterval(10)
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

    private TNOrbEntities() {
    }

    /** 挂到 mod 事件总线上（由 {@code TNMod} 构造函数调用，必须在注册事件之前 ✓）。 */
    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
