import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;

/** Preserve installed shared classes, including Apollo's separately installed boss bar. */
public final class WireLightMirrorInstall {
    public static void main(String[] args) throws Exception {
        var original=new ClassReader(Files.readAllBytes(Path.of(args[1])));
        var donor=new ClassReader(Files.readAllBytes(Path.of(args[2])));
        var output=new ClassWriter(original,ClassWriter.COMPUTE_MAXS);
        String mode=args[0];
        Set<String> targets=mode.equals("apollo")?Set.of("beginAcolyteSummon","m_8107_","startCast","applySpell","m_7380_","m_7378_"):
                mode.equals("catalog")?Set.of("<clinit>"):Set.of();
        Set<String> fields=new HashSet<>();int[] injected={0},pathSlot={-1};boolean[] getter={false},bar={false};
        if(mode.equals("apollo"))original.accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                if(!n.equals("m_8107_"))return null;
                return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int o,String owner,String name,String desc,boolean itf){if(name.equals("tickBossBar"))bar[0]=true;}};
            }
        },0);
        if(mode.equals("catalog")) {
            Set<String> old=new HashSet<>(),fresh=new HashSet<>();
            for(var pair:List.of(Map.entry(original,old),Map.entry(donor,fresh)))pair.getKey().accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                    if(!n.equals("<clinit>"))return null;
                    return new MethodVisitor(Opcodes.ASM9){@Override public void visitLdcInsn(Object v){if(v instanceof String str)pair.getValue().add(str);}};
                }
            },0);
            if(!fresh.containsAll(old))throw new IllegalStateException("Donor would drop installed catalog entries: "+old.stream().filter(v->!fresh.contains(v)).toList());
        }
        original.accept(new ClassVisitor(Opcodes.ASM9,output){
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){fields.add(n);return super.visitField(a,n,d,s,v);}
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                if(targets.contains(n))return null;
                var visitor=super.visitMethod(a,n,d,s,e);
                if(!mode.equals("cast")||!n.equals("onSpellCast"))return visitor;
                return new MethodVisitor(Opcodes.ASM9,visitor){
                    @Override public void visitVarInsn(int opcode,int var){if(getter[0]&&opcode==Opcodes.ASTORE){pathSlot[0]=var;getter[0]=false;}super.visitVarInsn(opcode,var);}
                    @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf){
                        if(owner.equals("com/tnc/tnc/light/LightMirrorSpells"))throw new IllegalStateException("Cast hook already installed");
                        if(owner.equals("net/minecraft/resources/ResourceLocation")&&desc.equals("()Ljava/lang/String;"))getter[0]=true;
                        if(owner.equals("com/tnc/tnc/magic/ManaGate")&&name.equals("noteCastHappened")){
                            if(pathSlot[0]<0)throw new IllegalStateException("No validated spell-path local");
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,pathSlot[0]);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"com/tnc/tnc/light/LightMirrorSpells","cast","(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)V",false);injected[0]++;
                        }
                        super.visitMethodInsn(opcode,owner,name,desc,itf);
                    }
                };
            }
            @Override public void visitEnd(){
                donor.accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){
                        if(mode.equals("apollo")&&Set.of("SUMMON_ACOLYTES","acolyteCooldown").contains(n)&&!fields.contains(n))return output.visitField(a,n,d,s,v);
                        return null;
                    }
                    @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                        if(!targets.contains(n))return null;
                        var visitor=output.visitMethod(a,n,d,s,e);injected[0]++;
                        if(!mode.equals("apollo")||!n.equals("m_8107_")||!bar[0])return visitor;
                        return new MethodVisitor(Opcodes.ASM9,visitor){boolean added;
                            @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf){
                                if(!added&&name.equals("m_6084_")&&desc.equals("()Z")){
                                    // On server after the donor's client-side early return; leave its operand stack intact.
                                    super.visitVarInsn(Opcodes.ALOAD,0);
                                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"com/tnc/tnc/boss/TNApolloEntity","tickBossBar","()V",false);added=true;
                                }
                                super.visitMethodInsn(opcode,owner,name,desc,itf);
                            }
                            @Override public void visitEnd(){if(!added)throw new IllegalStateException("Boss bar was not preserved");super.visitEnd();}
                        };
                    }
                },0);
                super.visitEnd();
            }
        },0);
        int expected=mode.equals("apollo")?6:1;
        if(injected[0]!=expected)throw new IllegalStateException("Wrong patched method count: "+injected[0]);
        Files.write(Path.of(args[3]),output.toByteArray());
    }
}
