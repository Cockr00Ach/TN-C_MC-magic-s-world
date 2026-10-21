import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.*;

/** Replace only the boss-music method in the currently installed controller. */
public final class WireBossMusicMethod {
    public static void main(String[] args) throws Exception {
        ClassReader original = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        ClassReader compiled = new ClassReader(Files.readAllBytes(Path.of(args[1])));
        ClassWriter output = new ClassWriter(original, 0);
        String method = "tickBossMusic";
        int[] counts = {0, 0};
        original.accept(new ClassVisitor(Opcodes.ASM9, output) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc,
                    String signature, String[] exceptions) {
                if (name.equals(method)) { counts[0]++; return null; }
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
            @Override public void visitEnd() {
                compiled.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String desc,
                            String signature, String[] exceptions) {
                        if (!name.equals(method)) return null;
                        counts[1]++;
                        return output.visitMethod(access, name, desc, signature, exceptions);
                    }
                }, 0);
                super.visitEnd();
            }
        }, 0);
        if (counts[0] != 1 || counts[1] != 1) throw new IllegalStateException("Unexpected method count");
        Files.write(Path.of(args[2]), output.toByteArray());
    }
}
