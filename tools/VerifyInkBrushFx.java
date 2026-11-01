import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
public class VerifyInkBrushFx {
 static ClassNode read(JarFile z,String name)throws Exception{var n=new ClassNode();try(var in=z.getInputStream(z.getJarEntry(name))){new ClassReader(in).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}return n;}
 static byte[] code(MethodNode m){var w=new ClassWriter(0);w.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,"Comparison",null,"java/lang/Object",null);m.accept(w);w.visitEnd();return w.toByteArray();}
 public static void main(String[] args)throws Exception{
  Map<String,Set<String>> edits=Map.of("InkTigerSpellVisuals",Set.of("gather","burst","wash"),"InkCraneRitualVisuals",Set.of("brushTrail","ritual","gathering"),"InkDragonBreathVisuals",Set.of("impact"));
  int classes=0,methods=0,preserved=0;
  try(var base=new JarFile(args[0]);var z=new JarFile(args[1])){
   for(var entry:Collections.list(z.entries())){
    var name=entry.getName();if(!name.endsWith(".class")||!(name.contains("/InkBrush")||name.endsWith("/InkWashVisuals.class")||edits.keySet().stream().anyMatch(k->name.endsWith("/"+k+".class"))))continue;
    var node=read(z,name);for(var m:node.methods){if((m.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))==0){new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name,m);methods++;}}
    String shortName=name.substring(name.lastIndexOf('/')+1,name.length()-6);
    if(edits.containsKey(shortName))for(var old:read(base,name).methods)if(!edits.get(shortName).contains(old.name)){
     var m=node.methods.stream().filter(x->x.name.equals(old.name)&&x.desc.equals(old.desc)).findFirst().orElseThrow();if(!Arrays.equals(code(old),code(m)))throw new IllegalStateException("Unrelated method changed: "+name+" "+old.name);preserved++;
    }
    classes++;
   }
  }
  System.out.println("Verified "+classes+" classes / "+methods+" methods; "+preserved+" unrelated methods identical.");
 }
}
