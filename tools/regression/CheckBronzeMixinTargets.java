import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Check actual shipped selectors against SRG classes without starting Minecraft. */
public final class CheckBronzeMixinTargets {
    static ClassNode read(JarFile jar,String name) throws Exception {
        var entry=jar.getJarEntry(name+".class");if(entry==null)throw new AssertionError("Missing class: "+name);
        var node=new ClassNode();try(var input=jar.getInputStream(entry)){new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}return node;
    }
    static Object value(AnnotationNode annotation,String key){
        for(int i=0;i<annotation.values.size();i+=2)if(key.equals(annotation.values.get(i)))return annotation.values.get(i+1);return null;
    }
    @SuppressWarnings("unchecked") public static void main(String[] args) throws Exception {
        try(var mod=new JarFile(args[0]);var mc=new JarFile(args[1])){
            var targets=Map.of("BronzeHeldItemMixin","net/minecraft/client/renderer/entity/layers/ItemInHandLayer",
                "BronzeRepairMixin","net/minecraft/world/item/crafting/RepairItemRecipe");int checked=0;
            for(var target:targets.entrySet()){
                var mixin=read(mod,"com/tnc/tnc/mixin/"+target.getKey());var runtime=read(mc,target.getValue());
                for(var method:mixin.methods){
                    var annotations=new ArrayList<AnnotationNode>();if(method.visibleAnnotations!=null)annotations.addAll(method.visibleAnnotations);if(method.invisibleAnnotations!=null)annotations.addAll(method.invisibleAnnotations);
                    for(var annotation:annotations)if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")){
                        var selectors=(List<String>)value(annotation,"method");boolean match=false;var matched=new ArrayList<MethodNode>();
                        for(var selector:selectors){int split=selector.indexOf('(');String name=split<0?selector:selector.substring(0,split),desc=split<0?null:selector.substring(split);
                            for(var actual:runtime.methods)if(actual.name.equals(name)&&(desc==null||actual.desc.equals(desc))){match=true;matched.add(actual);}
                        }
                        if(!match)throw new AssertionError(target.getKey()+" selectors missing from SRG runtime: "+selectors);
                        boolean needsInvoke=false,foundInvoke=false;
                        for(var at:(List<AnnotationNode>)value(annotation,"at"))if("INVOKE".equals(value(at,"value"))){
                            needsInvoke=true;String call=(String)value(at,"target");
                            for(var actual:matched)for(var instruction:actual.instructions)if(instruction instanceof MethodInsnNode invoked&&call.equals("L"+invoked.owner+";"+invoked.name+invoked.desc))foundInvoke=true;
                        }
                        if(needsInvoke&&!foundInvoke)throw new AssertionError("Missing scoped hand-render injection point");
                        checked++;
                    }
                }
            }
            if(checked!=3)throw new AssertionError("Expected three injection sites, got "+checked);
            System.out.println("PASS: all "+checked+" bronze Mixin injection sites resolve in the actual SRG Minecraft jar");
        }
    }
}
