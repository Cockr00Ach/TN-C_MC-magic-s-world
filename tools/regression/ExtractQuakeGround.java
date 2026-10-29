import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Run the actual capture bytecode without initializing OpenGL or Minecraft's client. */
public class ExtractQuakeGround implements Opcodes {
 public static void main(String[] args)throws Exception {
  String owner="com/tnc/tnc/boss/fire/client/QuakeBandFx";Path out=Path.of(args[1]);
  try(JarFile jar=new JarFile(args[0])) {
   ClassNode original=new ClassNode();
   new ClassReader(jar.getInputStream(jar.getJarEntry(owner+".class"))).accept(original,0);
   ClassWriter writer=new ClassWriter(0);writer.visit(V17,ACC_PUBLIC,owner,null,"java/lang/Object",null);
   for(String nested:List.of("$Face","$Surface")) {
    writer.visitNestMember(owner+nested);Path p=out.resolve(owner+nested+".class");
    Files.createDirectories(p.getParent());
    Files.copy(jar.getInputStream(jar.getJarEntry(owner+nested+".class")),p,StandardCopyOption.REPLACE_EXISTING);
   }
   int count=0;
   for(MethodNode method:original.methods)if(Set.of("capture","fallbackCube","modelTop").contains(method.name)) {
    method.accept(writer);count++;
   }
   if(count<2)throw new AssertionError("Missing actual capture methods");
   writer.visitEnd();Files.write(out.resolve(owner+".class"),writer.toByteArray());
   System.out.println("Extracted unchanged ground capture bytecode");
  }
 }
}
