package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class TNWaterFieldRenderer extends EntityRenderer<TNWaterFieldEntity> {
    public TNWaterFieldRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(TNWaterFieldEntity e){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/water_still.png");}
    // 第十批：可见距离 256 → **144 格** ✗ —— 半径 64 的灭世之海在 256 格外仍在全量作画 ✗
    //（144 格时整个 128 格宽的海面还在画面里 ✓，但**背后那些远到看不清细节的场**不再白烧 GPU ✓）
    @Override public boolean shouldRender(TNWaterFieldEntity e,Frustum f,double x,double y,double z){if(e.distanceToSqr(x,y,z)>=144*144)return false;
        Vec3 center=e.waveCenter(e.age());
        double r=(e.kind()==2&&e.tier()==5?Math.max(e.radius(),46.8):e.radius()*1.25), h=e.kind()==2&&e.tier()==5?Math.max(120,SeaGodSwordRules.DROP_HEIGHT+16):e.height()+4;
        // Rolled slash blades also extend below their flight axis.
        double below=e.kind()==1&&e.tier()==3?r:1;
        return f.isVisible(new net.minecraft.world.phys.AABB(e.position(),center).inflate(r,below,r).expandTowards(0,h,0));}
    @Override public void render(TNWaterFieldEntity e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        buffers=WaterTransparencyPass.route(buffers);
        if(e.isEmitter()) {
            WaterGeometry.staticSeal(buffers,stack,e.direction().scale(.7),e.direction(),1.1,
                    e.visualAge(partial),1,e.tier());
            return;
        }
        double animation=e.visualAge(partial);
        double age=e.age()+partial;float fade=(float)Math.min(1,Math.min(age/5,(e.life()-age)/8))*(e.kind()==2?FirstPersonSpellView.opacity(e.visualOwner(),e.waveCenter(age),Math.min(4,e.radius())):1);if(fade<=0)return;
        if(e.kind()==3){WaterRainVisuals.render(e,animation,fade,stack,buffers);return;}
        var out=buffers.getBuffer(WaterRenderTypes.geometry());var pose=stack.last().pose();double radius=e.radius();Vec3 up=new Vec3(0,1,0);
        if(e.kind()==1) {
            Vec3 center=e.waveCenter(age).subtract(e.position());
            if(e.tier()==4) {
                boolean near=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(e.waveCenter(age))<64*64;
                TsunamiVisuals.render(stack,buffers,center,e.direction(),radius,8,age,fade,near);
                WaterGeometry.richCircle(buffers,stack,new Vec3(0,.05,0),up,8,animation,fade*.9F,3,4);
                return;
            }
            if(e.tier()==1) {
                // 第十批：地面三环原来每圈 96 段（384 顶点 ✗）⇒ 改成 48 段 ✓
                for(int i=0;i<3;i++)WaterGeometry.ring(out,pose,new Vec3(0,.1+i*.12,0),up,Math.min(radius,age*.2+i*.3),.09,0,fade,48);
            } else if(e.tier()==5) {
                WaterGeometry.disk(out,pose,new Vec3(0,.08,0),up,radius,.005F,.12F,.42F,fade*.16F);
                for(int i=0;i<OceanWaveRules.MAX_FRONTS;i++) {
                    double r=OceanWaveRules.frontRadius(age,i);if(r<0)continue;
                    float edge=(float)Math.min(1,Math.max(0,(radius-r)/8));
                    WaterWaveVisuals.breakingWave(out,pose,r,OceanWaveRules.frontHeight(r),radius,age+r*.1,fade*edge,WaterWaveVisuals.frontTint(i));
                }
                for(int i=0;i<3;i++)WaterGeometry.ringColor(out,pose,new Vec3(0,.12+i*.05,0),up,
                        (age*.9+i*radius/3)%radius,.18,.04F,.45F,.8F,fade*.5F);
            } else {
                if(e.tier()==3)WaterWaveVisuals.crescent(out,pose,center,e.direction(),radius,age,fade,WaterSpellRules.slashRollRadians(e.roll(),age));
                // t4 海啸：浪体与浪花水柱都加粗一倍 ✓（作者 2026-10-10"水柱可以加粗一倍"✗）；t2 水波保持 1 倍 ✓
                else {
                    double waveHeight=e.tier()==4?8:2,bulk=e.tier()==4?2.0:1.0;
                    WaterWaveVisuals.rollingWave(out,pose,center,e.direction(),radius,waveHeight,age,fade,bulk);
                }
            }
            // t4/t5 释放时在**脚下**铺一个自发光法阵 ✓（作者 2026-10-10："水波链释放 t4t5 脚下要生成魔法阵"✗）
            // 只用 3 个子阵 ✗：这两个阵要和大浪一起画，子阵给 6 个纯属白烧 GPU ✗
            if(e.tier()>=4)WaterGeometry.richCircle(buffers,stack,new Vec3(0,.05,0),up,e.tier()==5?16:8,animation,fade*.9F,3,e.tier());
            /* ---- 加色发光层（2026-10-10 第二批次 · 水纹链）----
               只给"浪唇 / 环边"补一层加色亮线 ✓：alpha 混合的水体会把重叠处涂浑 ✗，
               加色才会把浪唇推成被光照亮的样子 ✗。kind==2/3 两条链本批**没动** ✗。 */
            var glow=buffers.getBuffer(WaterRenderTypes.glow());var gpose=stack.last().pose();
            float pulse=(float)(.74+.26*Math.sin(age*.45));
            if(e.tier()==1) {
                WaterWaveVisuals.rippleGlow(glow,gpose,Math.min(radius,age*.2+.6),age,fade*pulse);
            } else if(e.tier()==5) {
                for(int i=0;i<OceanWaveRules.MAX_FRONTS;i++) {
                    double r=OceanWaveRules.frontRadius(age,i);if(r<0)continue;
                    float edge=(float)Math.min(1,Math.max(0,(radius-r)/8));
                    WaterWaveVisuals.breakingGlow(glow,gpose,r,OceanWaveRules.frontHeight(r),radius,age+r*.1,fade*edge*pulse,WaterWaveVisuals.frontTint(i));
                }
                WaterGeometry.ringColor(glow,gpose,new Vec3(0,.14,0),up,(age*.9)%radius,.30,.45F,.90F,1,fade*.35F*pulse);
                WaterGeometry.disk(glow,gpose,new Vec3(0,.10,0),up,radius*.5,.02F,.35F,.85F,fade*.10F*pulse);
            } else if(e.tier()==3) {
                WaterWaveVisuals.crescentGlow(glow,gpose,center,e.direction(),radius,age,fade*pulse,WaterSpellRules.slashRollRadians(e.roll(),age));
            } else {
                WaterWaveVisuals.rollingGlow(glow,gpose,center,e.direction(),radius,e.tier()==4?8:2,age,fade*pulse,e.tier()==4?2.0:1.0);
            }
        } else if(e.kind()==2) {
            /* Open cage: no continuous opaque wall, even at the highest tier.
               t1–t4 改成**水板圆筒** ✓（作者 2026-10-10："水牢…把他改成一个个竖起来的水板
               连在一起变成一个圆困住敌人"✗）；t5 冥穴保留线条笼 ✓（作者选择 ✗ ——
               它已有空中大阵＋落剑，再加一圈半径 26 的水板墙会糊掉 ✓）。 */
            double h=e.height();
            // ★ 2026-11-10 作者："水牢的隔板再改改吧，我想做成一个个罗马圣柱那样" ✗
            //   ⇒ 原来是**一片片隔板**（谁都不认识的一大片）✗，现在是一圈**独立的罗马柱** ✓：
            //   柱距按 3.4 格给（不是隔板的 1.5 格 ✗ —— 挤那么近就又是一堵墙 ✗），柱子本体按柱距收一点 ✓。
            int columns=WaterGeometry.columnCount(radius);
            double span=Math.PI*2/columns;
            // 单根多粗：柱距的六成、且别超过 height/6.5（见 WaterGeometry#columnDiameter ✓）
            double diameter=WaterGeometry.columnDiameter(radius,h,columns);
            boolean solid=true;
            // 柱子的中心落在 radius 上 ⇒ 环要贴着柱子走，不然会切进柱身 ✗
            double ringR=solid?radius:radius*1.018;
            for(int i=0;i<3;i++)WaterGeometry.ringColor(out,pose,new Vec3(0,.12+h*i/2,0),up,ringR,.12,.01F,.28F,1,fade*.85F);
            if(solid) {
                // Textured panels are submitted after all colored geometry below.
            } else {
                for(int i=0;i<8+e.tier()*2;i++) {
                    double a=i*Math.PI*2/(8+e.tier()*2)+age*.003;
                    Vec3 p=new Vec3(Math.cos(a)*radius,.12,Math.sin(a)*radius);
                    WaterGeometry.tube(out,pose,p,p.add(0,h,0),.055,.025F,.42F,1,fade*.62F);
                }
                for(int i=0;i<2;i++)WaterGeometry.spiral(out,pose,Vec3.ZERO,up,radius*.97,h,-age*.022+i*Math.PI,.01F,.2F,.82F,fade*.65F);
            }
            WaterGeometry.ringColor(out,pose,new Vec3(0,.08,0),up,radius,.22,.42F,.12F,.95F,fade*.9F);
            if(e.tier()==5) {
                // Sea God's Crypt: restrained gold inlays around the dim panel cage.
                for(int stripe=0;stripe<3;stripe++)
                    WaterGeometry.ringColor(out,pose,new Vec3(0,h*(.24+stripe*.27),0),up,ringR,.11,
                            .74F,.48F,.08F,fade*.58F);
            }
            /* ---- 加色发光层（2026-10-10 第五批次 · 水缚链）----
               本批之前 kind==2 一条加色线都没有 ✗ ⇒ 水牢在夜里是"黑笼子"✗。
               只推亮三处标志物 ✓：三环的环边、立柱顶端（水板档改成板缝＋板顶 ✓）、底部主环 ✓；
               柱身不加 ✗（几十根柱身全加会糊成一片光墙 ✗，而且它本来就多 ✗）。 */
            var glow=buffers.getBuffer(WaterRenderTypes.glow());var gpose=stack.last().pose();
            float pulse=(float)(.76+.24*Math.sin(age*.42));
            for(int i=0;i<3;i++)
                WaterGeometry.ringColor(glow,gpose,new Vec3(0,.12+h*i/2,0),up,radius,.20,.28F,.66F,1,fade*.55F*pulse);
            if(solid) {
                // 柱廊档：柱缝之间的亮竖线 ＋ 柱顶亮环 ✓（照抄同一个 columnCount ✗，否则会和柱子错开 ✗）
                double seamR=radius;
                for(int p=0;p<columns;p++) {
                    // 亮线落在**两根柱子的正中间** ✓（柱子本身有自发光，中间再补一条才像"廊"✓）
                    double a=(p+.5)*span+age*.003;
                    Vec3 seam=new Vec3(Math.cos(a)*seamR,0,Math.sin(a)*seamR);
                    WaterGeometry.tube(glow,gpose,seam.add(0,.12,0),seam.add(0,h,0),.04,.18F,.45F,.62F,fade*.10F*pulse);
                }
                WaterGeometry.ringColor(glow,gpose,new Vec3(0,h,0),up,radius,.18,.22F,.48F,.68F,fade*.16F*pulse);
            } else {
                for(int i=0;i<8+e.tier()*2;i+=2) {
                    double a=i*Math.PI*2/(8+e.tier()*2)+age*.003;
                    WaterGeometry.ringColor(glow,gpose,new Vec3(Math.cos(a)*radius,.12+h,Math.sin(a)*radius),up,.26,.06F,.45F,.85F,1,fade*.60F*pulse);
                }
            }
            WaterGeometry.ringColor(glow,gpose,new Vec3(0,.08,0),up,radius,.30,.50F,.85F,1,fade*.60F*pulse);
            if(e.tier()>=4)WaterGeometry.disk(glow,gpose,new Vec3(0,.10,0),up,radius*.6,.02F,.20F,.60F,fade*.12F*pulse);
        }
        // 第十批：显式 3 个子阵 ✗ —— 半径 ≥6 的默认是 6 个 ✗，而水缚链 t5 冥穴半径 26 ✗
        //（每个子阵原来是一座完整 sigil ✗ ⇒ 光这一句就上万顶点 ✗）
        if(e.kind()==2)WaterGeometry.richCircle(buffers,stack,new Vec3(0,.06,0),up,radius,animation,fade*.65F,radius>20?2:3,e.tier());
        // ★ 罗马圣柱柱廊（作者 2026-11-10 ✗）：柱子是半透明的（虚化 ✓）＋ 一层淡自发光 ✓
        //   柱头浮雕（茛苕叶 / 涡卷 / 玫瑰花）只给**离镜头 18 格内**的那几根画 ✓ ——
        //   它是整个柱廊最贵的部分 ✗（水牢半径最大 26 格，站在里面时只有身边三四根需要细节 ✓）
        if(e.kind()==2) {
            int columns=WaterGeometry.columnCount(radius);
            double diameter=WaterGeometry.columnDiameter(radius,e.height(),columns);
            Vec3 camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera()
                    .getPosition().subtract(e.position());
            WaterSolidModels.romanColumns(stack,buffers,radius,e.height(),columns,diameter,
                    age,fade*.62F,.85F,LightTexture.FULL_BRIGHT,camera);
        }
        if(e.kind()==2 && e.tier()==5)SeaGodSwordVisuals.render(age,animation,radius,fade,stack,buffers,e.swordCount(),e.skySealHeight(),e.bossCrypt());
    }
}
