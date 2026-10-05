package com.tnc.tnc.magic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>「法术里写了一个不是粒子的粒子 id ⇒ 客户端闪退」的离线闸门</b> ✓
 * （2026-10-09 真事故：作者报"<b>释放黑雾怎么游戏闪退了</b>"）。
 *
 * <h2>事故是什么</h2>
 * 黑雾五档第一版用了 {@code block_factorys_bosses:ink_fog} / {@code :fog_1}。
 * 那两个 id 在**那个 mod 的资源里真的有 json、也有贴图** ✓ —— 但它们**不是** Minecraft 粒子：
 * 那个 mod 把它们注册成了 <b>Bedrock 粒子</b>（{@code registerBedrockParticle(String)}）✗。
 * 于是 {@code BuiltInRegistries.PARTICLE_TYPE} 查不到、Forge 返回它自己的对象，
 * 引擎照惯例强转：
 * <pre>
 * java.lang.ClassCastException: class net.unusual.block_factorys_bosses.init.BossesRiseParticleTypes$1
 *   cannot be cast to class net.minecraft.core.particles.ParticleOptions
 *     at net.spell_engine.particle.ParticleHelper.convertToInstructions(ParticleHelper.java:146)
 *     at net.spell_engine.client.ClientNetwork.lambda$initializeHandlers$3(ClientNetwork.java:25)
 * </pre>
 * 关键在于它抛在**客户端读 {@code spell_engine:particle_effects} 包**的时候 ✗ ⇒
 * netty 通道直接死掉 ⇒ 游戏掉回主菜单（一次日志里 298 次）✗。
 *
 * <h2>为什么本地没炸、只有客户端炸</h2>
 * 同一 tick 里 {@code ParticleHelper.play:121} 也报了 "Failed to play particle batch"
 * ——那是**本地**那条路（它自己 catch 了）✓；而**网络包**那条路没人 catch ✗ ⇒ 只有联机/单人
 * 客户端的收包线程会死 ✓。所以"服务端日志看起来只是 WARN"这件事本身也是陷阱 ✗。
 *
 * <h2>这条测试怎么把关</h2>
 * 把 jar 与整合包两份法术 JSON 里出现的**每一个 {@code particle_id}** 收集起来 ✓，
 * 然后要求它满足下面之一：
 * <ul>
 *   <li>{@code minecraft:} / 无命名空间（原版，一定是真粒子）✓；</li>
 *   <li>在 {@link #VERIFIED_SAFE} 名单里 —— 这些是**用 javap 逐个查过各自 mod 的注册表**、
 *       确认是 {@code RegistryObject<SimpleParticleType>}（或别的
 *       {@code ParticleType<... extends ParticleOptions>}）的 id ✓。</li>
 * </ul>
 * 名单外的**直接构建失败** ✗，并告诉你"加之前先用 javap 确认它是 ParticleType"。
 *
 * <p>★ 这条闸门想固定的**教训**：<b>"assets/&lt;ns&gt;/particles/&lt;id&gt;.json 存在"证明不了任何事</b> ✗
 * —— 能不能当粒子用，只看那个 mod 有没有把它注册成 {@code ParticleType} ✓。
 */
class SpellParticleSafetyTest {

    /** 本仓库的工作区根（测试的工作目录就是项目根 ✓，`build.gradle` 里没改过）。 */
    private static final Path SPELL_DIRS_FROM_ROOT = Path.of("src/main/resources/data/tnc/spells");

    /** 整合包那一份（可能不存在，比如在队友机器上还没 sync 过）。 */
    private static final String PACK_SPELL_GLOB = "modpack/*/kubejs/data/tnc/spells";

    /**
     * <b>逐个 javap 验证过</b>的第三方粒子 id（2026-10-09）✓。
     *
     * <p>加新 id 之前必须做同一件事：
     * <pre>
     * javap -p -classpath &lt;那个 mod 的 jar&gt; &lt;它的粒子注册类&gt;
     * #   然后确认那一行是 RegistryObject&lt;SimpleParticleType&gt;（或 ParticleType&lt;...&gt;）✓
     * </pre>
     * 只看到 {@code assets/<ns>/particles/<id>.json} **不算数** ✗（这正是 ink_fog 的坑 ✓）。
     */
    private static final Set<String> VERIFIED_SAFE = Set.of(
            // spell_engine: 引擎自己的粒子，引擎到处都在用 ✓
            "spell_engine:electric_arc_a",
            "spell_engine:electric_arc_b",
            "spell_engine:electric_spark",
            "spell_engine:smoke_medium",
            "spell_engine:weakness_smoke",
            // soulslike-weaponry -> net.soulsweaponry.registry.ParticleRegistry:
            //   RegistryObject<SimpleParticleType> BLACK_FLAME ✓
            "soulsweapons:black_flame",
            // fromtheshadows -> net.sonmok14...registry.ParticleRegistry:
            //   RegistryObject<SimpleParticleType> BLOOD / SHADOW ✓
            "fromtheshadows:blood",
            "fromtheshadows:shadow",
            // block_factorys_bosses: 这个 mod 绝大多数粒子是 **Bedrock 粒子**（不能用 ✗）,
            // 只有少数几个是真正的 SimpleParticleType —— soul_flip 是查过的那一个 ✓。
            // ⚠ 绝对不要加 ink_fog / fog_1 / mist_cloud / soul_cloud / soul_smoke ✗
            "block_factorys_bosses:soul_flip"
    );

    @Test
    void everyParticleIdIsARealParticleType() throws Exception {
        List<Path> files = new ArrayList<>();
        collect(SPELL_DIRS_FROM_ROOT, files);
        // the pack copy of the kubejs spells (may be absent on a teammate's machine)
        Path packRoot = Path.of("modpack");
        if (Files.isDirectory(packRoot)) {
            try (java.nio.file.DirectoryStream<Path> packs = Files.newDirectoryStream(packRoot)) {
                for (Path pack : packs) {
                    collect(pack.resolve("kubejs/data/tnc/spells"), files);
                }
            }
        }
        assertFalse(files.isEmpty(), "没找到任何法术 JSON ✗（目录结构变了？）");

        Set<String> ids = new LinkedHashSet<>();
        for (Path file : files) {
            collectParticleIds(JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8)), ids);
        }
        assertFalse(ids.isEmpty(), "一个 particle_id 都没读到 ✗");

        Set<String> unsafe = new LinkedHashSet<>();
        for (String id : ids) {
            if (id.startsWith("minecraft:") || !id.contains(":")) {
                continue;                                   // 原版：一定是真粒子 ✓
            }
            if (!VERIFIED_SAFE.contains(id)) {
                unsafe.add(id);
            }
        }
        assertTrue(unsafe.isEmpty(),
                "这些粒子 id 不在\"已验证\"名单里 ✗ —— 它们可能不是 ParticleType"
                        + "（assets 里有 json 也证明不了 ✗，block_factorys_bosses:ink_fog 就是这么"
                        + "把客户端打崩的）。先 javap 确认，再把它加进 VERIFIED_SAFE："
                        + unsafe);
    }

    private static void collect(Path dir, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".json")).forEach(out::add);
        }
    }

    private static void collectParticleIds(JsonElement node, Set<String> out) {
        if (node == null || node.isJsonNull()) {
            return;
        }
        if (node.isJsonArray()) {
            JsonArray array = node.getAsJsonArray();
            for (JsonElement child : array) {
                collectParticleIds(child, out);
            }
            return;
        }
        if (!node.isJsonObject()) {
            return;
        }
        JsonObject object = node.getAsJsonObject();
        JsonElement pid = object.get("particle_id");
        if (pid != null && pid.isJsonPrimitive()) {
            out.add(pid.getAsString());
        }
        for (String key : object.keySet()) {
            collectParticleIds(object.get(key), out);
        }
    }
}
