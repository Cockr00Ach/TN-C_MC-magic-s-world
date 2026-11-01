package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.world.ink.InkCraneAttackRules;
import com.tnc.tnc.world.ink.InkCranePhantomEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Bounded meshes, not particle clouds: long ink ribbons, rotating seal, growing sphere. */
final class InkCraneRitualVisuals {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/ink_crane_primitive.png");
    private static final ResourceLocation BRUSH = ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/ink_brush_strip.png");
    private InkCraneRitualVisuals() {}

    static void trail(InkCranePhantomEntity entity, PoseStack pose, MultiBufferSource buffers) {
        if (entity.trail().size() < 2) return;
        brushTrail(entity.trail(), entity.position(), entity.age(),
                entity.mode() == InkCranePhantomEntity.DRAKE_CHARGE ? 5.4 : 1.6, pose, buffers);
    }

    static void brushTrail(Iterable<Vec3> points, Vec3 origin, double age, double width,
                           PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(BRUSH));
        List<Vec3> path = new ArrayList<>(); points.forEach(path::add);
        for(int layer=0;layer<4;layer++) {
            List<Vec3> curve=new ArrayList<>();int index=0;
            for(Vec3 p:path) {
                double shift=layer<2?0:Math.sin(index*.3+layer*2.4-age*.12)*width*.42;
                curve.add(p.subtract(origin).add(shift,layer*width*.035,-shift*.4));index++;
            }
            ribbon(out,pose.last(),curve,width*(layer==0?1.8:layer==1?1:.12),
                    layer==0?.1F:layer==3?.65F:.005F,layer==0?.15F:layer==1?.95F:.55F,true);
        }
        Vec3 previous = null; int i = 0;
        for (Vec3 point : path) {
            Vec3 local = point.subtract(origin);
            if (previous != null && local.distanceToSqr(previous) > 0.001) {
                float fade = 1 - i / (path.size() + 1.0F);
                if (i % 3 == 0) {
                    Vec3 drop = local.add(Math.sin(i * 2.3) * width, Math.cos(i * 1.8) * width, 0);
                    InkWashVisuals.fleck(pose,buffers,drop,.14*fade,i,age,i%6==0?.8F:.01F,fade*.75F);
                }
            }
            previous = local; i++;
        }
    }

    static void ritual(InkCranePhantomEntity entity, float partial, PoseStack pose, MultiBufferSource buffers) {
        double chargeAge=entity.chargeAge()<0?-1:entity.chargeAge()+partial;
        if(chargeAge>=InkCraneAttackRules.BALL_EXPLODE+18) {
            InkPaintingRenderer.aftermath(chargeAge,pose,buffers);return;
        }
        if(chargeAge>=InkCraneAttackRules.BALL_EXPLODE) InkPaintingRenderer.aftermath(chargeAge,pose,buffers);
        pose.pushPose();
        try {
        pose.scale(InkCraneAttackRules.BALL_SCALE, InkCraneAttackRules.BALL_SCALE, InkCraneAttackRules.BALL_SCALE);
        double age = entity.chargeAge() < 0 ? -1 : entity.chargeAge() + partial;
        double turn = (entity.age() + partial) * .027;
        float fade = (float) Math.min(1, (118 - age) / 12.0);
        if (age >= 0 && age < InkCraneAttackRules.BALL_EXPLODE) {
            double core=InkCraneAttackRules.ballRadius(age)/InkCraneAttackRules.BALL_SCALE;
            InkWashVisuals.gather(pose,buffers,age,new Vec3(0,age<32?.35+age/32:core+.35,0),core,14);
        }
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        for (int layer = 0; layer < 3; layer++) {
            double radius = 5.8 - layer * 1.35;
            ring(out, pose.last(), radius, .12 - layer * .025, .055 + layer * .006, turn * (layer == 1 ? -1.6 : 1), .018F, .9F * fade, true);
            ring(out, pose.last(), radius + .16, .025, .06 + layer * .006, turn, .9F, .65F * fade, false);
        }
        // Detached, rotating brush marks; no text or full characters on the seal.
        for (int i = 0; i < 16; i++) {
            double a = turn + i * Math.PI / 8;
            stroke(out, pose.last(), point(a, 4.5, .085), point(a + .09, 5.2, .085), .1, .02F, fade);
            stroke(out, pose.last(), point(a + .09, 5.2, .085), point(a + .17, 5.0, .085), .06, .8F, fade * .65F);
        }
        if (age >= InkCraneAttackRules.DIVE_TICKS && age < InkCraneAttackRules.BALL_EXPLODE) {
            double r = InkCraneAttackRules.ballRadius(age) / InkCraneAttackRules.BALL_SCALE;
            sphere(out, pose.last(), r, r + .35, age, fade);
        }
        if (age >= InkCraneAttackRules.BALL_EXPLODE) {
            pose.scale((float)InkCraneAttackRules.EXPLOSION_VISUAL_SCALE,
                    (float)InkCraneAttackRules.EXPLOSION_VISUAL_SCALE,(float)InkCraneAttackRules.EXPLOSION_VISUAL_SCALE);
            double t = (age - InkCraneAttackRules.BALL_EXPLODE) / 18;
            ring(out, pose.last(), 1 + t * 13, .8 * (1 - t) + .05, .2, 0, .01F, (float) (1 - t), false);
            ring(out, pose.last(), 1.3 + t * 13, .1, .22, 0, .94F, (float) ((1 - t) * .8), false);
            InkWashVisuals.burst(pose,buffers,t*36,10);
        }
        } finally { pose.popPose(); }
    }

    static void aura(InkCranePhantomEntity entity, float partial, PoseStack pose, MultiBufferSource buffers) {
        if (entity.mode() == InkCranePhantomEntity.RITUAL) return;
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(BRUSH));
        double age = entity.age() + partial;
        double size = entity.mode() == InkCranePhantomEntity.LONG_WINGS ? 9
                : entity.mode() == InkCranePhantomEntity.GIANT_DIVE ? 24
                : entity.mode() == InkCranePhantomEntity.DRAKE_CHARGE ? 14 : 2.8;
        float formed = (float) Math.min(1, age / 12);
        for (int i = 0; i < 14; i++) {
            Vec3 previous = null;
            for (int j = 0; j < 10; j++) {
                double a = i * 2.39996 + j * .18 + age * .024;
                double r = size * (.45 + .1 * Math.sin(i + age * .08));
                Vec3 at = point(a, r, (j - 5) * size * .32);
                if (previous != null) {
                    stroke(out, pose.last(), previous, at, size * .11, .08F, .13F * formed);
                    stroke(out, pose.last(), previous, at, size * .035, .004F, .75F * formed);
                }
                previous = at;
            }
        }
        if (age < 16) bloom(out, pose.last(), age, size * 1.3, (float) (1 - age / 16));
        if (entity.finished()) bloom(out, pose.last(), age % 16, size * 2, .8F);
    }

    static void caster(int move, double age, PoseStack pose, MultiBufferSource buffers) {
        if (move == 0) return;
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        for (int i = 0; i < 3; i++) ring(out, pose.last(), 2.2 + i * .7, .1,
                .055 + i * .01, age * .035 * (i == 1 ? -1 : 1), .018F, .7F, true);
        for (int i = 0; i < 12; i++) {
            double a = age * .04 + i * Math.PI / 6;
            Vec3 p = point(a, 2.8, .15 + (age * .045 + i * .27) % 4.5);
            stroke(out, pose.last(), p, p.add(-Math.sin(a) * .45,.55,Math.cos(a) * .45), .12, .01F, .8F);
        }
    }

    static void bloom(VertexConsumer out, PoseStack.Pose pose, double age, double radius, float alpha) {
        for (int i = 0; i < 24; i++) {
            double a = i * 2.39996, t = Math.min(1, age / 18), r = radius * t * (0.5 + (i % 5) * .12);
            Vec3 p = point(a, r, .2 + Math.sin(t * Math.PI) * (1 + i % 4));
            stroke(out, pose, p, p.add(Math.cos(a) * .6, -.25, Math.sin(a) * .6), .08 + .16 * (1 - t), .006F, alpha);
        }
    }

    static void sphere(VertexConsumer out, PoseStack.Pose pose, double r, double y, double age, float fade) {
        for (int lat = 0; lat < 12; lat++) {
            double a = -Math.PI / 2 + lat * Math.PI / 12, b = a + Math.PI / 12;
            for (int lon = 0; lon < 24; lon++) {
                double c = lon * Math.PI / 12 + age * .014, d = c + Math.PI / 12;
                quad(out, pose, spherical(r, y, a, c, age), spherical(r, y, a, d, age),
                        spherical(r, y, b, d, age), spherical(r, y, b, c, age), .009F, .96F * fade);
            }
        }
        for (int stream = 0; stream < 18; stream++) {
            Vec3 previous = null;
            for (int j = 0; j <= 14; j++) {
                double lat = -1.45 + j * 2.9 / 14;
                double lon = stream * 2.39996 + lat * 1.7 + age * .035 + .13 * Math.sin(lat * 5 + age * .2);
                Vec3 at = spherical(r * 1.025, y, lat, lon, age);
                if (previous != null) {
                    stroke(out, pose, previous, at, r * .045, stream % 3 == 0 ? .085F : .002F, .8F * fade);
                    if (stream % 3 == 0) stroke(out, pose, previous, at, r * .007, .55F, .6F * fade);
                }
                previous = at;
            }
        }
        for (int drop = 0; drop < 14; drop++) {
            double lat = Math.sin(age * .025 + drop) * 1.2, lon = drop * 2.39996 + age * .04;
            Vec3 p = spherical(r * (1.07 + .1 * Math.sin(age * .14 + drop)), y, lat, lon, age);
            stroke(out, pose, p, p.add(.02, -r * .16, .03), r * .035, .004F, .9F * fade);
        }
        for (int i = 0; i < 4; i++) {
            double latitude = -.8 + i * .5;
            ring(out, pose, Math.cos(latitude) * (r + .025), .016,
                    y + Math.sin(latitude) * (r + .025), age * .025 + i, .75F, .65F * fade, true);
        }
    }

    private static Vec3 spherical(double r, double y, double latitude, double longitude, double age) {
        r *= 1 + .075 * Math.sin(longitude * 5 + age * .18) * Math.sin(latitude * 3 - age * .13)
                + .035 * Math.cos(longitude * 11 - age * .27);
        return new Vec3(r * Math.cos(latitude) * Math.cos(longitude), y + r * Math.sin(latitude),
                r * Math.cos(latitude) * Math.sin(longitude));
    }
    private static Vec3 point(double a, double r, double y) { return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r); }

    static void ring(VertexConsumer out, PoseStack.Pose pose, double radius, double width, double y,
                             double turn, float gray, float alpha, boolean broken) {
        for (int i = 0; i < 96; i++) {
            if (broken && i % 24 >= 20) continue;
            double a = i * Math.PI / 48 + turn, b = a + Math.PI / 48;
            double w = width * (1 + .23 * Math.sin(i * .7 + turn));
            quad(out, pose, point(a, radius - w, y), point(b, radius - w, y),
                    point(b, radius + w, y), point(a, radius + w, y), gray, alpha);
        }
    }

    static void stroke(VertexConsumer out, PoseStack.Pose pose, Vec3 a, Vec3 b, double width, float gray, float alpha) {
        Vec3 n = b.subtract(a).cross(new Vec3(0, 1, 0)).normalize().scale(width);
        if (n.lengthSqr() < 1e-8) n = new Vec3(width,0,0);
        quad(out, pose, a.add(n), b.add(n), b.subtract(n), a.subtract(n), gray, alpha);
    }

    private static void ribbon(VertexConsumer out,PoseStack.Pose pose,List<Vec3> path,double width,float gray,float alpha,boolean taper) {
        if(path.size()<2)return;
        Vec3 previousLeft=null,previousRight=null;
        for(int i=0;i<path.size();i++) {
            Vec3 p=path.get(i),before=path.get(Math.max(0,i-1)),after=path.get(Math.min(path.size()-1,i+1));
            Vec3 n=after.subtract(before).cross(new Vec3(0,1,0)).normalize();
            if(n.lengthSqr()<1e-8)n=new Vec3(1,0,0);
            double fade=taper?Math.pow(1-i/(double)path.size(),.75):1;
            Vec3 side=n.scale(width*fade),left=p.add(side),right=p.subtract(side);
            if(previousLeft!=null)quad(out,pose,previousLeft,left,right,previousRight,gray,alpha*(float)fade);
            previousLeft=left;previousRight=right;
        }
    }

    static void quad(VertexConsumer out, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float gray, float alpha) {
        if (alpha <= 0) return;
        vertex(out, pose, a, gray, alpha,0,0); vertex(out, pose, b, gray, alpha,1,0);
        vertex(out, pose, c, gray, alpha,1,1); vertex(out, pose, d, gray, alpha,0,1);
        vertex(out, pose, d, gray, alpha,0,1); vertex(out, pose, c, gray, alpha,1,1);
        vertex(out, pose, b, gray, alpha,1,0); vertex(out, pose, a, gray, alpha,0,0);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 p, float gray, float alpha,float u,float v) {
        out.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z).color(gray, gray, gray, alpha)
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 1, 0).endVertex();
    }
}
