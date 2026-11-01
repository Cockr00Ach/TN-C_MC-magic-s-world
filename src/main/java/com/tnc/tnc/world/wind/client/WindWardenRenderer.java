package com.tnc.tnc.world.wind.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.client.WindConstructModels;
import com.tnc.tnc.world.wind.WindWardenAttackRules;
import com.tnc.tnc.world.wind.WindWardenEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders the boss and its conjured weapon.
 *
 * <p>The weapon is not a bone in the geo model: it is the shared wind arsenal
 * model ({@code tnc:wind/arsenal/sword} / {@code spear}) that the player's own
 * wind spells use, drawn at the {@code weapon_1} anchor bone. That keeps one
 * single definition of what a "wind blade" looks like in this pack.
 */
public final class WindWardenRenderer extends GeoEntityRenderer<WindWardenEntity> {
    /** Arsenal model index: 1 = wind wing sword, 2 = piercing wind spear. */
    private static final int SWORD_FORM = 1;
    private static final int SPEAR_FORM = 2;

    public WindWardenRenderer(EntityRendererProvider.Context context) {
        super(context, new WindWardenModel());
        withScale(WindWardenAttackRules.BODY_SCALE);
        this.shadowRadius = 1.6F;
    }

    @Override
    public void renderRecursively(PoseStack pose, WindWardenEntity entity, GeoBone bone,
                                  RenderType type, MultiBufferSource buffers, VertexConsumer out,
                                  boolean reRender, float partial, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        // Emissive parts (eyes, throat vent) read better at full brightness.
        String name = bone.getName();
        if (name.equals("eyes")) {
            light = LightTexture.FULL_BRIGHT;
        }
        super.renderRecursively(pose, entity, bone, type, buffers, out, reRender,
                partial, light, overlay, red, green, blue, alpha);
        if (!reRender && name.equals("weapon_1")) {
            drawWeapon(pose, bone, entity, buffers);
        }
    }

    private void drawWeapon(PoseStack pose, GeoBone bone, WindWardenEntity entity,
                            MultiBufferSource buffers) {
        if (!entity.isAlive() || entity.shifting()) {
            return;
        }
        // The arsenal model points along -Z, so the blade is rolled upright and
        // canted forward to read as "held", not "mounted on the fist".
        int form = entity.isSpearPhase() ? SPEAR_FORM : SWORD_FORM;
        pose.pushPose();
        // The anchor's own transform is already popped by the time this hook runs, so the
        // pose is re-seated on its pivot first (same fix as the Canglan warlord's renderer).
        WindConstructModels.atBone(pose, bone);
        pose.mulPose(Axis.XP.rotationDegrees(-74.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(entity.isSpearPhase() ? 4.0F : -8.0F));
        float scale = entity.isSpearPhase() ? 1.45F : 1.30F;
        pose.scale(scale, scale, scale);
        WindConstructModels.weapon(form, pose, buffers, 0.94F, false);
        pose.popPose();
    }

    @Override
    protected float getDeathMaxRotation(WindWardenEntity entity) {
        return 0.0F;
    }
}
