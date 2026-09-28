import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * 把作者任务书（ysjxmodel 的 YsjxQuestIntegration）**从字节码层面删掉**。
 *
 * <h2>为什么不用 KubeJS 覆盖</h2>
 * 作者的任务在两处：① 220 个 json 数据；② {@code YsjxQuestIntegration.class} 里
 * **硬编码 139 处 registerQuest() 调用**。删 json 没用（代码会重注册）；
 * 用 enabled:false 覆盖去压住则要永远留着 219 个补丁文件，且刷新池里仍是作者的任务。
 *
 * <h2>这一刀切在哪</h2>
 * {@code YsjxModel} 构造时只调 {@code registerQuests()}（注册 2 个目标处理器，**保留**）；
 * 任务书本体全在 {@code registerNativeChapters()}（注册章节 + 139 处 registerQuest）。
 * ⇒ 把该方法体清成单条 {@code RETURN}。NPC / 对话 / 实体代码不受影响。
 *
 * <h2>踩过的坑</h2>
 * 第一版只拦了 {@code visitEnd}（以为不转发就丢掉了整段体），结果 stub 的
 * {@code visitCode→RETURN} 和**原指令一起**被写出去 —— 方法体反而没变。
 * 正确做法：stub 必须把**所有指令访客**都变成空操作，只在 visitCode 里放 RETURN。
 *
 * <p>用法：java -cp &lt;asm.jar&gt;;. ClearAuthorQuestbook &lt;in.jar&gt; &lt;out.jar&gt;
 */
public class ClearAuthorQuestbook {

    static final String TARGET = "com/example/ysjxmodel/quest/YsjxQuestIntegration";
    static final String METHOD = "registerNativeChapters";
    static final String DESC = "()V";

    public static void main(String[] args) throws Exception {
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int patched = 0, entries = 0;

        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(in));
             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(out))) {
            ZipEntry e;
            byte[] buf = new byte[1 << 16];
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                entries++;
                byte[] data;
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    int n;
                    while ((n = zin.read(buf)) > 0) bos.write(buf, 0, n);
                    data = bos.toByteArray();
                }
                if (name.equals(TARGET + ".class")) {
                    byte[] p = patch(data);
                    if (p != null) { data = p; patched++; }
                }
                ZipEntry ne = new ZipEntry(name);
                ne.setTime(e.getTime());
                zout.putNextEntry(ne);
                zout.write(data);
                zout.closeEntry();
            }
        }
        System.out.println("entries=" + entries + " patched=" + patched);
        System.out.println("out=" + out + " (" + Files.size(out) + " bytes)");
    }

    static byte[] patch(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        boolean[] done = {false};
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                if (!name.equals(METHOD) || !desc.equals(DESC)) return mv;
                done[0] = true;
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    boolean emitted;
                    @Override public void visitCode() {
                        super.visitCode();               // 转发（声明帧等）
                        if (!emitted) { emitted = true; super.visitInsn(Opcodes.RETURN); }
                    }
                    // ★ 关键：以下指令访客一律**不转发** —— 原方法体就此丢弃
                    @Override public void visitInsn(int op) { }
                    @Override public void visitIntInsn(int op, int v) { }
                    @Override public void visitVarInsn(int op, int v) { }
                    @Override public void visitTypeInsn(int op, String t) { }
                    @Override public void visitFieldInsn(int op, String o, String n, String d) { }
                    @Override public void visitMethodInsn(int op, String o, String n, String d, boolean itf) { }
                    @Override public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... bsmArgs) { }
                    @Override public void visitJumpInsn(int op, Label l) { }
                    @Override public void visitLabel(Label l) { }
                    @Override public void visitLdcInsn(Object c) { }
                    @Override public void visitIincInsn(int v, int inc) { }
                    @Override public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) { }
                    @Override public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) { }
                    @Override public void visitMultiANewArrayInsn(String d, int dims) { }
                    @Override public void visitFrame(int type, int nLocal, Object[] l, int nStack, Object[] s) { }
                    @Override public void visitTryCatchBlock(Label s, Label e, Label h, String t) { }
                    @Override public void visitLocalVariable(String n, String d, String s, Label a, Label b, int i) { }
                    @Override public void visitLineNumber(int line, Label start) { }
                    @Override public void visitMaxs(int maxStack, int maxLocals) {
                        super.visitMaxs(0, 0);
                    }
                };
            }
        }, 0);
        return done[0] ? cw.toByteArray() : null;
    }
}
