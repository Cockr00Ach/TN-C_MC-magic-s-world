import java.nio.file.*;
import org.objectweb.asm.*;

/** Replace only the typed recursive-render method; preserve every other installed renderer method. */
public final class WireCanglanRendererResync {
    private static boolean chosen(String name,String descriptor){return name.equals("renderRecursively")&&descriptor.contains("Lcom/tnc/tnc/world/wind/CanglanWarlordEntity;");}
    public static void main(String[] args)throws Exception{
        var original=new ClassReader(Files.readAllBytes(Path.of(args[0])));var donor=new ClassReader(Files.readAllBytes(Path.of(args[1])));
        var writer=new ClassWriter(original,ClassWriter.COMPUTE_MAXS);int[] count={0};
        original.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return chosen(n,d)?null:super.visitMethod(a,n,d,s,e);}
            @Override public void visitEnd(){
                donor.accept(new ClassVisitor(Opcodes.ASM9){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){if(!chosen(n,d))return null;count[0]++;return writer.visitMethod(a,n,d,s,e);}},0);
                super.visitEnd();
            }
        },0);
        if(count[0]!=1)throw new IllegalStateException("Wrong rendered method count: "+count[0]);Files.write(Path.of(args[2]),writer.toByteArray());
    }
}
