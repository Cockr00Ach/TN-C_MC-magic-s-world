import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;

/** Graft only the new animation registration; retain every installed entity override. */
public final class WireCanglanExecutionInstall {
    public static void main(String[] args)throws Exception{
        var original=new ClassReader(Files.readAllBytes(Path.of(args[0])));
        var donor=new ClassReader(Files.readAllBytes(Path.of(args[1])));
        var writer=new ClassWriter(original,ClassWriter.COMPUTE_MAXS);int[] count={0};
        Set<String> originalMethods=new HashSet<>();
        original.accept(new ClassVisitor(Opcodes.ASM9){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){originalMethods.add(n+d);return null;}},0);
        original.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return n.equals("registerControllers")?null:super.visitMethod(a,n,d,s,e);}
            @Override public void visitEnd(){
                donor.accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                        if(!n.equals("registerControllers"))return null;count[0]++;
                        return new MethodVisitor(Opcodes.ASM9,writer.visitMethod(a,n,d,s,e)){
                            @Override public void visitInvokeDynamicInsn(String name,String desc,Handle bootstrap,Object... values){
                                for(Object value:values)if(value instanceof Handle h&&h.getOwner().equals(original.getClassName())&&!originalMethods.contains(h.getName()+h.getDesc()))throw new IllegalStateException("Missing installed animation predicate "+h);
                                super.visitInvokeDynamicInsn(name,desc,bootstrap,values);
                            }
                        };
                    }
                },0);super.visitEnd();
            }
        },0);
        if(count[0]!=1)throw new IllegalStateException("Wrong controller registration count");
        Files.write(Path.of(args[2]),writer.toByteArray());
    }
}
