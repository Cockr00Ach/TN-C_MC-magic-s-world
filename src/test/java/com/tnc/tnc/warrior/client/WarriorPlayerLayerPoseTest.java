package com.tnc.tnc.warrior.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Replays vanilla's skin-layer copy followed by the actual post-setupAnim pose hook. */
class WarriorPlayerLayerPoseTest {
    @Test void pairedIdleGripKeepsBothSleevesAttachedOnSteveAndAlex() throws Exception {
        for (boolean slim : new boolean[]{false,true}) for (HumanoidArm hand : HumanoidArm.values()) {
            var model=new PlayerModel<Player>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot(),slim);
            var main=hand==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
            main.xRot=-.314159f;main.yRot=.35f;main.zRot=.12f;
            model.leftSleeve.copyFrom(model.leftArm);
            model.rightSleeve.copyFrom(model.rightArm);
            // PlayerModel has already copied sleeves when WarriorPoseMixin runs at TAIL.
            applyIdlePairPose(model,hand);
            assertAttached(model.leftArm,model.leftSleeve,"left sleeve creates a detached third arm; slim="+slim+", main="+hand);
            assertAttached(model.rightArm,model.rightSleeve,"right sleeve creates a detached third arm; slim="+slim+", main="+hand);
        }
    }

    @Test void outerSkinTransformsFollowTheFinalPoseWithoutChangingVisibility() throws Exception {
        var model=new PlayerModel<Player>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,false),64,64).bakeRoot(),false);
        model.body.xRot=.27f;model.body.yRot=-.4f;model.body.x=1.25f;
        model.leftLeg.zRot=.18f;model.rightLeg.xRot=-.31f;
        model.head.xRot=.2f;model.head.yRot=.42f;
        model.leftSleeve.visible=false;model.jacket.visible=false;
        applyIdlePairPose(model,HumanoidArm.RIGHT);
        assertAttached(model.body,model.jacket,"jacket");
        assertAttached(model.leftLeg,model.leftPants,"left pants");
        assertAttached(model.rightLeg,model.rightPants,"right pants");
        assertAttached(model.head,model.hat,"hat");
        assertFalse(model.leftSleeve.visible,"Respect the player's skin-layer settings");
        assertFalse(model.jacket.visible,"Respect the player's skin-layer settings");
    }

    private static void assertAttached(ModelPart limb,ModelPart layer,String message) {
        assertAll(message,
                ()->assertEquals(limb.xRot,layer.xRot,0,message+" xRot"),
                ()->assertEquals(limb.yRot,layer.yRot,0,message+" yRot"),
                ()->assertEquals(limb.zRot,layer.zRot,0,message+" zRot"),
                ()->assertEquals(limb.x,layer.x,0,message+" x"),
                ()->assertEquals(limb.y,layer.y,0,message+" y"),
                ()->assertEquals(limb.z,layer.z,0,message+" z"));
    }

    private static void applyIdlePairPose(PlayerModel<?> model,HumanoidArm hand) throws Exception {
        // Replay the compiled production entry point. Replace only external avatar/equipment
        // reads so this test needs neither a world nor Forge's transformed event bootstrap.
        var writer=new ClassWriter(0);
        int[] fixtures={0};
        try(var stream=WarriorPose.class.getResourceAsStream("WarriorPose.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9,writer) {
                @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,desc,signature,exceptions)) {
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf) {
                            if(owner.equals("net/minecraft/world/entity/player/Player")&&name.equals("getId")) {
                                super.visitInsn(Opcodes.POP);super.visitInsn(Opcodes.ICONST_0);
                            } else if(owner.equals("com/tnc/tnc/warrior/client/WarriorAnimationClient")&&name.equals("active")) {
                                super.visitInsn(Opcodes.POP);super.visitInsn(Opcodes.ACONST_NULL);
                            } else if(owner.equals("com/tnc/tnc/bronze/BronzeTwinWeapon")&&name.equals("active")) {
                                super.visitInsn(Opcodes.POP);super.visitInsn(Opcodes.ICONST_1);
                            } else if(owner.equals("net/minecraft/world/entity/player/Player")&&name.equals("getMainArm")) {
                                super.visitInsn(Opcodes.POP);super.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/world/entity/HumanoidArm",hand.name(),"Lnet/minecraft/world/entity/HumanoidArm;");
                            } else {super.visitMethodInsn(opcode,owner,name,desc,itf);return;}
                            fixtures[0]++;
                        }
                    };
                }
            },0);
        }
        assertTrue(fixtures[0]>=4,"Equipment fixtures must reach the production idle-pair path");
        byte[] bytes=writer.toByteArray();
        Class<?> replay=new ClassLoader(WarriorPose.class.getClassLoader()) {
            Class<?> replay(){return defineClass(WarriorPose.class.getName(),bytes,0,bytes.length);}
        }.replay();
        replay.getMethod("apply",Player.class,net.minecraft.client.model.HumanoidModel.class).invoke(null,null,model);
    }
}
