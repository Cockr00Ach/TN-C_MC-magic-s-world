package com.tnc.tnc.life.pasture.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.life.pasture.PastureAnimal;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared animation runner; the skeletons and proportions are species-specific. */
public final class PastureAnimalModel extends EntityModel<PastureAnimal> {
    private final ModelPart root;
    private final String species;
    private final Map<String, ModelPart> bones = new LinkedHashMap<>();
    private float glowStrength = 1.0F;

    public PastureAnimalModel(ModelPart root, String species) {
        this.root = root;
        this.species = species;
        bones.put("root", root);
        for (String[] pair : PastureGeometry.bones(species)) {
            bones.put(pair[0], bones.get(pair[1]).getChild(pair[0]));
        }
    }

    private void turn(String bone, float x, float y, float z) {
        ModelPart p = bones.get(bone);
        if (p != null) { p.xRot += x; p.yRot += y; p.zRot += z; }
    }
    private void move(String bone, float x, float y, float z) {
        ModelPart p = bones.get(bone);
        if (p != null) { p.x += x; p.y += y; p.z += z; }
    }
    private void scale(String bone, float x, float y, float z) {
        ModelPart p = bones.get(bone);
        if (p != null) { p.xScale *= x; p.yScale *= y; p.zScale *= z; }
    }
    private void show(String bone, boolean visible) {
        ModelPart p = bones.get(bone);
        if (p != null) p.visible = visible;
    }
    private void walk(float swing, float amount, float pace, float strength) {
        float a = Mth.cos(swing * pace) * amount * strength;
        turn("leg_fl", a, 0, 0); turn("leg_br", a, 0, 0);
        turn("leg_fr", -a, 0, 0); turn("leg_bl", -a, 0, 0);
        turn("leg_ml", -a * .7F, 0, 0); turn("leg_mr", a * .7F, 0, 0);
    }
    private void flap(String left, String right, float phase, float strength) {
        float f = Mth.sin(phase) * strength;
        turn(left, 0, 0, f); turn(right, 0, 0, -f);
    }

    @Override
    public void setupAnim(PastureAnimal entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(p -> { p.resetPose(); p.visible = true; });
        float partial = Mth.clamp(ageInTicks - entity.tickCount, 0.0F, 1.0F);
        float t = entity.animationPhase(partial);
        float moving = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float touch = Mth.sin(entity.getAttackAnim(partial) * Mth.PI);
        float work = entity.isWorking() ? 1.0F : 0.0F;
        float stored = Mth.clamp(entity.resourceLevel(), 0.0F, 1.0F);
        if (entity.productionReady()) stored = Math.max(stored, .8F);
        float breath = Mth.sin(t) * .012F;
        scale("body", 1.0F, 1.0F + breath, 1.0F);
        turn("head", headPitch * Mth.DEG_TO_RAD * .65F + touch * .2F,
                netHeadYaw * Mth.DEG_TO_RAD * .6F, 0);
        glowStrength = (.25F + .75F * stored) * (.9F + .1F * Mth.sin(t * 1.3F));
        for (var entry : bones.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith("resource_sac") || name.startsWith("resource_bag")) {
                float s = .62F + .38F * stored;
                scale(name, s, .7F + .3F * stored, s);
            }
            if (name.startsWith("pip_")) show(name, stored > Integer.parseInt(name.substring(4)) / 4.0F);
        }
        if (young) {
            scale("head", 1.18F, 1.18F, 1.18F);
            scale("horn_l", .5F, .5F, .5F); scale("horn_r", .5F, .5F, .5F);
            scale("bowl_horn", .6F, .6F, .6F);
        }

