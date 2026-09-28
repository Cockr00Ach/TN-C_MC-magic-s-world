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
 * 周坐望的 <b>Bedrock 模型版</b> —— 行为与 {@link ZuowangNpcEntity} 完全一样
 * （对话/NPC 逻辑都在父类），区别只有一个：<b>它实现 {@link GeoEntity}，
 * 所以客户端能用 GeckoLib 画 {@code zuowang.geo.json}</b>，也才能播 Blockbench 录的关键帧动作 ✓。
 *
 * <p>骨架与 {@code self} 那套同源（原版人形 7 骨骼 {@code head / hat / body / armRight /
 * armLeft / legRight / legLeft}，脚底 y=0、头顶 y=32 ≈ 2 格），
 * 额外挂了一整套<b>头发 / 胡须骨骼</b>（{@code hairTop / hairBangs / hairSideL→L3 /
 * hairSideR→R3 / hairBack→Back4 / hairBeard→Beard2 / hairAhoge}，共 21 根）——
 * 那是给动画专题做"须发飘动"用的 ✓。生成器：{@code tools/gen_zuowang_model.py}。
 *
 * <h2>动作怎么播（与 self 同一套约定）</h2>
 * <ul>
 *   <li><b>平时就是站着</b> ✓ —— 不注册"待机动画"控制器：没有动画在播时这个控制器返回 STOP、
 *       什么骨骼都不写，于是他就保持模型的静止站姿 ✓；</li>
 *   <li>插槽 {@code action} —— <b>点播</b>：{@code triggerAnim("action", "&lt;名字&gt;")} ✓，
 *       由剧本里的 {@code @act &lt;名字&gt;} 挂到台词上（见 {@code DialogueLoader} 的格式说明）；</li>
 *   <li>⚠️ 点播动画必须在 JSON 里写 {@code "loop": false} ✗，否则 {@code thenPlayXTimes}
 *       的"次数"没有意义。</li>
 * </ul>
 *
 * <h2>⚠️ 现在<b>还没有</b>动画文件</h2>
 * 作者只给了模型与贴图，{@code zuowang.animation.json} 还不存在 ✗。
 * 因此 {@link #animationResource()} 指向的路径<b>暂时没有文件</b> ——
 * 这是**有意为之**：GeckoLib 找不到动画文件时只是没有可播的动作，模型照样正常渲染 ✓，
 * 也就等于"他一直站着"（正是我们现在要的效果 ✓）。
 * 等动画专题在 Blockbench 里录好导出到那个路径，本类<b>一行都不用改</b> ✓。
 *
 * <p>⚠️ 本类引用了 GeckoLib 的类型 —— 一旦被加载，**没装 GeckoLib 的环境会
 * {@code NoClassDefFoundError}** ✗，所以只在 {@code GeoSelfSupport.available()} 为真时才会被 new ✓。
 */
public class ZuowangBedrockNpcEntity extends ZuowangNpcEntity implements GeoEntity {

    /** 每个点播动作播几遍（与 self 一致：两遍就停）。 */
    private static final int ACTION_REPEATS = 2;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ZuowangBedrockNpcEntity(EntityType<? extends ZuowangNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 只有一个控制器：点播动作。**故意不给默认动画** ——
        // 没有动作在播时它返回 STOP、什么骨骼都不写，于是他就保持模型的静止站姿 ✓。
        //
        // ⚠️ 现在动画文件还不存在，所以这一段是"空转"的（没有任何 triggerableAnim 注册）。
        //    这不是 bug：作者录好动画后，在下面按 self 的写法加 triggerableAnim 即可。
        AnimationController<ZuowangBedrockNpcEntity> action =
                new AnimationController<>(this, "action", 2, state ->
                        state.getController().isPlayingTriggeredAnimation()
                                ? PlayState.CONTINUE : PlayState.STOP);

        // TODO(动画专题): 录好动作后照下面这行加，名字与 Blockbench 导出的动画名一致
        // action.triggerableAnim("lookup", RawAnimation.begin()
        //         .thenPlayXTimes("lookup", ACTION_REPEATS));

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
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "geo/entity/zuowang.geo.json");
    }

    /** 128×128 的 Bedrock 图集（作者用 tools/gen_zuowang_model.py 生成）。 */
    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/zuowang_bedrock.png");
    }

    /**
     * 动画文件：⚠️ <b>目前还没有这个文件</b> ✗（作者只给了模型与贴图）。
     * GeckoLib 找不到时不会崩，只是没有动作可播 —— 见类注释。
     * 动画专题录好后放到这个路径即可，本类不用改 ✓。
     */
    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "animations/entity/zuowang.animation.json");
    }
}
