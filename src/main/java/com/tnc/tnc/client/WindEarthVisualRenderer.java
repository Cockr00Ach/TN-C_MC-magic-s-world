package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.WindEarthVisualEntity;
import com.tnc.tnc.magic.WindEarthVisualRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.WeakHashMap;

public final class WindEarthVisualRenderer extends EntityRenderer<WindEarthVisualEntity> {
    private final EarthSpellModelRenderer earthModels=new EarthSpellModelRenderer();
    private final WeakHashMap<WindEarthVisualEntity,WindEarthGroundSurface> surfaces=new WeakHashMap<>();
    public WindEarthVisualRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    public ResourceLocation getTextureLocation(WindEarthVisualEntity entity){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/stone.png");}
    /**
     * ★ 风系自身的"漩涡线"光环（2026-11-15 作者「改啊」）✓
     *
     * <p>风场/风神降临这些<b>自己身上的</b>风系法术，视觉还停在旧的"模型风翼 + 贴图披风"✗。
     * 这里**不替换**旧视觉（作者 11-10 的规矩），只在外面加一层和风球/龙卷风同源的螺旋线 ✓：
     * 档越高线越多、转得越快，而且和 {@code WindBallLines} 完全共用几何 ⇒ 风系内部一致 ✓。
     */
    private static void windAuraLines(WindEarthVisualEntity entity,Vec3 centre,double age,float envelope,
                                      PoseStack poses,MultiBufferSource buffers){
        int tier=Math.max(1,Math.min(5,entity.tier()));
        double radius=1.15D+0.16D*tier;
        var cam=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double camX=cam.x-centre.x,camY=cam.y-centre.y,camZ=cam.z-centre.z;
        var vc=buffers.getBuffer(com.tnc.tnc.client.lightning.LightningRenderTypes.BOLT);
        var m=poses.last().pose();
        double spin=Math.toRadians(age*22.0D+tier*17.0D);
        for(var line:WindBallLines.vortex(radius,tier,spin,entity.getId())){
            float[] core=switch(line.palette()){
                case WindBallLines.PALETTE_CORE -> WindBladeQi.CORE;
                case WindBallLines.PALETTE_MID -> WindBladeQi.MID;
                default -> WindBladeQi.GLOW;
            };
            float[] mid=line.palette()==WindBallLines.PALETTE_CORE?WindBladeQi.MID:WindBladeQi.GLOW;
            com.tnc.tnc.client.lightning.LightningBoltGeometry.polyline(vc,m,line.points(),
                    (float)(WindBallLines.width(radius)*line.widthScale()),
                    envelope*0.35F*line.alphaScale(),core,mid,WindBladeQi.GLOW,camX,camY,camZ);
        }
    }
    @Override public void render(WindEarthVisualEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light) {
        double age=entity.visualAge(partial);
        float envelope=WindEarthVisualRules.envelope(age,entity.life());
        if(envelope<=.005)return;
        Entity owner=entity.level().getEntity(entity.ownerId());
        if(entity.followsOwner()&&entity.kind()==0&&FirstPersonSpellView.own(owner))return;
        Vec3 centre=entity.followsOwner()&&owner!=null?owner.getPosition(partial):entity.position();
        poses.pushPose();
        Vec3 offset=centre.subtract(entity.getPosition(partial));poses.translate(offset.x,offset.y,offset.z);
        WindEarthGeometry.Ground ground=WindEarthGeometry.FLAT;
        if(entity.kind()==4||entity.kind()==5) {
            WindEarthGroundSurface surface=surfaces.get(entity);
            if(surface==null||!surface.origin.equals(BlockPos.containing(centre))) {
                surface=new WindEarthGroundSurface(entity.level(),centre,entity.radius());surfaces.put(entity,surface);
            }
            ground=surface;
        }
        if(entity.kind()==0) {
            poses.mulPose(Axis.YP.rotationDegrees(-(owner==null?entity.getYRot():owner.getViewYRot(partial))));
            if(owner==Minecraft.getInstance().player&&Minecraft.getInstance().options.getCameraType().isFirstPerson())envelope*=.55F;
        }
        if(entity.kind()==3) {
            poses.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            poses.translate(0,.35,.7);
        }
        if(entity.kind()>=3) {
            earthModels.render(entity,age,envelope,poses,buffers,light,ground);
            poses.popPose();return;
        }
        if(entity.kind()==0){
            // 原有的风之翼/风场**保留**（作者 11-10 的规矩：线条是叠加上去的，不是替换 ✓），
            // 外面再加一层 2026-11 的"漩涡线"（和风球/龙卷风同一套 WindBallLines）✓
            WindSolidModels.flight(entity.tier(),age,envelope,poses,buffers);
            windAuraLines(entity,centre,age,envelope,poses,buffers);
            poses.popPose();
            return;
        }
        var matrix=poses.last().pose();var terrain=ground;float alpha=envelope;
        WindEarthVisualBuffers.draw(buffers,(body,glow)->WindEarthGeometry.spell(body,glow,matrix,
                entity.kind(),entity.tier(),entity.radius(),age,alpha,terrain));
        // ★ 2026-11-15「全部改完」：风系剩下的两种（1＝召唤风灵的小阵、2＝狂风波）
        //   也在外面叠一层新线条语言（旧的 seal 保留，不替换）✓
        if(entity.kind()==1||entity.kind()==2){
            windKindLines(entity,centre,age,envelope,poses,buffers);
        }
        poses.popPose();
    }

    /**
     * ★ 风系剩下两种的线条叠加（2026-11-15「全部改完」）✓
     *
     * <ul>
     *   <li><b>kind 1（召唤风灵的小阵）</b>：一圈小漩涡线（{@code WindBallLines.vortex}），像"风在阵里转"✓</li>
     *   <li><b>kind 2（狂风波）</b>：一道<b>弯月剑气</b>（{@code WindBladeQi}）立在波前 ＋ 3 根螺旋风线
     *       ⇒ 和玩家风刃／风刃波是同一套语言 ✓</li>
     * </ul>
     *
     * <p>优化：48 格外直接不画（远处看不出是线）；alpha 随距离线性衰减；旧的 seal／helix 都还在 ✓
     */
    private static void windKindLines(WindEarthVisualEntity entity,Vec3 centre,double age,float envelope,
                                      PoseStack poses,MultiBufferSource buffers){
        var cam=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distSqr=cam.distanceToSqr(centre);
        if(distSqr>48.0D*48.0D)return;
        float distFade=(float)Math.max(0.0D,1.0D-Math.sqrt(distSqr)/48.0D);
        int tier=Math.max(1,Math.min(5,entity.tier()));
        double radius=Math.max(.6D,entity.radius());
        double camX=cam.x-centre.x,camY=cam.y-centre.y,camZ=cam.z-centre.z;
        var m=poses.last().pose();
        var vc=buffers.getBuffer(com.tnc.tnc.client.lightning.LightningRenderTypes.BOLT);
        if(entity.kind()==1){
            double spin=Math.toRadians(age*26.0D);
            double ring=radius*.62D;
            for(var line:WindBallLines.vortex(ring,tier,spin,entity.getId()+31L)){
                float[] core=line.palette()==WindBallLines.PALETTE_CORE?WindBladeQi.CORE:WindBladeQi.MID;
                float[] mid=line.palette()==WindBallLines.PALETTE_CORE?WindBladeQi.MID:WindBladeQi.GLOW;
                com.tnc.tnc.client.lightning.LightningBoltGeometry.polyline(vc,m,line.points(),
                        (float)(WindBallLines.width(ring)*line.widthScale()),
                        envelope*0.45F*distFade*line.alphaScale(),core,mid,WindBladeQi.GLOW,camX,camY,camZ);
            }
            return;
        }
        // kind 2：波前的弯月剑气（竖着立在传播方向上）✓
        float waveAlpha=envelope*0.75F*distFade;
        if(waveAlpha>0.01F){
            poses.pushPose();
            poses.translate(0.0D,radius*.55D,0.0D);
            poses.mulPose(Axis.XP.rotationDegrees(-90.0F));      // 弯月立起来，像一片竖着的刀光 ✓
            WindBladeQi.draw(vc,poses.last().pose(),(float)(radius*.85D),waveAlpha);
            poses.popPose();
        }
        // 3 根螺旋风线（旧的 arc 之外再加一层，读作"整团风在旋转前进"）✓
        double spin=Math.toRadians(age*40.0D);
        for(int i=0;i<3;i++){
            double phase=spin+i*(Math.PI*2.0D/3.0D);
            for(var strand:WindBallLines.vortex(radius*.9D,2+i,phase+i,entity.getId()*7L+i)){
                com.tnc.tnc.client.lightning.LightningBoltGeometry.polyline(vc,m,strand.points(),
                        (float)(WindBallLines.width(radius*.9D)*.9D*strand.widthScale()),
                        envelope*0.30F*distFade*strand.alphaScale(),WindBladeQi.MID,WindBladeQi.GLOW,WindBladeQi.GLOW,
                        camX,camY,camZ);
            }
        }
    }
}
