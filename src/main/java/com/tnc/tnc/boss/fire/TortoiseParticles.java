package com.tnc.tnc.boss.fire;

import com.tnc.tnc.TNMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 火山巨龟 / 火法 Boss 专用粒子类型。
 *
 * <p>重踏火环用 {@link #SLAM_FLAME}：粒子自带圆周角度与径向偏移，全部共享同一个传播半径，
 * 因此整道环只有一个轮廓、一条时间线，不会在旧半径上留下残留。
 *
 * <p>火球与蓄力用三层 {@code FIRE_*}：同一球面上按半径分层取色，
 * 近白黄核心 → 橙色主体 → 深橙红外缘，三层叠在一起是一个完整的球，而不是几颗零散火苗。
 */
public final class TortoiseParticles {

    private static final DeferredRegister<ParticleType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, TNMod.MODID);

    /** overrideLimiter=true：即使玩家把粒子设为「最少」，火环也完整保留。 */
    public static final RegistryObject<SimpleParticleType> SLAM_FLAME =
            TYPES.register("slam_flame", () -> new SimpleParticleType(true));

    /** 火球核心：近白黄，体积小、寿命略长。 */
    public static final RegistryObject<SimpleParticleType> FIRE_CORE =
            TYPES.register("fire_core", () -> new SimpleParticleType(true));

    /** 火球主体：金黄→橙，数量最多。 */
    public static final RegistryObject<SimpleParticleType> FIRE_MID =
            TYPES.register("fire_mid", () -> new SimpleParticleType(true));

    /** 火球外缘：深橙红，体积大、寿命短，负责轮廓。 */
    public static final RegistryObject<SimpleParticleType> FIRE_EDGE =
            TYPES.register("fire_edge", () -> new SimpleParticleType(true));

    /** 蓄力专用：寿命很短，发射瞬间不会在嘴前留下第二颗球。 */
    public static final RegistryObject<SimpleParticleType> FIRE_CHARGE =
            TYPES.register("fire_charge", () -> new SimpleParticleType(true));

    private TortoiseParticles() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
