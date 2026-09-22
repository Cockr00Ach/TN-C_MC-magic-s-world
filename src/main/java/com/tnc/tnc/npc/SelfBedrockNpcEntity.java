package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Self 的 <b>Bedrock 模型版</b> —— 行为与 {@link SelfNpcEntity} 完全一样（对话/NPC 逻辑都在父类），
 * 区别只有一个：<b>它实现 {@link GeoEntity}，所以客户端能用 GeckoLib 画
 * {@code self.geo.json}</b>，也才能播 Blockbench 录的关键帧动作 ✓。
 *
 * <p>骨架由 {@code tools/gen_npc_bedrock.py} 按原版人形几何生成 ✓
 * （骨骼名 {@code head / hat / body / armRight / armLeft / legRight / legLeft}，
 * 脚底 y=0、头顶 y=32 = 2 格高）。皮肤是那张 64×64 经典皮肤转出来的 128×128 ✓。
 *
 * <h2>动作怎么播（★ 动画系统MMM 的约定）</h2>
 * <ul>
 *   <li><b>平时就是站着</b> ✓ —— 我们不注册"待机动画"控制器：
 *       模型的静止姿态就是自然站姿（见 {@code tools/gen_npc_bedrock.py}），
 *       没有动画在播时他就那么站着（用户 2026-09-23 的要求："播完两遍就站着"）✓；</li>
 *   <li>插槽 {@code action} —— <b>点播</b>：{@code triggerAnim("action", "wipehand")} ✓，
 *       每个动作<b>连着播两遍</b>（{@code thenPlayXTimes(name, 2)}）然后回到站姿 ✓；</li>
 *   <li>⚠️ 点播动画必须在 JSON 里写 {@code "loop": false} ✗，
 *       否则 {@code thenPlayXTimes} 的"次数"就没意义了（它会自己一直循环）；</li>
 *   <li>名字（2026-09-22 与用户在 Blockbench 里做的一致）：
 *       {@code wipehand} / {@code pointcup} / {@code confused} ——
 *       剧本里用 {@code @act <名字>} 挂在台词上（见 {@code DialogueLoader} 的格式说明）✓。</li>
 * </ul>
 *
 * <p>⚠️ 本类引用了 GeckoLib 的类型 —— 一旦被加载，**没装 GeckoLib 的环境会
 * {@code NoClassDefFoundError}** ✗，所以只在 {@code GeoSelfSupport.available()} 为真时才会被 new ✓。
 */
public class SelfBedrockNpcEntity extends SelfNpcEntity implements GeoEntity {

    /** 每个点播动作播几遍（用户 2026-09-23：播两遍就好）。 */
    private static final int ACTION_REPEATS = 2;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SelfBedrockNpcEntity(EntityType<? extends SelfNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 只有一个控制器：点播动作。**故意不给默认动画** ——
        // 没有动作在播时它返回 STOP、什么骨骼都不写，于是他就保持模型的静止站姿 ✓。
        AnimationController<SelfBedrockNpcEntity> action =
                new AnimationController<>(this, "action", 2, state ->
                        state.getController().isPlayingTriggeredAnimation()
                                ? PlayState.CONTINUE : PlayState.STOP);
        action.triggerableAnim("wipehand",
                RawAnimation.begin().thenPlayXTimes("wipehand", ACTION_REPEATS));
        action.triggerableAnim("pointcup",
                RawAnimation.begin().thenPlayXTimes("pointcup", ACTION_REPEATS));
        action.triggerableAnim("confused",
                RawAnimation.begin().thenPlayXTimes("confused", ACTION_REPEATS));

        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------------
    //  资源位置（字符串只写一处，模型/渲染器都从这里读）
    // ------------------------------------------------------------------

    public static ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "geo/entity/self.geo.json");
    }

    /** 128×128 版皮肤（经典 64×64 转出来的，见 tools/gen_npc_bedrock.py）。 */
    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/self_bedrock.png");
    }

    /** 动画文件：用户在 Blockbench 录完导出到这里（见 docs/NPC动作产线_Blockbench配方.md）。 */
    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "animations/entity/self.animation.json");
    }
}
