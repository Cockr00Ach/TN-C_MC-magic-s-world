package com.tnc.tnc.skyacademy.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.mana.sight.client.ManaSightClient;
import com.tnc.tnc.skyacademy.AcademyRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Vector3f;

/** A local instructional current. Its landmarks remain legible with minimal particles and in daylight. */
final class WindLessonVisuals {
    private static final RenderType GLOW = RenderType.entityTranslucentEmissive(
            ResourceLocation.parse("tnc:textures/particle/mana_glow.png"));
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(32768));
    private static final float[] PITCH = {.84f, 1.06f, 1.26f};
    private static final int JADE = 0x5AD4BB, MINT = 0xC6FFDD, CREAM = 0xFFDEA1;
    private static ClientLevel world;
    private static BlockPos anchor;
    private static WindLessonPath path;
    private static long touchedAt;
    private static int touched = -1, step, progress;
    private static int movieTime = -1, rehearsal = -1;

    static void clear() {
        world = null;
        anchor = null;
        path = null;
        touched = -1;
        movieTime = rehearsal = -1;
        progress = 0;
    }

    static WindLessonPath path(BlockPos origin) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != world || !origin.equals(anchor)) {
            clear();
            anchor = origin;
            world = level;
            BlockPos[] bases=new BlockPos[3];
            for(int i=0;i<bases.length;i++)bases[i]=origin.offset(AcademyClient.offset("rune"+i));
            path = new WindLessonPath(bases);
        }
        return path;
    }

    static void touch(BlockPos origin, int index, int result) {
        var mc = Minecraft.getInstance();
        if (index < 0 || index >= 3 || result < 0 || result > 3 || mc.level == null || mc.player == null) return;
        WindLessonPath current = path(origin);
        Vec3 bell = current.bell(index);
        if (bell.distanceToSqr(mc.player.position()) > 18*18) return;
        touchedAt = world.getGameTime();
        touched = index;
        step = result;
        sound(bell, SoundEvents.AMETHYST_BLOCK_HIT, .85f, result == 0 ? .68f : 1.04f);
        if (result == 0) {
            sound(bell, SoundEvents.NOTE_BLOCK_BASS.value(), .75f, .75f);
            rehearsal = -1;
        } else if (result < 3) {
            sound(bell, SoundEvents.NOTE_BLOCK_BELL.value(), .81f, PITCH[result-1]);
        } else {
            sound(bell, SoundEvents.BELL_RESONATE, .65f, 1.14f);
        }
    }

    static void tick(BlockPos origin, boolean demo, int age, int duration, boolean practicing, int completedRunes) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || origin == null || !mc.player.isAlive()) { clear(); return; }
        if (!demo && !practicing && touched == -1) {
            movieTime = rehearsal = -1;
            return;
        }
        WindLessonPath route = path(origin);
        boolean nearby = mc.player.position().distanceToSqr(route.bell(1)) < 42*42;
        if (!nearby) { clear(); return; }
        progress = Mth.clamp(completedRunes, 0, 3);
        movieTime = demo ? (age * WindLessonPath.LENGTH / duration) : -1;
        rehearsal = !demo && practicing && ManaSightClient.enabled() ? (rehearsal+1) % 190 : -1;
        int time = demo ? movieTime : rehearsal;
        for (int i=0; i<WindLessonPath.STRIKES.length; i++) {
            if (time == WindLessonPath.STRIKES[i]) {
                sound(route.bell(AcademyRules.runeAtStep(i)), SoundEvents.NOTE_BLOCK_BELL.value(),
                        demo ? .7f : .36f, PITCH[i]);
            }
        }
        if (touched < 0) return;
        long since = world.getGameTime() - touchedAt;
        if (step == 3 && since >= 1 && since <= 11 && (since-1)%5 == 0) {
            int note = (int)(since-1)/5;
            sound(route.bell(AcademyRules.runeAtStep(note)), SoundEvents.NOTE_BLOCK_BELL.value(), .9f, PITCH[note]);
        }
        if (since >= 32) touched = -1;
    }

    private static void sound(Vec3 at, SoundEvent sound, float volume, float pitch) {
        world.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, false);
    }

    static void shake(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        if (touched < 0 || world == null || mc.level != world || mc.player == null || !mc.player.isAlive()
                || mc.getCameraEntity() != mc.player || mc.isPaused()) return;
        double t = world.getGameTime() - touchedAt + event.getPartialTick();
        if (t < 0 || t > 8) return;
        double amplitude = (step == 3 ? 1.22 : step == 0 ? .65 : .9) *
                Math.exp(-t*.45) * mc.options.damageTiltStrength().get();
        event.setPitch(event.getPitch() + (float)(amplitude*Math.cos(t*2.1)));
        event.setRoll(event.getRoll() + (float)(.38*amplitude*Math.sin(t*1.85)));
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || path == null
                || world != Minecraft.getInstance().level || (movieTime < 0 && rehearsal < 0 && touched < 0)) return;
        double partial = event.getPartialTick();
        var camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        if (eye.distanceToSqr(path.bell(1)) > 48*48) return;
        Vector3f v = new Vector3f(1,0,0).rotate(camera.rotation());
        Vec3 right = new Vec3(v.x,v.y,v.z);
        v.set(0,1,0).rotate(camera.rotation());
        Vec3 up = new Vec3(v.x,v.y,v.z);
        Frame frame = new Frame(BUFFER.getBuffer(GLOW), eye, right, up);
        if (movieTime >= 0 || rehearsal >= 0 && ManaSightClient.enabled()) {
            double t = (movieTime >= 0 ? movieTime : rehearsal) + partial;
            if (t <= WindLessonPath.LENGTH) flight(frame, t);
            for (int i=0; i<3; i++) {
                Vec3 bell = path.bell(AcademyRules.runeAtStep(i));
                double elapsed = t - WindLessonPath.STRIKES[i];
                if (elapsed >= 0 && elapsed <= 18) echo(frame, bell, elapsed/18, JADE, .65);
                if (movieTime < 0 && i < progress) {
                    frame.spark(bell.add(0,-.8,.42), .19, .26, CREAM, .95);
                }
            }
        }
        if (touched >= 0) {
            double t = world.getGameTime() - touchedAt + partial;
            int color = step == 0 ? 0xF09C83 : CREAM;
            Vec3 face = path.bell(touched).add(0,-.92,.38);
            if (t >= 0 && t < 20) {
                frame.spark(face, .56*(1-t/21), .78*(1-t/21), color, .92);
                echo(frame, face, t/20, color, step == 0 ? .55 : .88);
            }
            if (step == 3) for (int i=0; i<3; i++) {
                double u = (t-5*i)/20;
                if (u >= 0 && u < 1) echo(frame, path.bell(AcademyRules.runeAtStep(i)), u, MINT, 1.2);
            }
        }
        BUFFER.endBatch(GLOW);
    }

    private static void flight(Frame f, double time) {
        double fade = WindLessonPath.smooth(time/6) * (1-WindLessonPath.smooth((time-148)/12));
        Vec3 head = path.wind(time);
        // Unequal lengths and curl keep the illuminated pressure front and its wake visibly distinct.
        for (int strand=0; strand<3; strand++) {
            int spans = 20;
            double life = 11 + strand*2.6;
            Vec3 last = head;
            for (int i=1; i<=spans; i++) {
                double u = i/(double)spans;
                double bend = strand*.095*Math.sin(Math.PI*u);
                double phase = u*8 + time*.15 + strand*1.9;
                Vec3 next = path.wind(Math.max(0,time-u*life)).add(bend*Math.cos(phase), bend*Math.sin(phase), bend*.4);
                double width = (strand==0 ? .145 : .065)*(.25+.75*Math.sin(Math.PI*u));
                f.line(last,next,width,strand==0?JADE:strand==1?MINT:CREAM,fade*(1-u)*.96);
                last=next;
            }
        }
        f.spark(head, .35, .29, JADE, fade);
        f.spark(head, .12, .10, 0xFFF7D4, fade);
        for (int i=0; i<10; i++) {
            double u = (time*.037+i*.137)%1;
            Vec3 mote = path.wind(Math.max(0,time-u*15)).add(.17*Math.sin(i*2.6+time*.11),
                    .20*Math.cos(i*3.4+time*.08), .14*Math.sin(i+time*.12));
            f.spark(mote,.055,.047,i%3==0?CREAM:MINT,fade*Math.sin(u*Math.PI));
        }
    }

    private static void echo(Frame frame, Vec3 origin, double u, int color, double reach) {
        // Three broken crescents spread from the front of the struck bell.
        double radius = .15 + reach*(1-Math.pow(1-u,2));
        double alpha = 1-u;
        for (int arc=0; arc<3; arc++) {
            double angle = arc*2*Math.PI/3 + u*.23;
            Vec3 last = rim(origin, radius, angle);
            for (int i=1; i<=9; i++) {
                Vec3 next = rim(origin, radius, angle+1.7*i/9);
                frame.line(last,next,.048+alpha*.026,color,alpha*.94);
                last=next;
            }
        }
    }

    private static Vec3 rim(Vec3 origin, double radius, double angle) {
        return origin.add(radius*Math.cos(angle),radius*Math.sin(angle),.31+.10*Math.sin(angle));
    }

    private record Frame(VertexConsumer out, Vec3 eye, Vec3 right, Vec3 up) {
        void spark(Vec3 center, double w, double h, int color, double alpha) {
            Vec3 a=right.scale(w), b=up.scale(h);
            vertex(center.subtract(a).subtract(b),0,1,color,alpha);
            vertex(center.add(a).subtract(b),1,1,color,alpha);
            vertex(center.add(a).add(b),1,0,color,alpha);
            vertex(center.subtract(a).add(b),0,0,color,alpha);
        }

        void line(Vec3 a, Vec3 b, double width, int color, double alpha) {
            Vec3 side = b.subtract(a).cross(eye.subtract(a)).normalize().scale(width);
            vertex(a.subtract(side),.46f,.88f,color,alpha);
            vertex(b.subtract(side),.54f,.88f,color,alpha);
            vertex(b.add(side),.54f,.12f,color,alpha);
            vertex(a.add(side),.46f,.12f,color,alpha);
        }

        void vertex(Vec3 p, float u, float v, int color, double alpha) {
            out.vertex(p.x-eye.x,p.y-eye.y,p.z-eye.z).color(color>>16&255,color>>8&255,color&255,
                    Mth.clamp((int)(alpha*255),0,255)).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal(0,1,0).endVertex();
        }
    }

    private WindLessonVisuals() {}
}
