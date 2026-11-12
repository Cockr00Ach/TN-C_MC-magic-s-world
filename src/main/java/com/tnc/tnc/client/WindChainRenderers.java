package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.wind.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class WindChainRenderers {
    /**
     * ★ <b>风球</b>（2026-11-10 作者：「风球也可以优化一下」→ 之后纠正：
     * 「你应该是在原有的特效上加上这个线条去优化」）✓
     *
     * <h2>正确的做法：叠加，不是替换</h2>
     * 原有的 {@code WindConstructModels.orb(...)}（核心/笼子色带 + 内收环 + 风刃）
     * 和炸开时翻倒的 {@code cage} 是<b>作者已经认可的画面</b>，不该被删掉 ✗。
     * 现在两样都在，<b>线条只当"外面那层风"</b>：
     * <ul>
     *   <li>飞行 / 吸住：原有 {@code orb(...)} ＋ {@link WindBallLines#swirl} 的螺旋风线
     *       （半径略大于模型 ⇒ 像风裹在球外面转）✓</li>
     *   <li>压缩：球越缩越小，风线跟着挤 ⇒ 越吸越紧；压缩 >55% 补一下亮芯 ✓</li>
     *   <li>炸开：原有翻倒的 {@code cage} ＋ 新增的<b>冲击波环 + 径向风痕</b> ✓</li>
     * </ul>
     *
     * <p>⚠️ 线条画在<b>世界尺度</b>（半径 = {@code WindBallEntity.radius()}），
     * 不走原模型的 {@code poseStack.scale(r,r,r)} —— 否则会被缩放两次 ✗。
     */
    public static final class Ball extends EntityRenderer<WindBallEntity>{
        public Ball(EntityRendererProvider.Context c){super(c);}
        @Override public ResourceLocation getTextureLocation(WindBallEntity e){return LightningRenderTypes.WHITE_TEXTURE;}
        @Override public void render(WindBallEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
            double age=e.level().getGameTime()+partial;
            float radius=(float)e.radius(partial);
            if(radius<.02F)return;
            int tier=WindChainRules.tier(e.tier());
            int phase=e.phase();
            // 压缩阶段转得更快（"越吸越紧"），炸开那一瞬几乎不转（冲击波才是主体）✓
            double spin=Math.toRadians(age*(phase==1?34.0D:phase==2?8.0D:22.0D));
            double compression=0;
            if(phase==1){
                double total=Math.max(1,WindChainRules.compressionTicks(tier));
                compression=Math.min(1,e.phaseAge(partial)/total);
            }
            double blast=phase==2?Math.min(1,e.phaseAge(partial)/12.0D):0;
            float alpha=phase==2?(float)Math.max(0,1-blast):(float)(.78+.22*compression);

            var cam=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            var at=e.getPosition(partial);
            double camX=cam.x-at.x,camY=cam.y-at.y,camZ=cam.z-at.z;
            VertexConsumer vc=b.getBuffer(LightningRenderTypes.BOLT);
            Matrix4f m=p.last().pose();
            float width=(float)WindBallLines.width(radius);

            // ★ 先画**原有**特效（作者认可的画面，不许删 ✗）
            p.pushPose();
            p.scale(radius,radius,radius);
            if(phase==2){
                p.pushPose();
                p.mulPose(Axis.XP.rotationDegrees(80));
                p.mulPose(Axis.ZP.rotationDegrees((float)(age*8%360)));
                WindConstructModels.draw("cage",p,b,(float)Math.max(0,1-e.phaseAge(partial)/12));
                p.popPose();
            } else {
                p.translate(0,-.2,0);
                WindConstructModels.orb(2,age,p,b);
            }
            p.popPose();

            // ★ 再把**线条**叠上去（新增那层"裹在外面的风"）✓
            m=p.last().pose();
            if(phase!=2){
                // ① 漩涡风线（作者 2026-11-10：不要组成球，要"漩涡感"；数量三倍、且不是一样的线）✓
                //    每条线自带宽度／亮度／配色三档渐变 ⇒ 亮白细丝 + 青绿中层 + 深青宽扫叠在一起 ✓
                for(WindBallLines.Line line:WindBallLines.vortex(radius,tier,spin,e.getId())){
                    float[] core=switch(line.palette()){
                        case WindBallLines.PALETTE_CORE -> WindBladeQi.CORE;
                        case WindBallLines.PALETTE_MID -> WindBladeQi.MID;
                        default -> WindBladeQi.GLOW;
                    };
                    float[] mid=switch(line.palette()){
                        case WindBallLines.PALETTE_CORE -> WindBladeQi.MID;
                        default -> WindBladeQi.GLOW;
                    };
                    LightningBoltGeometry.polyline(vc,m,line.points(),
                            (float)(width*line.widthScale()),alpha*line.alphaScale(),
                            core,mid,WindBladeQi.GLOW,camX,camY,camZ);
                }
                // ② 压缩到最紧的那一瞬给一下"亮芯"✓
                if(compression>.55D){
                    LightningBoltGeometry.billboardFlash(vc,m,0,0,0,
                            (float)Math.max(.35D,radius*2.4D),LightningTheme.BLUE,
                            (float)((compression-.55D)/.45D*.45D),camX,camY,camZ);
                }
            } else {
                // 炸开：在原有 cage 外面再加冲击波环 + 径向风痕 ✓
                float fade=(float)Math.max(0,1-blast);
                for(double[][] ring:WindBallLines.shockRings(radius,blast)){
                    LightningBoltGeometry.polyline(vc,m,ring,(float)(width*1.6D),fade*.75F,
                            WindBladeQi.CORE,WindBladeQi.MID,WindBladeQi.GLOW,camX,camY,camZ);
                }
                for(double[][] streak:WindBallLines.streaks(radius,blast,e.getId())){
                    LightningBoltGeometry.polyline(vc,m,streak,(float)(width*1.1D),fade*.55F,
                            WindBladeQi.CORE,WindBladeQi.MID,WindBladeQi.GLOW,camX,camY,camZ);
                }
            }
        }
    }
    public static final class Volley extends EntityRenderer<WindVolleyEntity>{
        public Volley(EntityRendererProvider.Context c){super(c);}
        @Override public ResourceLocation getTextureLocation(WindVolleyEntity e){return TextureAtlas.LOCATION_BLOCKS;}
        @Override public void render(WindVolleyEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
            double age=e.visualAge(partial);float alpha=(float)Math.min(1,age/8);
            if(e.tier()>=1){p.pushPose();var marker=e.aimPoint().subtract(e.position());p.translate(marker.x,marker.y+.05,marker.z);WindSolidModels.seal(e.tier()==5?34:5+e.tier(),age,alpha,p,b);p.popPose();}
            for(int slot=0;slot<e.count();slot++){
                double end=e.endTime(slot);
                if(!e.visible(slot,age)||e.done(slot)&&age<end)continue;
                WindWeaponTrails.volley(e,slot,age,p,b,alpha);
                var at=e.weaponAt(slot,age).subtract(e.position());
                var direction=e.direction(slot,age);
                if(direction.lengthSqr()<.001)direction=new net.minecraft.world.phys.Vec3(0,-1,0);
                p.pushPose();p.translate(at.x,at.y,at.z);
                p.mulPose(new Quaternionf().rotationTo(0,0,1,(float)direction.x,(float)direction.y,(float)direction.z));
                float scale=WindChainRules.modelScale(e.tier())*e.bossScale();p.scale(scale,scale,scale);
                float fade=(float)Math.max(0,Math.min(1,(end+8-age)/8));
                WindConstructModels.phantomWeapon(WindChainRules.form(e.tier(),slot),age,p,b,alpha*fade,true);p.popPose();
            }
            // A bounded set of inward cutting currents, rather than making the weapons lunge.
            if(e.tier()<5&&age>=38&&age<100){
                int cuts=Math.min(6,e.count());
                for(int i=0;i<cuts;i++){
                    int slot=i*e.count()/cuts;double t=((age-38+i*2)%12)/12;
                    var start=e.orbitPoint(slot,age);var end=e.aimPoint().add(0,1.3,0);
                    var at=start.lerp(end,t).subtract(e.position());var direction=end.subtract(start).normalize();
                    p.pushPose();p.translate(at.x,at.y,at.z);
                    p.mulPose(new Quaternionf().rotationTo(0,0,1,(float)direction.x,(float)direction.y,(float)direction.z));
                    float s=.65F+e.tier()*.16F;p.scale(s,s,s);
                    WindConstructModels.draw("crescent",p,b,(float)(Math.sin(t*Math.PI)*.55));p.popPose();
                }
            }
        }
    }
}
