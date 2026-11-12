package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import java.util.List;

/** Normal textured BakedModels on water entities, independent of Spell Engine. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WaterSolidModels {
    public static final List<String> MODELS=List.of("seal","raindrop","rain_crown","sea_god_sword","water_prison_panel",
            // ★ 2026-11-10 水牢的隔板改成**罗马圣柱**（作者："做成一个个罗马圣柱那样"✗）
            //   三段式：柱础 / 柱身 / 柱头 —— 柱子高度是变的，一整根只能整体拉 y ✗（柱础柱头会跟着拉成一坨 ✗）。
            //   ⚠️ 这三个名字**必须**在这个名单里 ✗ —— 不在 = 模型不进烘焙 = 游戏里柱子直接隐形 ✗
            //   （`WaterPrisonColumnTest` 会在构建期盯住这件事 ✓）
            "roman_column_base","roman_column_shaft","roman_column_capital",
            // ★ 2026-11-10 第二版（作者："不够精致，尽管做精致"）：柱头浮雕（茛苕叶 / 四角涡卷 / 玫瑰花）
            //   单独一个模型 ⇒ **远处不画它**（`romanColumns(..., detailed)`）✓
            "roman_column_ornament");
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional e) {
        for(String name:MODELS)e.register(id(name));
    }
    @SubscribeEvent public static void validate(ModelEvent.ModifyBakingResult e) {
        for(String name:MODELS) {
            var model=e.getModels().get(id(name));
            if(model==null||model.getParticleIcon().contents().name().getPath().equals("missingno"))
                org.slf4j.LoggerFactory.getLogger("TN-C/water-models").error("Missing baked water model: {}",name);
        }
    }
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("tnc","water/"+name);}
    static void draw(String name,PoseStack poses,MultiBufferSource buffers) {
        draw(name,poses,buffers,1);
    }
    static void draw(String name,PoseStack poses,MultiBufferSource buffers,float opacity) {
        draw(name,poses,buffers,opacity,1.0F,LightTexture.FULL_BRIGHT);
    }
    static void draw(String name,PoseStack poses,MultiBufferSource buffers,float opacity,float brightness,int packedLight) {
        var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(id(name));
        poses.pushPose();poses.translate(-.5,0,-.5);
// Open seal strokes keep depth writes while dimmed models retain local tinting.
        boolean translucent=opacity<1 || brightness<1;
        boolean solidRelic=name.equals("seal")||name.equals("sea_god_sword")||name.equals("raindrop");
        var consumer=buffers.getBuffer(translucent?WaterModelRenderTypes.alpha():solidRelic?RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS):RenderType.eyes(TextureAtlas.LOCATION_BLOCKS));
        if(translucent)consumer=new TintAlpha(consumer,brightness,opacity);
        mc.getItemRenderer().renderModelLists(model,ItemStack.EMPTY,packedLight,
                OverlayTexture.NO_OVERLAY,poses,consumer);
        if(name.equals("sea_god_sword")||name.equals("seal")) {
            // Full light-map brightness alone does not create the additive glow.
            var emission=new TintAlpha(buffers.getBuffer(WaterModelRenderTypes.emission()),1,
                    name.equals("sea_god_sword")?.85F:Math.max(.42F,opacity*.7F));
            mc.getItemRenderer().renderModelLists(model,ItemStack.EMPTY,LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,poses,emission);
        } else if(name.startsWith("roman_column_")) {
            // 柱子的"虚化"：底下那遍是半透明水色本体 ✓，这一遍是很淡的加色自发光 ✓
            //（作者："弄成蓝色贴图再虚化"✗ —— 只有半透明会像塑料，加一点自发光才是"水做的"✓）
            var emission=new TintAlpha(buffers.getBuffer(WaterModelRenderTypes.emission()),1,.30F);
            mc.getItemRenderer().renderModelLists(model,ItemStack.EMPTY,LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,poses,emission);
        }
        poses.popPose();
    }
    static void seal(PoseStack poses,MultiBufferSource buffers,Vec3 at,Vec3 normal,double radius,double age,float envelope) {
        seal(poses,buffers,at,normal,radius,age,envelope,1);
    }
    static void seal(PoseStack poses,MultiBufferSource buffers,Vec3 at,Vec3 normal,double radius,double age,float envelope,float opacity) {
        if(envelope<=0)return;
        poses.pushPose();poses.translate(at.x,at.y,at.z);
        poses.mulPose(new Quaternionf().rotationTo(0,1,0,(float)normal.x,(float)normal.y,(float)normal.z));
        poses.mulPose(Axis.YP.rotationDegrees((float)(age*.55)));
        float size=(float)(radius*2*Math.min(1,envelope*3));
        poses.scale(size,(float)(.8+Math.min(radius,8)*.15),size);
        draw("seal",poses,buffers,opacity);poses.popPose();
    }
    static void prisonPanels(PoseStack poses,MultiBufferSource buffers,double radius,double height,int panels,double age,
                             float opacity,float brightness,int packedLight) {
        double circumference=Math.PI*2*radius, width=circumference/panels;
        for(int i=0;i<panels;i++) {
            double angle=i*Math.PI*2/panels+age*.003;
            poses.pushPose();
            poses.translate(Math.cos(angle)*radius*1.018,.08,Math.sin(angle)*radius*1.018);
            poses.mulPose(Axis.YP.rotationDegrees((float)(90-angle*180/Math.PI)));
            poses.scale((float)(width/.76),(float)height,.72F);
            draw("water_prison_panel",poses,buffers,opacity,brightness,packedLight);
            poses.popPose();
        }
    }
    /** Preview/backward-compatible overload; gameplay passes explicit dimming and scene light. */
    static void prisonPanels(PoseStack poses,MultiBufferSource buffers,double radius,double height,int panels,double age,float opacity) {
        prisonPanels(poses,buffers,radius,height,panels,age,opacity,1.0F,LightTexture.FULL_BRIGHT);
    }

    /**
     * ★ <b>水牢的罗马圣柱</b>（作者 2026-11-10：「水牢的隔板再改改吧，我想做成一个个罗马圣柱那样，
     * 你可以先做个圣柱的建模，然后弄成蓝色贴图再虚化」→ 第二版「圣柱有点丑，不够精致，尽管做精致」✓）。
     *
     * <p>和老的 {@link #prisonPanels} 的区别：那些是**一片片贴在一起的隔板** ✗，
     * 现在是一根根**独立的柱子**围成一圈 ⇒ 水牢读作"神殿的柱廊"而不是"水板桶"✓。
     *
     * <p>四段分开画（柱础 / 柱身 / 柱头 / 柱头浮雕 ✓）：
     * <ul>
     *   <li>柱础、柱头按<b>直径等比</b>缩放 ✓（比例永远对 ✓）；</li>
     *   <li>柱身只拉<b>高度</b> ✓（t1 七格到 t5 二十四格都是对的 ✗ —— 整根拉会把柱础柱头拉成一坨 ✗）；</li>
     *   <li>柱身分三段、直径 100%/98.5%/97% ⇒ <b>收分 entasis</b> ✓（古典柱"上细下粗"✓）；</li>
     *   <li>柱头浮雕（茛苕叶 / 四角涡卷 / 玫瑰花）最贵 ✗ —— 只给<b>离镜头 18 格内</b>的那几根画 ✓
     *       （水牢半径最大 26 格，玩家站在里面时只有身边三四根需要细节 ✓）；</li>
     *   <li>全部走半透明通道（{@code opacity} &lt; 1 ✓ = 作者要的"虚化"✗）＋ 一层很淡的加色自发光 ✓。</li>
     * </ul>
     *
     * @param columns  一圈几根（{@link WaterGeometry#columnCount} ✓）
     * @param diameter 单根柱子的直径（格 ✓ 由柱距推出来，见 {@link WaterGeometry#columnDiameter} ✓）
     * @param camera   <b>pose 空间</b>里的相机位置（{@code null} = 全画满 ✓）
     */
    static void romanColumns(PoseStack poses,MultiBufferSource buffers,double radius,double height,int columns,
                             double diameter,double age,float opacity,float brightness,int packedLight,
                             Vec3 camera) {
        if(columns<=0||radius<=0||height<=0.2)return;
        double s=Math.max(.35,diameter/.5);              // 模型柱身 8px = 0.5 格 ⇒ 直径 = .5×s
        double baseH=.3875*s*1.15;                       // 柱础模型 6.2px 高（×1.15 拔高）
        double capH=.3594*s*1.5;                         // 柱头模型 5.75px 高（×1.5 拔高）
        double shaftH=Math.max(.5,height-baseH-capH);
        double span=Math.PI*2/columns;
        double[] taper={1.0,0.985,0.97};                 // 收分：下 100% → 上 97% ✓
        for(int i=0;i<columns;i++) {
            double angle=i*span+age*.003;
            double x=Math.cos(angle)*radius,z=Math.sin(angle)*radius;
            float yaw=(float)(90-Math.toDegrees(angle));  // 柱子的"正面"朝外 ✓
            boolean detailed=camera==null
                    ||camera.distanceToSqr(x,height*.5,z)<18*18;
            poses.pushPose();
            poses.translate(x,.06,z);
            poses.mulPose(Axis.YP.rotationDegrees(yaw));
            poses.scale((float)s,(float)(s*1.15),(float)s);
            draw("roman_column_base",poses,buffers,opacity,brightness,packedLight);
            poses.popPose();

            for(int seg=0;seg<taper.length;seg++) {
                double segH=shaftH/taper.length;
                poses.pushPose();
                poses.translate(x,.06+baseH+segH*seg,z);
                poses.mulPose(Axis.YP.rotationDegrees(yaw));
                poses.scale((float)(s*taper[seg]),(float)segH,(float)(s*taper[seg]));
                draw("roman_column_shaft",poses,buffers,opacity,brightness,packedLight);
                poses.popPose();
            }

            poses.pushPose();
            poses.translate(x,.06+baseH+shaftH,z);
            poses.mulPose(Axis.YP.rotationDegrees(yaw));
            poses.scale((float)s,(float)(s*1.5),(float)s);
            draw("roman_column_capital",poses,buffers,opacity,brightness,packedLight);
            if(detailed)draw("roman_column_ornament",poses,buffers,opacity,brightness,packedLight);
            poses.popPose();
        }
    }

    /** 老签名（不画浮雕）—— 给预览 / 老调用点留着 ✓。 */
    static void romanColumns(PoseStack poses,MultiBufferSource buffers,double radius,double height,int columns,
                             double diameter,double age,float opacity,float brightness,int packedLight) {
        romanColumns(poses,buffers,radius,height,columns,diameter,age,opacity,brightness,packedLight,null);
    }
    private record TintAlpha(VertexConsumer target,float brightness,float opacity) implements VertexConsumer {
        public VertexConsumer vertex(double x,double y,double z){target.vertex(x,y,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){target.color(Math.round(r*brightness),Math.round(g*brightness),Math.round(b*brightness),Math.round(a*opacity));return this;}
        public VertexConsumer uv(float u,float v){target.uv(u,v);return this;}
        public VertexConsumer overlayCoords(int u,int v){target.overlayCoords(u,v);return this;}
        public VertexConsumer uv2(int u,int v){target.uv2(u,v);return this;}
        public VertexConsumer normal(float x,float y,float z){target.normal(x,y,z);return this;}
        public void endVertex(){target.endVertex();}
        public void defaultColor(int r,int g,int b,int a){target.defaultColor(Math.round(r*brightness),Math.round(g*brightness),Math.round(b*brightness),Math.round(a*opacity));}
        public void unsetDefaultColor(){target.unsetDefaultColor();}
    }
}
