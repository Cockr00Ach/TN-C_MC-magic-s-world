package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.fire.FireSpellRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * <b>施法时给锁定目标描一圈轮廓</b>。
 *
 * <h2>演进（两轮作者实测反馈）</h2>
 * <ol>
 *   <li><b>第一版</b>：自己用 {@code LevelRenderer.renderLineBox} 在目标外面画一个<b>红色方框</b> ✗
 *       —— 作者 2026-10-05 看了截图说：不要方框，要像参考图那样<b>贴着生物外形的一圈黄线</b> ✗</li>
 *   <li><b>现在</b>：直接用<b>原版的「发光」轮廓</b>（{@code Entity#setGlowingTag}）✓
 *       —— 它就是那个效果：沿模型轮廓描一圈、自动处理遮挡与深度 ✓
 *       而且这是纯客户端标记，不发包、不改服务端状态 ✓</li>
 * </ol>
 *
 * <h2>什么时候亮</h2>
 * <p>只有<b>正在施法</b>（按住右键）且准心对着生物时才亮 ✓
 * （作者 2026-10-05：「只有当瞄准目标释放法术时才会有红线」✗ —— 一直亮会干扰视线）。
 *
 * <h2>为什么不用原版准心判定</h2>
 * <p>原版 {@code hitResult} 的实体距离只有约 3 格 ✗，而火球最远打到 72 格 ——
 * 那样"锁定"基本没意义。这里自己按 {@link #REACH} 做扫掠，并且
 * <b>判据与火球完全一致</b>（同一个 {@link FireSpellRules#hittable}）
 * ⇒ <b>亮了就一定打得到，不亮就打不到</b> ✓
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNFireTargetOutline {

    private TNFireTargetOutline() {
    }

    /** 判定距离（格）—— 比原版准心（约 3 格）远得多，又不必到 t5 的 72 格那么夸张 ✓ */
    private static final double REACH = 32.0D;

    /**
     * 上一帧标亮的生物 —— 用来在<b>松开右键 / 移开准心</b>时把它的发光标记清掉 ✓
     *
     * <p>⚠️ 别的实体不能不管：发光标记是**共享标记位**（会同步），
     * 留着不清的话那只怪会一直亮着 ✗
     */
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

        // 只有「正在施法」才提示：本模组用法杖施法就是按住右键 ✓（作者要求不要一直亮 ✗）
        LivingEntity target = minecraft.options.keyUse.isDown() ? aimedAt(minecraft) : null;

        if (marked != null && marked != target) {
            marked.setGlowingTag(false);          // 上一只恢复原样 ✓
            marked = null;
        }
        if (target != null) {
            target.setGlowingTag(true);           // 原版轮廓：贴外形的一圈线 ✓
            marked = target;
        }
    }

    /** 退出世界 / 换维度时别把发光标记落在实体上（{@code level == null} 时会被调到 ✓）。 */
    private static void clear() {
        if (marked != null) {
            marked.setGlowingTag(false);
            marked = null;
        }
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
