package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
 * <h2>持杖变体 + 施法动作（2026-10-30）</h2>
 * 同一个实体类型有两种外观，开关是同步字段 {@code DATA_WAND}（{@link #setWand}）：
 * <ul>
 *   <li>普通：{@code self.geo.json} ＋ {@code self_bedrock.png} ＋ {@code self.animation.json}
 *       —— 剧情 NPC 用的就是这一套，<b>没有杖</b>；</li>
 *   <li>持杖：{@code self_wand.geo.json} ＋ {@code self_wand_bedrock.png} ＋
 *       {@code player_magic-animation.json} —— 多出 {@code wand / wandJewel} 两根道具骨，
 *       播用户 2026-10-30 做的三条施法动作 ✓。</li>
 * </ul>
 * 为什么不合进同一个动画文件：NPC 的点播动作（擦手/放抹布/看酒杯）只对人形骨有意义，
 * 而施法动作要带杖；分成两个文件，谁的骨头对不上就只影响自己那一套 ✓（{@code crashIfBoneMissing=false}）。
 *
 * <p>⚠️ 本类引用了 GeckoLib 的类型 —— 一旦被加载，**没装 GeckoLib 的环境会
 * {@code NoClassDefFoundError}** ✗，所以只在 {@code GeoSelfSupport.available()} 为真时才会被 new ✓。
 */
public class SelfBedrockNpcEntity extends SelfNpcEntity implements GeoEntity {

    /** 每个点播动作播几遍（用户 2026-09-23：播两遍就好）。 */
    private static final int ACTION_REPEATS = 2;

    // ------------------------------------------------------------------
    //  施法动作名 —— 名字表在 com.tnc.tnc.magic.PlayerCastAnimations（唯一来源）
    // ------------------------------------------------------------------

    /** 强力法术：抬手前推 + 转杖，动作最长（2.33 s）。 */
    public static final String CAST_POWERFUL =
            com.tnc.tnc.magic.PlayerCastAnimations.POWERFUL.animation();

    /** 常规施法：举杖连续挥动（2.46 s）—— 用户 2026-10-30："主要都用 waving"。 */
    public static final String CAST_STANDARD =
            com.tnc.tnc.magic.PlayerCastAnimations.STANDARD.animation();

    /** 召唤：双手开合、杖头翻上（2.92 s）—— "召唤法术用召唤"。 */
    public static final String CAST_SUMMON =
            com.tnc.tnc.magic.PlayerCastAnimations.SUMMON.animation();

    /** 全部施法动作（顺序即命令补全顺序：常规 → 召唤 → 强力）。 */
    public static final String[] CAST_ANIMATIONS =
            {CAST_STANDARD, CAST_SUMMON, CAST_POWERFUL};

    /** 循环版触发器名的后缀（{@code wavingstaff_loop}）。 */
    public static final String LOOP_SUFFIX = "_loop";

    /** 持杖开关 —— 同步字段，客户端靠它换模型/贴图/动画文件。 */
    private static final EntityDataAccessor<Boolean> DATA_WAND =
            SynchedEntityData.defineId(SelfBedrockNpcEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 当前正在循环的施法动作（服务端记账，{@link #stopCastAnimation()} 用）。null = 没在循环。 */
    private String loopingCast;

    public SelfBedrockNpcEntity(EntityType<? extends SelfNpcEntity> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------------
    //  同步字段 / 存档
    // ------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_WAND, Boolean.FALSE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TNCWand", this.isWand());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TNCWand")) {
            this.setWand(tag.getBoolean("TNCWand"));
        }
    }

    /** 这一只是不是"持杖"外观（演示/施法用）。 */
    public boolean isWand() {
        return this.entityData.get(DATA_WAND);
    }

    /**
     * 持杖版是"演示/施法"用的 —— 右键<b>不要弹剧情对话</b> ✗。
     *
     * <p>否则你站在它面前看动作，手一抖右键就被剧本糊一脸；剧情用的普通外观行为不变 ✓。
     */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (this.isWand()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        return super.mobInteract(player, hand);
    }

    /** 切外观。切换后客户端下一帧就会换成另一套模型/贴图/动画 ✓（GeckoLib 按资源路径缓存）。 */
    public void setWand(boolean wand) {
        this.entityData.set(DATA_WAND, wand);
        if (!wand) {
            // 普通模型没有杖骨，也没加载施法动画文件 —— 别留一个永远播不动的循环
            this.loopingCast = null;
        }
    }

    // ------------------------------------------------------------------
    //  控制器
    // ------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 控制器 1：剧情点播动作。**故意不给默认动画** ——
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

        // 控制器 2：施法动作（只在持杖外观下有意义）。
        // 每个动作注册两个触发器：<名字> 播一次；<名字>_loop 循环（方便在游戏里反复看）。
        AnimationController<SelfBedrockNpcEntity> cast =
                new AnimationController<>(this, "cast", 2, state ->
                        state.getController().isPlayingTriggeredAnimation()
                                ? PlayState.CONTINUE : PlayState.STOP);
        for (String name : CAST_ANIMATIONS) {
            cast.triggerableAnim(name, RawAnimation.begin().thenPlay(name));
            cast.triggerableAnim(name + LOOP_SUFFIX, RawAnimation.begin().thenLoop(name));
        }
        controllers.add(cast);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------------
    //  施法动作的点播接口（命令 / 以后的施法流程都走这里）
    // ------------------------------------------------------------------

    /** 这个名字是不是我们注册过的施法动作。 */
    public static boolean isCastAnimation(String name) {
        if (name == null) {
            return false;
        }
        for (String candidate : CAST_ANIMATIONS) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 播一条施法动作。
     *
     * @param name 动作名（{@link #CAST_ANIMATIONS} 之一）
     * @param loop true = 一直循环（观察用），false = 播完回到站姿
     * @return 真的触发了没有（不是持杖外观 / 名字不对 → false，不抛异常）
     */
    public boolean playCastAnimation(String name, boolean loop) {
        if (!this.isWand() || !isCastAnimation(name)) {
            return false;
        }
        this.loopingCast = loop ? name : null;
        this.triggerAnim("cast", loop ? name + LOOP_SUFFIX : name);
        return true;
    }

    /** 停止循环并回到站姿（没在播也无害）。 */
    public void stopCastAnimation() {
        this.loopingCast = null;
        for (String name : CAST_ANIMATIONS) {
            this.stopTriggeredAnimation("cast", name);
            this.stopTriggeredAnimation("cast", name + LOOP_SUFFIX);
        }
    }

    /** 正在循环的施法动作（给命令回显用）。 */
    public String loopingCastAnimation() {
        return this.loopingCast;
    }

    // ------------------------------------------------------------------
    //  资源位置（字符串只写一处，模型/渲染器都从这里读）
    // ------------------------------------------------------------------

    private static final ResourceLocation GEO_SELF =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "geo/entity/self.geo.json");
    private static final ResourceLocation GEO_SELF_WAND =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "geo/entity/self_wand.geo.json");

    private static final ResourceLocation TEX_SELF =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/self_bedrock.png");
    private static final ResourceLocation TEX_SELF_WAND =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/self_wand_bedrock.png");

    private static final ResourceLocation ANIM_SELF =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "animations/entity/self.animation.json");
    private static final ResourceLocation ANIM_PLAYER_MAGIC =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "animations/entity/player_magic-animation.json");

    /** 普通外观的模型（NPC 用）。 */
    public static ResourceLocation modelResource() {
        return modelResource(false);
    }

    /** 指定外观的模型。 */
    public static ResourceLocation modelResource(boolean wand) {
        return wand ? GEO_SELF_WAND : GEO_SELF;
    }

    /** 128×128 版皮肤（经典 64×64 转出来的，见 tools/gen_npc_bedrock.py）。 */
    public static ResourceLocation textureResource() {
        return textureResource(false);
    }

    /** 指定外观的皮肤（持杖那张右边半张画的是杖）。 */
    public static ResourceLocation textureResource(boolean wand) {
        return wand ? TEX_SELF_WAND : TEX_SELF;
    }

    /** 动画文件：用户在 Blockbench 录完导出到这里（见 docs/NPC动作产线_Blockbench配方.md）。 */
    public static ResourceLocation animationResource() {
        return ANIM_SELF;
    }

    /** 指定外观的动画文件：持杖走施法动作那一份（{@code player_magic-animation.json}）。 */
    public static ResourceLocation animationResource(boolean wand) {
        return wand ? ANIM_PLAYER_MAGIC : ANIM_SELF;
    }

    /** 实例版：GeoModel 按这一只的开关取模型。 */
    public ResourceLocation renderModelResource() {
        return modelResource(this.isWand());
    }

    /** 实例版：GeoModel 按这一只的开关取贴图。 */
    public ResourceLocation renderTextureResource() {
        return textureResource(this.isWand());
    }

    /** 实例版：GeoModel 按这一只的开关取动画文件。 */
    public ResourceLocation renderAnimationResource() {
        return animationResource(this.isWand());
    }
}
