package com.tnc.tnc.arcana.client;

import com.tnc.tnc.arcana.*;
import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.mana.sight.client.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

public final class ArcanaDeviceVisual {
    private static final net.minecraft.client.model.geom.ModelPart SPIRIT=com.tnc.tnc.arcana.client.ArcanaEntityModel.layer(true).bakeRoot();
    private static final java.util.Map<RouteNodeEntity,Long> LAST=new java.util.WeakHashMap<>();
    public static void render(RouteNodeEntity n,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(n.getLevel()==null)return;var mc=Minecraft.getInstance();var renderer=mc.getItemRenderer();
        if(n.kind()==RouteKind.MEAL_PLATE)for(int i=0;i<9;i++){
            var stack=n.inventory.getItem(i);if(stack.isEmpty())continue;pose.pushPose();pose.translate(.25+(i%3)*.25,.14,.25+(i/3)*.25);pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(.23f,.23f,.23f);renderer.renderStatic(stack,ItemDisplayContext.FIXED,light,overlay,pose,buffers,n.getLevel(),i);pose.popPose();
        }
        if(n.kind()==RouteKind.NURTURE&&!n.inventory.getItem(0).isEmpty()){
            pose.pushPose();pose.translate(.5,1,.5);pose.mulPose(Axis.YP.rotationDegrees((n.getLevel().getGameTime()+partial)*1.5f));pose.scale(.6f,.6f,.6f);renderer.renderStatic(n.inventory.getItem(0),ItemDisplayContext.FIXED,0xf000f0,overlay,pose,buffers,n.getLevel(),0);pose.popPose();
        }
        if(n.kind()==RouteKind.NURTURE&&ManaSightClient.enabled()&&!n.inventory.getItem(0).isEmpty()){
            String element=n.inventory.getItem(0).getItem() instanceof com.tnc.tnc.adventure.ElementWands.Wand w?w.design.element().id():"water";
            int color=switch(element){case "fire"->0xff954f;case "water"->0x69dfff;case "lightning"->0xe1d483;case "earth"->0xcbaa64;case "dark"->0xa98aff;default->0x7aefc8;};
            double age=n.getLevel().getGameTime()+partial;SPIRIT.getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);for(int i=0;i<3;i++)SPIRIT.getChild("ring"+i).yRot+=(float)age*.025f*(i%2==0?1:-1);
            pose.pushPose();pose.translate(.5,1.85+Math.sin(age*.04)*.05,.5);pose.scale(.22f,-.22f,.22f);SPIRIT.render(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(net.minecraft.resources.ResourceLocation.parse("tnc:textures/block/alchemy_glow.png"))),0xf000f0,overlay,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);pose.popPose();
        }
        long now=n.getLevel().getGameTime();if(LAST.getOrDefault(n,-1L)==now)return;LAST.put(n,now);
        boolean sight=ManaSightClient.enabled();var center=Vec3.atCenterOf(n.getBlockPos());
        if(n.kind()==RouteKind.MEAL_PLATE)for(int i=0;i<9;i++){var stack=n.inventory.getItem(i);if(!ArcanaMeals.allowed(stack))continue;int color=ArcanaMeals.color(ArcanaMeals.taste(stack));if(!sight&&now%3!=i%3)continue;for(int k=0;k<(sight?3:1);k++){double a=now*.045+k*Math.PI*2/3;var at=Vec3.atLowerCornerOf(n.getBlockPos()).add(.25+(i%3)*.25+Math.cos(a)*.065,.18,.25+(i/3)*.25+Math.sin(a)*.065);ManaSightParticles.effect(at,at.add(0,.25,0),color,k==0?1:0,3,sight?.035f:.022f,25);}}
        if(n.kind()==RouteKind.MEAL_PLATE&&sight){boolean good=ArcanaMeals.active(n),bad=ArcanaMeals.conflict(n);for(int i=0;i<(good?12:bad?4:0);i++){double angle=now*.03+i*Math.PI/6;var at=center.add(Math.cos(angle)*.42,-.32,Math.sin(angle)*.42);ManaSightParticles.effect(at,at.add(0,.18,0),good?0xffdc79:0xf05e53,1,0,.035f,22);}}
        if(n.kind()==RouteKind.MEAL_PLATE&&sight){for(int i=0;i<9;i++)for(int j:new int[]{i%3<2?i+1:-1,i<6?i+3:-1})if(j>=0){var a=ArcanaMeals.taste(n.inventory.getItem(i));var b=ArcanaMeals.taste(n.inventory.getItem(j));boolean bad=ArcanaMeals.clash(a,b);if(!bad&&ArcanaMeals.generation(a,b)<0)continue;var start=Vec3.atLowerCornerOf(n.getBlockPos()).add(.25+(i%3)*.25,.19,.25+(i/3)*.25);var end=Vec3.atLowerCornerOf(n.getBlockPos()).add(.25+(j%3)*.25,.19,.25+(j/3)*.25);for(int k=0;k<3;k++){var at=start.lerp(end,(now*.04+k/3.)%1);ManaSightParticles.effect(at,at.add(0,bad?.12:.04,0),bad?0xe65bcd:k%2==0?ArcanaMeals.color(a):ArcanaMeals.color(b),bad?2:0,0,bad?.025f:.018f,14);}}
            if(ArcanaMeals.active(n))ManaSightParticles.effect(center.add(0,.18,0),center.add(0,.18,0),0xffe296,1,0,.16f,16);
        }
        if(ArcanaTechnology.device(n)){int c=n.kind()==RouteKind.IGNITER?(now%40<20?0xff8452:0x65cfff):n.kind()==RouteKind.LEYLINE_COMPASS?0xffdc7c:n.kind()==RouteKind.PHASE_SHIFTER?ArcanaEnergy.color(n):n.kind()==RouteKind.RESONANCE?0xb4f0a2:n.kind()==RouteKind.FUMIGATOR?0xc2a9ed:0x84e8d3;int count=sight?6:2;
            for(int i=0;i<count;i++){double a=now*.045+i*Math.PI*2/count;double r=n.kind()==RouteKind.FUMIGATOR?.25:.18;var at=center.add(Math.cos(a)*r,.15+Math.sin(a*2)*.08,Math.sin(a)*r);var end=n.kind()==RouteKind.FUMIGATOR?at.add(Math.cos(a)*.35,1.2,Math.sin(a)*.35):center.add(0,.45,0);ManaSightParticles.effect(at,end,c,n.kind()==RouteKind.FUMIGATOR?2:i==0?1:0,3,n.kind()==RouteKind.FUMIGATOR?.12f:.035f,35);}
        }
        if(n.kind()==RouteKind.NURTURE&&sight&&n.getPersistentData().getLong("RejectedUntil")>now){for(int i=0;i<3;i++)ManaSightParticles.effect(center.add((Math.random()-.5)*.4,.5,(Math.random()-.5)*.4),center.add(0,1.2,0),0x382f46,2,3,.12f,22);}
        if(n.kind()==RouteKind.NURTURE&&sight&&!n.inventory.getItem(0).isEmpty()){
            int c=n.inventory.getItem(0).getOrCreateTag().getString("SpiritElement").equals("fire")?0xff9b59:0x8df3e0;
            for(int i=0;i<5;i++){double a=now*.08+i*Math.PI*.4;var at=center.add(Math.cos(a)*.25,1.05+Math.sin(a*2)*.12,Math.sin(a)*.25);ManaSightParticles.effect(at,center.add(0,1.45,0),c,i==0?1:0,0,.055f,24);}
        }
        if(n.kind()==RouteKind.ABYSS_TOWER&&sight){int stage=n.getPersistentData().getInt("OverloadStage"),c=stage>=2?0xff304d:stage==1?0xffad45:0x5ce4cb;
            for(int i=0;i<5+stage*7;i++){double a=i*Math.PI*.4+now*(stage>=2?.18:.03),h=(i%7)*.35;var at=center.add(Math.cos(a)*(1+stage*.25),h,Math.sin(a)*(1+stage*.25));ManaSightParticles.effect(at,at.add(-Math.sin(a)*.3,.5,Math.cos(a)*.3),c,i%6==0?1:2,3,.08f+stage*.02f,30);}
        }
        if((n.kind()==RouteKind.SOLAR||n.kind()==RouteKind.LUNAR)&&sight&&n.inRate+n.mana>0){int c=n.kind()==RouteKind.SOLAR?0xffdb83:0xc9e9ff;for(int i=0;i<6;i++){double a=now*.03+i;var at=center.add(Math.cos(a)*.4,3+i*2,Math.sin(a)*.4);ManaSightParticles.effect(at,center.add(0,.4,0),c,1,3,.08f,45);}}
        if(ArcanaHighway.ring(n)&&n.getPersistentData().getLong("SkyActiveUntil")>now&&n.getPersistentData().contains("SkyTarget")){
            var end=ArcanaHighway.center(BlockPos.of(n.getPersistentData().getLong("SkyTarget")));var start=ArcanaHighway.center(n.getBlockPos());var axis=end.subtract(start).normalize();var u=axis.cross(new Vec3(0,1,0));if(u.lengthSqr()<.01)u=new Vec3(1,0,0);u=u.normalize();var v=axis.cross(u).normalize();
            int grains=Math.min(120,(int)Math.ceil(start.distanceTo(end)*(sight?5:2)));for(int i=0;i<grains;i++){double phase=(now*.004+i/(double)grains)%1,angle=now*.018+i*2.4;var at=start.lerp(end,phase).add(u.scale(Math.cos(angle)*.7)).add(v.scale(Math.sin(angle)*.7));int color=java.awt.Color.HSBtoRGB((float)((phase*.4+now*.0003)%1),.34f,1)&0xffffff;ManaSightParticles.effect(at,at.add(axis.scale(.75)),color,i%9==0?1:0,3,sight?.065f:.035f,48);}
            for(var item:n.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(start,end).inflate(1.2)))if(item.getPersistentData().hasUUID("SkyOwner")){for(int i=0;i<3;i++){double a=now*.2+i*2.09;var at=item.position().add(Math.cos(a)*.25,.2,Math.sin(a)*.25);ManaSightParticles.effect(at,at.add(0,.08,0),0xa4efff,1,3,.035f,16);}}
        }
    }
    private ArcanaDeviceVisual(){}
}
