package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.warrior.WeaponChampion;
import com.tnc.tnc.warrior.WeaponChampionDuel;
import com.tnc.tnc.warrior.WeaponChampionEntity;
import com.tnc.tnc.warrior.WarriorWeaponRanks.Branch;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WeaponChampionRenderer extends HumanoidMobRenderer<WeaponChampionEntity, HumanoidModel<WeaponChampionEntity>> {
    private final ResourceLocation skin;

    private WeaponChampionRenderer(EntityRendererProvider.Context context, WeaponChampion style) {
        super(context, new Model(context, style), .45f * style.scale());
        skin = ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/champions/" + style.id() + ".png");
        // Equipment attributes remain server-side. The visible armor is built into each outfit.
    }

    @Override
    protected void scale(WeaponChampionEntity entity, PoseStack pose, float partialTick) {
        float scale = entity.champion().scale();
        float breadth = entity.champion().width() / .76f;
        pose.scale(breadth, scale, breadth);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        for (var style : WeaponChampion.values()) {
            event.registerEntityRenderer(WeaponChampionDuel.type(style), context -> new WeaponChampionRenderer(context, style));
        }
    }

    @Override
    public ResourceLocation getTextureLocation(WeaponChampionEntity entity) {
        return skin;
    }

    private static final class Model extends HumanoidModel<WeaponChampionEntity> {
        private final WeaponChampion style;
        private final List<WeaponChampionOutfit.Joint> joints;
        private final WeaponChampionOutfit.Beast beast;
        private final ModelPart weaponJoint;
        private final WeaponChampionHead headSurface;
        private int expression;

        private Model(EntityRendererProvider.Context context, WeaponChampion style) {
            this(WeaponChampionOutfit.bake(context.getResourceManager(), style), style);
        }

        private Model(WeaponChampionOutfit.Baked outfit, WeaponChampion style) {
            super(outfit.root());
            this.style = style;
            this.joints = outfit.joints();
            this.beast = outfit.beast();
            this.weaponJoint = beast == null ? null : outfit.root().getChild(beast.gripBone());
            this.headSurface = outfit.headSurface();
        }

        @Override
        public void renderToBuffer(PoseStack poses, VertexConsumer out, int light, int overlay,
                                   float red, float green, float blue, float alpha) {
            super.renderToBuffer(poses, out, light, overlay, red, green, blue, alpha);
            if (headSurface == null || !head.visible) return;
            poses.pushPose();
            head.translateAndRotate(poses);
            headSurface.render(poses.last(), out, light, overlay, red, green, blue, alpha, expression);
            poses.popPose();
        }

        @Override
        public void translateToHand(HumanoidArm arm, PoseStack pose) {
            if (beast == null) {
                super.translateToHand(arm, pose);
                return;
            }
            weaponJoint.translateAndRotate(pose);
            float[] grip = beast.grip(), rot = beast.gripRotation();
            pose.translate(grip[0] / 16, grip[1] / 16, grip[2] / 16);
            pose.mulPose(Axis.ZP.rotationDegrees(rot[2]));
            pose.mulPose(Axis.YP.rotationDegrees(rot[1]));
            pose.mulPose(Axis.XP.rotationDegrees(rot[0]));
            // ItemInHandLayer adds (-1, 10, -2) model units for the right wrist after this call.
            pose.translate((arm == HumanoidArm.RIGHT ? 1 : -1) / 16f, -10 / 16f, 2 / 16f);
        }

        @Override
        public void setupAnim(WeaponChampionEntity entity, float walk, float power, float age, float yaw, float pitch) {
            head.resetPose();
            body.resetPose();
            rightArm.resetPose();
            leftArm.resetPose();
            rightLeg.resetPose();
            leftLeg.resetPose();
            var warning = entity.warning();
            double now = entity.level().getGameTime() + age - entity.tickCount;
            float wind = warning == null ? 0 : (float) Math.sin(warning.progress(now) * Math.PI / 2);
            var impact = entity.impactCue();
            float recovery = impact == null ? 1 : (float) impact.progress(now);
            float hit = impact == null ? 0 : (float) Math.sin(recovery * Math.PI);
            float open = entity.weaponPose() == 3 ? 1 : 0;
            float breath = (float) Math.sin(age * .115f);
            float step = (float) Math.sin(walk * .67f) * Math.min(1, power);
            if (beast == null) {
                super.setupAnim(entity, 0, 0, age, yaw, pitch);
                weaponStance(entity);
                switch (style) {
                    case GREATSWORD_LEGEND -> body.yRot += .26f * wind - .34f * hit;
                    case SPEAR_LEGEND -> body.zRot += .15f * wind - .12f * hit;
                    case HALBERD_LEGEND -> head.xRot -= .12f * wind;
                    default -> {}
                }
            } else {
                if (open == 0 && impact != null) {
                    // The server can start the next windup on the same tick as the previous impact.
                    float release = Math.min(1, recovery * 4);
                    wind = Math.max(wind, 1 - release * release * (3 - 2 * release));
                    hit = recovery < .25f ? release * release * (3 - 2 * release)
                        : (float) (.5 + .5 * Math.cos((recovery - .25) / .75 * Math.PI));
                }
                expression = wind > .28f || hit > .16f ? 1
                    : Math.floorMod(entity.tickCount + entity.getId() * 17, 83) < 3 ? 2 : 0;
                head.yRot += Math.toRadians(yaw);
                head.xRot += Math.toRadians(pitch);
                animalStance(breath, step, wind, hit, open);
                if (style == WeaponChampion.SPEAR_ELITE && entity.weaponPose() == 4) {
                    float flap = (float) Math.sin(age * .36);
                    rightArm.zRot = 1.05f + .43f * flap;
                    leftArm.zRot = -rightArm.zRot;
                    rightLeg.xRot -= .50f;
                    leftLeg.xRot += .32f;
                } else if (style == WeaponChampion.SPEAR_ELITE && open > 0) {
                    rightArm.zRot += .82f;
                    leftArm.zRot -= .45f;
                    head.xRot += .55f;
                    rightLeg.y += 1.2f;
                } else if (style == WeaponChampion.GREATSWORD_MASTER && entity.weaponPose() == 5) {
                    body.xRot += .35f;
                    head.xRot += .42f;
                    rightArm.xRot -= .42f;
                    rightLeg.xRot -= .30f;
                } else if (style == WeaponChampion.HAMMER_LEGEND && open > 0) {
                    head.xRot -= .35f;
                    rightArm.zRot += .65f;
                    leftArm.zRot -= .65f;
                }
            }
            if (entity.weaponPose() >= 6) {
                float turn = switch (entity.weaponPose()) {
                    case 6 -> -.52f;
                    case 7 -> .52f;
                    case 8 -> -.85f;
                    default -> .98f;
                };
                float twist = turn * (wind - hit);
                body.yRot += twist * .48f;
                if (beast != null && beast.form() == WeaponChampionOutfit.Form.BIRD) {
                    rightLeg.yRot += twist;
                    leftArm.zRot -= Math.abs(twist) * .44f;
                } else if (style.branch() == Branch.BOW) {
                    leftArm.yRot += .50f * wind;
                    rightArm.xRot -= .33f * wind;
                } else {
                    rightArm.yRot += twist;
                    leftArm.yRot += twist * .42f;
                    rightArm.zRot -= twist * .32f;
                }
            }
            if (entity.weaponPose() == 0 && wind == 0 && hit < .05f) {
                guardedFootwork(entity, step, power);
            }
            // Use the weapon's authored keyframes on compatible skeletons. Birds carry their weapon in a claw.
            if (open == 0 && (beast == null || beast.form() == WeaponChampionOutfit.Form.UPRIGHT)) {
                boolean releasing = impact != null && impact.animation() != null && recovery < .35f;
                var cue = releasing ? impact : warning;
                if (cue != null && cue.animation() != null) {
                    var clip = WarriorMartialClips.clip(cue.animation(), entity.getMainHandItem());
                    if (clip != null) {
                        float t = releasing ? .57f + recovery : .57f * (float) cue.progress(now);
                        WarriorPose.applyClip(this, clip, t, false, beast == null ? .95f : .60f);
                    }
                }
            }
            for (var joint : joints) joint.pose(breath, step, wind, hit, open);
            hat.copyFrom(head);
        }

        private void guardedFootwork(WeaponChampionEntity entity, float step, float power) {
            float yaw = (float) Math.toRadians(entity.yBodyRot);
            double dx = entity.getX() - entity.xo;
            double dz = entity.getZ() - entity.zo;
            double advance = dz * Math.cos(yaw) - dx * Math.sin(yaw);
            float side = (float) Math.max(-1, Math.min(1, (dx * Math.cos(yaw) + dz * Math.sin(yaw)) * 6));
            float stride = advance < -.005 ? -step : step;
            boolean heavy = style.branch() == Branch.HAMMER || style.branch() == Branch.GREATSWORD;
            float load = Math.min(1, power) * (heavy ? .38f : .22f);
            if (beast != null && beast.form() == WeaponChampionOutfit.Form.BIRD) {
                // Keep the armed claw tucked; the free leg and wings carry the step.
                rightLeg.xRot = -.62f + stride * .10f;
                leftLeg.xRot = stride * .50f;
                leftLeg.y -= Math.max(0, stride) * .68f;
                rightArm.zRot += load + side * .12f;
                leftArm.zRot -= load - side * .12f;
                head.z -= Math.abs(step) * .29f;
                return;
            }
            float reach = heavy ? .24f : .39f;
            float flank = heavy ? .24f : .15f;
            rightLeg.xRot = -.20f + reach * stride;
            leftLeg.xRot = .14f - reach * stride;
            rightLeg.zRot = flank + side * step * .20f;
            leftLeg.zRot = -flank - side * step * .20f;
            rightLeg.y -= Math.max(0, stride) * .55f;
            leftLeg.y -= Math.max(0, -stride) * .55f;
            float settle = load * (1 - Math.abs(step));
            body.y += settle;
            head.y += settle;
            rightArm.y += settle;
            leftArm.y += settle;
            body.yRot += .17f + .10f * stride;
            body.zRot += side * .07f;
            if (beast == null) {
                boolean pole = style.branch() == Branch.SPEAR || style.branch() == Branch.HALBERD;
                rightArm.xRot = (heavy ? -1.30f : -.80f) + stride * .06f;
                rightArm.yRot = pole ? -.40f : .24f;
                leftArm.xRot = pole ? -1.10f : -.78f;
                leftArm.yRot = pole ? .45f : -.15f;
            } else {
                rightArm.xRot += step * (heavy ? .30f : .16f);
                leftArm.xRot -= step * (heavy ? .30f : .16f);
            }
        }

        private void weaponStance(WeaponChampionEntity entity) {
            if (attackTime > 0) return;
            if (entity.weaponPose() == 3) {
                head.xRot += .32f;
                rightArm.xRot += .22f;
                return;
            }
            if (entity.weaponPose() == 2) {
                rightArm.xRot = -1.16f;
                rightArm.yRot = -.56f;
                leftArm.xRot = -.88f;
                return;
            }
            if (entity.weaponPose() == 0) return;
            switch (style.branch()) {
                case BOW -> {
                    plantFeet(.10f, .24f);
                    rightArm.xRot = -1.57f + head.xRot;
                    leftArm.xRot = rightArm.xRot;
                    rightArm.yRot = -.15f + head.yRot;
                    leftArm.yRot = .76f + head.yRot;
                }
                case GREATSWORD, HAMMER, HALBERD -> {
                    boolean hammer = style.branch() == Branch.HAMMER;
                    boolean halberd = style.branch() == Branch.HALBERD;
                    plantFeet(halberd ? .14f : .20f, .17f);
                    rightArm.xRot = hammer ? -2.23f : -2.34f;
                    rightArm.yRot = halberd ? -.58f : -.22f;
                    rightArm.zRot = hammer ? -.45f : -.25f;
                    leftArm.xRot = -1.85f;
                    leftArm.yRot = .28f;
                }
                case SPEAR -> {
                    plantFeet(.12f, .30f);
                    rightArm.xRot = -1.25f;
                    rightArm.yRot = -.36f;
                    leftArm.xRot = -.92f;
                    leftArm.yRot = .40f;
                }
                case LONGSWORD, SCYTHE -> {
                    plantFeet(.10f, style.branch() == Branch.LONGSWORD ? .20f : -.24f);
                    rightArm.xRot = -.85f;
                    rightArm.yRot = .70f;
                    rightArm.zRot = .38f;
                }
            }
        }

        private void animalStance(float breath, float step, float wind, float hit, float open) {
            rightLeg.xRot = .48f * step;
            leftLeg.xRot = -.48f * step;
            head.xRot += .025f * breath + .17f * open;
            switch (beast.form()) {
                case UPRIGHT -> {
                    body.yRot = .16f * wind - .20f * hit;
                    rightLeg.zRot = .12f;
                    leftLeg.zRot = -.12f;
                    rightArm.xRot = -.58f - step * .24f;
                    leftArm.xRot = -.74f + step * .24f;
                    rightArm.zRot = .16f;
                    leftArm.zRot = -.22f;
                    switch (style.branch()) {
                        case HAMMER -> {
                            rightArm.xRot += -1.62f * wind + .28f * hit;
                            leftArm.xRot -= .80f * wind;
                            head.y += .55f * wind;
                            if (style == WeaponChampion.HAMMER_LEGEND) head.z += wind * .88f;
                        }
                        case BOW -> {
                            rightArm.xRot -= .94f * wind + .72f * hit;
                            leftArm.xRot -= .84f * wind;
                            leftArm.yRot += .86f * wind - .36f * hit;
                        }
                        default -> {
                            boolean heavy = style.branch() == Branch.GREATSWORD;
                            rightArm.xRot -= (heavy ? 1.24f : .74f) * wind + .35f * hit;
                            rightArm.yRot += .62f * wind - .95f * hit;
                            leftArm.zRot -= (heavy ? .22f : .54f) * hit;
                        }
                    }
                }
                case APE, BEAR -> {
                    boolean bear = beast.form() == WeaponChampionOutfit.Form.BEAR;
                    body.yRot = wind * .21f - hit * .27f;
                    rightArm.xRot = -.40f * step - (bear ? .38f : .60f) - wind * (bear ? 1.96f : .62f) + hit * .33f;
                    leftArm.xRot = .40f * step - .35f - wind * .81f;
                    rightArm.zRot = .14f + wind * .20f;
                    leftArm.zRot = -.14f - wind * .16f;
                    if (!bear) rightArm.yRot += wind * .47f - hit * .69f;
                    head.y += wind * .55f;
                }
                case BIRD -> {
                    rightArm.zRot = .09f + wind * .37f + hit * .65f;
                    leftArm.zRot = -rightArm.zRot;
                    head.z -= wind * .57f + hit * .87f;
                    head.zRot += style == WeaponChampion.SCYTHE_ELITE ? breath * .14f : step * .06f;
                    // The weapon is carried by a claw. The other leg stays planted during the draw.
                    rightLeg.xRot -= .22f + wind * .94f + hit * .30f;
                    rightLeg.y -= wind * .75f;
                    if (style == WeaponChampion.SPEAR_ELITE) {
                        rightLeg.xRot -= .52f + hit * .38f;
                        rightLeg.yRot += wind * .26f - hit * .44f;
                        leftArm.zRot -= wind * .25f;
                    }
                }
                case MANTIS -> {
                    rightArm.xRot = -.68f - wind * .75f + hit * .85f;
                    leftArm.xRot = -.58f - wind * .68f + hit * .72f;
                    rightArm.zRot = .30f + wind * .63f - hit * .68f;
                    leftArm.zRot = -.30f - wind * .63f + hit * .68f;
                    body.xRot = .04f * breath + .10f * open;
                    head.z -= .56f * hit;
                }
            }
        }

        private void plantFeet(float spread, float stride) {
            rightLeg.xRot = -stride;
            rightLeg.zRot = spread;
            leftLeg.xRot = stride * .72f;
            leftLeg.zRot = -spread;
        }
    }
}
