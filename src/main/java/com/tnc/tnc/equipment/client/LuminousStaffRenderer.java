package com.tnc.tnc.equipment.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
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
import org.joml.Vector3f;
import org.joml.Quaternionf;
import com.tnc.tnc.client.LightningCastFx;

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
    private record Staff(List<Group> groups,Vector3f core,Vector3f grip,float[] color,int tier,int moteCount,boolean crescent,boolean scarlet,List<Vector3f> crownContacts,ResourceLocation base,ResourceLocation emission){}
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
            var grip=json.has("tnc_grip")?vector(json.getAsJsonArray("tnc_grip")):new Vector3f(.5f,.3125f,.5f);
            int tier = json.get("tnc_tier").getAsInt();
            boolean crescent = json.has("tnc_design") && "amethyst_crescent_v10".equals(json.get("tnc_design").getAsString());
            boolean scarlet = json.has("tnc_revision") && "scarlet_marquise_11".equals(json.get("tnc_revision").getAsString());
            boolean guildStaff = tier >= 1 && tier <= 4 && json.has("tnc_artwork")
                && "guild_progression".equals(json.get("tnc_artwork").getAsString());
            int moteCount = crescent ? 24 : 18 + tier * 5;
            if (guildStaff) {
                moteCount = switch (tier) {
                    case 1 -> 0;
                    case 2 -> 4;
                    case 3 -> 9;
                    default -> 15;
                };
            }
            var core = vector(json.getAsJsonArray("tnc_core"));
            var contacts = id.startsWith("lightning_wand_") && groups.stream().noneMatch(group -> group.motion.equals("electrode"))
                    ? crownContacts(groups, core) : List.<Vector3f>of();
            return new Staff(List.copyOf(groups), core, grip,
                new float[]{c.get(0).getAsFloat()/255, c.get(1).getAsFloat()/255, c.get(2).getAsFloat()/255},
                tier, moteCount, crescent, scarlet, contacts,
                ResourceLocation.parse("tnc:textures/item/wands/held/" + id + ".png"),
                ResourceLocation.parse("tnc:textures/item/wands/held/" + id + "_emission.png"));
        }catch(Exception e){throw new IllegalStateException("Cannot load authored staff "+id,e);}
    }
    private static void pivot(PoseStack pose,Vector3f p,float rotation){pose.translate(p.x,p.y,p.z);pose.mulPose(Axis.YP.rotation(rotation));pose.translate(-p.x,-p.y,-p.z);}
    private static void face(VertexConsumer out,PoseStack pose,Face f,int light,int overlay,float intensity,float alpha){
        var p=pose.last();for(int i=0;i<4;i++){var v=f.points[i];out.vertex(p.pose(),v.x,v.y,v.z).color(intensity,intensity,intensity,alpha).uv(f.uv[i][0],f.uv[i][1]).overlayCoords(overlay).uv2(light).normal(p.normal(),f.normal.x,f.normal.y,f.normal.z).endVertex();}
    }
