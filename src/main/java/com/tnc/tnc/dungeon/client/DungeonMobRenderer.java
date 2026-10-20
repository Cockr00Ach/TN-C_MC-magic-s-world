package com.tnc.tnc.dungeon.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.dungeon.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
public final class DungeonMobRenderer extends MobRenderer<DungeonMob,DungeonMobModel> {
    public static ModelLayerLocation layer(MobCatalog.Family f){return new ModelLayerLocation(DungeonContent.id("dungeon_"+f.name().toLowerCase(java.util.Locale.ROOT)),"main");}
    public DungeonMobRenderer(EntityRendererProvider.Context c,MobCatalog.Family family){super(c,new DungeonMobModel(c.bakeLayer(layer(family)),family),family==MobCatalog.Family.TOAD||family==MobCatalog.Family.LIZARD?.75f:.45f);}
    @Override public ResourceLocation getTextureLocation(DungeonMob mob){return DungeonContent.id("textures/entity/dungeon/"+mob.species().id()+".png");}
    @Override protected void scale(DungeonMob mob,PoseStack p,float partial){float base=switch(mob.species().family()){case GOBLIN->1.5f;case SKELETON->1.82f;case ORC->1.9f;case DEMON->2.15f;case LIZARDFOLK->1.8f;case TOAD->1.3f;case TENTACLE->1.2f;case LIZARD->1f;case SLIME->1f;case MIMIC->.9f;};float s=mob.species().height()/base*(mob.guardian()?1.8f:1);p.scale(s,s,s);}
    @Override protected RenderType getRenderType(DungeonMob mob,boolean body,boolean invisible,boolean glow){return mob.species().family()==MobCatalog.Family.SLIME&&body?RenderType.entityTranslucent(getTextureLocation(mob)):super.getRenderType(mob,body,invisible,glow);}
}
