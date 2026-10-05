package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.fire.FireSpellRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * <b>施法时给锁定目标加高亮，停止施法就消失</b>（作者 2026-10-05）。
 *
 * <h2>为什么最后用"受伤闪白"这个做法</h2>
 * <p>前四版全被实测打回 ✗，记下来免得再走弯路：
 * <ol>
 *   <li><b>v1 自绘红方框</b>（{@code LevelRenderer.renderLineBox}）：<b>能亮</b> ✅
 *       但作者说"不要方框，要贴着生物外形的一圈线" ✗</li>
 *   <li><b>v2</b> {@code Entity#setGlowingTag(true)}：编译过、<b>实测不亮</b> ✗
 *       （那是"服务端要广播的标记"，不驱动客户端轮廓 ✗）</li>
 *   <li><b>v3</b> 发光状态效果 + 渲染阶段设置：<b>还是不亮</b> ✗</li>
 *   <li><b>v4</b> 发光状态效果 + 客户端 tick、去掉一切条件：<b>依然不亮</b> ✗
 *       ⇒ <b>结论：客户端挂 {@code MobEffects.GLOWING} 在你这个环境里就是不出轮廓</b> ✗
 *       （虽然包里有 20 个 mod 在用它 —— 但那些都是<b>服务端</b>挂、靠同步包下发 ✗）</li>
 *   <li><b>v5（现在）</b>：直接顶满<b>原版"受伤闪白"</b>的计时器 ✓
 *       —— 那是<b>原版渲染器自己画</b>的模型高亮 ✓，不依赖发光机制、不发包、不出声 ✓。
 *       而且作者给的第二张参考图里那圈亮，多半就是它 ✓</li>
 * </ol>
 *
 * <h2>行为</h2>
 * <ul>
 *   <li><b>正在施法</b>（按住右键 / 正在使用物品 / 正在挥臂）且准心对着能打中的生物
 *       ⇒ 每 tick 把目标的"受伤闪白"顶满 ⇒ <b>持续高亮</b> ✓</li>
 *   <li><b>停手 / 移开准心 / 退出世界</b> ⇒ 立刻清零 ⇒ <b>高亮消失</b> ✓</li>
 * </ul>
 *
 * <p>纯客户端：只改本地两个字段，不发包、不改服务端状态 ✓
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNFireTargetOutline {

    private TNFireTargetOutline() {
    }

    /** 判定距离（格）—— 比原版准心（约 3 格）远得多，又不必到 t5 的 72 格那么夸张 ✓ */
    private static final double REACH = 32.0D;

    /**
     * 每 tick 把闪白计时器顶到多少 —— 原版受伤闪白固定是 10 tick ✓
     * （顶满 = 满强度闪白；每 tick 重新顶一次 ⇒ 一直亮着 ✓）
     */
    private static final int FLASH_TICKS = 10;

    /** 上一只被点亮的生物 —— 停手时要把它清零 ✓ */
    private static LivingEntity marked;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clearMark();
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
     * <p>本模组施法由 SpellEngine 接管，法杖类里查不到 {@code isUsingItem} 的痕迹 ✗，
     * 所以接受三种信号里的任意一种 —— <b>宁可多亮一会儿，也不要该亮时不亮</b> ✓
     */
    private static boolean casting(Minecraft minecraft) {
        return minecraft.options.keyUse.isDown()
                || minecraft.player.isUsingItem()
                || minecraft.player.swinging;
    }

    /** 顶满"受伤闪白" ⇒ 原版渲染器会把模型整体高亮一下 ✓ */
    private static void mark(LivingEntity target) {
        target.hurtTime = FLASH_TICKS;
        target.hurtDuration = FLASH_TICKS;
        marked = target;
    }

    /** 清零 ⇒ 高亮立刻消失 ✓ */
    private static void clearMark() {
        if (marked != null) {
            marked.hurtTime = 0;
            marked.hurtDuration = 0;
            marked = null;
        }
    }

    /**
     * 沿视线找<b>最近的、火球链能打中的</b>生物（没有就是 null）。
     *
     * <p>和 {@code TNFireBoltEntity} 用同一套判据 ⇒ "亮出来的"就是"会命中的" ✓
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
