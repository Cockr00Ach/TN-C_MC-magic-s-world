package com.tnc.tnc.life.pasture.client;

import com.tnc.tnc.TNMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Generated from tools/generate_pasture_models.py. Every species owns its skeleton. */
public final class PastureGeometry {
    private PastureGeometry() {}
    public static final Map<String, ModelLayerLocation> LAYERS = new LinkedHashMap<>();
    static {
        LAYERS.put("stonebarrow_boar", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "stonebarrow_boar"), "main"));
        LAYERS.put("emberback_hog", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "emberback_hog"), "main"));
        LAYERS.put("tideback_newt", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "tideback_newt"), "main"));
        LAYERS.put("froststride_fowl", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "froststride_fowl"), "main"));
        LAYERS.put("apiary_toad", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "apiary_toad"), "main"));
        LAYERS.put("starfelt_hare", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "starfelt_hare"), "main"));
        LAYERS.put("dusk_lantern_deer", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "dusk_lantern_deer"), "main"));
        LAYERS.put("pattern_shell_snail", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "pattern_shell_snail"), "main"));
        LAYERS.put("post_heron", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "post_heron"), "main"));
        LAYERS.put("watch_mantis", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "watch_mantis"), "main"));
        LAYERS.put("mirrorwing_moth", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "mirrorwing_moth"), "main"));
        LAYERS.put("forgegill_tapir", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forgegill_tapir"), "main"));
        LAYERS.put("mistbelly_otter", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "mistbelly_otter"), "main"));
        LAYERS.put("ringstone_tortoise", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "ringstone_tortoise"), "main"));
        LAYERS.put("papersail_ray", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "papersail_ray"), "main"));
        LAYERS.put("wirecall_lizard", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "wirecall_lizard"), "main"));
        LAYERS.put("dewbound_whale", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "dewbound_whale"), "main"));
        LAYERS.put("satchelback_runner", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "satchelback_runner"), "main"));
        LAYERS.put("bowlhorn_rhino", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "bowlhorn_rhino"), "main"));
        LAYERS.put("pageforage_raccoon", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "pageforage_raccoon"), "main"));
        LAYERS.put("patternbuild_beaver", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "patternbuild_beaver"), "main"));
        LAYERS.put("springhoof_strider", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "springhoof_strider"), "main"));
        LAYERS.put("pillowlight_marten", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "pillowlight_marten"), "main"));
    }
    public static LayerDefinition layer(String species) {
        return switch (species) {
            case "stonebarrow_boar" -> stonebarrow_boar();
            case "emberback_hog" -> emberback_hog();
            case "tideback_newt" -> tideback_newt();
            case "froststride_fowl" -> froststride_fowl();
            case "apiary_toad" -> apiary_toad();
            case "starfelt_hare" -> starfelt_hare();
            case "dusk_lantern_deer" -> dusk_lantern_deer();
            case "pattern_shell_snail" -> pattern_shell_snail();
            case "post_heron" -> post_heron();
            case "watch_mantis" -> watch_mantis();
            case "mirrorwing_moth" -> mirrorwing_moth();
            case "forgegill_tapir" -> forgegill_tapir();
            case "mistbelly_otter" -> mistbelly_otter();
            case "ringstone_tortoise" -> ringstone_tortoise();
            case "papersail_ray" -> papersail_ray();
            case "wirecall_lizard" -> wirecall_lizard();
            case "dewbound_whale" -> dewbound_whale();
            case "satchelback_runner" -> satchelback_runner();
            case "bowlhorn_rhino" -> bowlhorn_rhino();
            case "pageforage_raccoon" -> pageforage_raccoon();
            case "patternbuild_beaver" -> patternbuild_beaver();
            case "springhoof_strider" -> springhoof_strider();
            case "pillowlight_marten" -> pillowlight_marten();
            default -> throw new IllegalArgumentException("Unknown pasture model: " + species);
        };
    }
    public static String[][] bones(String species) {
        return switch (species) {
            case "stonebarrow_boar" -> new String[][]{{"body", "root"}, {"head", "root"}, {"leg_fl", "root"}, {"leg_fr", "root"}, {"leg_bl", "root"}, {"leg_br", "root"}, {"tail", "body"}};
            case "emberback_hog" -> new String[][]{{"body", "root"}, {"head", "root"}, {"vent_l", "body"}, {"vent_r", "body"}, {"resource_heat", "body"}, {"leg_fl", "root"}, {"leg_fr", "root"}, {"leg_bl", "root"}, {"leg_br", "root"}, {"tail", "body"}};
            case "tideback_newt" -> new String[][]{{"body", "root"}, {"head", "root"}, {"resource_sac0", "body"}, {"leg_fl", "root"}, {"leg_fr", "root"}, {"resource_sac1", "body"}, {"leg_ml", "root"}, {"leg_mr", "root"}, {"resource_sac2", "body"}, {"leg_bl", "root"}, {"leg_br", "root"}, {"tail", "body"}, {"tail_tip", "tail"}};
            case "froststride_fowl" -> new String[][]{{"body", "root"}, {"head", "root"}, {"wing_l", "body"}, {"leg_fl", "root"}, {"wing_r", "body"}, {"leg_fr", "root"}, {"tail", "body"}};
            case "apiary_toad" -> new String[][]{{"body", "root"}, {"head", "root"}, {"resource_sac_l", "body"}, {"leg_fl", "root"}, {"leg_bl", "root"}, {"resource_sac_r", "body"}, {"leg_fr", "root"}, {"leg_br", "root"}, {"throat", "head"}};
            case "starfelt_hare" -> new String[][]{{"body", "root"}, {"head", "root"}, {"ear_l", "head"}, {"leg_fl", "root"}, {"leg_bl", "root"}, {"ear_r", "head"}, {"leg_fr", "root"}, {"leg_br", "root"}, {"tail", "body"}};
            case "dusk_lantern_deer" -> new String[][]{{"body", "root"}, {"neck", "body"}, {"head", "root"}, {"horn_l", "head"}, {"horn_r", "head"}, {"leg_fl", "root"}, {"leg_fr", "root"}, {"leg_bl", "root"}, {"leg_br", "root"}, {"tail", "body"}};
            case "pattern_shell_snail" -> new String[][]{{"body", "root"}, {"head", "root"}, {"shell", "body"}, {"antenna_l", "head"}, {"antenna_r", "head"}, {"resource_glue", "body"}};
            case "post_heron" -> new String[][]{{"body", "root"}, {"neck", "body"}, {"head", "root"}, {"resource_postbag", "body"}, {"wing_l", "body"}, {"wing_l_tip", "wing_l"}, {"leg_fl", "root"}, {"wing_r", "body"}, {"wing_r_tip", "wing_r"}, {"leg_fr", "root"}, {"tail", "body"}};
            case "watch_mantis" -> new String[][]{{"body", "root"}, {"head", "root"}, {"resource_lamp", "body"}, {"antenna_l", "head"}, {"arm_l", "root"}, {"arm_l_sickle", "arm_l"}, {"leg_fl", "root"}, {"leg_fl_foot", "leg_fl"}, {"leg_bl", "root"}, {"leg_bl_foot", "leg_bl"}, {"antenna_r", "head"}, {"arm_r", "root"}, {"arm_r_sickle", "arm_r"}, {"leg_fr", "root"}, {"leg_fr_foot", "leg_fr"}, {"leg_br", "root"}, {"leg_br_foot", "leg_br"}, {"wing_l", "body"}, {"wing_r", "body"}};
            case "mirrorwing_moth" -> new String[][]{{"body", "root"}, {"head", "root"}, {"antenna_l", "head"}, {"wing_front_l", "body"}, {"wing_back_l", "body"}, {"leg_fl", "root"}, {"leg_ml", "root"}, {"leg_bl", "root"}, {"antenna_r", "head"}, {"wing_front_r", "body"}, {"wing_back_r", "body"}, {"leg_fr", "root"}, {"leg_mr", "root"}, {"leg_br", "root"}};
            case "forgegill_tapir" -> new String[][]{{"body", "root"}, {"head", "root"}, {"gill_l0", "body"}, {"gill_l1", "body"}, {"gill_l2", "body"}, {"gill_l3", "body"}, {"gill_r0", "body"}, {"gill_r1", "body"}, {"gill_r2", "body"}, {"gill_r3", "body"}, {"leg_fl", "root"}, {"leg_fr", "root"}, {"leg_bl", "root"}, {"leg_br", "root"}, {"tail", "body"}};
            case "mistbelly_otter" -> new String[][]{{"body", "root"}, {"head", "root"}, {"resource_sac_l", "body"}, {"leg_fl", "root"}, {"leg_bl", "root"}, {"resource_sac_r", "body"}, {"leg_fr", "root"}, {"leg_br", "root"}, {"tail", "body"}, {"tail_tip", "tail"}};
            case "ringstone_tortoise" -> new String[][]{{"body", "root"}, {"shell", "body"}, {"head", "root"}, {"ring_0", "body"}, {"ring_1", "body"}, {"ring_2", "body"}, {"ring_3", "body"}, {"leg_fl", "root"}, {"leg_fr", "root"}, {"leg_bl", "root"}, {"leg_br", "root"}, {"tail", "body"}, {"resource_pebbles", "body"}};
            case "papersail_ray" -> new String[][]{{"body", "root"}, {"head", "root"}, {"wing_l", "body"}, {"wing_l_mid", "wing_l"}, {"wing_l_tip", "wing_l_mid"}, {"leg_fl", "root"}, {"leg_bl", "root"}, {"wing_r", "body"}, {"wing_r_mid", "wing_r"}, {"wing_r_tip", "wing_r_mid"}, {"leg_fr", "root"}, {"leg_br", "root"}, {"tail", "body"}, {"tail_tip", "tail"}, {"resource_air", "body"}};
            case "wirecall_lizard" -> new String[][]{{"body", "root"}, {"head", "root"}, {"ridge_l0", "body"}, {"ridge_l1", "body"}, {"ridge_l2", "body"}, {"ridge_l3", "body"}, {"leg_fl", "root"}, {"leg_bl", "root"}, {"ridge_r0", "body"}, {"ridge_r1", "body"}, {"ridge_r2", "body"}, {"ridge_r3", "body"}, {"leg_fr", "root"}, {"leg_br", "root"}, {"tail", "body"}, {"tail_tip", "tail"}, {"tail_end", "tail_tip"}};
            case "dewbound_whale" -> new String[][]{{"body", "root"}, {"head", "root"}, {"fin_front_l", "body"}, {"fin_back_l", "body"}, {"fin_front_r", "body"}, {"fin_back_r", "body"}, {"pip_0", "body"}, {"pip_1", "body"}, {"pip_2", "body"}, {"pip_3", "body"}, {"tail", "body"}, {"tail_tip", "tail"}};
            case "satchelback_runner" -> new String[][]{{"body", "root"}, {"head", "root"}, {"resource_bag_l", "body"}, {"bag_lid_l", "resource_bag_l"}, {"resource_bag_r", "body"}, {"bag_lid_r", "resource_bag_r"}, {"leg_fl", "root"}, {"leg_fr", "root"}, {"leg_bl", "root"}, {"leg_br", "root"}, {"tail", "body"}};
            case "bowlhorn_rhino" -> new String[][]{{"body", "root"}, {"head", "root"}, {"bowl_horn", "head"}, {"plate_l", "body"}, {"plate_r", "body"}, {"leg_fl", "root"}, {"leg_fr", "root"}, {"leg_bl", "root"}, {"leg_br", "root"}, {"tail", "body"}};
            case "pageforage_raccoon" -> new String[][]{{"body", "root"}, {"head", "root"}, {"arm_l", "root"}, {"leg_bl", "root"}, {"arm_r", "root"}, {"leg_br", "root"}, {"resource_paperbag", "body"}, {"tail", "body"}};
            case "patternbuild_beaver" -> new String[][]{{"body", "root"}, {"head", "root"}, {"panel_l", "body"}, {"arm_l", "root"}, {"leg_bl", "root"}, {"panel_r", "body"}, {"arm_r", "root"}, {"leg_br", "root"}, {"tail", "body"}};
            case "springhoof_strider" -> new String[][]{{"body", "root"}, {"neck", "body"}, {"head", "root"}, {"horn_l", "head"}, {"leg_fl", "root"}, {"leg_bl", "root"}, {"horn_r", "head"}, {"leg_fr", "root"}, {"leg_br", "root"}, {"tail", "body"}, {"resource_saddle", "body"}};
            case "pillowlight_marten" -> new String[][]{{"body", "root"}, {"head", "root"}, {"leg_fl", "root"}, {"leg_fr", "root"}, {"leg_bl", "root"}, {"leg_br", "root"}, {"resource_lamp", "body"}, {"pip_0", "body"}, {"pip_1", "body"}, {"pip_2", "body"}, {"pip_3", "body"}, {"tail", "body"}, {"tail_tip", "tail"}};
            default -> throw new IllegalArgumentException("Unknown pasture skeleton: " + species);
        };
    }
    private static LayerDefinition stonebarrow_boar() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-7.0F, -4.0F, -9.0F, 14.0F, 8.0F, 18.0F)
                .texOffs(101, 1).addBox(-7.0F, -6.0F, -5.0F, 3.0F, 4.0F, 5.0F)
                .texOffs(39, 28).addBox(-7.0F, -5.0F, 1.0F, 3.0F, 3.0F, 5.0F)
                .texOffs(101, 1).addBox(5.0F, -6.0F, -5.0F, 3.0F, 4.0F, 5.0F)
                .texOffs(39, 28).addBox(5.0F, -5.0F, 1.0F, 3.0F, 3.0F, 5.0F)
                .texOffs(56, 28).addBox(-4.0F, -6.0F, -6.0F, 8.0F, 2.0F, 5.0F)
                .texOffs(83, 28).addBox(-3.0F, -5.0F, 1.0F, 6.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 14.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(66, 1).addBox(-5.0F, -4.0F, -5.0F, 10.0F, 7.0F, 7.0F)
                .texOffs(14, 28).addBox(-4.0F, -1.0F, -8.0F, 8.0F, 4.0F, 4.0F)
                .texOffs(119, 28).addBox(-5.0F, -2.0F, -5.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(119, 28).addBox(4.0F, -2.0F, -5.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(14, 38).addBox(-5.0F, -6.0F, -2.0F, 2.0F, 3.0F, 3.0F)
                .texOffs(118, 1).addBox(-4.0F, 2.0F, -8.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(14, 38).addBox(3.0F, -6.0F, -2.0F, 2.0F, 3.0F, 3.0F)
                .texOffs(118, 1).addBox(2.0F, 2.0F, -8.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 14.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(1, 28).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 6.0F, 3.0F)
                .texOffs(104, 28).addBox(-1.50000F, 4.0F, -2.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(-5.0F, 18.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(1, 28).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 6.0F, 3.0F)
                .texOffs(104, 28).addBox(-1.50000F, 4.0F, -2.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(5.0F, 18.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(1, 28).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 6.0F, 3.0F)
                .texOffs(104, 28).addBox(-1.50000F, 4.0F, -2.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(-5.0F, 18.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(1, 28).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 6.0F, 3.0F)
                .texOffs(104, 28).addBox(-1.50000F, 4.0F, -2.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(5.0F, 18.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(1, 38).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 9.0F, -0.40000F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition emberback_hog() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-5.0F, -4.0F, -10.0F, 10.0F, 8.0F, 21.0F), PartPose.offsetAndRotation(0.0F, 14.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(97, 1).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 7.0F)
                .texOffs(30, 31).addBox(-2.0F, -1.0F, -12.0F, 4.0F, 3.0F, 8.0F)
                .texOffs(1, 43).addBox(-2.0F, 1.0F, -13.0F, 4.0F, 2.0F, 2.0F)
                .texOffs(116, 31).addBox(-3.30000F, -1.0F, -5.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(116, 31).addBox(2.30000F, -1.0F, -5.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(81, 31).addBox(-4.0F, -5.0F, 0.0F, 2.0F, 3.0F, 4.0F)
                .texOffs(94, 31).addBox(-3.0F, 2.0F, -9.0F, 1.0F, 2.0F, 4.0F)
                .texOffs(81, 31).addBox(2.0F, -5.0F, 0.0F, 2.0F, 3.0F, 4.0F)
                .texOffs(94, 31).addBox(1.0F, 2.0F, -9.0F, 1.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 13.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition vent_l = body.addOrReplaceChild("vent_l", CubeListBuilder.create()
                .texOffs(64, 1).addBox(-2.0F, -4.0F, -5.0F, 4.0F, 5.0F, 12.0F), PartPose.offsetAndRotation(-3.0F, -3.0F, -2.0F, 0.0F, 0.0F, -0.35000F));
        PartDefinition vent_r = body.addOrReplaceChild("vent_r", CubeListBuilder.create()
                .texOffs(64, 1).addBox(-2.0F, -4.0F, -5.0F, 4.0F, 5.0F, 12.0F), PartPose.offsetAndRotation(3.0F, -3.0F, -2.0F, 0.0F, 0.0F, 0.35000F));
        PartDefinition resource_heat = body.addOrReplaceChild("resource_heat", CubeListBuilder.create()
                .texOffs(1, 31).addBox(-2.0F, -1.0F, -5.0F, 4.0F, 1.0F, 10.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(72, 31).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(105, 31).addBox(-1.0F, 4.0F, -1.50000F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-3.70000F, 18.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(72, 31).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(105, 31).addBox(-1.0F, 4.0F, -1.50000F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(3.70000F, 18.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(72, 31).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(105, 31).addBox(-1.0F, 4.0F, -1.50000F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-3.70000F, 18.0F, 9.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(72, 31).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(105, 31).addBox(-1.0F, 4.0F, -1.50000F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(3.70000F, 18.0F, 9.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(55, 31).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition tideback_newt() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-5.0F, -3.0F, -9.0F, 10.0F, 5.0F, 20.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(1, 27).addBox(-6.0F, -2.0F, -6.0F, 12.0F, 4.0F, 8.0F)
                .texOffs(67, 27).addBox(-5.0F, 1.0F, -6.0F, 10.0F, 1.0F, 7.0F)
                .texOffs(112, 1).addBox(-6.0F, -2.0F, -4.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(112, 1).addBox(5.0F, -2.0F, -4.50000F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 19.0F, -10.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition resource_sac0 = body.addOrReplaceChild("resource_sac0", CubeListBuilder.create()
                .texOffs(102, 27).addBox(-3.0F, -2.0F, -2.0F, 6.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -3.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(18, 40).addBox(-4.0F, 0.0F, -1.0F, 5.0F, 2.0F, 3.0F)
                .texOffs(1, 40).addBox(-6.0F, 1.0F, -2.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-4.50000F, 21.0F, -6.0F, 0.0F, -0.25000F, 0.32000F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(18, 40).addBox(0.0F, 0.0F, -1.0F, 5.0F, 2.0F, 3.0F)
                .texOffs(1, 40).addBox(4.0F, 1.0F, -2.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(4.50000F, 21.0F, -6.0F, 0.0F, 0.25000F, -0.32000F));
        PartDefinition resource_sac1 = body.addOrReplaceChild("resource_sac1", CubeListBuilder.create()
                .texOffs(102, 27).addBox(-3.0F, -2.0F, -2.0F, 6.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_ml = root.addOrReplaceChild("leg_ml", CubeListBuilder.create()
                .texOffs(18, 40).addBox(-4.0F, 0.0F, -1.0F, 5.0F, 2.0F, 3.0F)
                .texOffs(1, 40).addBox(-6.0F, 1.0F, -2.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-4.50000F, 21.0F, 0.0F, 0.0F, -0.25000F, 0.32000F));
        PartDefinition leg_mr = root.addOrReplaceChild("leg_mr", CubeListBuilder.create()
                .texOffs(18, 40).addBox(0.0F, 0.0F, -1.0F, 5.0F, 2.0F, 3.0F)
                .texOffs(1, 40).addBox(4.0F, 1.0F, -2.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(4.50000F, 21.0F, 0.0F, 0.0F, 0.25000F, -0.32000F));
        PartDefinition resource_sac2 = body.addOrReplaceChild("resource_sac2", CubeListBuilder.create()
                .texOffs(102, 27).addBox(-3.0F, -2.0F, -2.0F, 6.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(18, 40).addBox(-4.0F, 0.0F, -1.0F, 5.0F, 2.0F, 3.0F)
                .texOffs(1, 40).addBox(-6.0F, 1.0F, -2.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-4.50000F, 21.0F, 6.0F, 0.0F, -0.25000F, 0.32000F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(18, 40).addBox(0.0F, 0.0F, -1.0F, 5.0F, 2.0F, 3.0F)
                .texOffs(1, 40).addBox(4.0F, 1.0F, -2.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(4.50000F, 21.0F, 6.0F, 0.0F, 0.25000F, -0.32000F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(62, 1).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 4.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail_tip = tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                .texOffs(42, 27).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 3.0F, 8.0F)
                .texOffs(93, 1).addBox(-1.0F, -4.0F, 1.0F, 2.0F, 6.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition froststride_fowl() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-4.0F, -4.0F, -5.0F, 8.0F, 9.0F, 10.0F)
                .texOffs(65, 21).addBox(-2.0F, -3.0F, -6.0F, 4.0F, 4.0F, 1.0F)
                .texOffs(76, 21).addBox(-1.0F, -4.0F, -6.0F, 2.0F, 1.0F, 1.0F)
                .texOffs(76, 21).addBox(-1.0F, 1.0F, -6.0F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 11.0F, 1.0F, 0.15000F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(57, 1).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F)
                .texOffs(108, 1).addBox(-2.0F, 0.0F, -7.0F, 4.0F, 2.0F, 5.0F)
                .texOffs(83, 21).addBox(-3.20000F, -1.0F, -2.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(83, 21).addBox(2.20000F, -1.0F, -2.50000F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 7.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wing_l = body.addOrReplaceChild("wing_l", CubeListBuilder.create()
                .texOffs(38, 1).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 7.0F, 7.0F)
                .texOffs(1, 21).addBox(-1.0F, 5.0F, -2.0F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(-4.0F, -2.0F, 0.0F, 0.20000F, -0.10000F, -0.08000F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(82, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F)
                .texOffs(91, 1).addBox(-1.0F, 7.0F, -4.0F, 2.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(-2.30000F, 15.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wing_r = body.addOrReplaceChild("wing_r", CubeListBuilder.create()
                .texOffs(38, 1).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 7.0F, 7.0F)
                .texOffs(1, 21).addBox(-1.0F, 5.0F, -2.0F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(4.0F, -2.0F, 0.0F, 0.20000F, 0.10000F, 0.08000F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(82, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F)
                .texOffs(91, 1).addBox(-1.0F, 7.0F, -4.0F, 2.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(2.30000F, 15.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(16, 21).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 2.0F, 4.0F)
                .texOffs(37, 21).addBox(-2.0F, -1.0F, 3.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(54, 21).addBox(-1.0F, -2.0F, 6.0F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 5.0F, -0.38000F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition apiary_toad() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-6.0F, -4.0F, -5.0F, 12.0F, 6.0F, 11.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(71, 1).addBox(-5.0F, -2.0F, -3.0F, 10.0F, 4.0F, 5.0F)
                .texOffs(33, 19).addBox(-4.0F, -4.0F, -1.0F, 3.0F, 3.0F, 3.0F)
                .texOffs(82, 19).addBox(-3.50000F, -3.0F, -2.0F, 2.0F, 2.0F, 1.0F)
                .texOffs(33, 19).addBox(2.0F, -4.0F, -1.0F, 3.0F, 3.0F, 3.0F)
                .texOffs(82, 19).addBox(2.50000F, -3.0F, -2.0F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 18.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition resource_sac_l = body.addOrReplaceChild("resource_sac_l", CubeListBuilder.create()
                .texOffs(48, 1).addBox(-2.0F, -2.0F, -3.0F, 4.0F, 4.0F, 7.0F), PartPose.offsetAndRotation(-5.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(20, 19).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 4.0F)
                .texOffs(65, 19).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-4.0F, 21.0F, -4.0F, 0.0F, 0.0F, -0.24000F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(102, 1).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 3.0F, 6.0F)
                .texOffs(1, 19).addBox(-2.0F, 3.0F, 1.0F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-5.0F, 20.0F, 4.0F, 0.0F, 0.32000F, 0.0F));
        PartDefinition resource_sac_r = body.addOrReplaceChild("resource_sac_r", CubeListBuilder.create()
                .texOffs(48, 1).addBox(-2.0F, -2.0F, -3.0F, 4.0F, 4.0F, 7.0F), PartPose.offsetAndRotation(5.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(20, 19).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 4.0F)
                .texOffs(65, 19).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(4.0F, 21.0F, -4.0F, 0.0F, 0.0F, 0.24000F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(102, 1).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 3.0F, 6.0F)
                .texOffs(1, 19).addBox(-2.0F, 3.0F, 1.0F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(5.0F, 20.0F, 4.0F, 0.0F, -0.32000F, 0.0F));
        PartDefinition throat = head.addOrReplaceChild("throat", CubeListBuilder.create()
                .texOffs(46, 19).addBox(-3.0F, 0.0F, -1.0F, 6.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 1.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition starfelt_hare() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 7.0F, 8.0F)
                .texOffs(30, 1).addBox(-4.0F, -6.0F, 0.0F, 8.0F, 7.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 18.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(59, 1).addBox(-3.0F, -3.0F, -4.0F, 6.0F, 6.0F, 5.0F)
                .texOffs(44, 17).addBox(-1.0F, 0.0F, -5.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(53, 17).addBox(-3.20000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(53, 17).addBox(2.20000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 16.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition ear_l = head.addOrReplaceChild("ear_l", CubeListBuilder.create()
                .texOffs(101, 1).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 8.0F, 2.0F)
                .texOffs(110, 1).addBox(-0.50000F, -7.0F, -1.50000F, 1.0F, 6.0F, 1.0F)
                .texOffs(58, 17).addBox(-0.50000F, -6.0F, -2.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(58, 17).addBox(-0.50000F, -4.0F, -2.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(58, 17).addBox(-0.50000F, -2.0F, -2.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-1.60000F, -3.0F, -1.0F, 0.10000F, 0.0F, -0.12000F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(35, 17).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(22, 17).addBox(-1.0F, 3.0F, -3.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-2.0F, 20.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(82, 1).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 5.0F)
                .texOffs(1, 17).addBox(-2.0F, 5.0F, -4.0F, 4.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(-3.0F, 18.0F, 5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition ear_r = head.addOrReplaceChild("ear_r", CubeListBuilder.create()
                .texOffs(101, 1).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 8.0F, 2.0F)
                .texOffs(110, 1).addBox(-0.50000F, -7.0F, -1.50000F, 1.0F, 6.0F, 1.0F)
                .texOffs(58, 17).addBox(-0.50000F, -6.0F, -2.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(58, 17).addBox(-0.50000F, -4.0F, -2.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(58, 17).addBox(-0.50000F, -2.0F, -2.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(1.60000F, -3.0F, -1.0F, -0.10000F, 0.0F, 0.12000F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(35, 17).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(22, 17).addBox(-1.0F, 3.0F, -3.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(2.0F, 20.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(82, 1).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 5.0F)
                .texOffs(1, 17).addBox(-2.0F, 5.0F, -4.0F, 4.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(3.0F, 18.0F, 5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(115, 1).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 5.0F, -0.35000F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition dusk_lantern_deer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-4.0F, -3.0F, -7.0F, 8.0F, 7.0F, 15.0F), PartPose.offsetAndRotation(0.0F, 9.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                .texOffs(48, 1).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 8.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, -0.25000F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(76, 1).addBox(-3.0F, -2.0F, -5.0F, 6.0F, 5.0F, 7.0F)
                .texOffs(1, 24).addBox(-2.0F, 1.0F, -8.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(88, 24).addBox(-3.20000F, 0.0F, -5.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(88, 24).addBox(2.20000F, 0.0F, -5.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(51, 24).addBox(-5.0F, -2.0F, 0.0F, 4.0F, 1.0F, 3.0F)
                .texOffs(51, 24).addBox(1.0F, -2.0F, 0.0F, 4.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition horn_l = head.addOrReplaceChild("horn_l", CubeListBuilder.create()
                .texOffs(103, 1).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(66, 24).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(75, 24).addBox(-3.0F, -5.0F, -1.0F, 4.0F, 1.0F, 2.0F)
                .texOffs(42, 24).addBox(-4.0F, -7.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(18, 24).addBox(-1.0F, -6.0F, 0.0F, 2.0F, 1.0F, 4.0F)
                .texOffs(42, 24).addBox(-1.0F, -8.0F, 3.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, -2.0F, -1.0F, 0.0F, 0.0F, -0.18000F));
        PartDefinition horn_r = head.addOrReplaceChild("horn_r", CubeListBuilder.create()
                .texOffs(103, 1).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(66, 24).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(75, 24).addBox(0.0F, -5.0F, -1.0F, 4.0F, 1.0F, 2.0F)
                .texOffs(42, 24).addBox(2.0F, -7.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(18, 24).addBox(-1.0F, -6.0F, 0.0F, 2.0F, 1.0F, 4.0F)
                .texOffs(42, 24).addBox(-1.0F, -8.0F, 3.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(2.0F, -2.0F, -1.0F, 0.0F, 0.0F, 0.18000F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(67, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F)
                .texOffs(31, 24).addBox(-1.0F, 9.0F, -1.50000F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-2.60000F, 13.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(67, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F)
                .texOffs(31, 24).addBox(-1.0F, 9.0F, -1.50000F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(2.60000F, 13.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(67, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F)
                .texOffs(31, 24).addBox(-1.0F, 9.0F, -1.50000F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-2.60000F, 13.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(67, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F)
                .texOffs(31, 24).addBox(-1.0F, 9.0F, -1.50000F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(2.60000F, 13.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, -0.30000F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition pattern_shell_snail() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-4.0F, -1.0F, -8.0F, 8.0F, 3.0F, 16.0F)
                .texOffs(66, 21).addBox(-3.0F, 0.0F, 7.0F, 6.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 22.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(1, 21).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 21.0F, -7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shell = body.addOrReplaceChild("shell", CubeListBuilder.create()
                .texOffs(50, 1).addBox(-5.0F, -7.0F, -5.0F, 10.0F, 8.0F, 10.0F)
                .texOffs(91, 1).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 3.0F, 8.0F)
                .texOffs(22, 21).addBox(-2.0F, -12.0F, -2.0F, 5.0F, 2.0F, 5.0F)
                .texOffs(92, 21).addBox(-1.0F, -13.0F, -1.0F, 3.0F, 1.0F, 3.0F)
                .texOffs(1, 30).addBox(-5.0F, -5.0F, -6.0F, 10.0F, 2.0F, 1.0F)
                .texOffs(114, 21).addBox(-3.0F, -8.0F, -6.0F, 2.0F, 3.0F, 1.0F)
                .texOffs(24, 30).addBox(-1.0F, -8.0F, -6.0F, 4.0F, 1.0F, 1.0F)
                .texOffs(121, 21).addBox(2.0F, -7.0F, -6.0F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(1.0F, -2.0F, 2.0F, 0.0F, 0.12000F, 0.0F));
        PartDefinition antenna_l = head.addOrReplaceChild("antenna_l", CubeListBuilder.create()
                .texOffs(87, 21).addBox(-0.50000F, -4.0F, -0.50000F, 1.0F, 4.0F, 1.0F)
                .texOffs(105, 21).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, -1.0F, -2.0F, -0.35000F, 0.0F, -0.20000F));
        PartDefinition antenna_r = head.addOrReplaceChild("antenna_r", CubeListBuilder.create()
                .texOffs(87, 21).addBox(-0.50000F, -4.0F, -0.50000F, 1.0F, 4.0F, 1.0F)
                .texOffs(105, 21).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(2.0F, -1.0F, -2.0F, -0.35000F, 0.0F, 0.20000F));
        PartDefinition resource_glue = body.addOrReplaceChild("resource_glue", CubeListBuilder.create()
                .texOffs(43, 21).addBox(-3.0F, 0.0F, -1.0F, 6.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 1.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition post_heron() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-3.0F, -3.0F, -4.0F, 6.0F, 7.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 11.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                .texOffs(91, 1).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(102, 1).addBox(-2.0F, -2.0F, -3.0F, 4.0F, 4.0F, 5.0F)
                .texOffs(68, 1).addBox(-1.0F, 0.0F, -11.0F, 2.0F, 1.0F, 9.0F)
                .texOffs(52, 18).addBox(-1.0F, -3.0F, 0.0F, 2.0F, 1.0F, 4.0F)
                .texOffs(72, 18).addBox(-2.20000F, -1.0F, -2.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(72, 18).addBox(1.20000F, -1.0F, -2.50000F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition resource_postbag = body.addOrReplaceChild("resource_postbag", CubeListBuilder.create()
                .texOffs(22, 18).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 4.0F, 3.0F)
                .texOffs(65, 18).addBox(-1.0F, 0.0F, -2.60000F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wing_l = body.addOrReplaceChild("wing_l", CubeListBuilder.create()
                .texOffs(32, 1).addBox(-1.0F, 0.0F, -2.0F, 2.0F, 7.0F, 7.0F), PartPose.offsetAndRotation(-3.0F, -2.0F, 0.0F, 0.0F, 0.0F, -0.13000F));
        PartDefinition wing_l_tip = wing_l.addOrReplaceChild("wing_l_tip", CubeListBuilder.create()
                .texOffs(51, 1).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 5.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 6.0F, 2.0F, 0.20000F, 0.0F, 0.0F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(121, 1).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 8.0F, 1.0F)
                .texOffs(37, 18).addBox(-1.0F, 8.0F, -3.0F, 2.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-1.70000F, 15.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wing_r = body.addOrReplaceChild("wing_r", CubeListBuilder.create()
                .texOffs(32, 1).addBox(-1.0F, 0.0F, -2.0F, 2.0F, 7.0F, 7.0F), PartPose.offsetAndRotation(3.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.13000F));
        PartDefinition wing_r_tip = wing_r.addOrReplaceChild("wing_r_tip", CubeListBuilder.create()
                .texOffs(51, 1).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 5.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 6.0F, 2.0F, 0.20000F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(121, 1).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 8.0F, 1.0F)
                .texOffs(37, 18).addBox(-1.0F, 8.0F, -3.0F, 2.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(1.70000F, 15.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(1, 18).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 5.0F, -0.35000F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition watch_mantis() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(32, 1).addBox(-2.0F, -4.0F, -3.0F, 4.0F, 8.0F, 5.0F)
                .texOffs(1, 1).addBox(-3.0F, 1.0F, 1.0F, 6.0F, 6.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, 0.18000F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(72, 1).addBox(-4.0F, -2.0F, -2.0F, 8.0F, 4.0F, 4.0F)
                .texOffs(27, 17).addBox(-4.0F, -1.0F, -2.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(27, 17).addBox(3.0F, -1.0F, -2.50000F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition resource_lamp = body.addOrReplaceChild("resource_lamp", CubeListBuilder.create()
                .texOffs(113, 1).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -3.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition antenna_l = head.addOrReplaceChild("antenna_l", CubeListBuilder.create()
                .texOffs(122, 1).addBox(-0.50000F, -5.0F, -0.50000F, 1.0F, 5.0F, 1.0F)
                .texOffs(20, 17).addBox(-0.50000F, -6.0F, -1.50000F, 1.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, -2.0F, 0.0F, -0.30000F, 0.0F, -0.30000F));
        PartDefinition arm_l = root.addOrReplaceChild("arm_l", CubeListBuilder.create()
                .texOffs(97, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, 10.0F, -2.0F, -0.60000F, 0.0F, -0.45000F));
        PartDefinition arm_l_sickle = arm_l.addOrReplaceChild("arm_l_sickle", CubeListBuilder.create()
                .texOffs(106, 1).addBox(-0.50000F, 0.0F, -1.0F, 1.0F, 5.0F, 2.0F)
                .texOffs(6, 17).addBox(-0.50000F, 4.0F, -2.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, -1.70000F, 0.0F, 0.0F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(1, 17).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(-2.50000F, 17.0F, 0.0F, 0.0F, -0.35000F, 0.70000F));
        PartDefinition leg_fl_foot = leg_fl.addOrReplaceChild("leg_fl_foot", CubeListBuilder.create()
                .texOffs(15, 17).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, -0.70000F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(1, 17).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(-2.50000F, 17.0F, 6.0F, 0.0F, -0.35000F, 0.70000F));
        PartDefinition leg_bl_foot = leg_bl.addOrReplaceChild("leg_bl_foot", CubeListBuilder.create()
                .texOffs(15, 17).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, -0.70000F));
        PartDefinition antenna_r = head.addOrReplaceChild("antenna_r", CubeListBuilder.create()
                .texOffs(122, 1).addBox(-0.50000F, -5.0F, -0.50000F, 1.0F, 5.0F, 1.0F)
                .texOffs(20, 17).addBox(-0.50000F, -6.0F, -1.50000F, 1.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(2.0F, -2.0F, 0.0F, -0.30000F, 0.0F, 0.30000F));
        PartDefinition arm_r = root.addOrReplaceChild("arm_r", CubeListBuilder.create()
                .texOffs(97, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F), PartPose.offsetAndRotation(2.0F, 10.0F, -2.0F, -0.60000F, 0.0F, 0.45000F));
        PartDefinition arm_r_sickle = arm_r.addOrReplaceChild("arm_r_sickle", CubeListBuilder.create()
                .texOffs(106, 1).addBox(-0.50000F, 0.0F, -1.0F, 1.0F, 5.0F, 2.0F)
                .texOffs(6, 17).addBox(-0.50000F, 4.0F, -2.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, -1.70000F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(1, 17).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(2.50000F, 17.0F, 0.0F, 0.0F, 0.35000F, -0.70000F));
        PartDefinition leg_fr_foot = leg_fr.addOrReplaceChild("leg_fr_foot", CubeListBuilder.create()
                .texOffs(15, 17).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.70000F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(1, 17).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(2.50000F, 17.0F, 6.0F, 0.0F, 0.35000F, -0.70000F));
        PartDefinition leg_br_foot = leg_br.addOrReplaceChild("leg_br_foot", CubeListBuilder.create()
                .texOffs(15, 17).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.70000F));
        PartDefinition wing_l = body.addOrReplaceChild("wing_l", CubeListBuilder.create()
                .texOffs(51, 1).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 1.0F, 8.0F), PartPose.offsetAndRotation(-2.0F, 2.0F, 3.0F, 0.0F, 0.0F, -0.20000F));
        PartDefinition wing_r = body.addOrReplaceChild("wing_r", CubeListBuilder.create()
                .texOffs(51, 1).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 1.0F, 8.0F), PartPose.offsetAndRotation(2.0F, 2.0F, 3.0F, 0.0F, 0.0F, 0.20000F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition mirrorwing_moth() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 4.0F, 9.0F)
                .texOffs(109, 1).addBox(-2.0F, -1.0F, 4.0F, 4.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 17.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(61, 1).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F)
                .texOffs(79, 15).addBox(-2.20000F, -1.0F, -2.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(79, 15).addBox(1.20000F, -1.0F, -2.50000F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 16.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition antenna_l = head.addOrReplaceChild("antenna_l", CubeListBuilder.create()
                .texOffs(49, 15).addBox(-0.50000F, -4.0F, -0.50000F, 1.0F, 4.0F, 1.0F)
                .texOffs(70, 15).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-1.0F, -2.0F, -1.0F, -0.25000F, 0.0F, -0.30000F));
        PartDefinition wing_front_l = body.addOrReplaceChild("wing_front_l", CubeListBuilder.create()
                .texOffs(28, 1).addBox(-9.0F, -1.0F, -3.0F, 9.0F, 1.0F, 7.0F)
                .texOffs(1, 15).addBox(-8.0F, -1.50000F, -2.0F, 7.0F, 1.0F, 5.0F)
                .texOffs(59, 15).addBox(-5.0F, -2.0F, -1.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-1.50000F, 0.0F, -3.0F, 0.0F, -0.12000F, 0.45000F));
        PartDefinition wing_back_l = body.addOrReplaceChild("wing_back_l", CubeListBuilder.create()
                .texOffs(78, 1).addBox(-9.0F, -1.0F, -3.0F, 9.0F, 1.0F, 6.0F)
                .texOffs(26, 15).addBox(-8.0F, -1.50000F, -2.0F, 7.0F, 1.0F, 4.0F)
                .texOffs(70, 15).addBox(-5.0F, -2.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-1.50000F, 0.0F, 2.0F, 0.0F, -0.12000F, 0.45000F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(54, 15).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-1.0F, 19.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_ml = root.addOrReplaceChild("leg_ml", CubeListBuilder.create()
                .texOffs(54, 15).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-1.0F, 19.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(54, 15).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-1.0F, 19.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition antenna_r = head.addOrReplaceChild("antenna_r", CubeListBuilder.create()
                .texOffs(49, 15).addBox(-0.50000F, -4.0F, -0.50000F, 1.0F, 4.0F, 1.0F)
                .texOffs(70, 15).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(1.0F, -2.0F, -1.0F, -0.25000F, 0.0F, 0.30000F));
        PartDefinition wing_front_r = body.addOrReplaceChild("wing_front_r", CubeListBuilder.create()
                .texOffs(28, 1).addBox(0.0F, -1.0F, -3.0F, 9.0F, 1.0F, 7.0F)
                .texOffs(1, 15).addBox(1.0F, -1.50000F, -2.0F, 7.0F, 1.0F, 5.0F)
                .texOffs(59, 15).addBox(3.0F, -2.0F, -1.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(1.50000F, 0.0F, -3.0F, 0.0F, 0.12000F, -0.45000F));
        PartDefinition wing_back_r = body.addOrReplaceChild("wing_back_r", CubeListBuilder.create()
                .texOffs(78, 1).addBox(0.0F, -1.0F, -3.0F, 9.0F, 1.0F, 6.0F)
                .texOffs(26, 15).addBox(1.0F, -1.50000F, -2.0F, 7.0F, 1.0F, 4.0F)
                .texOffs(70, 15).addBox(3.0F, -2.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(1.50000F, 0.0F, 2.0F, 0.0F, 0.12000F, -0.45000F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(54, 15).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(1.0F, 19.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_mr = root.addOrReplaceChild("leg_mr", CubeListBuilder.create()
                .texOffs(54, 15).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(1.0F, 19.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(54, 15).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(1.0F, 19.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition forgegill_tapir() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-7.0F, -5.0F, -10.0F, 14.0F, 10.0F, 20.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(70, 1).addBox(-4.0F, -4.0F, -6.0F, 8.0F, 7.0F, 8.0F)
                .texOffs(1, 32).addBox(-3.0F, 0.0F, -9.0F, 6.0F, 4.0F, 4.0F)
                .texOffs(61, 32).addBox(-3.0F, 3.0F, -10.0F, 6.0F, 2.0F, 3.0F)
                .texOffs(123, 1).addBox(-4.10000F, -2.0F, -5.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(123, 1).addBox(3.10000F, -2.0F, -5.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(50, 32).addBox(-4.0F, -6.0F, 0.0F, 2.0F, 3.0F, 3.0F)
                .texOffs(50, 32).addBox(2.0F, -6.0F, 0.0F, 2.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 12.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition gill_l0 = body.addOrReplaceChild("gill_l0", CubeListBuilder.create()
                .texOffs(116, 1).addBox(-1.0F, -2.0F, -0.50000F, 2.0F, 6.0F, 1.0F)
                .texOffs(91, 32).addBox(-1.50000F, -1.0F, -0.50000F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-6.0F, -2.0F, -7.0F, 0.0F, -0.22000F, 0.15000F));
        PartDefinition gill_l1 = body.addOrReplaceChild("gill_l1", CubeListBuilder.create()
                .texOffs(116, 1).addBox(-1.0F, -2.0F, -0.50000F, 2.0F, 6.0F, 1.0F)
                .texOffs(91, 32).addBox(-1.50000F, -1.0F, -0.50000F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-6.0F, -2.0F, -5.0F, 0.0F, -0.22000F, 0.15000F));
        PartDefinition gill_l2 = body.addOrReplaceChild("gill_l2", CubeListBuilder.create()
                .texOffs(116, 1).addBox(-1.0F, -2.0F, -0.50000F, 2.0F, 6.0F, 1.0F)
                .texOffs(91, 32).addBox(-1.50000F, -1.0F, -0.50000F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-6.0F, -2.0F, -3.0F, 0.0F, -0.22000F, 0.15000F));
        PartDefinition gill_l3 = body.addOrReplaceChild("gill_l3", CubeListBuilder.create()
                .texOffs(116, 1).addBox(-1.0F, -2.0F, -0.50000F, 2.0F, 6.0F, 1.0F)
                .texOffs(91, 32).addBox(-1.50000F, -1.0F, -0.50000F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-6.0F, -2.0F, -1.0F, 0.0F, -0.22000F, 0.15000F));
        PartDefinition gill_r0 = body.addOrReplaceChild("gill_r0", CubeListBuilder.create()
                .texOffs(116, 1).addBox(-1.0F, -2.0F, -0.50000F, 2.0F, 6.0F, 1.0F)
                .texOffs(91, 32).addBox(-1.50000F, -1.0F, -0.50000F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(6.0F, -2.0F, -7.0F, 0.0F, 0.22000F, -0.15000F));
        PartDefinition gill_r1 = body.addOrReplaceChild("gill_r1", CubeListBuilder.create()
                .texOffs(116, 1).addBox(-1.0F, -2.0F, -0.50000F, 2.0F, 6.0F, 1.0F)
                .texOffs(91, 32).addBox(-1.50000F, -1.0F, -0.50000F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(6.0F, -2.0F, -5.0F, 0.0F, 0.22000F, -0.15000F));
        PartDefinition gill_r2 = body.addOrReplaceChild("gill_r2", CubeListBuilder.create()
                .texOffs(116, 1).addBox(-1.0F, -2.0F, -0.50000F, 2.0F, 6.0F, 1.0F)
                .texOffs(91, 32).addBox(-1.50000F, -1.0F, -0.50000F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(6.0F, -2.0F, -3.0F, 0.0F, 0.22000F, -0.15000F));
        PartDefinition gill_r3 = body.addOrReplaceChild("gill_r3", CubeListBuilder.create()
                .texOffs(116, 1).addBox(-1.0F, -2.0F, -0.50000F, 2.0F, 6.0F, 1.0F)
                .texOffs(91, 32).addBox(-1.50000F, -1.0F, -0.50000F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(6.0F, -2.0F, -1.0F, 0.0F, 0.22000F, -0.15000F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(103, 1).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 7.0F, 3.0F)
                .texOffs(22, 32).addBox(-1.50000F, 5.0F, -2.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(-4.80000F, 17.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(103, 1).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 7.0F, 3.0F)
                .texOffs(22, 32).addBox(-1.50000F, 5.0F, -2.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(4.80000F, 17.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(103, 1).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 7.0F, 3.0F)
                .texOffs(22, 32).addBox(-1.50000F, 5.0F, -2.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(-4.80000F, 17.0F, 9.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(103, 1).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 7.0F, 3.0F)
                .texOffs(22, 32).addBox(-1.50000F, 5.0F, -2.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(4.80000F, 17.0F, 9.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(37, 32).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F)
                .texOffs(80, 32).addBox(-2.0F, -2.0F, 3.0F, 4.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 10.0F, -0.20000F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition mistbelly_otter() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-4.0F, -3.0F, -8.0F, 8.0F, 6.0F, 17.0F)
                .texOffs(52, 1).addBox(-3.0F, 2.0F, -6.0F, 6.0F, 1.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(1, 25).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 6.0F, 6.0F)
                .texOffs(99, 25).addBox(-3.0F, 0.0F, -6.0F, 6.0F, 3.0F, 3.0F)
                .texOffs(14, 38).addBox(-1.0F, 0.0F, -7.0F, 2.0F, 1.0F, 1.0F)
                .texOffs(21, 38).addBox(-4.10000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(21, 38).addBox(3.10000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(116, 1).addBox(-4.0F, -4.0F, 0.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(118, 25).addBox(-6.0F, 0.0F, -4.0F, 3.0F, 1.0F, 1.0F)
                .texOffs(116, 1).addBox(2.0F, -4.0F, 0.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(118, 25).addBox(3.0F, 0.0F, -4.0F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 18.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition resource_sac_l = body.addOrReplaceChild("resource_sac_l", CubeListBuilder.create()
                .texOffs(89, 1).addBox(-1.0F, -2.0F, -5.0F, 3.0F, 3.0F, 10.0F), PartPose.offsetAndRotation(-3.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(1, 38).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 4.0F)
                .texOffs(80, 25).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-3.0F, 21.0F, -5.0F, 0.0F, 0.0F, 0.50000F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(1, 38).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 4.0F)
                .texOffs(80, 25).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-3.0F, 21.0F, 7.0F, 0.0F, 0.0F, 0.50000F));
        PartDefinition resource_sac_r = body.addOrReplaceChild("resource_sac_r", CubeListBuilder.create()
                .texOffs(89, 1).addBox(-1.0F, -2.0F, -5.0F, 3.0F, 3.0F, 10.0F), PartPose.offsetAndRotation(3.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(1, 38).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 4.0F)
                .texOffs(80, 25).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(3.0F, 21.0F, -5.0F, 0.0F, 0.0F, -0.50000F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(1, 38).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 4.0F)
                .texOffs(80, 25).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(3.0F, 21.0F, 7.0F, 0.0F, 0.0F, -0.50000F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(61, 25).addBox(-2.0F, -1.0F, 0.0F, 4.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 9.0F, 0.10000F, 0.0F, 0.0F));
        PartDefinition tail_tip = tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                .texOffs(30, 25).addBox(-4.0F, -0.50000F, 0.0F, 8.0F, 1.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition ringstone_tortoise() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-7.0F, -3.0F, -8.0F, 14.0F, 5.0F, 16.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shell = body.addOrReplaceChild("shell", CubeListBuilder.create()
                .texOffs(62, 1).addBox(-6.0F, -4.0F, -7.0F, 12.0F, 4.0F, 14.0F)
                .texOffs(1, 23).addBox(-4.0F, -6.0F, -5.0F, 8.0F, 2.0F, 10.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(38, 23).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 5.0F)
                .texOffs(122, 1).addBox(-3.20000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(122, 1).addBox(2.20000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 20.0F, -7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition ring_0 = body.addOrReplaceChild("ring_0", CubeListBuilder.create()
                .texOffs(115, 1).addBox(-2.0F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F)
                .texOffs(115, 1).addBox(2.0F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F)
                .texOffs(104, 23).addBox(-1.0F, -3.0F, -1.0F, 3.0F, 1.0F, 2.0F)
                .texOffs(104, 23).addBox(-1.0F, 2.0F, -1.0F, 3.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-5.0F, -4.0F, -4.0F, 0.0F, 0.0F, -0.20000F));
        PartDefinition ring_1 = body.addOrReplaceChild("ring_1", CubeListBuilder.create()
                .texOffs(115, 1).addBox(-2.0F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F)
                .texOffs(115, 1).addBox(2.0F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F)
                .texOffs(104, 23).addBox(-1.0F, -3.0F, -1.0F, 3.0F, 1.0F, 2.0F)
                .texOffs(104, 23).addBox(-1.0F, 2.0F, -1.0F, 3.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(5.0F, -5.0F, 1.0F, 0.15000F, 0.20000F, 0.0F));
        PartDefinition ring_2 = body.addOrReplaceChild("ring_2", CubeListBuilder.create()
                .texOffs(115, 1).addBox(-2.0F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F)
                .texOffs(115, 1).addBox(2.0F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F)
                .texOffs(104, 23).addBox(-1.0F, -3.0F, -1.0F, 3.0F, 1.0F, 2.0F)
                .texOffs(104, 23).addBox(-1.0F, 2.0F, -1.0F, 3.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-4.0F, -3.0F, 6.0F, 0.30000F, 0.40000F, 0.20000F));
        PartDefinition ring_3 = body.addOrReplaceChild("ring_3", CubeListBuilder.create()
                .texOffs(115, 1).addBox(-2.0F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F)
                .texOffs(115, 1).addBox(2.0F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F)
                .texOffs(104, 23).addBox(-1.0F, -3.0F, -1.0F, 3.0F, 1.0F, 2.0F)
                .texOffs(104, 23).addBox(-1.0F, 2.0F, -1.0F, 3.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(2.0F, -6.0F, -5.0F, 0.45000F, 0.60000F, 0.40000F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(91, 23).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-5.0F, 22.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(91, 23).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(5.0F, 22.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(91, 23).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-5.0F, 22.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(91, 23).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(5.0F, 22.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(61, 23).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition resource_pebbles = body.addOrReplaceChild("resource_pebbles", CubeListBuilder.create()
                .texOffs(74, 23).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition papersail_ray() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-3.0F, -2.0F, -7.0F, 6.0F, 4.0F, 15.0F), PartPose.offsetAndRotation(0.0F, 17.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(68, 21).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 3.0F, 4.0F)
                .texOffs(17, 32).addBox(-3.20000F, -1.0F, -2.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(17, 32).addBox(2.20000F, -1.0F, -2.50000F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 16.0F, -7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wing_l = body.addOrReplaceChild("wing_l", CubeListBuilder.create()
                .texOffs(44, 1).addBox(-6.0F, 0.0F, -6.0F, 6.0F, 1.0F, 12.0F)
                .texOffs(81, 1).addBox(-6.0F, -0.50000F, -6.0F, 1.0F, 1.0F, 12.0F), PartPose.offsetAndRotation(-2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.10000F));
        PartDefinition wing_l_mid = wing_l.addOrReplaceChild("wing_l_mid", CubeListBuilder.create()
                .texOffs(1, 21).addBox(-5.0F, 0.0F, -4.0F, 5.0F, 1.0F, 9.0F)
                .texOffs(30, 21).addBox(-5.0F, -0.50000F, -4.0F, 1.0F, 1.0F, 9.0F), PartPose.offsetAndRotation(-6.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.15000F));
        PartDefinition wing_l_tip = wing_l_mid.addOrReplaceChild("wing_l_tip", CubeListBuilder.create()
                .texOffs(108, 21).addBox(-3.0F, 0.0F, -2.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-5.0F, 0.0F, 1.0F, 0.0F, 0.0F, -0.25000F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(1, 32).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F)
                .texOffs(6, 32).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-2.0F, 19.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(1, 32).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F)
                .texOffs(6, 32).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-2.0F, 19.0F, 5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wing_r = body.addOrReplaceChild("wing_r", CubeListBuilder.create()
                .texOffs(44, 1).addBox(0.0F, 0.0F, -6.0F, 6.0F, 1.0F, 12.0F)
                .texOffs(81, 1).addBox(5.0F, -0.50000F, -6.0F, 1.0F, 1.0F, 12.0F), PartPose.offsetAndRotation(2.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.10000F));
        PartDefinition wing_r_mid = wing_r.addOrReplaceChild("wing_r_mid", CubeListBuilder.create()
                .texOffs(1, 21).addBox(0.0F, 0.0F, -4.0F, 5.0F, 1.0F, 9.0F)
                .texOffs(30, 21).addBox(4.0F, -0.50000F, -4.0F, 1.0F, 1.0F, 9.0F), PartPose.offsetAndRotation(6.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.15000F));
        PartDefinition wing_r_tip = wing_r_mid.addOrReplaceChild("wing_r_tip", CubeListBuilder.create()
                .texOffs(108, 21).addBox(0.0F, 0.0F, -2.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(5.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.25000F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(1, 32).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F)
                .texOffs(6, 32).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(2.0F, 19.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(1, 32).addBox(-0.50000F, 0.0F, -0.50000F, 1.0F, 4.0F, 1.0F)
                .texOffs(6, 32).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(2.0F, 19.0F, 5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(108, 1).addBox(-1.0F, -0.50000F, 0.0F, 2.0F, 1.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, -0.15000F, 0.0F, 0.0F));
        PartDefinition tail_tip = tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                .texOffs(51, 21).addBox(-0.50000F, -0.50000F, 0.0F, 1.0F, 1.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, -0.60000F, 0.0F, 0.0F));
        PartDefinition resource_air = body.addOrReplaceChild("resource_air", CubeListBuilder.create()
                .texOffs(89, 21).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition wirecall_lizard() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-3.0F, -2.0F, -7.0F, 6.0F, 4.0F, 15.0F), PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(44, 1).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 5.0F)
                .texOffs(18, 21).addBox(-2.0F, 0.0F, -6.0F, 4.0F, 2.0F, 3.0F)
                .texOffs(57, 21).addBox(-3.20000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(57, 21).addBox(2.20000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 20.0F, -7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition ridge_l0 = body.addOrReplaceChild("ridge_l0", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-0.50000F, -3.0F, -1.0F, 1.0F, 4.0F, 2.0F)
                .texOffs(119, 1).addBox(-0.50000F, -3.0F, -1.50000F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.0F, -2.0F, -5.0F, 0.0F, 0.0F, -0.25000F));
        PartDefinition ridge_l1 = body.addOrReplaceChild("ridge_l1", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-0.50000F, -3.0F, -1.0F, 1.0F, 4.0F, 2.0F)
                .texOffs(119, 1).addBox(-0.50000F, -3.0F, -1.50000F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.0F, -2.0F, -1.0F, 0.0F, 0.0F, -0.25000F));
        PartDefinition ridge_l2 = body.addOrReplaceChild("ridge_l2", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-0.50000F, -3.0F, -1.0F, 1.0F, 4.0F, 2.0F)
                .texOffs(119, 1).addBox(-0.50000F, -3.0F, -1.50000F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.0F, -2.0F, 3.0F, 0.0F, 0.0F, -0.25000F));
        PartDefinition ridge_l3 = body.addOrReplaceChild("ridge_l3", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-0.50000F, -3.0F, -1.0F, 1.0F, 4.0F, 2.0F)
                .texOffs(119, 1).addBox(-0.50000F, -3.0F, -1.50000F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.0F, -2.0F, 7.0F, 0.0F, 0.0F, -0.25000F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(33, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 4.0F)
                .texOffs(1, 21).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-2.50000F, 22.0F, -4.0F, 0.0F, -0.30000F, 0.40000F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(33, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 4.0F)
                .texOffs(1, 21).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-2.50000F, 22.0F, 6.0F, 0.0F, -0.30000F, 0.40000F));
        PartDefinition ridge_r0 = body.addOrReplaceChild("ridge_r0", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-0.50000F, -3.0F, -1.0F, 1.0F, 4.0F, 2.0F)
                .texOffs(119, 1).addBox(-0.50000F, -3.0F, -1.50000F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.0F, -2.0F, -5.0F, 0.0F, 0.0F, 0.25000F));
        PartDefinition ridge_r1 = body.addOrReplaceChild("ridge_r1", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-0.50000F, -3.0F, -1.0F, 1.0F, 4.0F, 2.0F)
                .texOffs(119, 1).addBox(-0.50000F, -3.0F, -1.50000F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.0F, -2.0F, -1.0F, 0.0F, 0.0F, 0.25000F));
        PartDefinition ridge_r2 = body.addOrReplaceChild("ridge_r2", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-0.50000F, -3.0F, -1.0F, 1.0F, 4.0F, 2.0F)
                .texOffs(119, 1).addBox(-0.50000F, -3.0F, -1.50000F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.0F, -2.0F, 3.0F, 0.0F, 0.0F, 0.25000F));
        PartDefinition ridge_r3 = body.addOrReplaceChild("ridge_r3", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-0.50000F, -3.0F, -1.0F, 1.0F, 4.0F, 2.0F)
                .texOffs(119, 1).addBox(-0.50000F, -3.0F, -1.50000F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.0F, -2.0F, 7.0F, 0.0F, 0.0F, 0.25000F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(33, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 4.0F)
                .texOffs(1, 21).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(2.50000F, 22.0F, -4.0F, 0.0F, 0.30000F, -0.40000F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(33, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 4.0F)
                .texOffs(1, 21).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(2.50000F, 22.0F, 6.0F, 0.0F, 0.30000F, -0.40000F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(67, 1).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail_tip = tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                .texOffs(84, 1).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, 0.0F, 0.85000F, 0.0F));
        PartDefinition tail_end = tail_tip.addOrReplaceChild("tail_end", CubeListBuilder.create()
                .texOffs(99, 1).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F)
                .texOffs(46, 21).addBox(-2.0F, -1.0F, 1.0F, 4.0F, 1.0F, 1.0F)
                .texOffs(46, 21).addBox(-2.0F, -1.0F, 2.0F, 4.0F, 1.0F, 1.0F)
                .texOffs(46, 21).addBox(-2.0F, -1.0F, 3.0F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, 1.30000F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition dewbound_whale() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-9.0F, -6.0F, -11.0F, 18.0F, 12.0F, 22.0F)
                .texOffs(1, 36).addBox(-7.0F, 5.0F, -9.0F, 14.0F, 2.0F, 18.0F)
                .texOffs(82, 1).addBox(-10.0F, -3.0F, -8.0F, 1.0F, 6.0F, 16.0F)
                .texOffs(82, 1).addBox(9.0F, -3.0F, -8.0F, 1.0F, 6.0F, 16.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(66, 36).addBox(-7.0F, -4.0F, -4.0F, 14.0F, 9.0F, 6.0F)
                .texOffs(61, 57).addBox(-5.0F, 3.0F, -5.0F, 10.0F, 2.0F, 4.0F)
                .texOffs(1, 65).addBox(-7.0F, 0.0F, -3.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(1, 65).addBox(6.0F, 0.0F, -3.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(109, 57).addBox(-3.0F, 3.0F, -5.50000F, 6.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 11.0F, -10.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition fin_front_l = body.addOrReplaceChild("fin_front_l", CubeListBuilder.create()
                .texOffs(40, 57).addBox(-5.0F, -1.0F, -2.0F, 5.0F, 2.0F, 5.0F)
                .texOffs(117, 1).addBox(-5.0F, -1.0F, -1.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-8.0F, 2.0F, -5.0F, 0.0F, -0.10000F, -0.50000F));
        PartDefinition fin_back_l = body.addOrReplaceChild("fin_back_l", CubeListBuilder.create()
                .texOffs(40, 57).addBox(-5.0F, -1.0F, -2.0F, 5.0F, 2.0F, 5.0F)
                .texOffs(117, 1).addBox(-5.0F, -1.0F, -1.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-8.0F, 2.0F, 5.0F, 0.0F, -0.10000F, -0.50000F));
        PartDefinition fin_front_r = body.addOrReplaceChild("fin_front_r", CubeListBuilder.create()
                .texOffs(40, 57).addBox(0.0F, -1.0F, -2.0F, 5.0F, 2.0F, 5.0F)
                .texOffs(117, 1).addBox(4.0F, -1.0F, -1.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(8.0F, 2.0F, -5.0F, 0.0F, 0.10000F, 0.50000F));
        PartDefinition fin_back_r = body.addOrReplaceChild("fin_back_r", CubeListBuilder.create()
                .texOffs(40, 57).addBox(0.0F, -1.0F, -2.0F, 5.0F, 2.0F, 5.0F)
                .texOffs(117, 1).addBox(4.0F, -1.0F, -1.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(8.0F, 2.0F, 5.0F, 0.0F, 0.10000F, 0.50000F));
        PartDefinition pip_0 = body.addOrReplaceChild("pip_0", CubeListBuilder.create()
                .texOffs(90, 57).addBox(-3.0F, -3.0F, -1.0F, 6.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -6.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition pip_1 = body.addOrReplaceChild("pip_1", CubeListBuilder.create()
                .texOffs(90, 57).addBox(-3.0F, -3.0F, -1.0F, 6.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -6.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition pip_2 = body.addOrReplaceChild("pip_2", CubeListBuilder.create()
                .texOffs(90, 57).addBox(-3.0F, -3.0F, -1.0F, 6.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition pip_3 = body.addOrReplaceChild("pip_3", CubeListBuilder.create()
                .texOffs(90, 57).addBox(-3.0F, -3.0F, -1.0F, 6.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(107, 36).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail_tip = tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                .texOffs(1, 57).addBox(-7.0F, -1.0F, 0.0F, 14.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition satchelback_runner() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-5.0F, -4.0F, -8.0F, 10.0F, 8.0F, 17.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 2.0F, -0.12000F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(89, 1).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 7.0F)
                .texOffs(1, 40).addBox(-2.0F, 0.0F, -7.0F, 4.0F, 3.0F, 3.0F)
                .texOffs(27, 40).addBox(-3.20000F, -1.0F, -4.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(27, 40).addBox(2.20000F, -1.0F, -4.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(116, 1).addBox(-4.0F, -5.0F, -1.0F, 2.0F, 3.0F, 3.0F)
                .texOffs(116, 1).addBox(2.0F, -5.0F, -1.0F, 2.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 14.0F, -7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition resource_bag_l = body.addOrReplaceChild("resource_bag_l", CubeListBuilder.create()
                .texOffs(56, 1).addBox(-3.0F, -1.0F, -5.0F, 6.0F, 7.0F, 10.0F)
                .texOffs(1, 27).addBox(-3.0F, -2.0F, -5.0F, 6.0F, 2.0F, 10.0F)
                .texOffs(16, 40).addBox(-2.0F, 1.0F, -5.50000F, 4.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-5.0F, -2.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition bag_lid_l = resource_bag_l.addOrReplaceChild("bag_lid_l", CubeListBuilder.create()
                .texOffs(34, 27).addBox(-3.0F, -1.0F, -9.0F, 6.0F, 1.0F, 9.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition resource_bag_r = body.addOrReplaceChild("resource_bag_r", CubeListBuilder.create()
                .texOffs(56, 1).addBox(-3.0F, -1.0F, -5.0F, 6.0F, 7.0F, 10.0F)
                .texOffs(1, 27).addBox(-3.0F, -2.0F, -5.0F, 6.0F, 2.0F, 10.0F)
                .texOffs(16, 40).addBox(-2.0F, 1.0F, -5.50000F, 4.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(5.0F, -2.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition bag_lid_r = resource_bag_r.addOrReplaceChild("bag_lid_r", CubeListBuilder.create()
                .texOffs(34, 27).addBox(-3.0F, -1.0F, -9.0F, 6.0F, 1.0F, 9.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(65, 27).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 7.0F, 3.0F)
                .texOffs(99, 27).addBox(-1.50000F, 5.0F, -2.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(-3.50000F, 17.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(65, 27).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 7.0F, 3.0F)
                .texOffs(99, 27).addBox(-1.50000F, 5.0F, -2.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(3.50000F, 17.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(65, 27).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 7.0F, 3.0F)
                .texOffs(99, 27).addBox(-1.50000F, 5.0F, -2.0F, 3.0F, 2.0F, 4.0F)
                .texOffs(114, 27).addBox(-1.50000F, 6.0F, -1.50000F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-3.50000F, 15.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(65, 27).addBox(-1.50000F, 0.0F, -1.50000F, 3.0F, 7.0F, 3.0F)
                .texOffs(99, 27).addBox(-1.50000F, 5.0F, -2.0F, 3.0F, 2.0F, 4.0F)
                .texOffs(114, 27).addBox(-1.50000F, 6.0F, -1.50000F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(3.50000F, 15.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(78, 27).addBox(-2.0F, -1.0F, 0.0F, 4.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 9.0F, -0.35000F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition bowlhorn_rhino() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-8.0F, -5.0F, -10.0F, 16.0F, 10.0F, 21.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(76, 1).addBox(-6.0F, -4.0F, -8.0F, 12.0F, 8.0F, 10.0F)
                .texOffs(70, 33).addBox(-5.0F, 1.0F, -10.0F, 10.0F, 4.0F, 4.0F)
                .texOffs(121, 1).addBox(-6.0F, -1.0F, -5.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(121, 1).addBox(5.0F, -1.0F, -5.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(35, 47).addBox(-6.0F, -6.0F, -1.0F, 3.0F, 3.0F, 3.0F)
                .texOffs(35, 47).addBox(4.0F, -6.0F, -1.0F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 13.0F, -9.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition bowl_horn = head.addOrReplaceChild("bowl_horn", CubeListBuilder.create()
                .texOffs(99, 33).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 4.0F, 4.0F)
                .texOffs(53, 33).addBox(-4.0F, -5.0F, -3.0F, 2.0F, 3.0F, 6.0F)
                .texOffs(53, 33).addBox(2.0F, -5.0F, -3.0F, 2.0F, 3.0F, 6.0F)
                .texOffs(48, 47).addBox(-2.0F, -5.0F, -4.0F, 4.0F, 3.0F, 2.0F)
                .texOffs(48, 47).addBox(-2.0F, -5.0F, 2.0F, 4.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -3.0F, -7.0F, -0.25000F, 0.0F, 0.0F));
        PartDefinition plate_l = body.addOrReplaceChild("plate_l", CubeListBuilder.create()
                .texOffs(1, 33).addBox(-3.0F, -2.0F, -5.0F, 6.0F, 2.0F, 11.0F), PartPose.offsetAndRotation(-4.0F, -5.0F, 0.0F, 0.0F, 0.0F, -0.25000F));
        PartDefinition plate_r = body.addOrReplaceChild("plate_r", CubeListBuilder.create()
                .texOffs(1, 33).addBox(-3.0F, -2.0F, -5.0F, 6.0F, 2.0F, 11.0F), PartPose.offsetAndRotation(4.0F, -5.0F, 0.0F, 0.0F, 0.0F, 0.25000F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(36, 33).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F)
                .texOffs(1, 47).addBox(-2.0F, 5.0F, -2.50000F, 4.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(-5.0F, 17.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(36, 33).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F)
                .texOffs(1, 47).addBox(-2.0F, 5.0F, -2.50000F, 4.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(5.0F, 17.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(36, 33).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F)
                .texOffs(1, 47).addBox(-2.0F, 5.0F, -2.50000F, 4.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(-5.0F, 17.0F, 10.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(36, 33).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F)
                .texOffs(1, 47).addBox(-2.0F, 5.0F, -2.50000F, 4.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(5.0F, 17.0F, 10.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(20, 47).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, -0.20000F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition pageforage_raccoon() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-3.0F, -4.0F, -4.0F, 6.0F, 7.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 18.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(32, 1).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 6.0F, 6.0F)
                .texOffs(75, 18).addBox(-4.0F, -1.0F, -4.50000F, 8.0F, 2.0F, 1.0F)
                .texOffs(94, 1).addBox(-2.0F, 0.0F, -6.0F, 4.0F, 2.0F, 3.0F)
                .texOffs(94, 18).addBox(-1.0F, 0.0F, -7.0F, 2.0F, 1.0F, 1.0F)
                .texOffs(101, 18).addBox(-3.0F, -0.80000F, -5.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(101, 18).addBox(2.0F, -0.80000F, -5.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(1, 18).addBox(-4.0F, -5.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(1, 18).addBox(2.0F, -5.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 15.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition arm_l = root.addOrReplaceChild("arm_l", CubeListBuilder.create()
                .texOffs(61, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(109, 1).addBox(-1.0F, 5.0F, -2.0F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-3.0F, 17.0F, -1.0F, -0.15000F, 0.0F, 0.20000F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(83, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-2.0F, 21.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition arm_r = root.addOrReplaceChild("arm_r", CubeListBuilder.create()
                .texOffs(61, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(109, 1).addBox(-1.0F, 5.0F, -2.0F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(3.0F, 17.0F, -1.0F, -0.15000F, 0.0F, -0.20000F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(83, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(2.0F, 21.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition resource_paperbag = body.addOrReplaceChild("resource_paperbag", CubeListBuilder.create()
                .texOffs(70, 1).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 4.0F, 2.0F)
                .texOffs(120, 1).addBox(-1.0F, 1.0F, -2.50000F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(66, 18).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(55, 18).addBox(-1.50000F, -1.0F, 2.0F, 3.0F, 2.0F, 2.0F)
                .texOffs(42, 18).addBox(-2.0F, -1.0F, 4.0F, 4.0F, 2.0F, 2.0F)
                .texOffs(27, 18).addBox(-2.50000F, -1.0F, 6.0F, 5.0F, 2.0F, 2.0F)
                .texOffs(10, 18).addBox(-3.0F, -1.0F, 8.0F, 6.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 5.0F, -0.40000F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition patternbuild_beaver() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-5.0F, -4.0F, -5.0F, 10.0F, 8.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 17.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(46, 1).addBox(-4.0F, -3.0F, -5.0F, 8.0F, 6.0F, 7.0F)
                .texOffs(63, 22).addBox(-3.0F, 0.0F, -7.0F, 6.0F, 3.0F, 3.0F)
                .texOffs(1, 34).addBox(-2.0F, 2.0F, -7.50000F, 4.0F, 2.0F, 1.0F)
                .texOffs(121, 22).addBox(-1.0F, 0.0F, -8.0F, 2.0F, 1.0F, 1.0F)
                .texOffs(12, 34).addBox(-4.10000F, -1.0F, -4.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(12, 34).addBox(3.10000F, -1.0F, -4.50000F, 1.0F, 1.0F, 1.0F)
                .texOffs(99, 22).addBox(-4.0F, -4.0F, -1.0F, 2.0F, 2.0F, 3.0F)
                .texOffs(99, 22).addBox(2.0F, -4.0F, -1.0F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 15.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition panel_l = body.addOrReplaceChild("panel_l", CubeListBuilder.create()
                .texOffs(77, 1).addBox(-1.0F, -3.0F, -3.0F, 2.0F, 6.0F, 7.0F)
                .texOffs(82, 22).addBox(-1.50000F, -1.0F, -2.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-5.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.12000F));
        PartDefinition arm_l = root.addOrReplaceChild("arm_l", CubeListBuilder.create()
                .texOffs(115, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 3.0F)
                .texOffs(110, 22).addBox(-1.0F, 3.0F, -2.0F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-4.0F, 18.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(96, 1).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(-3.0F, 21.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition panel_r = body.addOrReplaceChild("panel_r", CubeListBuilder.create()
                .texOffs(77, 1).addBox(-1.0F, -3.0F, -3.0F, 2.0F, 6.0F, 7.0F)
                .texOffs(82, 22).addBox(-1.50000F, -1.0F, -2.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(5.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.12000F));
        PartDefinition arm_r = root.addOrReplaceChild("arm_r", CubeListBuilder.create()
                .texOffs(115, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 3.0F)
                .texOffs(110, 22).addBox(-1.0F, 3.0F, -2.0F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(4.0F, 18.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(96, 1).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(3.0F, 21.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(1, 22).addBox(-4.0F, -0.50000F, 0.0F, 8.0F, 1.0F, 10.0F)
                .texOffs(38, 22).addBox(-3.0F, -1.0F, 2.0F, 6.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 1.0F, 7.0F, -0.05000F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition springhoof_strider() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-4.0F, -3.0F, -5.0F, 8.0F, 6.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 9.0F, 1.0F, -0.14000F, 0.0F, 0.0F));
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                .texOffs(67, 1).addBox(-2.0F, -5.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -4.0F, -0.20000F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(42, 1).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 5.0F, 6.0F)
                .texOffs(28, 20).addBox(-2.0F, 0.0F, -6.0F, 4.0F, 3.0F, 3.0F)
                .texOffs(84, 20).addBox(-3.20000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(84, 20).addBox(2.20000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition horn_l = head.addOrReplaceChild("horn_l", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-1.0F, -6.0F, -0.50000F, 2.0F, 6.0F, 1.0F)
                .texOffs(119, 1).addBox(-1.0F, -6.0F, -1.50000F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, -2.0F, 0.0F, -0.65000F, 0.0F, -0.15000F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(84, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F)
                .texOffs(43, 20).addBox(-1.50000F, 8.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(58, 20).addBox(-1.50000F, 9.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(43, 20).addBox(-1.50000F, 10.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(-1.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(-0.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(0.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-2.70000F, 12.0F, -2.0F, 0.12000F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(84, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F)
                .texOffs(43, 20).addBox(-1.50000F, 8.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(58, 20).addBox(-1.50000F, 9.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(43, 20).addBox(-1.50000F, 10.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(-1.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(-0.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(0.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-2.70000F, 12.0F, 6.0F, -0.12000F, 0.0F, 0.0F));
        PartDefinition horn_r = head.addOrReplaceChild("horn_r", CubeListBuilder.create()
                .texOffs(112, 1).addBox(-1.0F, -6.0F, -0.50000F, 2.0F, 6.0F, 1.0F)
                .texOffs(119, 1).addBox(-1.0F, -6.0F, -1.50000F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(2.0F, -2.0F, 0.0F, -0.65000F, 0.0F, 0.15000F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(84, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F)
                .texOffs(43, 20).addBox(-1.50000F, 8.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(58, 20).addBox(-1.50000F, 9.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(43, 20).addBox(-1.50000F, 10.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(-1.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(-0.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(0.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(2.70000F, 12.0F, -2.0F, 0.12000F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(84, 1).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F)
                .texOffs(43, 20).addBox(-1.50000F, 8.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(58, 20).addBox(-1.50000F, 9.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(43, 20).addBox(-1.50000F, 10.0F, -1.50000F, 3.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(-1.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(-0.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F)
                .texOffs(73, 20).addBox(0.50000F, 11.0F, -2.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(2.70000F, 12.0F, 6.0F, -0.12000F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(93, 1).addBox(-2.0F, -1.0F, 0.0F, 4.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 7.0F, -0.30000F, 0.0F, 0.0F));
        PartDefinition resource_saddle = body.addOrReplaceChild("resource_saddle", CubeListBuilder.create()
                .texOffs(1, 20).addBox(-4.0F, -1.0F, -2.0F, 8.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition pillowlight_marten() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-3.0F, -2.0F, -8.0F, 6.0F, 4.0F, 17.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(81, 1).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 6.0F)
                .texOffs(43, 23).addBox(-2.0F, 0.0F, -6.0F, 4.0F, 2.0F, 3.0F)
                .texOffs(85, 23).addBox(-1.0F, 0.0F, -7.0F, 2.0F, 1.0F, 1.0F)
                .texOffs(92, 23).addBox(-3.20000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(92, 23).addBox(2.20000F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(58, 23).addBox(-3.0F, -3.0F, 0.0F, 2.0F, 2.0F, 3.0F)
                .texOffs(58, 23).addBox(1.0F, -3.0F, 0.0F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 18.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(69, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, 22.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(69, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(2.0F, 22.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create()
                .texOffs(69, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, 22.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create()
                .texOffs(69, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(2.0F, 22.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition resource_lamp = body.addOrReplaceChild("resource_lamp", CubeListBuilder.create()
                .texOffs(106, 1).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition pip_0 = body.addOrReplaceChild("pip_0", CubeListBuilder.create()
                .texOffs(78, 23).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition pip_1 = body.addOrReplaceChild("pip_1", CubeListBuilder.create()
                .texOffs(78, 23).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition pip_2 = body.addOrReplaceChild("pip_2", CubeListBuilder.create()
                .texOffs(78, 23).addBox(-1.0F, -2.0F, 1.0F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition pip_3 = body.addOrReplaceChild("pip_3", CubeListBuilder.create()
                .texOffs(78, 23).addBox(-1.0F, -2.0F, 2.0F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(26, 23).addBox(-2.0F, -1.0F, 0.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 9.0F, -0.20000F, 0.0F, 0.0F));
        PartDefinition tail_tip = tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                .texOffs(48, 1).addBox(-4.0F, -2.0F, 0.0F, 8.0F, 3.0F, 8.0F)
                .texOffs(1, 23).addBox(-3.0F, -2.50000F, 1.0F, 6.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
