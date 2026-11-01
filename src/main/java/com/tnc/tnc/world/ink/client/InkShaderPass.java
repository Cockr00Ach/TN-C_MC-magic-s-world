package com.tnc.tnc.world.ink.client;

import java.lang.invoke.*;

/** Optional Oculus hook, verified against the installed 1.8.0 API. */
final class InkShaderPass {
    private static final MethodHandle SHADOW=resolve();
    private InkShaderPass(){}
    private static MethodHandle resolve(){
        try{return MethodHandles.publicLookup().findStatic(Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState"),
                "areShadowsCurrentlyBeingRendered",MethodType.methodType(boolean.class));}
        catch(ReflectiveOperationException|LinkageError e){return null;}
    }
    static boolean shadow(){try{return SHADOW!=null&&(boolean)SHADOW.invokeExact();}catch(Throwable ignored){return false;}}
}
