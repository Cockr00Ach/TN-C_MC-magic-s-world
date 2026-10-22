package com.tnc.tnc.world.wind.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.client.WindConstructModels;
import com.tnc.tnc.world.wind.CanglanWarlordAttackRules;
import com.tnc.tnc.world.wind.CanglanWarlordEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders the boss and the arsenal blade in its fist.
 *
 * <p>The warlord models no weapon: it has an empty {@code weapon_1} anchor on the
 * right fist, and this renderer draws the same wind arsenal model the player's own
 * wind magic conjures — the sword in phase one, the long spear in phase two. That
 * is what makes the phase shift a real weapon change without a second rig.
 *
 * <p>Grip numbers ({@code GEAR}) are the same the offline blade solver used, so
 * the wrist keys baked into the animation clips put the blade exactly where the
 * rig audit measured it.
 */
public final class CanglanWarlordRenderer extends GeoEntityRenderer<CanglanWarlordEntity> {
    private static final java.util.Map<CanglanWarlordEntity,net.minecraft.world.phys.Vec3> FACING=new java.util.WeakHashMap<>();
    public static net.minecraft.world.phys.Vec3 facing(CanglanWarlordEntity e){return FACING.getOrDefault(e,net.minecraft.world.phys.Vec3.directionFromRotation(0,e.yBodyRot));}
    private org.joml.Matrix4f entityFrameInverse;
    private net.minecraft.world.phys.Vec3 spearTip;
    private org.joml.Matrix4f eyePose;
    private org.joml.Matrix3f eyeNormal;
    private GeoBone eyeBone;
    private static final net.minecraft.resources.ResourceLocation EYES=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/canglan_eyes_emissive.png");
    private org.joml.Matrix4f gripPose;
    private org.joml.Matrix3f gripNormal;
    private GeoBone gripBone;
    /** Arsenal model index, shared with {@link WindConstructModels#weapon}. */
    private static final int SWORD_FORM = CanglanWarlordAttackRules.FORM_SWORD;
    private static final int SPEAR_FORM = CanglanWarlordAttackRules.FORM_SPEAR;

    /** Roll/pitch that stand the item model up into a held blade, per form. */
    private static final float SWORD_PITCH = -76.0F;
    private static final float SWORD_ROLL = -8.0F;
    private static final float SPEAR_PITCH = -80.0F;
    private static final float SPEAR_ROLL = 4.0F;
    /** The boss is 6.75 blocks tall, so the blade is scaled up with it. */
    private static final float SWORD_SCALE = 2.10F;
    private static final float SPEAR_SCALE = 2.40F;
    /** Slides the fist along the haft instead of gripping the butt cap. */
    // Sword grip centre is (8,8,-0.8) in the item mesh; spear grip is (8,8,0).
    // Compensate the model's -0.5 block centring *after* applying weapon scale.
    private static final float SWORD_GRIP = 1.155F;
    private static final float SPEAR_GRIP = 1.20F;

