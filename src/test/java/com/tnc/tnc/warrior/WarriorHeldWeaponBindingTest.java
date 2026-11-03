package com.tnc.tnc.warrior;

import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.jupiter.api.Assertions.*;

class WarriorHeldWeaponBindingTest {
    @Test void heldItemHookMatchesTheRealMinecraftMethod() throws Exception {
        String descriptor="(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
        AtomicBoolean found=new AtomicBoolean();
        try(var stream=getClass().getResourceAsStream("/net/minecraft/client/renderer/ItemInHandRenderer.class")){
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                    if((name.equals("renderItem")||name.equals("m_269530_")) && desc.equals(descriptor)) found.set(true);
                    return null;
                }
            },ClassReader.SKIP_CODE);
        }
        assertTrue(found.get(),"Held-weapon injection must match the actual runtime method");
    }

    @Test void gripRemainsOnTheActualWeaponAfterTwoHandedMountTransforms() {
        Vector3f anchor=new Vector3f(.17f,-.12f,.09f);
        for(float angle:new float[]{0,.7f,-1.2f,2.8f}){
            // Different mounts: right/left, cross-body hold, overhead strike and recoil.
            Matrix4f mount=new Matrix4f().translation(.8f,1.3f,-.4f).rotateXYZ(angle,-angle*.4f,angle*.6f).scale(.9f,1.1f,1);
            Vector3f weaponGrip=mount.transformPosition(new Vector3f(anchor));
            Matrix4f ghost=new Matrix4f(mount);
            Vector3f correction=WarriorGripGeometry.compensation(anchor,1.45);
            ghost.translate(correction).scale(1.45f);
            Vector3f ghostGrip=ghost.transformPosition(new Vector3f(anchor));
            assertEquals(0,weaponGrip.distance(ghostGrip),1e-6);
        }
    }
}
