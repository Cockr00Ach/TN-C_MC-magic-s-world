import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class ExtractSpawnMethods implements Opcodes {
    static final String BOSS = "com/tnc/tnc/boss/fire/VolcanicTortoiseEntity";
    static final String MONSTER = "net/minecraft/world/entity/monster/Monster";
    static final String ACCESSOR = "net/minecraft/network/syncher/EntityDataAccessor";
    static final String CLOCK = "com/tnc/tnc/boss/fire/TortoiseDeathClock";
    static void accessor(MethodVisitor m, String name, boolean longValue, int value) {
        m.visitTypeInsn(NEW, ACCESSOR);m.visitInsn(DUP);
        if(longValue){m.visitLdcInsn((long)value);m.visitMethodInsn(INVOKESTATIC,"java/lang/Long","valueOf","(J)Ljava/lang/Long;",false);}
        else{m.visitLdcInsn(value);m.visitMethodInsn(INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);}
        m.visitMethodInsn(INVOKESPECIAL,ACCESSOR,"<init>","(Ljava/lang/Object;)V",false);
        m.visitFieldInsn(PUTSTATIC,BOSS,name,"L"+ACCESSOR+";");
    }
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[1]);Files.createDirectories(output.resolve("com/tnc/tnc/boss/fire"));
        ClassNode original=new ClassNode();
        try(JarFile jar=new JarFile(args[0])){
            new ClassReader(jar.getInputStream(jar.getJarEntry(BOSS+".class"))).accept(original,0);
            var entry=jar.getJarEntry(CLOCK+".class");
            if(entry!=null)Files.copy(jar.getInputStream(entry),output.resolve(CLOCK+".class"),StandardCopyOption.REPLACE_EXISTING);
            else {
                if(args.length<3)throw new IllegalArgumentException("Input patch lacks TortoiseDeathClock; supply dependency Mojmap jar");
                try(JarFile dependency=new JarFile(args[2])){
                    Files.copy(dependency.getInputStream(dependency.getJarEntry(CLOCK+".class")),output.resolve(CLOCK+".class"),StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        writer.visit(V17,ACC_PUBLIC,BOSS,null,MONSTER,null);
        for(String name:List.of("DATA_DEATH_AGE","DATA_TRANSITION_START","DATA_PHASE"))
            writer.visitField(ACC_PRIVATE|ACC_STATIC|ACC_FINAL,name,"L"+ACCESSOR+";",null,null).visitEnd();
        writer.visitField(ACC_PRIVATE|ACC_FINAL,"clientDeathClock","L"+CLOCK+";",null,null).visitEnd();
        writer.visitField(ACC_PRIVATE,"serverDeathTicks","I",null,null).visitEnd();
        MethodVisitor m=writer.visitMethod(ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
        accessor(m,"DATA_DEATH_AGE",false,-1);accessor(m,"DATA_TRANSITION_START",true,-1);accessor(m,"DATA_PHASE",false,1);
        m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();
        m=writer.visitMethod(ACC_PUBLIC,"<init>","(Z)V",null,null);m.visitCode();
        m.visitVarInsn(ALOAD,0);m.visitVarInsn(ILOAD,1);m.visitMethodInsn(INVOKESPECIAL,MONSTER,"<init>","(Z)V",false);
        m.visitVarInsn(ALOAD,0);m.visitTypeInsn(NEW,CLOCK);m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,CLOCK,"<init>","()V",false);m.visitFieldInsn(PUTFIELD,BOSS,"clientDeathClock","L"+CLOCK+";");
        m.visitVarInsn(ALOAD,0);m.visitInsn(ICONST_M1);m.visitFieldInsn(PUTFIELD,BOSS,"serverDeathTicks","I");
        m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();
        Set<String> wanted=Set.of("isDyingAnimation","isTransitioning","setHealth","deathAge","onSyncedDataUpdated");
        int count=0;
        for(MethodNode method:original.methods)if(wanted.contains(method.name)){method.accept(writer);count++;}
        if(count!=5)throw new AssertionError("Expected 5 real production methods, got "+count);
        // This hook models early superclass callbacks without copying or rewriting production methods.
        m=writer.visitMethod(ACC_PUBLIC,"probeDuringConstruction","()V",null,null);m.visitCode();
        m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETSTATIC,BOSS,"DATA_DEATH_AGE","L"+ACCESSOR+";");m.visitMethodInsn(INVOKEVIRTUAL,BOSS,"onSyncedDataUpdated","(L"+ACCESSOR+";)V",false);
        m.visitVarInsn(ALOAD,0);m.visitVarInsn(ALOAD,0);m.visitInsn(FCONST_0);m.visitMethodInsn(INVOKEVIRTUAL,BOSS,"deathAge","(F)F",false);m.visitFieldInsn(PUTFIELD,MONSTER,"constructorAge","F");
        m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();
        writer.visitEnd();Files.write(output.resolve(BOSS+".class"),writer.toByteArray());
        System.out.println("Extracted 5 unmodified production method bodies from "+args[0]);
    }
}
