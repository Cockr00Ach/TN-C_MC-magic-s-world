package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.magic.DarkSiphonRules;
import com.tnc.tnc.magic.TNDarkDrainEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * ★ <b>献祭/吸血的"血线"</b>（受害者 ⇄ 施法者）—— 作者 2026-11-05：
 * 「献祭的血线也是同理」「我不希望像雷电一样瞬间就过去，你可以一节一节过去」✓。
 *
 * <h2>之前是什么样</h2>
 * 用的是 {@code NightHandGeometry.tube}：一根<b>实心管</b>，整条一次性出现 ✗ ——
 * 既不像"血在抽"，也不像"线"，更像插了根吸管。
 *
 * <h2>现在</h2>
 * 换成和黑手、黑阵同一套 {@link LightningBoltGeometry#darkBolt}（黑芯 ＋ 血红边）：
 * <ul>
 *   <li>第 i 条线晚 {@link #STAGGER} tick 出发 ⇒ 多条线<b>不是同时</b>铺满的 ✓；</li>
 *   <li>每条线自己按 {@code growth} <b>一截一截</b>从受害者长到施法者（线头带血珠）✓；</li>
 *   <li>长满之后，沿线上还有血珠往施法者那边"流"（{@link DarkSiphonRules#flow}）✓。</li>
 * </ul>
 */
public final class DarkSiphonRenderer extends EntityRenderer<TNDarkDrainEntity> {

    /** 一条线从受害者长到施法者要多少 tick。 */
    private static final double GROW_TICKS = 14.0D;

    /** 第 i 条线相对前一条晚多少 tick 出发（越大越"一串一串"地来）。 */
    private static final double STAGGER = 2.0D;

    /** 流在线上往施法者走的速度（tick 换算成 0..1 的比例）。 */
    private static final double FLOW_TICKS = 0.045D;

    public DarkSiphonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNDarkDrainEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/redstone_block.png");
    }

    @Override
    public void render(TNDarkDrainEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        var a = entity.level().getEntity(entity.victimId());
        var b = entity.level().getEntity(entity.casterId());
        if (!(a instanceof LivingEntity victim) || !(b instanceof LivingEntity caster)
                || !a.isAlive() || !b.isAlive()) {
            return;
        }

        Vec3 origin = entity.getPosition(partial);
        Vec3 start = victim.getPosition(partial).add(0.0D, victim.getBbHeight() * 0.55D, 0.0D);
        Vec3 end = caster.getPosition(partial).add(0.0D, caster.getBbHeight() * 0.62D, 0.0D);

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double cx = cam.x - origin.x;
        double cy = cam.y - origin.y;
        double cz = cam.z - origin.z;

        float age = entity.tickCount + partial;
        int strands = DarkSiphonRules.strands(entity.tier());
        boolean giant = caster instanceof com.tnc.tnc.boss.TNDarkGiantEntity;
        float width = (float) ((0.030D + entity.tier() * 0.006D) * (giant ? 10.0D : 1.0D));
        // 粗一点时淡一点，免得 t5 那条线像一堵墙 ✗
        float alpha = (float) Mth.clamp(0.95D / Math.max(1.0D, width / 0.05D), 0.45D, 0.95D);

        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(LightningRenderTypes.DARK);
        long seed = entity.getId() * 1315423911L;

        Vec3 s0 = start.subtract(origin);
        Vec3 e0 = end.subtract(origin);

        for (int i = 0; i < strands; i++) {
            // 每条错开 ⇒ 血线是一串一串铺过去的，不是同时出现 ✓
            double grow = Mth.clamp((age - i * STAGGER) / GROW_TICKS, 0.0D, 1.0D);
            if (grow <= 0.02D) {
                continue;
            }
            LightningBoltGeometry.darkBolt(vc, m,
                    s0.x, s0.y, s0.z, e0.x, e0.y, e0.z,
                    seed + i * 977L, width, alpha, grow, cx, cy, cz);
        }

        // 长满之后：血珠沿着线往施法者那边流 ⇒ "血被抽走了" ✓
        if (age > GROW_TICKS + strands * STAGGER) {
            float beadRadius = Math.max(0.045F, width * 1.6F);
            for (int k = 0; k < DarkSiphonRules.BEADS; k++) {
                double f = DarkSiphonRules.flow(age, k);
                // flow 是 0..1 循环；让它从受害者(0) 走向施法者(1) ✓
                Vec3 p = s0.lerp(e0, f);
                float fade = (float) Math.sin(f * Math.PI);
                LightningBoltGeometry.darkBead(vc, m, p.x, p.y, p.z, beadRadius,
                        alpha * 0.55F * fade, cx, cy, cz);
            }
        }
    }
}
