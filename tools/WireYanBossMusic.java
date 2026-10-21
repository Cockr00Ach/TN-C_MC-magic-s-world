import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.*;

/** Patch only the existing live music ID, preserving concurrent controller fixes. */
public final class WireYanBossMusic {
    public static void main(String[] args) throws Exception {
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        ClassWriter writer = new ClassWriter(reader, 0);
        int[] changed = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc,
                    String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, desc, signature, exceptions);
                if (!name.equals("<clinit>")) return base;
                return new MethodVisitor(Opcodes.ASM9, base) {
                    @Override public void visitLdcInsn(Object value) {
                        if ("minecraft".equals(value)) { value = "tnc"; changed[0]++; }
                        else if ("music.dragon".equals(value)) {
                            value = "music.boss.yan_crywolf"; changed[0]++;
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        }, 0);
        if (changed[0] != 2) throw new IllegalStateException("Unexpected music initializer: " + changed[0]);
        Files.write(Path.of(args[1]), writer.toByteArray());
    }
}
