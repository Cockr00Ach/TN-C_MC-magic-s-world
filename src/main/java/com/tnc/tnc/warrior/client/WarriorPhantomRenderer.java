package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.warrior.WarriorPhantom;
import com.tnc.tnc.warrior.WarriorWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;
import org.joml.Vector3f;

/** Weapon-bound overlay: uses the actual held-item render invocation and its final mounting matrix. */
public final class WarriorPhantomRenderer {

    /** 出过一次错就彻底停用：宁可没虚影，也不能把玩家卡掉线。 */
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static volatile boolean disabled;
    private static boolean logged;

    private WarriorPhantomRenderer() {
    }

    static boolean disabled() {
        return disabled || Boolean.getBoolean("tnc.phantom.off");
    }

    static void disable(Throwable error) {
        disabled = true;
        if (logged) {
            return;
        }
        logged = true;
        LOGGER.error("[tnc] 虚影渲染出错，已自动关掉虚影（剑照常）。请把这段发给开发者：", error);
    }

    /** 当前玩家的虚影倍率：读的是 {@link TemperingClient} 早就同步好的那份镜像。 */
    static double scale() {
        var progress = TemperingClient.progress();
        return WarriorPhantom.scale(progress.points(), progress.cap());
    }

    /**
     * 任意玩家的虚影倍率：自己读本地进度，别人读同步过来的那份（{@link WarriorPhantomScales}）。
     *
     * <p>刀光要用它来知道"虚影剑尖在哪" —— 外圈那片斩气是跟着虚影走的，
     * 所以别人屏幕上也能看到他放大了多少。
     */
    static double scaleFor(AbstractClientPlayer player) {
        return player == Minecraft.getInstance().player ? scale() : WarriorPhantomScales.get(player.getUUID());
    }

    public static void renderBound(net.minecraft.world.entity.LivingEntity entity, ItemStack stack,
            ItemDisplayContext context, boolean left, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (disabled() || !(entity instanceof AbstractClientPlayer player) || !WarriorWeapons.bladed(stack)
                || (context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) return;
        try {
            renderWeapon(player, stack, context, left, poseStack, buffer, packedLight);
        } catch (RuntimeException | LinkageError error) {
            disable(error);
        }
    }

    private static void renderWeapon(AbstractClientPlayer player, ItemStack stack, ItemDisplayContext context,
            boolean left, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        double scale = player == Minecraft.getInstance().player ? scale() : WarriorPhantomScales.get(player.getUUID());
        // 始终比真剑大 2%：完全重合会和真模型 z-fighting，寒毛一点就看不出来了
        double draw = Math.max(scale, 1.02);
        boolean combo = WarriorTrail.finisher(player.getId());
        float alpha = alpha(scale, combo);

        // ItemInHandRenderer uses entity id + display context as the model override seed.
        int seed = player.getId() + context.ordinal();
        BakedModel baked = Minecraft.getInstance().getItemRenderer().getModel(stack, player.level(), player, seed);
        ItemTransform transform = baked.getTransforms().getTransform(context);
        // A displayed model is T * R * S * (model - 0.5), not just T.
        // Transform the actual grip through the same item transform, including left-hand mirroring.
        PoseStack itemPose = new PoseStack();
        transform.apply(left, itemPose);
        net.minecraft.world.phys.Vec3 grip = grip(stack);
        Vector3f anchor = com.tnc.tnc.warrior.WarriorGripGeometry.anchor(itemPose.last().pose(),
                (float)grip.x, (float)grip.y, (float)grip.z);

        poseStack.pushPose();
        try {
            // The caller matrix already contains the real weapon mount, including two-handed adjustments.
            // Only grow the mesh about its displayed grip; never attach to an arm a second time.
            Vector3f compensation = com.tnc.tnc.warrior.WarriorGripGeometry.compensation(anchor, draw);
            poseStack.translate(compensation.x(), compensation.y(), compensation.z());
            poseStack.scale((float) draw, (float) draw, (float) draw);
            Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, context, left, poseStack,
                    ghost(buffer, alpha), player.level(), packedLight, OverlayTexture.NO_OVERLAY, seed);
        } finally {
            // 必须在 finally 里弹栈：中间抛异常而不弹，整帧的 PoseStack 就不平衡了
            poseStack.popPose();
        }
    }

    /** Actual authoring grip centers, in untransformed model coordinates (pixels / 16). */
    private static net.minecraft.world.phys.Vec3 grip(ItemStack stack) {
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        var authored = WarriorModelGrip.find(key);
        if (authored.isPresent()) return authored.get();
        String id = key.toString();
        return switch (id) {
            case "tnc:dragon_sword" -> new net.minecraft.world.phys.Vec3(7 / 16.0, .5 / 16.0, 2.05 / 16.0);
            case "tnc:wind_relic_sword" -> new net.minecraft.world.phys.Vec3(.5, .5, -.8 / 16.0);
            default -> stack.getItem() instanceof net.minecraft.world.item.SwordItem
                    && (id.startsWith("minecraft:") || id.equals("tnc:greatsword"))
                    ? new net.minecraft.world.phys.Vec3(3 / 16.0, 3 / 16.0, .5)
                    : new net.minecraft.world.phys.Vec3(.5, .5, .5);
        };
    }

    /**
     * 透明度：磨砺 0 时虚影和真剑等大、只是薄薄一层（"一直都有虚影跟着"）；
     * 连招放出来的那几帧亮一档并轻微脉动（"只有释放连招时才明显"）。
     */
    private static float alpha(double scale, boolean combo) {
        double grown = (scale - 1.0) / WarriorPhantom.MAX_GROWTH;
        float base = (float) (0.14 + 0.26 * grown);
        if (!combo) {
            return base;
        }
        float pulse = 0.88f + 0.12f * (float) Math.sin(System.currentTimeMillis() / 70.0);
        return Math.min(0.85f, (float) (0.40 + 0.34 * grown) * pulse);
    }

    private static MultiBufferSource ghost(MultiBufferSource base, float alpha) {
        return new MultiBufferSource() {
            @Override
            public VertexConsumer getBuffer(RenderType type) {
                // 物品模型走的是 entityCutout（不透明裁剪），这里统一换成半透明版本
                RenderType ghostType = RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
                return new Alpha(base.getBuffer(ghostType), alpha);
            }
        };
    }

    /** 只改 alpha 的顶点消费者：颜色、UV、光照都原样透传。 */
    private static final class Alpha extends VertexConsumerWrapper {
        private final float alpha;

        Alpha(VertexConsumer parent, float alpha) {
            super(parent);
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int a) {
            return super.color(red, green, blue, Math.round(a * alpha));
        }

        @Override
        public VertexConsumer color(float red, float green, float blue, float a) {
            return super.color(red, green, blue, a * alpha);
        }
    }
}
