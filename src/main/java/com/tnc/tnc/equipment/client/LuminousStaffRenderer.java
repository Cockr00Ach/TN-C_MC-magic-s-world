package com.tnc.tnc.equipment.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.tnc.tnc.client.LightningCastFx;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Actual three-dimensional jewels and local energy, in every held-item context. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class LuminousStaffRenderer extends BlockEntityWithoutLevelRenderer {
    public static final class Extension implements IClientItemExtensions {
        private LuminousStaffRenderer renderer;
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){
            if(renderer==null)renderer=new LuminousStaffRenderer();return renderer;
        }
    }
    private record Face(Vector3f[] points,float[][] uv,Vector3f normal,int material,boolean glow){}
    private record Group(List<Face> faces,String motion,Vector3f pivot,float phase){}
    private record Staff(List<Group> groups,Vector3f core,float[] color,int tier,ResourceLocation base,ResourceLocation emission){}
    private static final Map<String,Staff> CACHE=new HashMap<>();
    private static final class ShaderPack {
        private static Object api;
        private static java.lang.reflect.Method active;
        static {
            try {
                var type=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api=type.getMethod("getInstance").invoke(null);active=type.getMethod("isShaderPackInUse");
            } catch(ReflectiveOperationException|LinkageError ignored) {}
        }
        static float exposure(){
            try{return active!=null&&Boolean.TRUE.equals(active.invoke(api))?.45f:1f;}
            catch(ReflectiveOperationException ignored){return 1f;}
        }
    }
    @SubscribeEvent public static void rebaked(ModelEvent.BakingCompleted e){CACHE.clear();}
    public LuminousStaffRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    private static Vector3f vector(JsonArray a){return new Vector3f(a.get(0).getAsFloat()/16,a.get(1).getAsFloat()/16,a.get(2).getAsFloat()/16);}
    private static Staff load(String id){
        var mc=Minecraft.getInstance();var location=ResourceLocation.parse("tnc:models/item/wands/held/"+id+".json");
        try(var reader=mc.getResourceManager().getResourceOrThrow(location).openAsReader()){
            var json=JsonParser.parseReader(reader).getAsJsonObject();
            var lists=new LinkedHashMap<String,List<Face>>();var metadata=new HashMap<String,JsonObject>();
            for(var raw:json.getAsJsonArray("surfaces")){
                var f=raw.getAsJsonObject();String part=f.get("part").getAsString();
                String key=part+(f.has("pivot")?f.get("pivot").toString():"");
                var points=new Vector3f[4];var uv=new float[4][2];
                for(int i=0;i<4;i++){points[i]=vector(f.getAsJsonArray("vertices").get(i).getAsJsonArray());var t=f.getAsJsonArray("uv").get(i).getAsJsonArray();uv[i][0]=t.get(0).getAsFloat()/16;uv[i][1]=t.get(1).getAsFloat()/16;}
                var normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
                if(normal.lengthSquared()<1e-12)normal=new Vector3f(points[2]).sub(points[0]).cross(new Vector3f(points[3]).sub(points[0]));
                if(normal.lengthSquared()<1e-12)continue;normal.normalize();
                lists.computeIfAbsent(key,k->new ArrayList<>()).add(new Face(points,uv,normal,f.get("material").getAsInt(),f.has("glow")&&f.get("glow").getAsBoolean()));
                metadata.put(key,f);
            }
            var groups=new ArrayList<Group>();lists.forEach((key,faces)->{var f=metadata.get(key);groups.add(new Group(List.copyOf(faces),f.has("motion")?f.get("motion").getAsString():"",f.has("pivot")?vector(f.getAsJsonArray("pivot")):new Vector3f(),f.has("phase")?f.get("phase").getAsFloat():0));});
            var c=json.getAsJsonArray("tnc_color");
            return new Staff(List.copyOf(groups),vector(json.getAsJsonArray("tnc_core")),new float[]{c.get(0).getAsFloat()/255,c.get(1).getAsFloat()/255,c.get(2).getAsFloat()/255},json.get("tnc_tier").getAsInt(),ResourceLocation.parse("tnc:textures/item/wands/held/"+id+".png"),ResourceLocation.parse("tnc:textures/item/wands/held/"+id+"_emission.png"));
        }catch(Exception e){throw new IllegalStateException("Cannot load authored staff "+id,e);}
    }
    private static void pivot(PoseStack pose,Vector3f p,float rotation){pose.translate(p.x,p.y,p.z);pose.mulPose(Axis.YP.rotation(rotation));pose.translate(-p.x,-p.y,-p.z);}

    private static void animate(PoseStack pose, Group group, Staff staff, double time, float charge) {
        switch (group.motion) {
            case "orbit" -> {
                pivot(pose, staff.core, (float) (time * .009));
                pivot(pose, group.pivot, (float) (time * .016 + group.phase));
            }
            case "storm_orbit" -> {
                pivot(pose, staff.core, (float) (time * .019));
                pivot(pose, group.pivot, (float) (time * .028 + group.phase));
            }
            case "armillary" -> {
                float speed = group.phase == 0 ? .014F : group.phase;
                pivot(pose, group.pivot, (float) (time * speed + charge * .34));
            }
            case "crown" -> {
                Vector3f center = group.pivot;
                float open = .08F + .035F * (float) Math.sin(time * .07) + .33F * charge;
                pose.translate(center.x, center.y, center.z);
                pose.mulPose(new Quaternionf().rotationAxis(open,
                        (float) Math.sin(group.phase), 0, -(float) Math.cos(group.phase)));
                pose.translate(-center.x, -center.y, -center.z);
            }
            case "", "electrode" -> { }
            default -> pivot(pose, group.pivot, (float) (time * .016 + group.phase));
        }
    }
    private static void face(VertexConsumer out,PoseStack pose,Face f,int light,int overlay,float intensity,float alpha){
        var p=pose.last();for(int i=0;i<4;i++){var v=f.points[i];out.vertex(p.pose(),v.x,v.y,v.z).color(intensity,intensity,intensity,alpha).uv(f.uv[i][0],f.uv[i][1]).overlayCoords(overlay).uv2(light).normal(p.normal(),f.normal.x,f.normal.y,f.normal.z).endVertex();}
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var id=ForgeRegistries.ITEMS.getKey(stack.getItem());if(id==null)return;
        var staff=CACHE.computeIfAbsent(id.getPath(),LuminousStaffRenderer::load);
        var mc=Minecraft.getInstance();double time=mc.level==null?0:mc.level.getGameTime()+mc.getFrameTime();
        float exposure=ShaderPack.exposure();
        boolean lightning = id.getPath().startsWith("lightning_wand_");
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        float charge = lightning && mc.player != null
                && (mc.player.getMainHandItem() == stack || mc.player.getOffhandItem() == stack)
                ? LightningCastFx.localCharge() : 0;
        for(int layer=0;layer<2;layer++)for(var group:staff.groups){
            pose.pushPose();
            animate(pose, group, staff, time, firstPerson ? charge * .56F : charge);
            var out=buffers.getBuffer(layer==0?RenderType.entityCutoutNoCull(staff.base):StaffGlow.jewel(staff.emission));
            for(var f:group.faces){
                boolean crystal=f.material==2;
                // The opaque body is submitted before emission. A translucent
                // body can be sorted AFTER the glow by Oculus and hide it; no
                // body lets additive shader-pack eyes blend straight into sky.
                if(layer==0)face(out,pose,f,crystal?LightTexture.FULL_BRIGHT:light,overlay,1,1);
                if(layer==1&&crystal)face(out,pose,f,LightTexture.FULL_BRIGHT,overlay,exposure*(float)(.92+.08*Math.sin(time*.065+group.phase)),1);
            }
            pose.popPose();
            if (layer == 1 && group.motion.equals("electrode") && (firstPerson
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
                discharge(buffers, pose, group, staff, time, charge, exposure, firstPerson);
            }
        }
        // Local-volume glow works without a shader pack. It is not a screen-wide
        // particle emitter and never creates world particles in menus.
        var glow=buffers.getBuffer(StaffGlow.TYPE);var c=staff.color;
        if (lightning) {
            lightningEnergy(glow, pose, staff, time, exposure, charge, firstPerson);
            return;
        }
        float pulse=(float)(.82+.12*Math.sin(time*.065));
        spark(glow,pose,staff.core,.115f,c,pulse);
        spark(glow,pose,staff.core,.28f,c,.12f);
        int count=18+staff.tier*5;
        for(int i=0;i<count;i++){
            double a=i*2.39996+time*.019,t=(i/(double)count+time*.003)%1;
            float r=(float)(.10+.14*Math.sin(t*Math.PI));
            var at=new Vector3f(staff.core).add((float)Math.cos(a)*r,(float)(t-.5)*.72f,(float)Math.sin(a)*r);
            spark(glow,pose,at,i%7==0?.026f:.012f,c,(float)(.3+.5*Math.sin(t*Math.PI)));
        }
    }

    private static void discharge(MultiBufferSource buffers, PoseStack pose, Group group, Staff staff,
                                  double time, float charge, float exposure, boolean firstPerson) {
        double period = 42 - 3 * staff.tier;
        double clock = time + group.phase * 11;
        double age = clock % period;
        double duration = 4 + charge * 4;
        if (age >= duration) return;
        float alpha = (float) Math.sin(Math.PI * age / duration) * exposure * (firstPerson ? .68F : .95F);
        var from = group.pivot;
        var to = staff.core;
        long seed = (long) (clock / period) * 101 + (long) group.phase * 31 + (long) (age / 2);
        var out = buffers.getBuffer(com.tnc.tnc.client.lightning.LightningRenderTypes.BOLT);
        int theme = staff.tier == 5 ? 0 : staff.tier == 3 ? 2 : 1;
        com.tnc.tnc.client.lightning.LightningBoltGeometry.arc(out, pose.last().pose(),
                from.x, from.y, from.z, to.x, to.y, to.z, seed, .005F, alpha, theme, .5, 1, 3);
    }

    private static void lightningEnergy(VertexConsumer out, PoseStack pose, Staff staff, double time,
                                        float exposure, float charge, boolean firstPerson) {
        int tier = staff.tier;
        float pulse = exposure * (.67F + .12F * (float) Math.sin(time * .08) + .21F * charge);
        float view = firstPerson ? .46F : 1F;
        float radius = .057F + .017F * tier;
        spark(out, pose, staff.core, radius, staff.color, pulse * view);
        if (!firstPerson && tier >= 3) {
            spark(out, pose, staff.core, radius * 2.2F, staff.color, pulse * .12F);
        }
        int count = firstPerson ? tier : 3 + 2 * tier;
        for (int i = 0; i < count; i++) {
            double position = i / (double) count;
            double angle = position * Math.PI * 2 + time * (.031 + tier * .012);
            float r = radius * (1 + .82F * charge) * (tier >= 4 ? 1.66F : 1F);
            var at = new Vector3f(staff.core).add(r * (float) Math.cos(angle),
                    r * (float) Math.sin(angle * 2 + time * .031) * 1.24F, r * (float) Math.sin(angle));
            float size = (i % 3 == 0 ? .022F : .013F) * (.74F + tier * .095F);
            spark(out, pose, at, size, staff.color, pulse * view * (.42F + (float) (.21 * Math.sin(angle))));
        }
    }
    private static void spark(VertexConsumer out,PoseStack pose,Vector3f c,float size,float[] color,float alpha){
        var p=pose.last();
        for(int axis=0;axis<3;axis++)for(int i=0;i<4;i++){
            float u=i==0||i==3?0:1,v=i<2?0:1,a=(u-.5f)*size*2,b=(v-.5f)*size*2;
            float x=c.x+(axis==0?0:a),y=c.y+(axis==1?0:axis==0?a:b),z=c.z+(axis==2?0:b);
            out.vertex(p.pose(),x,y,z).color(.35f+color[0]*.65f,.35f+color[1]*.65f,.35f+color[2]*.65f,alpha).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(p.normal(),0,1,0).endVertex();
        }
    }
    private static final class StaffGlow extends RenderType {
        private StaffGlow(){super("tnc_staff_energy",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
        private static final Map<ResourceLocation,RenderType> JEWELS=new HashMap<>();
        static RenderType jewel(ResourceLocation texture){return JEWELS.computeIfAbsent(texture,t->create("tnc_staff_jewel",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new TextureStateShard(t,false,false)).setCullState(NO_CULL)
                .setTransparencyState(ADDITIVE_TRANSPARENCY).setLayeringState(POLYGON_OFFSET_LAYERING)
                .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));}
        static final RenderType TYPE=create("tnc_staff_energy",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,16384,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
                .setTextureState(new TextureStateShard(ResourceLocation.parse("tnc:textures/particle/mana_glow.png"),false,false))
                .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE).setOverlayState(OVERLAY).setOutputState(MAIN_TARGET).createCompositeState(false));
    }
}
