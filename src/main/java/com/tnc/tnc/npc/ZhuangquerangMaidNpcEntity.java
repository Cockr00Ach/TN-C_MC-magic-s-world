package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 庄鹊让的<b>女仆模型版</b>实体 —— 与 {@link ZhuangquerangNpcEntity} 行为完全一样（对话/NPC 逻辑都在父类），
 * 区别只有一个：<b>它实现 {@link GeoEntity}，所以客户端能用 GeckoLib 画那个女仆模型</b> ✓。
 *
 * <h2>为什么要分成两个类</h2>
 * 本类引用了 GeckoLib 的类型 —— 一旦这个类被加载，**没有装 GeckoLib 的环境就会
 * {@code NoClassDefFoundError}** ✗，而 TN-C 的法术/魔法石不该因为一个 NPC 的外观而整个加载不了。
 * 所以注册实体时按 {@code ModList.get().isLoaded("geckolib")} 二选一：
 * <ul>
 *   <li>装了 GeckoLib（整合包一定有，TLM 也依赖它）→ 注册本类，画女仆模型 ✓；</li>
 *   <li>没装 → 注册父类 {@link ZhuangquerangNpcEntity}，用原版人形模型 + 64×64 降级皮肤 ✓。</li>
 * </ul>
 * 分流点在 {@code com.tnc.tnc.npc.compat.MaidNpcSupport}（那里才引用本类）✓。
 *
 * <h2>模型/贴图/动画</h2>
 * <ul>
 *   <li>模型：{@code assets/tnc/geo/entity/zhuangquerang.geo.json} —— 十六夜咲夜的 bedrock 模型
 *       （1.12.0 格式，GeckoLib 直接能读 ✓）；源文件来自整合包的 TLM 模型包
 *       （CC BY-NC-SA 4.0，见 {@code docs/当前状态.md} 第四节）；</li>
 *   <li>贴图：{@code assets/tnc/textures/entity/zhuangquerang.png}（128×128 女仆图集，逐字节一致 ✓）；</li>
 *   <li>动画：{@code assets/tnc/animations/entity/zhuangquerang.animation.json} —— <b>我们自己写的</b>轻微 idle
 *       （只动 {@code head}：左右各 3° 慢慢看人）✗ 不是 TLM 的那套：TLM 的 {@code maid.animation.json}
 *       是 GeckoLib 骨架（{@code Root/UpBody/LeftArm…}），而咲夜是旧格式骨架（{@code head/body/armRight…}），
 *       <b>骨骼名对不上</b> ✗（实测确认），播了也不会动。</li>
 * </ul>
 */
public class ZhuangquerangMaidNpcEntity extends ZhuangquerangNpcEntity implements GeoEntity {

    /** 站着时的轻微 idle（只动头，避免覆盖模型其余骨骼的静止姿态 ✓）。 */
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ZhuangquerangMaidNpcEntity(EntityType<? extends ZhuangquerangNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 第一个参数是"过渡刻数"，5 = 动作之间平滑过渡
        controllers.add(new AnimationController<>(this, "idle", 5, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /** 供模型/渲染器引用资源用（避免字符串散落在多处）。 */
    public static net.minecraft.resources.ResourceLocation modelResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "geo/entity/zhuangquerang.geo.json");
    }

    public static net.minecraft.resources.ResourceLocation textureResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/zhuangquerang.png");
    }

    public static net.minecraft.resources.ResourceLocation animationResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "animations/entity/zhuangquerang.animation.json");
    }
}