        switch (species) {
            case "stonebarrow_boar" -> {
                walk(limbSwing, moving, .72F, .65F);
                turn("head", work * (.18F + .14F * Mth.sin(t * 4)) - touch * .38F, 0, Mth.sin(t * .7F) * .025F);
                turn("tail", 0, Mth.sin(t * 2) * .12F, 0);
            }
            case "emberback_hog" -> {
                walk(limbSwing, moving, .67F, .72F);
                float vent = .04F + .10F * stored + .04F * Mth.sin(t * 1.3F) + touch * .14F;
                turn("vent_l", 0, 0, -vent); turn("vent_r", 0, 0, vent);
                turn("head", work * .23F + Mth.sin(t * 2.8F) * touch * .15F, 0, 0);
                turn("tail", 0, Mth.sin(t * 2.1F) * .2F, 0);
            }
            case "tideback_newt" -> {
                boolean water = entity.isInWaterOrBubble();
                float swim = water ? .2F + moving * .6F : .08F;
                turn("body", 0, Mth.sin(t * 3) * swim * .18F, 0);
                turn("tail", 0, Mth.sin(t * 3) * swim, 0);
                turn("tail_tip", 0, Mth.sin(t * 3 - .8F) * swim, 0);
                walk(limbSwing, moving, .85F, water ? .12F : .4F);
                for (int i = 0; i < 3; i++) scale("resource_sac" + i, 1, 1 + stored * .08F * Mth.sin(t + i), 1);
                turn("head", -touch * .18F, 0, work * .04F);
            }
            case "froststride_fowl" -> {
                walk(limbSwing, moving, .88F, .85F);
                move("body", 0, Math.abs(Mth.sin(limbSwing * .88F)) * moving * .6F, 0);
                turn("head", touch * .65F + work * .2F, 0, 0);
                flap("wing_l", "wing_r", t * 4, touch * .5F + work * .08F);
                turn("tail", 0, Mth.sin(t * 1.2F) * .08F, 0);
            }
            case "apiary_toad" -> {
                float hop = Math.max(0, Mth.sin(limbSwing * 1.2F)) * moving;
                root.y -= hop * 2.2F;
                turn("leg_bl", hop * -.5F, 0, 0); turn("leg_br", hop * -.5F, 0, 0);
                turn("leg_fl", hop * .2F, 0, 0); turn("leg_fr", hop * .2F, 0, 0);
                scale("throat", 1 + .08F * Mth.sin(t * 2), 1 + .1F * Math.max(0, Mth.sin(t * 2)) + work * .15F, 1);
                turn("head", -touch * .3F, 0, 0);
            }
            case "starfelt_hare" -> {
                float hop = Math.max(0, Mth.sin(limbSwing * 1.8F)) * moving;
                root.y -= hop * 2.5F; turn("body", hop * -.18F, 0, 0);
                turn("leg_fl", hop * -.45F, 0, 0); turn("leg_fr", hop * -.45F, 0, 0);
                turn("leg_bl", hop * .65F, 0, 0); turn("leg_br", hop * .65F, 0, 0);
                turn("ear_l", Mth.sin(t * 1.3F) * .05F - touch * .2F, 0, work * -.15F);
                turn("ear_r", Mth.sin(t * 1.3F + 1) * .05F - touch * .2F, 0, work * .15F);
                turn("head", touch * -.22F, 0, 0);
            }
            case "dusk_lantern_deer" -> {
                walk(limbSwing, moving, .64F, .8F);
                turn("neck", Mth.sin(t * .7F) * .025F + work * .18F, 0, 0);
                turn("head", touch * -.3F + work * .3F, 0, 0);
                turn("tail", 0, Mth.sin(t * 1.6F) * .16F, 0);
                show("horn_l", !young); show("horn_r", !young);
                glowStrength *= entity.level().isDay() ? .12F : 1.0F;
            }
            case "pattern_shell_snail" -> {
                scale("body", 1, 1, 1 + Mth.sin(t * 3) * moving * .045F);
                move("head", 0, 0, -moving * .35F + touch * .7F);
                turn("antenna_l", touch * .5F, Mth.sin(t * .8F) * .1F, 0);
                turn("antenna_r", touch * .5F, -Mth.sin(t * .8F + 1) * .1F, 0);
                turn("shell", 0, Mth.sin(t * .5F) * .025F, 0);
                scale("resource_glue", 1, .5F + stored, 1);
            }
            case "post_heron" -> {
                walk(limbSwing, moving, .75F, .6F);
                boolean airborne = !entity.onGround() && !entity.isInWaterOrBubble();
                flap("wing_l", "wing_r", t * 4.5F, airborne ? .65F : touch * .25F);
                flap("wing_l_tip", "wing_r_tip", t * 4.5F - .6F, airborne ? .35F : .02F);
                turn("neck", touch * .32F + work * .12F, 0, 0);
                turn("head", touch * -.32F + work * .17F, 0, 0);
                scale("resource_postbag", 1 + stored * .15F, 1 + stored * .15F, 1);
            }
            case "watch_mantis" -> {
                walk(limbSwing, moving, .98F, .35F);
                turn("arm_l", touch * -.8F + work * Mth.sin(t * 3) * .25F, 0, 0);
                turn("arm_r", touch * -.8F - work * Mth.sin(t * 3) * .25F, 0, 0);
                turn("arm_l_sickle", touch * .55F, 0, 0); turn("arm_r_sickle", touch * .55F, 0, 0);
                turn("antenna_l", Mth.sin(t * 1.4F) * .08F, 0, .03F * Mth.sin(t));
                turn("antenna_r", Mth.sin(t * 1.4F + 1) * .08F, 0, -.03F * Mth.sin(t));
                glowStrength *= entity.isWorking() ? .6F + .4F * Mth.sin(t * 8) : .4F;
            }
            case "mirrorwing_moth" -> {
                boolean airborne = !entity.onGround();
                float f = airborne ? .65F : .09F + touch * .4F + work * .15F;
                flap("wing_front_l", "wing_front_r", t * (airborne ? 7 : 1.2F), f);
                flap("wing_back_l", "wing_back_r", t * (airborne ? 7 : 1.2F) - .8F, f * .85F);
                if (airborne) root.y += Mth.sin(t * 1.6F) * .25F;
                walk(limbSwing, moving, 1.2F, .1F);
                turn("antenna_l", touch * -.3F, 0, 0); turn("antenna_r", touch * -.3F, 0, 0);
            }
            case "forgegill_tapir" -> {
                walk(limbSwing, moving, .56F, .6F);
                for (int i = 0; i < 4; i++) {
                    float fan = (.03F + .045F * stored) * Mth.sin(t * 1.7F - i * .5F) + work * .04F;
                    turn("gill_l" + i, 0, -fan, -fan); turn("gill_r" + i, 0, fan, fan);
                }
                turn("head", touch * .32F + work * -.12F, 0, 0);
                turn("tail", 0, Mth.sin(t * 1.3F) * .1F, 0);
            }
            case "mistbelly_otter" -> {
                boolean water = entity.isInWaterOrBubble();
                walk(limbSwing, moving, .9F, water ? .16F : .5F);
                turn("tail", water ? Mth.sin(t * 3) * .25F : .02F, Mth.sin(t * 2) * (.1F + moving * .15F), 0);
                turn("tail_tip", water ? Mth.sin(t * 3 - .5F) * .15F : 0, 0, 0);
                if (water) { turn("body", -.12F, 0, Mth.sin(t) * .03F); root.y += Mth.sin(t * 1.2F) * .2F; }
                turn("head", touch * -.3F + work * .1F, 0, 0);
            }
            case "ringstone_tortoise" -> {
                walk(limbSwing, moving, .4F, .38F);
                for (int i = 0; i < 4; i++) {
                    turn("ring_" + i, Mth.sin(t * .65F + i) * .04F, Mth.sin(t * .4F + i) * .13F, 0);
                    move("ring_" + i, 0, Mth.sin(t * .6F + i) * .2F * stored, 0);
                }
                move("head", 0, 0, touch * .65F);
                scale("resource_pebbles", 1, .5F + stored * .5F, 1);
            }
            case "papersail_ray" -> {
                boolean airborne = !entity.onGround();
                float f = airborne ? .13F : .045F;
                flap("wing_l", "wing_r", t * 1.9F, f + touch * .2F);
                flap("wing_l_mid", "wing_r_mid", t * 1.9F - .55F, f * 1.2F);
                flap("wing_l_tip", "wing_r_tip", t * 1.9F - 1.0F, f * 1.4F);
                if (airborne) { root.y += Mth.sin(t) * .45F; turn("leg_fl", .8F, 0, 0); turn("leg_fr", .8F, 0, 0); }
                else walk(limbSwing, moving, 1.05F, .22F);
                turn("tail", 0, Mth.sin(t * 1.4F) * .18F, 0);
                turn("tail_tip", Mth.sin(t) * .06F, Mth.sin(t * 1.4F - .8F) * .12F, 0);
                scale("resource_air", 1, .65F + stored * .35F, 1);
            }
            case "wirecall_lizard" -> {
                walk(limbSwing, moving, 1.12F, .28F);
                turn("body", 0, Mth.sin(limbSwing * 1.12F) * moving * .09F, 0);
                turn("tail", 0, Mth.sin(t * 2.5F) * (.05F + moving * .18F), 0);
                turn("tail_tip", 0, Mth.sin(t * 2.5F - .8F) * .12F, 0);
                turn("tail_end", 0, touch * -.25F, 0);
                for (int i = 0; i < 4; i++) {
                    float r = Mth.sin(t * 4 - i) * stored * .035F;
                    turn("ridge_l" + i, 0, 0, -r); turn("ridge_r" + i, 0, 0, r);
                }
            }
            case "dewbound_whale" -> {
                root.y += Mth.sin(t * .65F) * .65F;
                turn("body", Mth.sin(t * .6F) * .035F - touch * .04F, 0, Mth.sin(t * .4F) * .035F);
                flap("fin_front_l", "fin_front_r", t * 1.6F, .12F + moving * .15F + touch * .15F);
                flap("fin_back_l", "fin_back_r", t * 1.6F - .9F, .1F + moving * .12F);
                turn("tail", Mth.sin(t * 1.3F) * (.09F + moving * .2F), 0, 0);
                turn("tail_tip", Mth.sin(t * 1.3F - .6F) * .13F, 0, 0);
                turn("head", touch * -.13F + work * .07F, 0, 0);
            }
            case "satchelback_runner" -> {
                walk(limbSwing, moving, .72F, .75F);
                turn("resource_bag_l", 0, 0, Mth.sin(limbSwing * .72F) * moving * .07F);
                turn("resource_bag_r", 0, 0, -Mth.sin(limbSwing * .72F) * moving * .07F);
                turn("bag_lid_l", touch * -.55F - work * .05F, 0, 0);
                turn("bag_lid_r", touch * -.55F - work * .05F, 0, 0);
                turn("head", work * .12F - touch * .2F, 0, 0);
                turn("tail", 0, Mth.sin(t * 1.3F) * .08F, 0);
            }
            case "bowlhorn_rhino" -> {
                walk(limbSwing, moving, .47F, .55F);
                turn("head", work * (.4F + .08F * Mth.sin(t * 4)) - touch * .2F, 0, 0);
                turn("plate_l", 0, 0, -.025F * Mth.sin(t)); turn("plate_r", 0, 0, .025F * Mth.sin(t));
                turn("tail", 0, Mth.sin(t * 1.5F) * .12F, 0);
            }
            case "pageforage_raccoon" -> {
                walk(limbSwing, moving, .8F, .55F);
                turn("arm_l", Mth.cos(limbSwing * .8F + Mth.PI) * moving * .55F - touch * .8F - work * .28F, 0, 0);
                turn("arm_r", Mth.cos(limbSwing * .8F) * moving * .55F - touch * .8F - work * .28F, 0, 0);
                turn("tail", 0, Mth.sin(t * 1.6F) * (.12F + work * .18F), 0);
                turn("head", work * .3F - touch * .3F, 0, 0);
                scale("resource_paperbag", 1, 1 + stored * .15F, .7F + stored * .3F);
            }
            case "patternbuild_beaver" -> {
                walk(limbSwing, moving, .65F, .5F);
                float pat = work * (.3F + .2F * Mth.sin(t * 5)) + touch * .65F;
                turn("arm_l", -pat, 0, 0); turn("arm_r", -pat, 0, 0);
                turn("head", work * .15F - touch * .25F, 0, 0);
                turn("tail", entity.isInWaterOrBubble() ? Mth.sin(t * 3) * .2F : -.02F, Mth.sin(t) * .05F, 0);
            }
            case "springhoof_strider" -> {
                walk(limbSwing, moving, .65F, .85F);
                boolean leap = !entity.onGround() && !entity.isInWaterOrBubble();
                if (leap) {
                    turn("leg_fl", -.45F, 0, 0); turn("leg_fr", -.45F, 0, 0);
                    turn("leg_bl", .4F, 0, 0); turn("leg_br", .4F, 0, 0);
                    turn("body", -.12F, 0, 0);
                }
                turn("head", touch * -.22F + work * .09F, 0, 0);
                turn("tail", 0, Mth.sin(t * 1.4F) * .15F, 0);
                show("resource_saddle", entity.isVehicle());
            }
            case "pillowlight_marten" -> {
                walk(limbSwing, moving, 1.3F, .6F);
                turn("body", 0, Mth.sin(limbSwing * 1.3F) * moving * .08F, 0);
                turn("tail", 0, Mth.sin(t * 1.8F) * (.12F + moving * .2F), 0);
                turn("tail_tip", 0, Mth.sin(t * 1.8F - .5F) * .12F, 0);
                turn("head", touch * -.25F + work * .08F, 0, 0);
                scale("resource_lamp", 1, .8F + stored * .2F, 1);
            }
            default -> throw new IllegalStateException("Missing pasture animation: " + species);
        }
    }

    public float glowStrength() { return glowStrength; }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay,
                                         float red, float green, float blue, float alpha) {
        pose.pushPose();
        if (young) { pose.translate(0.0D, .675D, 0.0D); pose.scale(.55F, .55F, .55F); }
        root.render(pose, consumer, light, overlay, red, green, blue, alpha);
        pose.popPose();
    }
}
