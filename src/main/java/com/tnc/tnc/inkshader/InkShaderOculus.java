package com.tnc.tnc.inkshader;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.Optional;

/** Optional Oculus 1.8/Iris bridge. No hard client/mod references are loaded on a server or without shaders. */
final class InkShaderOculus implements InkShaderSession.Backend {
    private final Class<?> iris;private final Object config;
    private final Method enabled,pack,setEnabled,setPack,save,reload;
    InkShaderOculus() throws ReflectiveOperationException {
        iris=Class.forName("net.irisshaders.iris.Iris");config=iris.getMethod("getIrisConfig").invoke(null);
        if(config==null)throw new IllegalStateException("Oculus has not initialized its config");
        var type=config.getClass();enabled=type.getMethod("areShadersEnabled");pack=type.getMethod("getShaderPackName");
        setEnabled=type.getMethod("setShadersEnabled",boolean.class);setPack=type.getMethod("setShaderPackName",String.class);
        save=type.getMethod("save");reload=iris.getMethod("reload");
    }
    @Override public InkShaderSession.State read() throws ReflectiveOperationException {
        return new InkShaderSession.State((boolean)enabled.invoke(config),(String)((Optional<?>)pack.invoke(config)).orElse(null));
    }
    @Override public void apply(InkShaderSession.State state) throws Exception {
        try {setPack.invoke(config,state.pack());setEnabled.invoke(config,state.enabled());save.invoke(config);reload.invoke(null);}
        catch(InvocationTargetException e){if(e.getCause() instanceof Exception exception)throw exception;throw e;}
    }
    Path shaderDirectory() throws ReflectiveOperationException {return (Path)iris.getMethod("getShaderpacksDirectory").invoke(null);}
    boolean renderingShaders() throws ReflectiveOperationException {
        var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");return (boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
    }
}
