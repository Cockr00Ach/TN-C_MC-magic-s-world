import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep production control/physics methods unchanged; replace only registry and Minecraft startup. */
public class ExtractTortoiseStun implements Opcodes {
 public static void main(String[] args)throws Exception {
  String owner="com/tnc/tnc/boss/fire/TortoiseStun";Path out=Path.of(args[1]);
  try(JarFile jar=new JarFile(args[0])) {
   ClassNode original=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(owner+".class"))).accept(original,0);
   ClassWriter w=new ClassWriter(0);w.visit(V17,ACC_PUBLIC,owner,null,"java/lang/Object",null);
   w.visitField(ACC_PRIVATE|ACC_STATIC,"STUN","Lnet/minecraftforge/registries/RegistryObject;",null,null).visitEnd();
   w.visitField(ACC_PRIVATE|ACC_STATIC,"HOLDS","Ljava/util/Map;",null,null).visitEnd();
   MethodVisitor m=w.visitMethod(ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
   m.visitTypeInsn(NEW,"net/minecraftforge/registries/RegistryObject");m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,"net/minecraftforge/registries/RegistryObject","<init>","()V",false);
   m.visitFieldInsn(PUTSTATIC,owner,"STUN","Lnet/minecraftforge/registries/RegistryObject;");
   m.visitTypeInsn(NEW,"java/util/WeakHashMap");m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,"java/util/WeakHashMap","<init>","()V",false);
   m.visitFieldInsn(PUTSTATIC,owner,"HOLDS","Ljava/util/Map;");m.visitInsn(RETURN);m.visitMaxs(2,0);m.visitEnd();
   w.visitNestMember(owner+"$Hold");Path nested=out.resolve(owner+"$Hold.class");Files.createDirectories(nested.getParent());
   Files.copy(jar.getInputStream(jar.getJarEntry(owner+"$Hold.class")),nested,StandardCopyOption.REPLACE_EXISTING);
   Set<String> names=Set.of("apply","isStunned","suppressInput","enforce","livingTick","tick","unload","attack","use");int copied=0;
   for(MethodNode method:original.methods)if(names.contains(method.name)||method.name.startsWith("lambda$")){method.accept(w);copied++;}
   if(copied<9)throw new AssertionError("Missing production methods");
   w.visitEnd();Files.write(out.resolve(owner+".class"),w.toByteArray());System.out.println("Extracted "+copied+" unchanged stun method bodies");
  }
 }
}
