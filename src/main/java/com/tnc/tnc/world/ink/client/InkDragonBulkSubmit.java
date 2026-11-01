package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;
import java.util.Map;
import org.lwjgl.system.*;
import com.tnc.tnc.world.ink.InkDragonVertexPacking;

/** Optional Embeddium API. Its registered Oculus serializer owns all extended attributes. */
final class InkDragonBulkSubmit {
    private record Bridge(MethodHandle writer,MethodHandle push,Object format) {}
    private static final Bridge BRIDGE=resolve();
    private static long bulkVertices,bulkCalls,legacyVertices;
    private static Bridge resolve() {
        try {
            var lookup=MethodHandles.publicLookup();
            var writer=Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter");
            var description=Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription");
            var registry=Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatRegistry");
            Object instance=lookup.findStatic(registry,"instance",MethodType.methodType(registry)).invoke();
            Object format=lookup.findVirtual(registry,"get",MethodType.methodType(description,VertexFormat.class)).invoke(instance,DefaultVertexFormat.NEW_ENTITY);
            int stride=(int)lookup.findVirtual(description,"stride",MethodType.methodType(int.class)).invoke(format);
            if(stride!=InkDragonVertexPacking.STRIDE)return null;
            return new Bridge(lookup.findStatic(writer,"tryOf",MethodType.methodType(writer,VertexConsumer.class))
                    .asType(MethodType.methodType(Object.class,VertexConsumer.class)),
                lookup.findVirtual(writer,"push",MethodType.methodType(void.class,MemoryStack.class,long.class,int.class,description))
                    .asType(MethodType.methodType(void.class,Object.class,MemoryStack.class,long.class,int.class,Object.class)),format);
        } catch(Throwable unavailable) { return null; }
    }
    static boolean available(){return BRIDGE!=null;}
    static Object writer(VertexConsumer consumer) {
        if(BRIDGE==null||Boolean.getBoolean("tnc.inkDragonLegacyVertices"))return null;
        try{return (Object)BRIDGE.writer.invokeExact(consumer);}
        catch(Throwable unavailable){return null;}
    }
    static void legacy(int count){legacyVertices+=count;}
    static void push(Object writer,ByteBuffer data,int count) {
        long base=MemoryUtil.memAddress(data);
        // Some wrappers copy on the LWJGL stack: bound each call, never allocate megabytes there.
        for(int first=0;first<count;first+=512) {
            int batch=Math.min(512,count-first);
            try(var stack=MemoryStack.stackPush()) {
                BRIDGE.push.invokeExact(writer,stack,base+(long)first*InkDragonVertexPacking.STRIDE,batch,BRIDGE.format);
            }catch(Throwable failure){throw new IllegalStateException("Ink dragon standard-entity bulk submission failed",failure);}
            bulkCalls++;bulkVertices+=batch;
        }
    }
    static Map<String,Object> diagnostics(){return Map.of("available",available(),"bulkVertices",bulkVertices,"bulkCalls",bulkCalls,"legacyVertices",legacyVertices);}
}
