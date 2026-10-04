package com.tnc.tnc.equipment.client;

import com.tnc.tnc.equipment.MageGear;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.*;

/** Real wearable geometry: coats, split robe hems, shoulder plates and four hat silhouettes. */
public final class GearModels {
    private static final Map<String,HumanoidModel<LivingEntity>> CACHE=new HashMap<>();
    public static final class Extension implements IClientItemExtensions {
        @Override public Model getGenericArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){
            var d=((MageGear.GearItem)stack.getItem()).design;
            var m=CACHE.computeIfAbsent(d.id(),id->new HumanoidModel<>(mesh(d).bakeRoot()));
            net.minecraftforge.client.ForgeHooksClient.copyModelProperties(original,m);m.head.copyFrom(original.head);m.body.copyFrom(original.body);
            m.rightArm.copyFrom(original.rightArm);m.leftArm.copyFrom(original.leftArm);
            m.rightLeg.copyFrom(original.rightLeg);m.leftLeg.copyFrom(original.leftLeg);
            m.setAllVisible(false);
            if(d.hat())m.head.visible=true;
            else {m.body.visible=true;m.rightArm.visible=true;m.leftArm.visible=true;m.rightLeg.visible=true;m.leftLeg.visible=true;}
            return m;
        }
    }
    private static CubeListBuilder box(int u,int v,float x,float y,float z,float w,float h,float depth,float inflate){return CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,depth,new CubeDeformation(inflate));}
    private static LayerDefinition mesh(MageGear.Design d){
        LayerDefinition authored=authoredModel(d.id());
        if(authored!=null)return authored;
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        for(String n:List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"))root.addOrReplaceChild(n,CubeListBuilder.create(),PartPose.ZERO);
        boolean heavy=d.style().equals("bastion"),chain=d.style().equals("runic"),light=d.style().equals("wanderer"),god=d.divine();
        if(d.hat()){
            var head=root.getChild("head");
            if(heavy){
                head.addOrReplaceChild("brim",box(0,0,-7,-9,-7,14,1,14,0),PartPose.ZERO);
                head.addOrReplaceChild("crown",box(0,20,-4,-13,-4,8,4,8,.25f),PartPose.ZERO);
                if(d.tier()>=3)head.addOrReplaceChild("crest",box(40,0,-1,-16,-4,2,3,7,0),PartPose.ZERO);
            }else if(chain){
                head.addOrReplaceChild("ring",box(0,20,-4.5f,-10,-4.5f,9,3,9,0),PartPose.ZERO);
                head.addOrReplaceChild("top",box(0,0,-3.5f,-12,-3.5f,7,2,7,0),PartPose.ZERO);
                if(d.tier()>=3)head.addOrReplaceChild("gem",box(44,0,-1,-11,-5.5f,2,2,1,0),PartPose.ZERO);
            }else if(light){
                head.addOrReplaceChild("hood_top",box(0,0,-4.7f,-9,-4.7f,9.4f,2,9.4f,0),PartPose.ZERO);
                head.addOrReplaceChild("hood_left",box(0,24,-4.7f,-7,-4.2f,1.2f,8,8.8f,0),PartPose.ZERO);
                head.addOrReplaceChild("hood_right",box(0,24,3.5f,-7,-4.2f,1.2f,8,8.8f,0),PartPose.ZERO);
                head.addOrReplaceChild("hood_back",box(32,32,-3.5f,-7,3.7f,7,8,1,0),PartPose.ZERO);
                head.addOrReplaceChild("neck",box(0,24,-4,0,-3,8,3,6,.3f),PartPose.ZERO);
            }else{
                head.addOrReplaceChild("brim",box(0,0,-6.5f,-9,-6.5f,13,1,13,0),PartPose.ZERO);
                head.addOrReplaceChild("cone_base",box(0,18,-4,-12,-4,8,3,8,0),PartPose.ZERO);
                head.addOrReplaceChild("cone_middle",box(0,32,-3,-16,-3,6,4,6,0),PartPose.ZERO);
                head.addOrReplaceChild("cone_tip",box(28,32,-1.5f,-20,-1.5f,3,4,3,0),PartPose.offsetAndRotation(0,0,0,0,0,.12f));
                if(god)head.addOrReplaceChild("halo",box(32,0,-6,-14,-.5f,12,1,1,0),PartPose.ZERO);
            }
        }else{
            var body=root.addOrReplaceChild("body",box(16,16,-4,0,-2,8,12,4,.65f),PartPose.ZERO);
            body.addOrReplaceChild("collar",box(0,32,-4,-1,-2,8,3,4,1),PartPose.ZERO);
            if(heavy||chain)body.addOrReplaceChild("breastplate",box(36,16,-3,2,-3,6,7,1,.2f),PartPose.ZERO);
            if(light)body.addOrReplaceChild("scarf",box(0,40,-5,1,-3,10,3,6,.1f),PartPose.ZERO);
            if(god)body.addOrReplaceChild("relic_seal",box(48,0,-2,3,-3,4,4,1,0),PartPose.ZERO);
            for(boolean left:new boolean[]{false,true}){
                var arm=root.addOrReplaceChild(left?"left_arm":"right_arm",box(40,16,left?-1:-3,-2,-2,4,12,4,.55f),PartPose.offset(left?5:-5,2,0));
                if(heavy||d.tier()>=3)arm.addOrReplaceChild("shoulder",box(32,36,left?-1.5f:-3.5f,-3,-2.5f,5,4,5,.25f),PartPose.ZERO);
                var leg=root.addOrReplaceChild(left?"left_leg":"right_leg",box(0,16,-2,0,-2,4,12,4,.5f),PartPose.offset(left?1.9f:-1.9f,12,0));
                float length=light?6:chain?8:11;
                leg.addOrReplaceChild("coat_hem",box(0,44,-2.6f,-1,-2.7f,5.2f,length,5.4f,0),PartPose.ZERO);
                leg.addOrReplaceChild("boot",box(32,48,-2,8,-3,4,4,5,.65f),PartPose.ZERO);
            }
        }
        return LayerDefinition.create(mesh,64,64);
    }
    /** Import output from tools/equipment_blockbench.py; exact ModelPart bones remain animated. */
    private static LayerDefinition authoredModel(String id){
        var path=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","models/armor/"+id+".json");
        var resource=net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(path);
        if(resource.isEmpty())return null;
        try(var reader=resource.get().openAsReader()){
            var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            var mesh=new MeshDefinition();var definitions=new HashMap<String,PartDefinition>();definitions.put("root",mesh.getRoot());
            for(var value:json.getAsJsonArray("parts")){
                var p=value.getAsJsonObject();var cubes=CubeListBuilder.create();
                for(var b:p.getAsJsonArray("boxes")){
                    var box=b.getAsJsonObject();var uv=box.getAsJsonArray("uv");var pos=box.getAsJsonArray("position");var size=box.getAsJsonArray("size");
                    cubes.texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt()).addBox(pos.get(0).getAsFloat(),pos.get(1).getAsFloat(),pos.get(2).getAsFloat(),
                            size.get(0).getAsFloat(),size.get(1).getAsFloat(),size.get(2).getAsFloat(),new CubeDeformation(box.get("inflate").getAsFloat()));
                }
                var pivot=p.getAsJsonArray("pivot");var rotation=p.getAsJsonArray("rotation");
                var part=definitions.get(p.get("parent").getAsString()).addOrReplaceChild(p.get("name").getAsString(),cubes,
                        PartPose.offsetAndRotation(pivot.get(0).getAsFloat(),pivot.get(1).getAsFloat(),pivot.get(2).getAsFloat(),rotation.get(0).getAsFloat(),rotation.get(1).getAsFloat(),rotation.get(2).getAsFloat()));
                definitions.put(p.get("name").getAsString(),part);
            }
            return LayerDefinition.create(mesh,json.get("texture_width").getAsInt(),json.get("texture_height").getAsInt());
        }catch(Exception e){throw new IllegalStateException("Invalid wearable source: "+path,e);}
    }
}
