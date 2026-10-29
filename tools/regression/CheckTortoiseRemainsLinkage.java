import java.io.InputStream;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Resolve final runtime invocations, including Minecraft methods owned by the mod subclass. */
public final class CheckTortoiseRemainsLinkage {
    private static final String REMAINS = "com/tnc/tnc/boss/fire/TortoiseRemainsEntity";
    private static final Map<String, ClassNode> CLASSES = new HashMap<>();
    private static final List<JarFile> JARS = new ArrayList<>();
    private static ClassNode load(String name) throws Exception {
        if (CLASSES.containsKey(name)) return CLASSES.get(name);
        for (JarFile jar : JARS) {
            JarEntry entry = jar.getJarEntry(name + ".class");
            if (entry != null) {
                ClassNode node = new ClassNode();
                try (InputStream in = jar.getInputStream(entry)) {
                    new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
                CLASSES.put(name, node);
                return node;
            }
        }
        if (name.startsWith("java/")) {
            try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
                if (in != null) {
                    ClassNode node = new ClassNode();
                    new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    CLASSES.put(name, node);
                    return node;
                }
            }
        }
        CLASSES.put(name, null);
        return null;
    }
    private static boolean resolve(String owner, String name, String descriptor, Set<String> visited) throws Exception {
        if (owner == null || !visited.add(owner)) return false;
        ClassNode node = load(owner);
        if (node == null) return false;
        if (node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor))) return true;
        if (name.equals("<init>")) return false;
        if (resolve(node.superName, name, descriptor, visited)) return true;
        for (String parent : node.interfaces) if (resolve(parent, name, descriptor, visited)) return true;
        return false;
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Final mod jar, actual Minecraft SRG jar, optional runtime dependency jars");
        try {
            for (String path : args) JARS.add(new JarFile(path));
            JarFile mod = JARS.get(0);
            int checked = 0;
            List<String> failures = new ArrayList<>();
            for (JarEntry entry : Collections.list(mod.entries())) {
                if (!entry.getName().startsWith("com/tnc/tnc/boss/fire/") || !entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                try (InputStream in = mod.getInputStream(entry)) { new ClassReader(in).accept(node, 0); }
                for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals(REMAINS)) {
                        checked++;
                        if (!resolve(call.owner, call.name, call.desc, new HashSet<>())) {
                            failures.add(node.name + "." + method.name + " -> " + call.owner + "." + call.name + call.desc);
                        }
                    }
                }
            }
            if (checked < 5) throw new AssertionError("Expected remains creation, positioning, identity and render calls; only " + checked);
            for (String failure : failures) System.err.println("MISSING runtime method: " + failure);
            if (!failures.isEmpty()) throw new AssertionError(failures.size() + " unresolved remains method references");
            System.out.println("PASS " + checked + " remains method references resolve against the actual Minecraft runtime hierarchy");
        } finally {
            for (JarFile jar : JARS) jar.close();
        }
    }
}
