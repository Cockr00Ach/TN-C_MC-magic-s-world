import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Verify method stack/local types without initializing Minecraft or modifying any archive. */
public final class VerifyLightMirrorInstall {
    public static void main(String[] args) throws Exception {
        int classes=0,methods=0;
        try(var jar=new JarFile(args[0])) {
            for(var entry:java.util.Collections.list(jar.entries())) {
                String name=entry.getName();
                if(!name.endsWith(".class")||!(name.startsWith("com/tnc/tnc/light/")
                    ||name.startsWith("com/tnc/tnc/bosshud/")
                    ||name.startsWith("com/tnc/tnc/inkshader/")
                    ||name.startsWith("com/tnc/tnc/world/ink/InkRealmClientEffects")
                    ||name.startsWith("com/tnc/tnc/world/ink/client/InkDragonB")
                    ||name.equals("com/tnc/tnc/world/ink/InkDragonVertexPacking.class")
                    ||name.equals("com/tnc/tnc/world/ink/mixin/InkSkyHeightMixin.class")
                    ||name.equals("com/tnc/tnc/world/wind/CanglanWarlordCombat.class")
                    ||name.equals("com/tnc/tnc/world/wind/CanglanWarGodRules.class")
                    ||name.equals("com/tnc/tnc/world/wind/client/CanglanCinematicCamera.class")
                    ||name.startsWith("com/tnc/tnc/world/wind/CanglanExecution")
                    ||name.startsWith("com/tnc/tnc/world/wind/CanglanCinematicNetwork")
                    ||name.startsWith("com/tnc/tnc/world/wind/client/CanglanExecution")
                    ||name.equals("com/tnc/tnc/world/wind/client/CanglanWarlordModel.class")
                    ||name.equals("com/tnc/tnc/world/wind/client/CanglanWarlordRenderer.class")
                    ||name.equals("com/tnc/tnc/world/wind/CanglanTestCommands.class")
                    ||name.equals("com/tnc/tnc/world/wind/CanglanWarlordEntity.class")
                    ||name.equals("com/tnc/tnc/magic/TnSpellMechanics.class")
                    ||name.equals("com/tnc/tnc/magic/SpellCatalog.class")
                    ||name.equals("com/tnc/tnc/boss/TNApolloEntity.class")))continue;
                var node=new ClassNode();try(var input=jar.getInputStream(entry)){new ClassReader(input).accept(node,0);}
                for(var method:node.methods)if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){
                    try {new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name,method);}
                    catch(Exception failure){throw new IllegalStateException(name+" "+method.name+method.desc,failure);}
                    methods++;
                }
                classes++;
            }
        }
        System.out.println("Verified "+classes+" classes and "+methods+" methods; no Minecraft registry initialization.");
    }
}
