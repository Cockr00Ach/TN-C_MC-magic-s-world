package com.tnc.tnc.equipment.client;

import com.google.gson.JsonParser;
import com.tnc.tnc.equipment.MageGear;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Authored wearable shells follow humanoid joints; draped cloth has its own restrained motion. */
public final class GearModels {
    private static final Map<String, TailoredModel> CACHE = new HashMap<>();

    public static final class Extension implements IClientItemExtensions {
        @Override public Model getGenericArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
            var design = ((MageGear.GearItem) stack.getItem()).design;
            var model = CACHE.computeIfAbsent(design.id(), id -> new TailoredModel(mesh(design).bakeRoot(),design));
            model.time = entity.tickCount + Minecraft.getInstance().getFrameTime();
            net.minecraftforge.client.ForgeHooksClient.copyModelProperties(original, model);
            model.head.copyFrom(original.head); model.body.copyFrom(original.body);
            model.rightArm.copyFrom(original.rightArm); model.leftArm.copyFrom(original.leftArm);
            model.rightLeg.copyFrom(original.rightLeg); model.leftLeg.copyFrom(original.leftLeg);
            model.drape(original, entity);
            model.setAllVisible(false);
            if (design.hat()) model.head.visible = true;
            else {
                model.body.visible = true; model.rightArm.visible = true; model.leftArm.visible = true;
                model.rightLeg.visible = true; model.leftLeg.visible = true;
            }
            return model;
        }
    }

    private static final class TailoredModel extends HumanoidModel<LivingEntity> {
        private final MageGear.Design design;
        private float time;
        TailoredModel(ModelPart root,MageGear.Design design) { super(root);this.design=design; }

        @Override public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose,
                com.mojang.blaze3d.vertex.VertexConsumer vertices,int light,int overlay,float red,float green,float blue,float alpha){
            super.renderToBuffer(pose,vertices,light,overlay,red,green,blue,alpha);
            if(design.tier()<2)return;
            int frame=Math.floorMod((int)(time/4),8);
            var texture=ResourceLocation.parse("tnc:textures/models/armor/"+design.id()+"_glow_"+frame+".png");
            var glow=Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.eyes(texture));
            super.renderToBuffer(pose,glow,15728880,overlay,1,1,1,design.tier()==2?.45f:design.tier()==3?.65f:1);
        }

        void drape(HumanoidModel<?> wearer, LivingEntity entity) {
            float idle = Mth.sin((entity.tickCount + Minecraft.getInstance().getFrameTime()) * .07f) * .012f;
            for (String side : List.of("left", "right")) {
                float step = side.equals("left") ? wearer.leftLeg.xRot : wearer.rightLeg.xRot;
                for (String face : List.of("front_", "back_", "side_")) {
                    String bone = face + side;
                    if (!body.hasChild(bone)) continue;
                    // Counter the crouched torso and yield to the moving leg; no solid tube per leg.
                    float follow = face.equals("front_") ? .25f : face.equals("back_") ? .1f : .16f;
                    float rotation = Mth.clamp(step * follow, -.32f, .32f);
                    if (wearer.riding) rotation = face.equals("front_") ? Mth.clamp(step, -1.5f, 0)
                            : face.equals("side_") ? .3f : -.12f;
                    var panel = body.getChild(bone);
                    panel.loadPose(panel.getInitialPose());
                    if (wearer.crouching && !wearer.riding) {
                        // Crouching moves the pelvis behind the tilted torso. Keep the coat
                        // anchored at that real hip position, rather than dragging it below the feet.
                        float hipY = (wearer.leftLeg.y + wearer.rightLeg.y) * .5f - wearer.body.y;
                        float hipZ = (wearer.leftLeg.z + wearer.rightLeg.z) * .5f - wearer.body.z;
                        float c = Mth.cos(wearer.body.xRot), s = Mth.sin(wearer.body.xRot);
                        panel.y += hipY * c + hipZ * s - 11.1f;
                        panel.z += -hipY * s + hipZ * c;
                        rotation -= wearer.body.xRot;
                    }
                    panel.xRot += rotation + idle;
                    // Sitting spreads the thighs. Open the side seam instead of cutting across them.
                    if (wearer.riding && face.equals("side_")) panel.x += side.equals("left") ? .8f : -.8f;
                }
            }
            if (body.hasChild("cape")) body.getChild("cape").xRot = .08f
                    + Math.min(.18f, Math.abs(wearer.leftLeg.xRot - wearer.rightLeg.xRot) * .06f)
                    - (wearer.crouching ? .25f : 0) + idle;
        }
    }

    @Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> CACHE.clear());
        }
    }

    /** Blockbench imports keep all seven standard bones; clothing children carry their own UVs. */
    private static LayerDefinition mesh(MageGear.Design design) {
        var path = ResourceLocation.fromNamespaceAndPath("tnc", "models/armor/" + design.id() + ".json");
        var resource = Minecraft.getInstance().getResourceManager().getResource(path)
                .orElseThrow(() -> new IllegalStateException("Missing authored wearable: " + path));
        try (var reader = resource.openAsReader()) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            var mesh = new MeshDefinition();
            var parts = new HashMap<String, PartDefinition>(); parts.put("root", mesh.getRoot());
            for (var value : json.getAsJsonArray("parts")) {
                var p = value.getAsJsonObject(); var cubes = CubeListBuilder.create();
                for (var boxValue : p.getAsJsonArray("boxes")) {
                    var box = boxValue.getAsJsonObject(); var uv = box.getAsJsonArray("uv");
                    var pos = box.getAsJsonArray("position"); var size = box.getAsJsonArray("size");
                    cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).addBox(
                            pos.get(0).getAsFloat(), pos.get(1).getAsFloat(), pos.get(2).getAsFloat(),
                            size.get(0).getAsFloat(), size.get(1).getAsFloat(), size.get(2).getAsFloat(),
                            new CubeDeformation(box.get("inflate").getAsFloat()));
                }
                var pivot = p.getAsJsonArray("pivot"); var rotation = p.getAsJsonArray("rotation");
                var parent = Objects.requireNonNull(parts.get(p.get("parent").getAsString()), "Parent must precede clothing bone");
                var part = parent.addOrReplaceChild(p.get("name").getAsString(), cubes,
                        PartPose.offsetAndRotation(pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(), pivot.get(2).getAsFloat(),
                                rotation.get(0).getAsFloat(), rotation.get(1).getAsFloat(), rotation.get(2).getAsFloat()));
                parts.put(p.get("name").getAsString(), part);
            }
            return LayerDefinition.create(mesh, json.get("texture_width").getAsInt(), json.get("texture_height").getAsInt());
        } catch (Exception error) {
            throw new IllegalStateException("Invalid authored wearable: " + path, error);
        }
    }

    private GearModels() {}
}
