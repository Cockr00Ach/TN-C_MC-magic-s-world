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
    private static final float SWORD_GRIP = 0.25F;
    private static final float SPEAR_GRIP = 0.55F;

    public CanglanWarlordRenderer(EntityRendererProvider.Context context) {
        super(context, new CanglanWarlordModel());
        withScale(CanglanWarlordAttackRules.BODY_SCALE);
        this.shadowRadius = 2.0F;
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
        if (!reRender && name.equals("weapon_1")) {
            drawBlade(pose, entity, buffers);
        }
    }

    /**
     * Draws the phase's arsenal blade on the anchor. The anchor bone itself stays
     * empty, so every pose the animator authored for the fist carries the blade
     * for free — the clip's wrist keys do the aiming.
     */
    private void drawBlade(PoseStack pose, CanglanWarlordEntity entity, MultiBufferSource buffers) {
        if (!entity.isAlive()) {
            return;
        }
        boolean spear = entity.weaponForm() == SPEAR_FORM;
        int form = spear ? SPEAR_FORM : SWORD_FORM;
        pose.pushPose();
        // Item-model +Z is the blade: roll it upright, then cant it forward so it
        // reads as held rather than mounted flat on the back of the hand.
        pose.mulPose(Axis.ZP.rotationDegrees(spear ? SPEAR_ROLL : SWORD_ROLL));
        pose.mulPose(Axis.XP.rotationDegrees(spear ? SPEAR_PITCH : SWORD_PITCH));
        pose.translate(0.0F, 0.0F, spear ? SPEAR_GRIP : SWORD_GRIP);
        float scale = spear ? SPEAR_SCALE : SWORD_SCALE;
        pose.scale(scale, scale, scale);
        WindConstructModels.weapon(form, pose, buffers, 0.96F, false);
        pose.popPose();
    }

    @Override
    protected float getDeathMaxRotation(CanglanWarlordEntity entity) {
        return 0.0F;
    }
}