private static final ResourceLocation CYAN_FIRE =
        ResourceLocation.parse("tnc:textures/effect/cyan_fire_flow.png");
    private static final ResourceLocation CYAN_FIRE_BODY =
        ResourceLocation.parse("tnc:textures/effect/cyan_fire_body.png");

    // A continuous UV seam follows the shaft. Inset each tile by half a texel so
    // linear filtering cannot pull a neighbouring frame into the silhouette.
    private static void flameFace(VertexConsumer out, PoseStack pose, Face face, int light, int overlay, int frame) {
        var transform = pose.last();
        int column = frame % 8, row = frame / 8;
        for (int i = 0; i < 4; i++) {
            var point = face.points[i];
            float u = (column + (0.5f + 127 * face.uv[i][0]) / 128) / 8;
            float v = (row + (0.5f + 159 * face.uv[i][1]) / 160) / 8;
            out.vertex(transform.pose(), point.x, point.y, point.z).color(1f, 1f, 1f, 1f)
                .uv(u, v).overlayCoords(overlay).uv2(light)
                .normal(transform.normal(), face.normal.x, face.normal.y, face.normal.z).endVertex();
        }
    }

    private static void glintFace(VertexConsumer out,PoseStack pose,Face face,int overlay,float size,float tint,float gain){
        float cx=0,cy=0,cz=0;
        for(var v:face.points){cx+=v.x;cy+=v.y;cz+=v.z;}
        cx*=.25f;cy*=.25f;cz*=.25f;
        var p=pose.last();
        for(int i=0;i<4;i++){
            var v=face.points[i];
            out.vertex(p.pose(),cx+(v.x-cx)*size,cy+(v.y-cy)*size,cz+(v.z-cz)*size)
                .color(gain,tint*gain,tint*gain,1f).uv(face.uv[i][0],face.uv[i][1]).overlayCoords(overlay)
                .uv2(LightTexture.FULL_BRIGHT).normal(p.normal(),face.normal.x,face.normal.y,face.normal.z).endVertex();
        }
    }



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
    // The new crystal crowns have no separate electrode parts. Pick two real vertices
    // on opposite sides of the stone once at model load, preserving the authored mesh.
    private static List<Vector3f> crownContacts(List<Group> groups, Vector3f core) {
        var contacts = new ArrayList<Vector3f>(2);
        for (float offset : new float[]{-.16F, .16F}) {
            var target = new Vector3f(core).add(offset, .04F, 0);
            float best = Float.POSITIVE_INFINITY;
            Vector3f nearest = null;
            for (Group group : groups) {
                if (!group.motion.isEmpty()) continue;
                for (Face face : group.faces) {
                    for (Vector3f point : face.points) {
                        float distance = point.distanceSquared(target);
                        if (distance < best) {
                            nearest = point;
                            best = distance;
                        }
                    }
                }
            }
            if (nearest != null && !contacts.contains(nearest)) contacts.add(nearest);
        }
        return List.copyOf(contacts);
    }

    private static void discharge(MultiBufferSource buffers, PoseStack pose, Vector3f from, float phase, Staff staff,
                                  double time, float charge, float exposure, boolean firstPerson) {
        double period = 42 - 3 * staff.tier;
        double clock = time + phase * 11;
        double age = clock % period;
        double duration = 4 + charge * 4;
        if (age >= duration) return;
        float alpha = (float) Math.sin(Math.PI * age / duration) * exposure * (firstPerson ? .68F : .95F);
        var to = staff.core;
        long seed = (long) (clock / period) * 101 + (long) phase * 31 + (long) (age / 2);
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
        int flameFrame = Math.floorMod((int)Math.floor((time % 40) * 1.6), 64);
        for(int layer=0;layer<2;layer++)for(var group:staff.groups){
            pose.pushPose();
            boolean moon=staff.crescent&&group.motion.startsWith("scarlet_");
            boolean dawn=group.motion.equals("dawn_feather");
            boolean dust=dawn||(moon&&!group.motion.equals("scarlet_veil"));
            double descent=dawn?time*.0072+group.phase:0;
            descent-=Math.floor(descent);
            if(dawn){
                double sway=Math.sin(time*.038+group.phase*3);
                pose.translate(.034*sway,.19-.38*descent,.014*Math.cos(time*.031+group.phase));
                pivot(pose,group.pivot,(float)(time*.011+group.phase+.20*sway));
            }else if(moon){
                double rate=switch(group.motion){
                    case "scarlet_veil" -> .021;
                    case "scarlet_silver" -> -.027;
                    case "scarlet_star" -> .034;
                    default -> .024;
                };
                pose.translate(0,.027*Math.sin(time*.045+group.phase),0);
                pivot(pose,group.pivot,(float)(time*rate+group.phase));
            }else{
                animate(pose,group,staff,time,firstPerson ? charge * .56F : charge);
            }
            int mote=0;
            var out=buffers.getBuffer(layer==0?RenderType.entityCutoutNoCull(staff.base):StaffGlow.jewel(staff.emission));
            for(var f:group.faces){
                if (f.material == 7) {
                    // The depth-bearing inner tongue keeps its colour against a shader sky.
                    // The wider translucent fringe is full-bright against terrain and in shadow.
                    var flame = layer == 0 ? RenderType.entityCutoutNoCull(CYAN_FIRE_BODY) : StaffGlow.mist(CYAN_FIRE);
                    flameFace(buffers.getBuffer(flame), pose, f, layer == 0 ? light : LightTexture.FULL_BRIGHT, overlay, flameFrame);
                    continue;
                }
                boolean crystal=f.material==2;
                boolean mist=staff.crescent&&f.material==6;
                boolean shell=staff.crescent&&(f.material==4||f.material==5);
                boolean luminous=staff.crescent&&f.material==3;
                // The opaque body is submitted before emission. A translucent
                // body can be sorted AFTER the glow by Oculus and hide it; no
                // body lets additive shader-pack eyes blend straight into sky.
                if(dust){
                    // Opaque rose rim and a full-bright centre give this sparkle contrast against sky.
                    // Change its projected size as well as its colour, so bloom is optional.
                    float wave=(float)(.5+.5*Math.sin(time*(.065+.009*(mote%6))+2.39996*mote+group.phase));
                    if(dawn){
                        float fade=(float)Math.min(1,Math.min(descent,1-descent)*7);
                        float flutter=(float)(.88+.12*Math.sin(time*.11+group.phase));
                        glintFace(out,pose,f,overlay,fade*flutter,.93f,layer==0?1f:.57f*fade);
                    }else glintFace(out,pose,f,overlay,.25f+.92f*wave*wave,.61f+.39f*wave,layer==0?1f:.46f+.42f*wave);
                    mote++;
                }else if(layer==0&&!shell&&!mist)face(out,pose,f,(crystal||luminous)?LightTexture.FULL_BRIGHT:light,overlay,1,1);
                if(layer==1&&mist){
                    face(buffers.getBuffer(StaffGlow.mist(staff.base)),pose,f,LightTexture.FULL_BRIGHT,overlay,1,1);
                    face(buffers.getBuffer(StaffGlow.jewel(staff.emission)),pose,f,LightTexture.FULL_BRIGHT,overlay,.96f,1);
                }
                if(layer==1&&shell)face(buffers.getBuffer(StaffGlow.shell(staff.emission)),pose,f,LightTexture.FULL_BRIGHT,overlay,exposure*(f.material==5?.65f:.9f),1);
                if(layer==1&&moon&&!dust)face(out,pose,f,LightTexture.FULL_BRIGHT,overlay,.72f,1);
                if(layer==1&&crystal){
                    float multiplier=staff.scarlet?.88f:exposure*(staff.crescent?.12f:1f);
                    face(out,pose,f,LightTexture.FULL_BRIGHT,overlay,multiplier*(float)(.92+.08*Math.sin(time*.065+group.phase)),1);
                }
            }
            pose.popPose();
            if (layer == 1 && group.motion.equals("electrode") && (firstPerson
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
                discharge(buffers, pose, group.pivot, group.phase, staff, time, charge, exposure, firstPerson);
            }
        }
        // Local-volume glow works without a shader pack. It is not a screen-wide
        // particle emitter and never creates world particles in menus.
        if (charge > 0 && (firstPerson || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            for (int i = 0; i < staff.crownContacts.size(); ++i) {
                discharge(buffers, pose, staff.crownContacts.get(i), i, staff, time, charge, exposure, firstPerson);
            }
        }
        if (staff.moteCount == 0 && !(lightning && charge > 0)) return;
        var glow=buffers.getBuffer(StaffGlow.TYPE);var c=staff.color;
        if (lightning) {
            lightningEnergy(glow, pose, staff, time, exposure, charge, firstPerson);
            return;
        }
        float pulse=(float)(.82+.12*Math.sin(time*.065));
        if(!staff.crescent){
            spark(glow,pose,staff.core,.115f,c,pulse);
            spark(glow,pose,staff.core,.28f,c,.12f);
        }
        int count=staff.moteCount;
        for(int i=0;i<count;i++){
            double a=i*2.39996+time*.019,t=(i/(double)count+time*.003)%1;
            float r=(float)(.10+.14*Math.sin(t*Math.PI));
            var at=new Vector3f(staff.core).add((float)Math.cos(a)*r,(float)(t-.5)*.72f,(float)Math.sin(a)*r);
            float fade=(float)Math.pow(Math.sin(t*Math.PI),2);
            float twinkle=(float)(.18+.82*Math.pow(.5+.5*Math.sin(time*(.08+(i%5)*.013)+i*1.73),3));
            float size=staff.crescent?(i%7==0?.018f:.009f):(i%7==0?.026f:.012f);
            spark(glow,pose,at,size,c,staff.crescent?exposure*fade*twinkle:(float)(.3+.5*Math.sin(t*Math.PI)));
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
        private static final Map<ResourceLocation,RenderType> MISTS=new HashMap<>();
        static RenderType mist(ResourceLocation texture){return MISTS.computeIfAbsent(texture,t->create("tnc_staff_local_mist",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new TextureStateShard(t,true,false)).setCullState(NO_CULL)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE).createCompositeState(false)));}
        private static final Map<ResourceLocation,RenderType> SHELLS=new HashMap<>();
        // Reverse-wound outline shells show only their far surface; the opaque crystal
        // occludes their centre. No depth writes and no whole-staff halo copies.
        static RenderType shell(ResourceLocation texture){return SHELLS.computeIfAbsent(texture,t->create("tnc_staff_crescent_shell",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new TextureStateShard(t,false,false)).setCullState(CULL)
                .setTransparencyState(ADDITIVE_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE).createCompositeState(false)));}
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
