import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/** Narrow adapter for an installed pre-Arcana forge; retains its original runtime dependencies. */
public final class PatchWarriorForge {
    private static final String RECIPE="com/tnc/tnc/production/ManaForgeRecipe";
    private static final String FORGE="com/tnc/tnc/production/MagicForgeBlockEntity.class";
    private static final String PREVIEW="(Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;";
    private static final String ASSEMBLE="(Lnet/minecraft/world/Container;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;";
    public static void main(String[] args)throws Exception {
        byte[] original,recipe;
        try(var old=new ZipFile(args[0]);var built=new ZipFile(args[1])){
            original=old.getInputStream(old.getEntry(FORGE)).readAllBytes();
            recipe=built.getInputStream(built.getEntry(RECIPE+".class")).readAllBytes();
        }
        String[] assembleName={null};
        new ClassReader(recipe).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                if(d.equals(ASSEMBLE)){if(assembleName[0]!=null)throw new IllegalStateException("Ambiguous assembly method");assembleName[0]=n;}return null;
            }
        },ClassReader.SKIP_CODE);
        if(assembleName[0]==null)throw new IllegalStateException("Missing assembly method");
        int[] changes={0},existing={0};var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                var delegate=super.visitMethod(a,n,d,s,e);
                return new MethodVisitor(Opcodes.ASM9,delegate){
                    @Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
                        if(owner.equals(RECIPE)&&desc.equals(ASSEMBLE)&&name.equals(assembleName[0])&&n.equals("tickForge"))existing[0]++;
                        if(owner.equals(RECIPE)&&desc.equals(PREVIEW)&&n.equals("tickForge")){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitInsn(Opcodes.SWAP);
                            super.visitMethodInsn(op,owner,assembleName[0],ASSEMBLE,itf);changes[0]++;
                        }else super.visitMethodInsn(op,owner,name,desc,itf);
                    }
                };
            }
        },0);
        if(changes[0]+existing[0]!=1)throw new IllegalStateException("Expected exactly one forge output call; found "+changes[0]+" preview and "+existing[0]+" assembly calls");
        var output=Path.of(args[2]);Files.createDirectories(output.getParent());Files.write(output,changes[0]==0?original:writer.toByteArray());
        System.out.println(changes[0]==0?"Existing dynamic forge adapter verified and preserved byte for byte.":"Patched exactly one forge output call to "+assembleName[0]+ASSEMBLE);
    }
}
