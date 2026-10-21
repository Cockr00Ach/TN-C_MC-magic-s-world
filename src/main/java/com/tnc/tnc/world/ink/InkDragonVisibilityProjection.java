package com.tnc.tnc.world.ink;
import org.joml.Matrix4f;
/** Extend only the remote model pass; never change the terrain projection/view distance. */
public final class InkDragonVisibilityProjection {
    private InkDragonVisibilityProjection(){}
    public static Matrix4f extend(Matrix4f original){
        var result=new Matrix4f(original);
        float near=original.m32()/(original.m22()-1),far=original.m32()/(original.m22()+1);
        if(original.m23()==-1&&Float.isFinite(near)&&near>0&&far>near&&far<1024){
            result.m22(-(1024+near)/(1024-near));result.m32(-2*1024*near/(1024-near));
        }
        return result;
    }
}
