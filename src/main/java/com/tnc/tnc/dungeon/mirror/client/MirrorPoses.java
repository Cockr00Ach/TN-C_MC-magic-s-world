package com.tnc.tnc.dungeon.mirror.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

public final class MirrorPoses {
    private MirrorPoses() {}
    private static float smooth(float a,float b,float t) {
        float f=Mth.clamp((t-a)/(b-a),0,1); return f*f*(3-2*f);
    }
    private static float key(float t,float a,float b,float from,float to) {
        return Mth.lerp(smooth(a,b,t),from,to);
    }
    public static void apply(PlayerModel<?> m,EchoPlayer body,float age) {
        var e=body.actor;
        String action=e.actionName();
        if(action.isEmpty()) return;
        boolean right=e.getMainArm()==HumanoidArm.RIGHT;
        float sign=right?1:-1, t=e.actionAge()+Mth.frac(age);
        ModelPart arm=right?m.rightArm:m.leftArm, off=right?m.leftArm:m.rightArm;
        ModelPart front=right?m.leftLeg:m.rightLeg, back=right?m.rightLeg:m.leftLeg;
        float fraction=Mth.clamp(t/Math.max(1,e.actionDuration()),0,1);
        switch(action) {
            case "axe" -> {
                arm.xRot= t<10?key(t,0,10,-.65f,-2.52f):t<14?key(t,10,14,-2.52f,-.76f):key(t,16,24,-.76f,-.3f);
                float turn=t<10?key(t,0,10,.0f,.36f):key(t,10,16,.36f,-.50f);
                m.body.yRot=sign*turn; arm.yRot=sign*turn; arm.zRot=sign*.17f;
                off.xRot=-.46f; front.xRot=t<12?-.32f:-.49f; back.xRot=.35f;
            }
            case "rod" -> {
                float angle=t<4?key(t,0,4,.1f,.82f):t<12?key(t,4,12,.82f,-.92f):key(t,12,18,-.92f,-.26f);
                m.body.yRot=sign*angle*.6f; arm.xRot=-1.28f; arm.yRot=sign*angle; arm.zRot=sign*(.14f+angle*.20f);
                off.xRot=-.52f; off.yRot=-sign*.32f;
            }
            case "sword","slash","leap" -> {
                boolean leap=action.equals("leap");
                float k=leap?1.0f:.76f;
                arm.xRot=(fraction<.48f?key(fraction,0,.48f,-.52f,-2.27f):key(fraction,.48f,.72f,-2.27f,-.58f))*k;
                m.body.yRot=sign*key(fraction,.34f,.75f,.30f,-.37f); off.xRot=-.73f;
                if(leap && !e.onGround()) { front.xRot=-.78f; back.xRot=.62f; }
            }
            case "block" -> { off.xRot=-1.15f; off.yRot=sign*.49f; m.body.yRot=-sign*.29f; arm.xRot=-.72f; }
            case "web","web_lava","lava","water" -> {
                arm.xRot=-1.44f+smooth(.39f,.83f,fraction)*.75f;
                arm.yRot=sign*.24f; m.body.xRot=.28f; m.head.xRot+=.27f; off.xRot=-.38f;
            }
            case "lava_overhead" -> {
                arm.xRot=t<9?key(t,0,9,-1.45f,-2.75f):key(t,9,16,-2.75f,-1.2f);
                arm.yRot=sign*.18f; off.xRot=-.45f;
            }
            case "pearl" -> {
                arm.xRot=t<8?key(t,0,8,-.45f,-2.43f):key(t,8,15,-2.43f,-.62f);
                m.body.yRot=sign*key(t,3,12,.25f,-.30f);
                off.xRot=-.46f;
            }
            case "pillar" -> {
                arm.xRot=-1.30f+.55f*(float)Math.cos(t*.88f); m.head.xRot=1.02f;
                if(!e.onGround()) { front.xRot=-.44f; back.xRot=.51f; }
            }
            case "flee","retreat" -> {
                m.body.xRot=.16f; m.head.yRot+=sign*.32f;
                off.xRot=-.82f; arm.xRot=-.31f+.37f*(float)Math.cos(t*.58f);
            }
            case "chase" -> {m.body.xRot=.19f; off.xRot=-.9f; arm.zRot=sign*.21f;}
            case "bow" -> { arm.xRot=-1.56f; off.xRot=-1.49f; arm.yRot=-sign*.42f; off.yRot=-sign*.94f; }
            case "apple" -> { arm.xRot=-1.65f-.1f*(float)Math.cos(t*.72f); arm.yRot=-sign*.45f; m.head.xRot=.09f; }
            case "cast","release" -> {
                float pulse=action.equals("cast")?smooth(0,.73f,fraction):1-smooth(.30f,1,fraction);
                arm.xRot=-.65f-1.34f*pulse; off.xRot=-.38f-.65f*pulse; off.zRot=-sign*.35f*pulse;
            }
            case "rise","recover" -> { arm.xRot=key(fraction,0,1,-.93f,-.21f); off.xRot=key(fraction,0,1,-.82f,-.12f); }
            default -> { return; }
        }
        m.hat.copyFrom(m.head); m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm); m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg); m.leftPants.copyFrom(m.leftLeg);
    }
    public static void rotations(PlayerModel<?> m,float[] buffer,boolean restore) {
        ModelPart[] parts={m.head,m.body,m.rightArm,m.leftArm,m.rightLeg,m.leftLeg,m.hat,m.jacket,m.rightSleeve,m.leftSleeve,m.rightPants,m.leftPants};
        int i=0;
        for(var p:parts) {
            if(restore) {p.xRot=buffer[i++];p.yRot=buffer[i++];p.zRot=buffer[i++];}
            else {buffer[i++]=p.xRot;buffer[i++]=p.yRot;buffer[i++]=p.zRot;}
        }
    }
}
