package com.tnc.tnc.light;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 光龙 t2「<b>三条龙头对尾绕成一个圆</b>」的离线自检 ✓（作者 2026-10-03 ✓）—— 不用进游戏 ✓。
 *
 * <h2>验什么</h2>
 * <ol>
 *   <li><b>动画的形状</b>：把 {@code dragon.animation.json} 里 {@code orbit} 的每一节角度按骨链累加起来 ✓，
 *       再把整条身体当成一串"梗节"摆到平面上 ✓ —— 每个关节都必须落在**同一个圆**上 ✓
 *       （偏差 &lt; 1 单位 ≈ 1.6 毫米 ✓）；</li>
 *   <li><b>半径对不对</b>：那个圆的半径必须正好 {@code 体长 × 3 / 2π} ✓
 *       （= {@link TNDragonOrbitMath#ringRadius} ✓）；</li>
 *   <li><b>首尾能不能接上</b>：鼻尖到尾尖在圆上必须正好跨 {@code 360/3 = 120°} ✓
 *       （差一点 ⇒ 三条龙围起来就会"差一截 / 叠一截" ✗）。</li>
 * </ol>
 *
 * <p>★ 这就是"我看不到画面"能做的部分 ✓：几何对了，进游戏看到的才可能是三条龙咬着尾巴围成一圈 ✓。
 */
class DragonOrbitRingTest {

    /** 从鼻子到尾巴的骨链（body1 是"身体切线 = 实体朝向"的那一节 ✓ = 环上的参考点 ✓）。 */
    private static final String[] CHAIN = {
            "head", "neck2", "neck1", "body1",
            "body2", "body3", "body4", "body5", "body6", "body7",
            "body8", "body9", "body10", "body11", "body12",
            "tail1", "tail2", "tail3", "tail4", "tail5", "tailFin"
    };

    /** 环绕时用几条（= 圆心角 360/3 = 120° ✓）。 */
    private static final int COUNT = 3;

    private static int indexOf(String bone) {
        for (int i = 0; i < CHAIN.length; i++) {
            if (CHAIN[i].equals(bone)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 骨头在层级里的父级 ✓ —— 注意颈链是 body1 的**子**级（物理上却在前面 ✓）⇒
     * head 的父级是 neck2、neck2 的父级是 neck1、neck1 的父级是 body1、body1 的父级是 root ✓。
     */
    private static String parentOf(String bone) {
        return switch (bone) {
            case "head" -> "neck2";
            case "neck2" -> "neck1";
            case "neck1" -> "body1";
            case "body1" -> null;                       // root
            default -> {
                int i = indexOf(bone);
                yield i <= 0 ? null : CHAIN[i - 1];
            }
        };
    }

    private static JsonObject readJson(String path) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
    }

    /**
     * 累加朝向（= 骨头自己在世界里的方向 ✓）—— <b>必须按层级递归</b> ✗：
     * 颈链（head/neck2/neck1）的父级在数组里排在**后面** ✓，
     * 顺着数组下标算会读到"还没算出来的 0" ✗（第一版就踩了这个坑：整条脖子少转了 16° ✗）。
     */
    private static double accumulated(int i, double[] rot, double[] acc, boolean[] done) {
        if (done[i]) {
            return acc[i];
        }
        done[i] = true;                                  // 先占坑，反正图里没有环 ✓
        String parent = parentOf(CHAIN[i]);
        acc[i] = rot[i] + (parent == null ? 0.0D : accumulated(indexOf(parent), rot, acc, done));
        return acc[i];
    }

    private static JsonArray geoBones() throws Exception {
        return readJson("src/main/resources/assets/tnc/geo/entity/dragon.geo.json")
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");
    }

    private static JsonObject orbitBones() throws Exception {
        JsonObject animations = readJson("src/main/resources/assets/tnc/animations/entity/dragon.animation.json")
                .getAsJsonObject("animations");
        assertNotNull(animations.get("dash"), "dash 动画被弄丢了 ✗");
        assertNotNull(animations.get("idle"), "idle 动画被弄丢了 ✗");
        JsonObject orbit = animations.getAsJsonObject("orbit");
        assertNotNull(orbit, "没有 orbit 动画 ✗（跑 tools/gen_dragon_orbit.ps1 ✓）");
        return orbit.getAsJsonObject("bones");
    }

    /** 模型体长必须跟 {@link TNDragonOrbitMath#BODY_LENGTH_BLOCKS} 对得上 ✓（改模型就要改那个常数 ✓）。 */
    @Test
    void modelLengthMatchesTheConstant() throws Exception {
        double[] range = zRange();
        double lengthBlocks = (range[1] - range[0]) / 16.0D;
        assertEquals(TNDragonOrbitMath.BODY_LENGTH_BLOCKS, lengthBlocks, 0.01D,
                "模型体长变了 ⇒ 改 TNDragonOrbitMath.BODY_LENGTH_BLOCKS 并重跑 tools/gen_dragon_orbit.ps1");
    }

    @Test
    void orbitCurvesTheBodyIntoExactlyOneThirdOfACircle() throws Exception {
        JsonObject bones = orbitBones();

        // ---- 1) 从 geo 量出骨链每一节的长度（顶点 = 鼻尖 + 所有 pivot（去重）+ 尾尖 ✓）----
        Map<String, Double> pivotZ = new LinkedHashMap<>();
        TreeSet<Double> uniq = new TreeSet<>();
        for (var element : geoBones()) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            if (indexOf(name) < 0) {
                continue;   // 腿 / 角 / 须 / 下巴那些不在骨链上 ✓（它们的 pivot 不参与"梗节"划分 ✓）
            }
            double z = bone.getAsJsonArray("pivot").get(2).getAsDouble();
            pivotZ.put(name, z);
            uniq.add(z);
        }
        double[] range = zRange();
        List<Double> verts = new ArrayList<>();
        verts.add(range[0]);
        verts.addAll(uniq);
        verts.add(range[1]);
        assertEquals(CHAIN.length + 1, verts.size(), "顶点数应该正好是骨链节数 + 1 ✓");
        for (String bone : CHAIN) {
            assertTrue(pivotZ.containsKey(bone), "geo 里没有骨头 " + bone + " ✗");
        }

        int n = CHAIN.length;
        double[] len = new double[n];
        double[] uMid = new double[n];
        double total = verts.get(verts.size() - 1) - verts.get(0);
        for (int i = 0; i < n; i++) {
            len[i] = verts.get(i + 1) - verts.get(i);
            uMid[i] = (verts.get(i) + verts.get(i + 1)) / 2.0D - verts.get(0);
        }

        // ---- 2) 读动画里每一节的角度，按层级累加 ----
        double[] rot = new double[n];
        for (int i = 0; i < n; i++) {
            assertTrue(bones.has(CHAIN[i]), "orbit 动画少了 " + CHAIN[i] + " ✗");
            rot[i] = bones.getAsJsonObject(CHAIN[i]).getAsJsonArray("rotation").get(1).getAsDouble();
        }
        double[] acc = new double[n];
        boolean[] done = new boolean[n];
        for (int i = 0; i < n; i++) {
            accumulated(i, rot, acc, done);
        }

        // ---- 3) 跟公式对账（允许整体镜像 = 环绕方向反过来 ✓，那是 config 里一个 ±1 的事 ✓）----
        double perUnit = TNDragonOrbitMath.arcDegrees(COUNT) / total;
        double curlU = TNDragonOrbitMath.CURL_CENTER_FROM_NOSE_BLOCKS * 16.0D;
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            String parent = parentOf(CHAIN[i]);
            double parentAcc = parent == null ? 0.0D
                    : (curlU - uMid[indexOf(parent)]) * perUnit;
            raw[i] = (curlU - uMid[i]) * perUnit - parentAcc;
        }
        // 整体正负（body2 一定不是 0 ✓）—— 镜像过的动画等价于把环绕方向反过来 ✓，同样成立 ✓
        int probe = indexOf("body2");
        double sign = Math.signum(rot[probe] * raw[probe]);
        assertTrue(Math.abs(sign) > 0.5D, "orbit 动画的角度全是 0 ✗（没弯曲，围不成圆 ✗）");
        for (int i = 0; i < n; i++) {
            assertEquals(raw[i] * sign, rot[i], 0.05D,
                    CHAIN[i] + " 的角度跟公式对不上 ✗（跑 tools/gen_dragon_orbit.ps1 重新生成 ✓）");
        }

        // ---- 4) 把身体摆到平面上：每个关节都必须在同一个圆上 ----
        double[] px = new double[n + 1];
        double[] pz = new double[n + 1];
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(acc[i]);
            px[i + 1] = px[i] + Math.sin(a) * len[i];
            pz[i + 1] = pz[i] + Math.cos(a) * len[i];
        }
        double[] circle = fitCircle(px, pz);
        double cx = circle[0];
        double cz = circle[1];
        double fitR = circle[2];
        double maxDev = 0.0D;
        for (int i = 0; i <= n; i++) {
            maxDev = Math.max(maxDev, Math.abs(Math.hypot(px[i] - cx, pz[i] - cz) - fitR));
        }

        double wantUnits = TNDragonOrbitMath.ringRadius(1.0D, COUNT) * 16.0D;
        assertEquals(wantUnits, fitR, wantUnits * 0.01D, "拟合出来的圆半径跟 ringRadius() 对不上 ✗");
        assertTrue(maxDev < 1.0D,
                "有关节不在圆上（最大偏差 " + maxDev + " 单位 = " + (maxDev / 16.0D) + " 格）✗");

        // ---- 5) 首尾要正好跨 120°（三条才能咬成一整圈 ✓）----
        double noseAngle = Math.toDegrees(Math.atan2(pz[0] - cz, px[0] - cx));
        double tailAngle = Math.toDegrees(Math.atan2(pz[n] - cz, px[n] - cx));
        double span = Math.abs(noseAngle - tailAngle);
        if (span > 180.0D) {
            span = 360.0D - span;
        }
        assertEquals(TNDragonOrbitMath.arcDegrees(COUNT), span, 0.5D,
                "鼻尖到尾尖没跨满 120° ⇒ 三条龙围起来会差一截 / 叠一截 ✗");
    }

    /**
     * <b>几何</b>最小二乘拟合圆 ✓ —— 返回 {@code {圆心x, 圆心z, 半径}} ✓。
     *
     * <p>★ 别用 Kasa（"代数距离"）那一套 ✗：它在一块**只有 120° 的弧**上会把半径算大 1.8% ✗
     * （我第一版就是这么写的，测试直接报 176.9 vs 173.8 ✗）—— 这里老老实实做
     * Gauss-Newton（残差 = {@code |p−c| − R} ✓，偏导 = {@code −单位向量} 和 {@code −1} ✓）。
     */
    private static double[] fitCircle(double[] xs, double[] zs) {
        int n = xs.length;
        double cx = 0.0D;
        double cz = 0.0D;
        for (int i = 0; i < n; i++) {
            cx += xs[i];
            cz += zs[i];
        }
        cx /= n;
        cz /= n;
        double radius = 0.0D;
        for (int iter = 0; iter < 40; iter++) {
            double suu = 0.0D;
            double suv = 0.0D;
            double svv = 0.0D;
            double su = 0.0D;
            double sv = 0.0D;
            double sf = 0.0D;
            double sfu = 0.0D;
            double sfv = 0.0D;
            radius = 0.0D;
            for (int i = 0; i < n; i++) {
                radius += Math.hypot(xs[i] - cx, zs[i] - cz);
            }
            radius /= n;
            for (int i = 0; i < n; i++) {
                double dx = xs[i] - cx;
                double dz = zs[i] - cz;
                double r = Math.hypot(dx, dz);
                if (r < 1.0E-9D) {
                    continue;
                }
                double u = dx / r;      // ∂r/∂cx = −u
                double v = dz / r;      // ∂r/∂cz = −v
                double f = r - radius;
                suu += u * u;
                suv += u * v;
                svv += v * v;
                su += u;
                sv += v;
                sf += f;
                sfu += f * u;
                sfv += f * v;
            }
            // [suu suv su; suv svv sv; su sv n] · [δcx; δcz; δR] = [sfu; sfv; sf]
            double[][] m = {
                    {suu, suv, su, sfu},
                    {suv, svv, sv, sfv},
                    {su, sv, n, sf}
            };
            double[] delta = solve3(m);
            cx += delta[0];
            cz += delta[1];
            radius += delta[2];
            if (Math.abs(delta[0]) + Math.abs(delta[1]) + Math.abs(delta[2]) < 1.0E-9D) {
                break;
            }
        }
        return new double[] {cx, cz, radius};
    }

    /** 解一个 3×3 的增广方程组（列主元高斯消元 ✓）。 */
    private static double[] solve3(double[][] m) {
        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int row = col + 1; row < 3; row++) {
                if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) {
                    pivot = row;
                }
            }
            double[] tmp = m[col];
            m[col] = m[pivot];
            m[pivot] = tmp;
            for (int row = 0; row < 3; row++) {
                if (row == col || Math.abs(m[col][col]) < 1.0E-12D) {
                    continue;
                }
                double f = m[row][col] / m[col][col];
                for (int k = col; k < 4; k++) {
                    m[row][k] -= f * m[col][k];
                }
            }
        }
        return new double[] {m[0][3] / m[0][0], m[1][3] / m[1][1], m[2][3] / m[2][2]};
    }

    /** geo 里所有方块合起来的 z 范围（鼻尖, 尾尖 ✓）。 */
    private static double[] zRange() throws Exception {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (var element : geoBones()) {
            JsonObject bone = element.getAsJsonObject();
            if (!bone.has("cubes")) {
                continue;
            }
            for (var cubeElement : bone.getAsJsonArray("cubes")) {
                JsonArray origin = cubeElement.getAsJsonObject().getAsJsonArray("origin");
                JsonArray size = cubeElement.getAsJsonObject().getAsJsonArray("size");
                double z0 = origin.get(2).getAsDouble();
                double z1 = z0 + size.get(2).getAsDouble();
                min = Math.min(min, Math.min(z0, z1));
                max = Math.max(max, Math.max(z0, z1));
            }
        }
        return new double[] {min, max};
    }
}
