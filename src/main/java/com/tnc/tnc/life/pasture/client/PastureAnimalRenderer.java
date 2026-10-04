package com.tnc.tnc.life.pasture.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.pasture.PastureAnimal;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** One renderer instance per unique entity layer and its matching UV atlas. */
public final class PastureAnimalRenderer extends MobRenderer<PastureAnimal, PastureAnimalModel> {
    private final ResourceLocation texture;

    public PastureAnimalRenderer(EntityRendererProvider.Context context, String species) {
        super(context, new PastureAnimalModel(context.bakeLayer(PastureGeometry.LAYERS.get(species)), species), shadow(species));
        texture = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/pasture/" + species + ".png");
        addLayer(new ResourceGlow(this, species));
    }

    private static float shadow(String species) {
        return switch (species) {
            case "stonebarrow_boar", "ringstone_tortoise" -> .65F;
            case "emberback_hog" -> .57F;
            case "tideback_newt" -> .55F;
            case "froststride_fowl", "mirrorwing_moth" -> .38F;
            case "apiary_toad", "mistbelly_otter" -> .46F;
            case "starfelt_hare" -> .34F;
            case "dusk_lantern_deer", "springhoof_strider" -> .59F;
            case "pattern_shell_snail", "wirecall_lizard" -> .43F;
            case "post_heron" -> .41F;
            case "watch_mantis" -> .36F;
            case "forgegill_tapir" -> .72F;
            case "papersail_ray" -> .56F;
            case "dewbound_whale" -> .71F;
            case "satchelback_runner" -> .62F;
            case "bowlhorn_rhino" -> .81F;
            case "patternbuild_beaver" -> .47F;
            default -> .37F;
        };
    }

    @Override public ResourceLocation getTextureLocation(PastureAnimal entity) { return texture; }
    @Override protected void scale(PastureAnimal entity, PoseStack pose, float partial) {
        float scale=com.tnc.tnc.life.pasture.PastureFlight.scale(entity.speciesId());
        pose.scale(scale,scale,scale);
    }

    /** Alpha-zero ordinary UV islands keep this glow strictly on luminous anatomy. */
    private static final class ResourceGlow extends RenderLayer<PastureAnimal, PastureAnimalModel> {
        private final RenderType renderType;
        ResourceGlow(PastureAnimalRenderer parent, String species) {
            super(parent);
            renderType = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(TNMod.MODID,
                    "textures/entity/pasture/" + species + "_glow.png"));
        }
        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, PastureAnimal animal,
                                     float limbSwing, float limbAmount, float partialTick, float age,
                                     float yaw, float pitch) {
            if (animal.isInvisible()) return;
            float strength = getParentModel().glowStrength();
            getParentModel().renderToBuffer(pose, buffers.getBuffer(renderType), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, strength, strength, strength, 1.0F);
        }
    }
}