    public CanglanWarlordRenderer(EntityRendererProvider.Context context) {
        super(context, new CanglanWarlordModel());
        withScale(CanglanWarlordAttackRules.BODY_SCALE);
        this.shadowRadius = 2.0F;
    }
    @Override public RenderType getRenderType(CanglanWarlordEntity e,net.minecraft.resources.ResourceLocation texture,MultiBufferSource b,float partial) {
        return e.phase()==2 ? RenderType.entityTranslucent(texture) : super.getRenderType(e,texture,b,partial);
    }
    @Override public software.bernie.geckolib.core.object.Color getRenderColor(CanglanWarlordEntity e,float partial,int light) {
        return e.phase()==2 ? software.bernie.geckolib.core.object.Color.ofRGBA(.55F,.95F,1F,.48F) : super.getRenderColor(e,partial,light);
    }
    @Override public void render(CanglanWarlordEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        gripPose=null;gripNormal=null;gripBone=null;eyePose=null;eyeNormal=null;eyeBone=null;spearTip=null;
        entityFrameInverse=new org.joml.Matrix4f(pose.last().pose()).invert();
        pose.pushPose();float scale=e.battleScale();pose.scale(scale,scale,scale);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180-e.yBodyRot));pose.translate(0,5.5,.9);
        pose.mulPose(Axis.XP.rotationDegrees(90));pose.mulPose(Axis.YP.rotationDegrees((e.tickCount+partial)*1.2F));
        pose.scale(4.2F,1,4.2F);pose.translate(-.5,0,-.5);
        var mc=net.minecraft.client.Minecraft.getInstance();
        mc.getItemRenderer().renderModelLists(mc.getModelManager().getModel(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","wind/seal")),
                net.minecraft.world.item.ItemStack.EMPTY,LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                pose,buffers.getBuffer(RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)));
        pose.popPose();
        super.render(e,yaw,partial,pose,buffers,e.phase()==2?LightTexture.FULL_BRIGHT:light);
        if(eyePose!=null){
            pose.pushPose();pose.last().pose().set(eyePose);pose.last().normal().set(eyeNormal);
            RenderType glow=RenderType.eyes(EYES);
            super.renderRecursively(pose,e,eyeBone,glow,buffers,buffers.getBuffer(glow),true,partial,
                    LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,1,1,1,1);
            pose.popPose();
        }
        if(gripPose!=null)drawBlade(pose,gripBone,e,buffers);
        pose.popPose();
        if(spearTip!=null)CanglanSpearTrail.render(e,spearTip,partial,pose,buffers);
    }

    @Override
    public void renderRecursively(PoseStack pose, CanglanWarlordEntity entity, GeoBone bone,
                                  RenderType type, MultiBufferSource buffers, VertexConsumer out,
                                  boolean reRender, float partial, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        String name = bone.getName();
        // The mask's eye slit is emissive: it has to survive a dark arena.
        int boneLight = name.equals("eyes") ? LightTexture.FULL_BRIGHT : light;
        super.renderRecursively(pose, entity, bone, type, buffers, out, reRender,
                partial, boneLight, overlay, red, green, blue, alpha);
        if(!reRender&&name.equals("eyes")){
            eyePose=new org.joml.Matrix4f(pose.last().pose());eyeNormal=new org.joml.Matrix3f(pose.last().normal());eyeBone=bone;
            pose.pushPose();WindConstructModels.atBone(pose,bone);
            var direction=new org.joml.Vector3f(0,0,-1);
            new org.joml.Matrix4f(entityFrameInverse).mul(pose.last().pose()).transformDirection(direction).normalize();
            FACING.put(entity,new net.minecraft.world.phys.Vec3(direction.x,direction.y,direction.z));pose.popPose();
        }
        if (!reRender && name.equals("weapon_1")) {
            // Cache the animated palm without changing texture buffers mid-body.
            pose.pushPose();WindConstructModels.atBone(pose, bone);
            gripPose=new org.joml.Matrix4f(pose.last().pose());
            gripNormal=new org.joml.Matrix3f(pose.last().normal());gripBone=bone;
            pose.popPose();
        }
    }

    /**
     * Draws the phase's arsenal blade on the anchor. The anchor bone itself stays
     * empty, so every pose the animator authored for the fist carries the blade
     * for free — the clip's wrist keys do the aiming.
     *
     * <p>{@code super.renderRecursively} has already popped the anchor's own transform by
     * the time this runs, so the pose is re-seated on the anchor's pivot first
     * ({@link WindConstructModels#atBone}); without that the blade hangs off the hand bone
     * and the wrist keys of all thirteen clips do nothing in game, which is exactly what the
     * offline blade solver assumed they did.
     */
    private void drawBlade(PoseStack pose, GeoBone bone, CanglanWarlordEntity entity,
                           MultiBufferSource buffers) {
        if (!entity.isAlive()) {
            return;
        }
        boolean spear = entity.weaponForm() == SPEAR_FORM;
        int form = spear ? SPEAR_FORM : SWORD_FORM;
        pose.pushPose();
        pose.last().pose().set(gripPose);pose.last().normal().set(gripNormal);
        // Item-model +Z is the blade: roll it upright, then cant it forward so it
        // reads as held rather than mounted flat on the back of the hand.
        pose.mulPose(Axis.ZP.rotationDegrees(spear ? SPEAR_ROLL : SWORD_ROLL));
        pose.mulPose(Axis.XP.rotationDegrees(spear ? SPEAR_PITCH : SWORD_PITCH));
        pose.translate(0.0F, 0.0F, spear ? SPEAR_GRIP : SWORD_GRIP);
        float scale = spear ? SPEAR_SCALE : SWORD_SCALE;
        pose.scale(scale, scale, scale);
        if(spear){
            // Current shared spear's real tip is (8,8,25.11) in 1/16-block item units.
            var tip=new org.joml.Vector3f(0,0,1.069375F);
            new org.joml.Matrix4f(entityFrameInverse).mul(pose.last().pose()).transformPosition(tip);
            spearTip=new net.minecraft.world.phys.Vec3(tip.x,tip.y,tip.z);
        }
        if(entity.phase()==2) WindConstructModels.phantomWeapon(form,entity.tickCount,pose,buffers,.68F,false);
        else WindConstructModels.weapon(form, pose, buffers, 0.96F, false);
        pose.popPose();
    }

    @Override
    protected float getDeathMaxRotation(CanglanWarlordEntity entity) {
        return 0.0F;
    }
}
