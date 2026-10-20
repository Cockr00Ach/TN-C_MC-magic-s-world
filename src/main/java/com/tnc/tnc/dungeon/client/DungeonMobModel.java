package com.tnc.tnc.dungeon.client;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.dungeon.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DungeonMobModel extends EntityModel<DungeonMob> {
    private final ModelPart root;private final Map<String,ModelPart> parts=new LinkedHashMap<>();
    public DungeonMobModel(ModelPart root,MobCatalog.Family family){this.root=root;parts.put("root",root);try(var reader=new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(DungeonContent.id("dungeon_models/"+family.name().toLowerCase(java.util.Locale.ROOT)+".json")).orElseThrow().open(),StandardCharsets.UTF_8)){for(var el:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("parts")){var obj=el.getAsJsonObject();String name=obj.get("name").getAsString();parts.put(name,parts.get(obj.get("parent").getAsString()).getChild(name));}}catch(IOException ex){throw new IllegalStateException(ex);}}
    private void index(ModelPart part,String name){parts.put(name,part);}
    public static LayerDefinition layer(MobCatalog.Family family){try(var reader=new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(DungeonContent.id("dungeon_models/"+family.name().toLowerCase(java.util.Locale.ROOT)+".json")).orElseThrow().open(),StandardCharsets.UTF_8)){
      var data=JsonParser.parseReader(reader).getAsJsonObject();var mesh=new MeshDefinition();var known=new LinkedHashMap<String,PartDefinition>();known.put("root",mesh.getRoot());
      for(var el:data.getAsJsonArray("parts")){var o=el.getAsJsonObject();var cubes=CubeListBuilder.create();for(var b:o.getAsJsonArray("boxes")){var c=b.getAsJsonObject();var at=c.getAsJsonArray("at");var size=c.getAsJsonArray("size");var uv=c.getAsJsonArray("uv");cubes.texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt()).addBox(at.get(0).getAsFloat(),at.get(1).getAsFloat(),at.get(2).getAsFloat(),size.get(0).getAsFloat(),size.get(1).getAsFloat(),size.get(2).getAsFloat());}var p=o.getAsJsonArray("pos");var rot=o.getAsJsonArray("rot");String name=o.get("name").getAsString();known.put(name,known.get(o.get("parent").getAsString()).addOrReplaceChild(name,cubes,PartPose.offsetAndRotation(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat(),rot.get(0).getAsFloat(),rot.get(1).getAsFloat(),rot.get(2).getAsFloat())));}
      return LayerDefinition.create(mesh,128,128);
    }catch(IOException|RuntimeException ex){throw new IllegalStateException("Dungeon model missing: "+family,ex);}}
    private ModelPart part(String name){return parts.get(name);}
    private void angle(String name,float x,float y,float z){var p=part(name);if(p!=null){p.xRot+=x;p.yRot+=y;p.zRot+=z;}}
    @Override public void setupAnim(DungeonMob mob,float walk,float amount,float age,float yaw,float pitch){root.getAllParts().forEach(ModelPart::resetPose);float hit=Mth.sin(mob.getAttackAnim(age-mob.tickCount)*Mth.PI);angle("head",pitch*Mth.DEG_TO_RAD*.6f,yaw*Mth.DEG_TO_RAD*.65f,0);angle("arm_r",Mth.cos(walk*.7f)*amount*.65f-hit*1.3f,0,0);angle("arm_l",-Mth.cos(walk*.7f)*amount*.65f-hit*.5f,0,0);angle("leg_r",-Mth.cos(walk*.7f)*amount*.8f,0,0);angle("leg_l",Mth.cos(walk*.7f)*amount*.8f,0,0);angle("tail",0,Mth.sin(age*.12f)*(.1f+amount*.3f),0);
      if(mob.species().family()==MobCatalog.Family.LIZARD)for(int i=0;i<4;i++)angle("leg_"+i,Mth.sin(walk*.7f+(i%2)*Mth.PI)*amount*.6f,0,0);
      if(mob.species().family()==MobCatalog.Family.TENTACLE)for(int i=0;i<8;i++)angle("tentacle_"+i,Mth.sin(age*.12f+i)*.12f,Mth.sin(age*.08f+i)*.18f,Mth.cos(age*.1f+i)*.16f);
      if(mob.species().family()==MobCatalog.Family.TOAD){angle("leg_r",0,0,hit*.3f);angle("leg_l",0,0,-hit*.3f);}
      if(mob.species().family()==MobCatalog.Family.DEMON){angle("wing_l",0,Mth.sin(age*.15f)*.18f,0);angle("wing_r",0,-Mth.sin(age*.15f)*.18f,0);}
      if(mob.species().family()==MobCatalog.Family.MIMIC){boolean awake=mob.visuallyAwake();angle("lid",awake?-.45f-hit*.6f:0,0,0);var t=part("tongue");if(t!=null)t.visible=awake;}
      if(mob.species().family()==MobCatalog.Family.SLIME){var body=part("body");if(body!=null){float pulse=Mth.sin(age*.22f)*.06f;body.xScale=1+pulse;body.zScale=1+pulse;body.yScale=1-pulse;}}
      for(String name:new String[]{"weapon_staff","weapon_blade","weapon_shield","weapon_bow"}){var p=part(name);if(p!=null)p.visible=switch(name){case "weapon_staff"->mob.species().role()==MobCatalog.Role.CASTER;case "weapon_shield"->mob.species().role()==MobCatalog.Role.TANK;case "weapon_bow"->mob.species().role()==MobCatalog.Role.ARCHER;default->mob.species().role()==MobCatalog.Role.WARRIOR||mob.species().role()==MobCatalog.Role.TANK;};}
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,float r,float g,float b,float a){root.render(pose,buffer,light,overlay,r,g,b,a);}
}
