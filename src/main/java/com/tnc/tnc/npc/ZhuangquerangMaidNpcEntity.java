package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
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

    /** 每个点播动作播几遍（与 self 同一规则：用户 2026-09-23 "播两遍就站着"）。 */
    private static final int ACTION_REPEATS = 2;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ZhuangquerangMaidNpcEntity(EntityType<? extends ZhuangquerangNpcEntity> type, Level level) {
        super(type, level);
    }

    /**
     * 只有一个控制器：<b>点播动作</b>。
     *
     * <p>平时它返回 {@code STOP}、一根骨头都不写 → 她保持模型自带的站姿 ✓
     * （用户 2026-09-23 的要求："播完两遍就站着"）。
     *
     * <p>动作名（2026-09-23 用户在 Blockbench 里做的，导出到
     * {@code assets/tnc/animations/entity/zhuangquerang.animation.json}）：
     * <ul>
     *   <li>{@code confusing} —— 困惑（头 + 双手），1.17 秒</li>
     *   <li>{@code shrug-shouder} —— 耸肩（双手 + 头发 {@code line}/{@code line2}），1.0 秒</li>     *   <li>{@code point} —— 抬手指（双手），1.71 秒</li>
     *   <li>{@code lowerhand} —— 放下手（头），1.0 秒</li>
     * </ul>
     * ⚠️ 导出时那个 {@code confusing} 曾带着一个中文顿号（{@code confusing、}）——
     * 已由 {@code tools/fix_zhuangquerang_anim.py} 改名 ✓，
     * 因为 {@code @act} 与动作校验只接受**短 ASCII 名**，带符号的名字叫不出来 ✗。
     *
     * <p>⚠️ 动画文件里还有一个 {@code idle}（咲夜那套原有的轻微 idle）——
     * <b>暂时不注册</b>，所以她现在不会自己晃 ✓；要开随时说一句。
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<ZhuangquerangMaidNpcEntity> action =
                new AnimationController<>(this, "action", 2, state ->
                        state.getController().isPlayingTriggeredAnimation()
                                ? PlayState.CONTINUE : PlayState.STOP);
        action.triggerableAnim("confusing",
                RawAnimation.begin().thenPlayXTimes("confusing", ACTION_REPEATS));
        action.triggerableAnim("shrug-shouder",
                RawAnimation.begin().thenPlayXTimes("shrug-shouder", ACTION_REPEATS));
        action.triggerableAnim("point",
                RawAnimation.begin().thenPlayXTimes("point", ACTION_REPEATS));
        action.triggerableAnim("lowerhand",
                RawAnimation.begin().thenPlayXTimes("lowerhand", ACTION_REPEATS));

        controllers.add(action);
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
