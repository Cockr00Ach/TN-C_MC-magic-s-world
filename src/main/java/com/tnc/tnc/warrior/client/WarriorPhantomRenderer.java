package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tnc.tnc.warrior.WarriorPhantom;
import com.tnc.tnc.warrior.WarriorWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Weapon-bound overlay: uses the actual held-item render invocation and its final mounting matrix. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorPhantomRenderer {

    /** 出过一次错就彻底停用：宁可没虚影，也不能把玩家卡掉线。 */
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static volatile boolean disabled;
    private static boolean logged;
    private record Ghost(ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,
                         int light,BakedModel model,float alpha) {}
    private static final WarriorPhantomFrame<Ghost> WORLD=new WarriorPhantomFrame<>(64);
    private static MultiBufferSource.BufferSource worldBuffer;
    private static Matrix4f worldModelView;
    private static float worldFogStart,worldFogEnd;

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
        if(WarriorChargeVisual.scale(player.getId(),Minecraft.getInstance().getFrameTime())>0 || WarriorHeavyGrowth.usesEntityGrowth(WarriorWeapons.family(player.getMainHandItem())))return 1;
        float peak=(float)(player==Minecraft.getInstance().player?scale():WarriorPhantomScales.get(player.getUUID()));
        var a=WarriorAnimationClient.active(player.getId());
        var mc=Minecraft.getInstance();
        if(a!=null && (a.hold() || a.progress(mc.getFrameTime())<1))
            return WarriorHeavyGrowth.phantomScale(a.animation(),a.hold(),a.progress(mc.getFrameTime()),peak);
        return WarriorHeavyGrowth.phantomScale(com.tnc.tnc.warrior.WarriorAnimation.SLASH_HORIZONTAL,false,
                player.getAttackAnim(mc.getFrameTime()),peak);
    }

    public static void renderBound(net.minecraft.world.entity.LivingEntity entity, ItemStack stack,
            ItemDisplayContext context, boolean left, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (disabled() || !(entity instanceof AbstractClientPlayer player) || !WarriorWeapons.bladed(stack)
                || WarriorChargeVisual.scale(player.getId(),Minecraft.getInstance().getFrameTime())>0
                || WarriorHeavyGrowth.usesEntityGrowth(WarriorWeapons.family(stack))
                || left!=(player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)
                || (!context.firstPerson() && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) return;
        var ultimate=WarriorAnimationClient.active(player.getId());if(ultimate!=null&&ultimate.animation()==com.tnc.tnc.warrior.WarriorAnimation.ULTIMATE&&com.tnc.tnc.warrior.WarriorPolearmUltimates.kind(stack).equals("halberd"))return;
        try {
            renderWeapon(player, stack, context, left, poseStack, buffer, packedLight);
        } catch (RuntimeException | LinkageError error) {
            disable(error);
        }
    }

    private static void renderWeapon(AbstractClientPlayer player, ItemStack stack, ItemDisplayContext context,
            boolean left, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float visibility=WarriorTrail.ghostVisibility(player);
        if(visibility<=.001f)return;
        double scale = player == Minecraft.getInstance().player ? scale() : WarriorPhantomScales.get(player.getUUID());
        // 始终比真剑大 2%：完全重合会和真模型 z-fighting，寒毛一点就看不出来了
        double draw = Math.max(scaleFor(player), 1.02);
        boolean combo = WarriorTrail.finisher(player.getId());
        float alpha = alpha(scale, combo)*visibility*(context.firstPerson() ? .65f : 1f);

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
            var model=WarriorGhostModels.find(stack,baked);
            if(context.firstPerson()) {
                renderGhost(stack,context,left,poseStack,buffer,packedLight,model,alpha);
            } else {
                // Preserve the actual mounted pose, not an approximation reconstructed from the player arm.
                var captured=new PoseStack();
                captured.last().pose().set(poseStack.last().pose());
                captured.last().normal().set(poseStack.last().normal());
                if(WORLD.add(player.level(),new Ghost(stack.copy(),context,left,captured,packedLight,model,alpha))
                        && worldModelView==null) {
                    worldModelView=new Matrix4f(RenderSystem.getModelViewMatrix());
                    worldFogStart=RenderSystem.getShaderFogStart();worldFogEnd=RenderSystem.getShaderFogEnd();
                }
            }
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

    /** Per-face opacity stays low even when the authored silhouette contains several overlapping boxes. */
    static float opacity(double scale, boolean combo) {
        double grown = Math.max(0,Math.min(1,(scale-3)/7));
        return (float)(combo ? .10+.04*grown : .07+.03*grown);
    }

    /** Attack opacity only; the envelope gate above removes all idle ghost submissions. */
    private static float alpha(double scale, boolean combo) {
        if(!combo)return opacity(scale,false);
        float pulse = 0.975f + 0.025f * (float) Math.sin(net.minecraft.Util.getNanos() / 240_000_000.0);
        return opacity(scale,true)*pulse;
    }

    private static void renderGhost(ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,
                                    MultiBufferSource buffer,int light,BakedModel baked,float alpha) {
        pose.pushPose();
        try {
            var model=ForgeHooksClient.handleCameraTransforms(pose,baked,context,left);
            if(model.isCustomRenderer())return;
            pose.translate(-.5,-.5,-.5);
            var out=new Alpha(buffer.getBuffer(WarriorPhantomRenderType.TYPE),alpha);
            // Render each baked pass once. Routing both glint and item passes to the same ghost buffer
            // duplicated enchanted sword geometry and made overlapping silhouettes nearly opaque.
            for(var pass:model.getRenderPasses(stack,true))
                if(!pass.isCustomRenderer())Minecraft.getInstance().getItemRenderer()
                        .renderModelLists(pass,stack,light,OverlayTexture.NO_OVERLAY,pose,out);
        } finally {pose.popPose();}
    }

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if(event.phase==TickEvent.Phase.START){WORLD.clear();worldModelView=null;}
    }

    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        var level=Minecraft.getInstance().level;
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            WORLD.begin(disabled()?null:level);worldModelView=null;return;
        }
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var ghosts=WORLD.end(level);
        if(ghosts.isEmpty() || disabled() || worldModelView==null)return;
        // AFTER_LEVEL is after Fabulous composition and before the first-person depth clear.
        // The event pose here is a projection helper, not the captured entity mounting matrix.
        var view=RenderSystem.getModelViewStack();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        view.pushPose();view.last().pose().set(worldModelView);RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderFogStart(worldFogStart);RenderSystem.setShaderFogEnd(worldFogEnd);
        try {
            if(worldBuffer==null)worldBuffer=MultiBufferSource.immediate(new BufferBuilder(65536));
            for(var ghost:ghosts)renderGhost(ghost.stack(),ghost.context(),ghost.left(),ghost.pose(),
                    worldBuffer,ghost.light(),ghost.model(),ghost.alpha());
            worldBuffer.endBatch();
        } catch(RuntimeException | LinkageError error) {
            worldBuffer=null;disable(error);
        } finally {
            view.popPose();RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    /** 只改 alpha 的顶点消费者：颜色、UV、光照都原样透传。 */
    static final class Alpha extends VertexConsumerWrapper {
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
            // Calling super.color(float) would dispatch back to color(int) and multiply alpha twice.
            parent.color(red,green,blue,a*alpha);
            return this;
        }

        @Override
        public void defaultColor(int red,int green,int blue,int a) {
            parent.defaultColor(red,green,blue,Math.round(a*alpha));
        }
    }
}
