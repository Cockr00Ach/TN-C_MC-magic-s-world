import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Preserve the actual sampling bytecode, replacing only Minecraft's heavy environment. */
public class ExtractQuakeSampling implements Opcodes {
    public static void main(String[] args) throws Exception {
        String owner = "com/tnc/tnc/boss/fire/client/SlamGroundWave";
        Path output = Path.of(args[1]);
        try (JarFile jar = new JarFile(args[0])) {
            ClassNode original = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(owner + ".class"))).accept(original, 0);
            ClassWriter writer = new ClassWriter(0);
            writer.visit(V17, ACC_PUBLIC, owner, null, "java/lang/Object", null);
            String buffers = "net/minecraft/client/renderer/MultiBufferSource$BufferSource";
            writer.visitField(ACC_PRIVATE | ACC_STATIC, "BUFFERS", "L" + buffers + ";", null, null).visitEnd();
            MethodVisitor init = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            init.visitCode(); init.visitTypeInsn(NEW, buffers); init.visitInsn(DUP);
            init.visitMethodInsn(INVOKESPECIAL, buffers, "<init>", "()V", false);
            init.visitFieldInsn(PUTSTATIC, owner, "BUFFERS", "L" + buffers + ";");
            init.visitInsn(RETURN); init.visitMaxs(2, 0); init.visitEnd();
            for (String nested : List.of("$Patch", "$1")) {
                var entry = jar.getJarEntry(owner + nested + ".class");
                if (entry == null) continue;
                writer.visitNestMember(owner + nested);
                Path target = output.resolve(owner + nested + ".class");
                Files.createDirectories(target.getParent());
                Files.copy(jar.getInputStream(entry), target, StandardCopyOption.REPLACE_EXISTING);
            }
            int copied = 0;
            for (MethodNode method : original.methods) {
                if (Set.of("sample", "sideIndex").contains(method.name)
                        || (method.name.equals("drawPatch") && method.desc.contains("PoseStack"))) {
                    method.accept(writer);
                    copied++;
                }
            }
            if (copied < 1) throw new AssertionError("No production sample method");
            writer.visitEnd();
            Path target = output.resolve(owner + ".class");
            Files.createDirectories(target.getParent());
            Files.write(target, writer.toByteArray());
            System.out.println("Extracted unchanged production sampling bytecode");
        }
    }
}
