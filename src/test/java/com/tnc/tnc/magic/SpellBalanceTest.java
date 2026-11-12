package com.tnc.tnc.magic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 施法手感（瞬发 / 蓄力）与冷却的**规则闸门**。
 *
 * <p>作者 2026-11-07：「调平衡吧数值冷却时间这块，什么技能该瞬发，什么技能该蓄力，你考虑一下」。
 *
 * <p>这一轮调的是 140 个法术 JSON 里的两个数：{@code cast.duration}（读条秒数）和
 * {@code cost.cooldown_duration}（冷却秒数）。数值本身写在
 * {@code tools/spell_balance.py} 的曲线表里（那份表就是设计稿），这里只钉住**规则**，
 * 免得以后有人手滑把某条链又改成"t1 和 t5 一样长"：
 *
 * <ol>
 *   <li><b>瞬发是特权</b>：{@code cast == 0} 只能是位移/逃命和光线 t1~t3 ——
 *       新加一个瞬发法术必须来这里登记，否则红。</li>
 *   <li><b>同一根链逐级变重</b>：读条和冷却都不能"越高级越快"（{@link #NO_RAMP} /
 *       {@link #NO_CD_RAMP} 里的是有理由的例外）。</li>
 *   <li><b>高级攻击要读条</b>：t5 攻击类读条不得低于 {@link #MIN_T5_CAST} 秒。</li>
 *   <li><b>冷却有上下限</b>：0.5 秒 ~ 180 秒。</li>
 *   <li><b>蓝耗只有一个来源</b>：引擎的 {@code cost.exhaust} 必须全是 0，
 *       真正的蓝耗走魔法石（{@code Config.manaCostForTier}）。</li>
 * </ol>
 */
class SpellBalanceTest {

    /** 内置资源（跟着 jar 走）。 */
    private static final Path JAR_SPELLS = Path.of("src/main/resources/data/tnc/spells");
    /** 数据包镜像（跟着整合包走）。 */
    private static final Path PACK_SPELLS = Path.of(
            "modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc/spells");

    /** 允许瞬发（读条 0 秒）的法术，按理由分组。 */
    private static final Map<String, String> INSTANT = new TreeMap<>();

    static {
        // 位移 / 逃命：反应类，晚 0.1 秒就没意义了
        INSTANT.put("blink_1", "闪现一档");
        INSTANT.put("blink_2", "闪现二档");
        INSTANT.put("blink_3", "闪现三档");
        INSTANT.put("lightning_blink", "雷速链的瞬移");
        // 光线：指哪打哪的射线，前三级瞬发，威力和冷却靠"一锤定音"平衡
        INSTANT.put("light_beam", "光线一档");
        INSTANT.put("great_light_beam", "光线二档");
        INSTANT.put("giant_light_beam", "光线三档");
    }

    /** 读条**刻意**不是逐级递增的链（链内混了不同定位的法术）。 */
    private static final Set<String> NO_RAMP = Set.of("speed");

    /** 冷却**刻意**不是逐级递增的链。 */
    private static final Set<String> NO_CD_RAMP = Set.of("speed", "light_mirror");

    /** 攻击类链：t5 必须有真正的读条（不然就是"点一下全屏炸"）。 */
    private static final Set<String> ATTACK_CHAINS = Set.of(
            "primary", "orb", "ball", "ray", "thunder_seal", "gale",
            "earth_shot", "earth_move", "earth_mud", "earth_ring",
            "water_ball", "water_wave", "water_bind", "water_rain",
            "dark_fog", "dark_hand", "dark_summon", "light_grace", "light_summon",
            "wind_ball", "wind_weapon");

    private static final double MIN_T5_CAST = 1.1D;
    private static final double CD_MIN = 0.5D;
    private static final double CD_MAX = 180D;

    /** 一个法术的施法手感。 */
    private record Spell(String id, String chain, int tier, double cast, double cooldown, double exhaust) {
    }

    // ------------------------------------------------------------------

    @Test
    void theWholeSpellBookLoads() throws IOException {
        List<Spell> all = loadAll();
        assertTrue(all.size() >= 130, "法术总数不该缩水，现在是 " + all.size());
        for (Spell s : all) {
            assertTrue(s.tier() >= 1 && s.tier() <= 5, s.id() + " 的档位越界: " + s.tier());
            assertTrue(s.cast() >= 0D, s.id() + " 读条是负数");
            assertTrue(s.cooldown() >= CD_MIN && s.cooldown() <= CD_MAX,
                    s.id() + " 冷却越界: " + s.cooldown());
        }
    }

    /** 瞬发只能给位移和光线；反过来，登记过的瞬发法术必须真的瞬发（防止白名单烂掉）。 */
    @Test
    void onlyEscapeSpellsAreInstant() throws IOException {
        List<Spell> all = loadAll();
        for (Spell s : all) {
            if (s.cast() == 0D) {
                assertNotNull(INSTANT.get(s.id()),
                        "「" + s.id() + "」是瞬发，但它是攻击/场/召唤 —— 要么给它读条，"
                                + "要么来 SpellBalanceTest.INSTANT 登记理由");
            }
        }
        for (String id : INSTANT.keySet()) {
            Spell s = all.stream().filter(x -> x.id().equals(id)).findFirst()
                    .orElseThrow(() -> new AssertionError("白名单里的「" + id + "」已经不存在了"));
            assertEquals(0D, s.cast(), 1e-6,
                    "「" + id + "」登记为瞬发（" + INSTANT.get(id) + "），但读条不是 0");
        }
    }

    /** 同一条链里，读条不能"越高级越快"（不能一律相等，至少不能倒挂）。 */
    @Test
    void castingWeightDoesNotGoBackwardsWithTier() throws IOException {
        for (Map.Entry<String, List<Spell>> e : byChain().entrySet()) {
            if (NO_RAMP.contains(e.getKey())) {
                continue;
            }
            List<Spell> chain = new ArrayList<>(e.getValue());
            chain.sort(Comparator.comparingInt(Spell::tier));
            for (int i = 1; i < chain.size(); i++) {
                Spell prev = chain.get(i - 1);
                Spell cur = chain.get(i);
                assertTrue(cur.tier() != prev.tier(), e.getKey() + " 同一档里有两个法术");
                assertTrue(cur.cast() >= prev.cast() - 1e-9,
                        e.getKey() + "：t" + prev.tier() + "「" + prev.id() + "」读条 " + prev.cast()
                                + " 秒，t" + cur.tier() + "「" + cur.id() + "」却只要 " + cur.cast()
                                + " 秒 —— 高级招不该比低级招更快");
            }
        }
    }

    /** 同一条链里，冷却必须严格变长（否则高级招在冷却上白嫖）。 */
    @Test
    void cooldownsGrowWithTier() throws IOException {
        for (Map.Entry<String, List<Spell>> e : byChain().entrySet()) {
            if (NO_CD_RAMP.contains(e.getKey()) || e.getValue().size() < 3) {
                continue;
            }
            List<Spell> chain = new ArrayList<>(e.getValue());
            chain.sort(Comparator.comparingInt(Spell::tier));
            for (int i = 1; i < chain.size(); i++) {
                Spell prev = chain.get(i - 1);
                Spell cur = chain.get(i);
                assertTrue(cur.cooldown() > prev.cooldown() + 1e-9,
                        e.getKey() + "：t" + prev.tier() + "「" + prev.id() + "」冷却 " + prev.cooldown()
                                + " 秒，t" + cur.tier() + "「" + cur.id() + "」只有 " + cur.cooldown()
                                + " 秒 —— 高级招的冷却必须更长");
            }
        }
    }

    /** 五档攻击法术的顶档必须有读条（"点一下就全屏"是这轮要修的病）。 */
    @Test
    void topTierAttacksActuallyChargeUp() throws IOException {
        for (Spell s : loadAll()) {
            if (s.tier() != 5 || !ATTACK_CHAINS.contains(s.chain())) {
                continue;
            }
            assertTrue(s.cast() >= MIN_T5_CAST,
                    "t5 攻击「" + s.id() + "」（" + s.chain() + "）读条只有 " + s.cast()
                            + " 秒 —— 顶档大招至少要 " + MIN_T5_CAST + " 秒的蓄力");
        }
    }

    /** 蓝耗只认魔法石那一套：引擎的 exhaustion 一律清零。 */
    @Test
    void theEngineNoLongerChargesItsOwnExhaustion() throws IOException {
        for (Spell s : loadAll()) {
            assertEquals(0D, s.exhaust(), 1e-9,
                    "「" + s.id() + "」还留着引擎 exhaustion " + s.exhaust()
                            + " —— 蓝耗应该只走魔法石（Config.manaCostForTier）");
        }
    }

    /** 同一根链的每个档位恰好一个法术（学习/替换规则依赖这一点）。 */
    @Test
    void eachChainHasOneSpellPerTier() throws IOException {
        for (Map.Entry<String, List<Spell>> e : byChain().entrySet()) {
            Map<Integer, Integer> count = new TreeMap<>();
            for (Spell s : e.getValue()) {
                count.merge(s.tier(), 1, Integer::sum);
            }
            for (Map.Entry<Integer, Integer> t : count.entrySet()) {
                assertEquals(1, t.getValue(),
                        e.getKey() + " 的 t" + t.getKey() + " 有 " + t.getValue() + " 个法术");
            }
        }
    }

    // ------------------------------------------------------------------

    private static List<Spell> loadAll() throws IOException {
        List<Spell> all = new ArrayList<>();
        for (Path dir : List.of(JAR_SPELLS, PACK_SPELLS)) {
            assertTrue(Files.isDirectory(dir), "找不到法术目录: " + dir.toAbsolutePath());
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    String text = Files.readString(p, StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(text).getAsJsonObject();
                    String id = p.getFileName().toString().replace(".json", "");
                    JsonObject cast = root.has("cast") ? root.getAsJsonObject("cast") : new JsonObject();
                    JsonObject cost = root.has("cost") ? root.getAsJsonObject("cost") : new JsonObject();
                    JsonObject learn = root.has("learn") ? root.getAsJsonObject("learn") : new JsonObject();
                    all.add(new Spell(id,
                            root.has("group") && !root.get("group").isJsonNull()
                                    ? root.get("group").getAsString() : "",
                            learn.has("tier") ? learn.get("tier").getAsInt() : 1,
                            cast.has("duration") ? cast.get("duration").getAsDouble() : 0D,
                            cost.has("cooldown_duration") ? cost.get("cooldown_duration").getAsDouble() : 0D,
                            cost.has("exhaust") ? cost.get("exhaust").getAsDouble() : 0D));
                }
            }
        }
        return all;
    }

    private static Map<String, List<Spell>> byChain() throws IOException {
        Map<String, List<Spell>> map = new LinkedHashMap<>();
        for (Spell s : loadAll()) {
            map.computeIfAbsent(s.chain(), k -> new ArrayList<>()).add(s);
        }
        return map;
    }
}
