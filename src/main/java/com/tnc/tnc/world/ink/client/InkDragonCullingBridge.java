package com.tnc.tnc.world.ink.client;
import java.lang.reflect.Method;
import java.util.Optional;
/** Optional EntityCulling API, resolved from the transformed entity without a hard dependency. */
public final class InkDragonCullingBridge {
    private record Api(Method culled,Method camera,Method timeout){}
    private static final ClassValue<Optional<Api>> API=new ClassValue<>(){
        @Override protected Optional<Api> computeValue(Class<?> type){
            try{return Optional.of(new Api(type.getMethod("setCulled",boolean.class),type.getMethod("setOutOfCamera",boolean.class),type.getMethod("setTimeout")));}
            catch(NoSuchMethodException e){return Optional.empty();}
        }
    };
    private InkDragonCullingBridge(){}
    public static boolean reveal(Object entity){
        var api=API.get(entity.getClass());if(api.isEmpty())return false;
        try{var a=api.get();a.culled.invoke(entity,false);a.camera.invoke(entity,false);a.timeout.invoke(entity);return true;}
        catch(ReflectiveOperationException e){return false;}
    }
}
