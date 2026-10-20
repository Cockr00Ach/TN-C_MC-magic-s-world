import java.nio.file.*;
import org.objectweb.asm.*;

/** Add only the dungeon DeferredRegister hook to an installed TNMod, retaining other live fixes. */
public final class WireDungeonMobs {
    private static final String TARGET="com/tnc/tnc/dungeon/DungeonMobRegistration";
    public static void main(String[] args) throws Exception {
        byte[] bytes=Files.readAllBytes(Path.of(args[0]));ClassReader input=new ClassReader(bytes);
        if(!input.getClassName().equals("com/tnc/tnc/TNMod"))throw new IllegalArgumentException("Expected TNMod");
        int[] existing={0};input.accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
                    if(owner.equals(TARGET)&&name.equals("register"))existing[0]++;
                }};
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(existing[0]>1)throw new IllegalStateException("Duplicate dungeon registration hooks");
        if(existing[0]==1){Files.write(Path.of(args[1]),bytes);System.out.println("Already wired; original class unchanged");return;}
        ClassWriter output=new ClassWriter(input,ClassWriter.COMPUTE_MAXS);int[] hooks={0};
        input.accept(new ClassVisitor(Opcodes.ASM9,output){
            @Override public MethodVisitor visitMethod(int a,String name,String d,String sig,String[] ex){
                MethodVisitor method=super.visitMethod(a,name,d,sig,ex);if(!name.equals("<init>"))return method;
                return new MethodVisitor(Opcodes.ASM9,method){
                    @Override public void visitMethodInsn(int op,String owner,String n,String desc,boolean itf){
                        super.visitMethodInsn(op,owner,n,desc,itf);
                        if(owner.equals("net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext")&&n.equals("getModEventBus")&&desc.equals("()Lnet/minecraftforge/eventbus/api/IEventBus;")){
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,TARGET,"register","(Lnet/minecraftforge/eventbus/api/IEventBus;)V",false);hooks[0]++;
                        }
                    }
                };
            }
        },0);
        if(hooks[0]!=1)throw new IllegalStateException("Expected one event-bus acquisition; got "+hooks[0]);
        Files.write(Path.of(args[1]),output.toByteArray());System.out.println("Added one dungeon registration hook; live class retained");
    }
}
