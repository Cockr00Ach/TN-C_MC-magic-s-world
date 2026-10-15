package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class TsunamiVisualsTest {
    private int mesh(boolean near,double age) {
        int[] count={0};boolean[] uv={false},normal={false};
        VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("vertex")) {
                count[0]++;
                for(int i=args.length-3;i<args.length;i++)assertTrue(Double.isFinite(((Number)args[i]).doubleValue()));
            }
            if(method.getName().equals("uv"))uv[0]=true;
            if(method.getName().equals("normal"))normal[0]=true;
            if(method.getName().equals("endVertex")){assertTrue(uv[0]&&normal[0],"Textured vertices need UV and normal data");uv[0]=false;normal[0]=false;}
            return method.getReturnType()==void.class?null:proxy;
        });
        TsunamiVisuals.mesh(out,new PoseStack().last(),Vec3.ZERO,new Vec3(0,0,1),22,8,age,.9F,near,
                new TsunamiVisuals.Uv(0,1,0,1),new TsunamiVisuals.Uv(0,1,0,1));
        assertEquals(0,count[0]%4);return count[0];
    }
    @Test void texturedTsunamiHasBoundedCostAndARealDistanceLod() {
        for(double age:new double[]{0,.5,40,150}) {
            int near=mesh(true,age),far=mesh(false,age);
            assertTrue(near<=4000);assertTrue(far<near&&far<=1800);
        }
    }
    @Test void swordLightSheathKeepsItsOwnVisibleEmission() {
        int[] count={0};float[] brightest={0};
        VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("vertex"))count[0]++;
            if(method.getName().equals("color"))brightest[0]=Math.max(brightest[0],((Number)args[3]).floatValue());
            return method.getReturnType()==void.class?null:proxy;
        });
        SeaGodSwordRadiance.draw(out,new org.joml.Matrix4f(),1);
        assertTrue(count[0]>0&&count[0]<=400);assertTrue(brightest[0]>=.79F);
    }
}
