package com.tnc.tnc.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.magic.TNMagicCircleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
/** Distinct, bounded wind compass and white-gold radiant sigils. */
final class ElementSealRenderer {
    static void render(TNMagicCircleEntity e,float partial,PoseStack p,MultiBufferSource b){
        boolean wind=e.style()==TNMagicCircleEntity.STYLE_WIND;
        double age=e.tickCount+partial,r=e.radius(),spin=age*(wind?.022:.008);
        float alpha=(float)Math.max(0,Math.min(1,(e.life()-age)/18));
        var out=b.getBuffer(WaterRenderTypes.geometry());var m=p.last().pose();
        Vec3 c=new Vec3(0,.08,0),up=new Vec3(0,1,0);
        float red=wind?.30F:1,green=wind?.90F:.78F,blue=wind?.86F:.32F;
        WaterGeometry.ringColor(out,m,c,up,r,r*.018,red,green,blue,alpha,64);
        WaterGeometry.ringColor(out,m,c,up,r*.92,r*.008,red,green,blue,alpha*.7F,64);
        WaterGeometry.ringColor(out,m,c,up,r*.56,r*.012,wind?.85F:1,1,wind?1:.80F,alpha,48);
        int count=wind?12:24;
        for(int i=0;i<count;i++) {
            double a=spin+i*Math.PI*2/count;
            Vec3 v=new Vec3(Math.cos(a),0,Math.sin(a)),t=new Vec3(-v.z,0,v.x),at=c.add(v.scale(r*.78));
            double length=r*(wind?.12:.045),width=r*.025;
            WaterGeometry.quad(out,m,at.subtract(v.scale(length)),at.add(t.scale(width)),at.add(v.scale(length)),at.subtract(t.scale(width)),red,green,blue,alpha);
        }
        if(wind)for(int arm=0;arm<3;arm++)for(int i=0;i<12;i++) {
            double a=spin+arm*Math.PI*2/3+i*.15,rad=r*(.12+i*.035);
            Vec3 from=c.add(Math.cos(a)*rad,0,Math.sin(a)*rad),to=c.add(Math.cos(a+.15)*(rad+r*.035),0,Math.sin(a+.15)*(rad+r*.035));
            WaterGeometry.stroke(out,m,up,from,to,r*.014,.72F,1,.95F,alpha);
        }
        if(!wind)for(int i=0;i<6;i++) {
            double a=spin+i*Math.PI/3;
            Vec3 from=c.add(Math.cos(a)*r*.46,0,Math.sin(a)*r*.46),to=c.add(Math.cos(a+Math.PI*2/3)*r*.46,0,Math.sin(a+Math.PI*2/3)*r*.46);
            WaterGeometry.stroke(out,m,up,from,to,r*.009,1,.93F,.65F,alpha);
        }
    }
}
