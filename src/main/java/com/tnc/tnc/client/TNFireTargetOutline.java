package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.fire.FireSpellRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * <b>施法时给锁定目标描一圈轮廓</b>（作者 2026-10-05：
 * 「准心指向生物时，生物的边框会有红色线条表示该法术锁定的目标」→ 后来改成
 * 「我希望是第二张图中的黄色线条那样」）。
 *
 * <h2>演进</h2>
 * <ol>
 *   <li><b>第一版</b>：自己用 {@code LevelRenderer.renderLineBox} 画红方框 ✗ —— 作者说不要方框 ✗</li>
 *   <li><b>第二版</b>：{@code Entity#setGlowingTag(true)} ✗ —— <b>作者实测"没有实现"</b> ✗
 *       （那个方法设的是"服务端要广播的发光标记"，<b>不直接驱动客户端轮廓</b> ✗）</li>
 *   <li><b>现在</b>：直接给实体挂 <b>原版「发光」状态效果</b>
 *       （{@link MobEffects#GLOWING}）✓ —— 这是"发光轮廓"的<b>正规来源</b>，
 *       {@code isCurrentlyGlowing()} 会因它而真 ✓，轮廓由原版渲染器画 ✓</li>
 * </ol>
 *
 * <p>⚠️ 挂的是 {@code (duration, amplifier, ambient=false, visible=false, showIcon=false)}
 * —— 后两个 false 表示<b>不显示 HUD 图标、不冒粒子</b> ✓，所以玩家只会看到轮廓，
 * 不会在状态栏多出一个"发光"图标 ✗
 *
 * <p>纯客户端：不发包、不改服务端状态 ✓（{@code setGlowingTag} 仍然保留作为双保险 ✓）
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNFireTargetOutline {

    private TNFireTargetOutline() {
    }

    /** 判定距离（格）—— 比原版准心（约 3 格）远得多，又不必到 t5 的 72 格那么夸张 ✓ */
    private static final double REACH = 32.0D;

    /** 发光效果每次续多久（tick）—— 每帧续一次，10 tick 足够稳 ✓ */
    private static final int GLOW_TICKS = 10;

    /** 上一帧标亮的生物 —— 松开右键 / 移开准心时要把它的发光清掉 ✓ */
    private static LivingEntity marked;

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }

        LivingEntity target = casting(minecraft) ? aimedAt(minecraft) : null;

        if (marked != null && marked != target) {
            clearMark();
        }
        if (target != null) {
            mark(target);
        }
    }

    /**
     * 玩家是不是"正在施法"。
     *
     * <p>⚠️ 这是<b>近似</b>：本模组施法由 SpellEngine 接管，法杖类里查不到
     * {@code isUsingItem} 的痕迹 ✗，所以这里接受三种信号里的任意一种
     * （按住右键 / 正在使用物品 / 正在挥动手臂）——
     * <b>宁可多亮一会儿，也不要该亮的时候不亮</b> ✓
     */
    private static boolean casting(Minecraft minecraft) {
        return minecraft.options.keyUse.isDown()
                || minecraft.player.isUsingItem()
                || minecraft.player.swinging;
    }

    /** 给目标挂上发光轮廓（两种手段一起上，确保一定看得见 ✓）。 */
    private static void mark(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0,
                false, false, false));
        target.setGlowingTag(true);
        marked = target;
    }

    /** 把上一只的发光清掉（不然它会一直亮 ✗）。 */
    private static void clearMark() {
        if (marked != null) {
            marked.removeEffect(MobEffects.GLOWING);
            marked.setGlowingTag(false);
            marked = null;
        }
    }

    private static void clear() {
        clearMark();
    }

    /**
     * 沿视线找<b>最近的、火球链能打中的</b>生物（没有就是 null）。
     *
     * <p>和 {@code TNFireBoltEntity} 用同一套判据，所以"亮出来的"就是"会命中的" ✓
     */
    private static LivingEntity aimedAt(Minecraft minecraft) {
        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 end = eye.add(minecraft.player.getViewVector(1.0F).scale(REACH));

        LivingEntity best = null;
        double closest = REACH * REACH;
        for (LivingEntity candidate : minecraft.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(1.0D),
                t -> FireSpellRules.hittable(minecraft.player, t))) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.25D).clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < closest) {
                closest = hit.get().distanceToSqr(eye);
                best = candidate;
            }
        }
        return best;
    }
}
