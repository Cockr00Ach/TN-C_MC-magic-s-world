package com.tnc.tnc.magic.free;

import net.minecraft.world.phys.Vec3;

/** The same formation positions are used for the visible array and authoritative launch. */
public final class WaterCastGeometry {
    public static Vec3 right(Vec3 direction){
        Vec3 right=new Vec3(-direction.z,0,direction.x);
        return right.lengthSqr()<.001?new Vec3(1,0,0):right.normalize();
    }
    public static Vec3 origin(FreeMagicIntent.Shape shape,Vec3 eye,Vec3 look,int index,int count){
        return origin(new FreeMagicIntent.Intent(shape,FreeMagicIntent.Mode.SINGLE),eye,look,index,count);
    }
    public static Vec3 origin(FreeMagicIntent.Intent intent,Vec3 eye,Vec3 look,int index,int count){
        var shape=intent.shape();boolean giant=intent.has(FreeMagicIntent.GIANT);
        double spread=(shape.large()?2.4:1.05)*(giant?3.2:1);
        Vec3 center=eye.add(look.scale(shape.large()?(giant?7.5:5.5):(giant?4.8:3.2))).add(0,-.25,0);
        if(count==1)return center.add(right(look).scale(.65));
        return center.add(right(look).scale((index-(count-1)*.5)*spread))
                .add(0,Math.sin(index*Math.PI/Math.max(1,count-1))*.5,0);
    }
    public static Vec3 direction(Vec3 eye,Vec3 look,Vec3 origin){
        return eye.add(look.scale(48)).subtract(origin).normalize();
    }
    private WaterCastGeometry(){}
}
