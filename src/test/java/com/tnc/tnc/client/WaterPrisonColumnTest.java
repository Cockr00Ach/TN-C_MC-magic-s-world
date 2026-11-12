package com.tnc.tnc.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 水牢的**罗马圣柱**（作者 2026-11-10：「水牢的隔板再改改吧，我想做成一个个罗马圣柱那样，
 * 你可以先做个圣柱的建模，然后弄成蓝色贴图再虚化」）。
 *
 * <p>为什么要专门写一个测试：这三根柱子是**贴图模型** ✗ —— 只要
 * <ol>
 *   <li>模型名不在 {@link WaterSolidModels#MODELS} 里（⇒ 不进烘焙 ⇒ 游戏里柱子隐形 ✗），</li>
 *   <li>贴图路径写错（⇒ 整根柱子变成黑白格子 ✗），</li>
 *   <li>贴图忘了生成（⇒ 同上 ✗）</li>
 * </ol>
 * 之一出问题，<b>编译照样过</b> ✗，只有进游戏才看得出来 ✗ —— 而这三件事都能在构建期查 ✓
 * （本项目"实体没渲染器就卡退"那条坑已经用同样的办法拦了三次 ✓）。
 */
class WaterPrisonColumnTest {

    private static final String TEXTURE = "tnc:block/roman_column_blue";

    private static JsonObject load(String name) throws Exception {
        String path = "/assets/tnc/models/water/" + name + ".json";
        try (InputStream in = WaterPrisonColumnTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "模型没打进资源：" + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void theColumnPartsAreRegisteredAndShipped() throws Exception {
        for (String name : List.of("roman_column_base", "roman_column_shaft", "roman_column_capital",
                "roman_column_ornament")) {
            assertTrue(WaterSolidModels.MODELS.contains(name),
                    name + " 不在 WaterSolidModels.MODELS 里 ⇒ 模型不进烘焙 ⇒ 游戏里这块是隐形的 ✗");
            JsonObject model = load(name);
            JsonArray elements = model.getAsJsonArray("elements");
            assertTrue(elements.size() >= 3, name + " 的元素太少（不像一根柱子）：" + elements.size());
            JsonObject textures = model.getAsJsonObject("textures");
            assertTrue(textures.has("particle"), name + " 缺 particle 贴图（缺失时整块模型会渲染成 missingno ✗）");
            assertEquals(TEXTURE, textures.get("particle").getAsString(),
                    name + " 的贴图应该指向这张蓝色石纹：" + TEXTURE);
        }
    }

    @Test
    void thePartsStackWithoutGapsOrOverlaps() throws Exception {
        // 三段必须"接着"：柱础 0..baseTop，柱身 0..16（渲染时按高度拉长 ✓），柱头 0..capTop
        JsonArray base = load("roman_column_base").getAsJsonArray("elements");
        JsonArray shaft = load("roman_column_shaft").getAsJsonArray("elements");
        JsonArray capital = load("roman_column_capital").getAsJsonArray("elements");
        double baseTop = maxY(base), shaftTop = maxY(shaft), capTop = maxY(capital);
        assertTrue(baseTop > 4.5D && baseTop < 8.0D, "柱础总高应该在 5~8 px（0.3~0.5 格）：" + baseTop);
        assertEquals(16.0D, shaftTop, 0.01D, "柱身必须是整整 16 px（= 1 格，渲染时按高度拉伸 ✓）");
        assertTrue(capTop > 4.5D && capTop < 8.0D, "柱头总高应该在 5~8 px：" + capTop);
        // 三段都要以柱子轴线 (x=z=8) 为中心 ✓ —— 偏了接起来就是歪的 ✗
        for (JsonArray part : List.of(base, shaft, capital)) {
            double cx = (minX(part) + maxX(part)) / 2.0D;
            double cz = (minZ(part) + maxZ(part)) / 2.0D;
            assertEquals(8.0D, cx, 0.75D, "元素没有绕柱子轴线居中（x）");
            assertEquals(8.0D, cz, 0.75D, "元素没有绕柱子轴线居中（z）");
        }
        // 柱础要比柱身粗、柱头又是最宽的（罗马柱的读法：座 < 身？不，座和顶板都更宽 ✓）
        assertTrue(width(base) > width(shaft) * 1.3D, "柱础要比柱身宽（不然柱脚看不出来 ✗）");
        assertTrue(width(capital) > width(shaft) * 1.3D, "柱头的顶板要比柱身宽（罗马柱头的方板 ✗）");
        // "精致"的落点：柱头浮雕必须真的有一批元素（叶 / 涡卷 / 花 ✓）
        JsonArray ornament = load("roman_column_ornament").getAsJsonArray("elements");
        assertTrue(ornament.size() >= 8, "柱头浮雕元素太少（不精致 ✗）：" + ornament.size());
    }

    @Test
    void theBlueTextureIsActuallyBlueAndOpaque() throws Exception {
        String path = "/assets/tnc/textures/block/roman_column_blue.png";
        try (InputStream in = WaterPrisonColumnTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "贴图没生成：" + path);
            BufferedImage image = ImageIO.read(in);
            assertEquals(64, image.getWidth(), "贴图宽要是 64（五个 16×16 的格子 ✓）");
            assertEquals(64, image.getHeight(), "贴图高要是 64");
            long blue = 0, red = 0, opaque = 0, total = 0;
            // 四个真正被模型用到的格子：柱身 / 浅石 / 金饰 / 深石 ✓
            // ★ 金饰那一格**故意是暖色**（R>G ✗），所以"必须偏蓝"只对三个石面格检查 ✓
            int[][] cells = {{0, 0}, {16, 0}, {32, 0}, {0, 16}};
            for (int[] cell : cells) {
                boolean goldCell = cell[0] == 32 && cell[1] == 0;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int argb = image.getRGB(cell[0] + x, cell[1] + y);
                        int a = (argb >>> 24) & 0xFF;
                        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                        total++;
                        if (a >= 200) opaque++;
                        assertTrue(a > 0, "用到的贴图格里不许有全透明像素（会漏出后面的东西 ✗）");
                        if (goldCell) {
                            assertTrue(r > g && g > b, "金饰那一格应该是暖色：" + r + "," + g + "," + b);
                            continue;
                        }
                        red += r;
                        blue += b;
                        assertTrue(g >= r, "蓝色石纹不该偏红：" + r + "," + g + "," + b);
                    }
                }
            }
            assertTrue(opaque > total * 0.95D, "贴图应该基本不透明（'虚化'是渲染时降 alpha 做的 ✗）");
            assertTrue(blue > red * 1.8D, "石面整体得真的是蓝的：R=" + red + " B=" + blue);
        }
    }

    @Test
    void aColonnadeUsesFarFewerColumnsThanTheOldPanels() {
        for (double radius : new double[]{5, 7, 12, 18, 26}) {
            int columns = WaterGeometry.columnCount(radius);
            int panels = WaterGeometry.panelCount(radius);
            assertTrue(columns >= 8 && columns <= 28, "柱子个数越界（半径 " + radius + " ⇒ " + columns + "）");
            assertTrue(columns < panels,
                    "柱子必须比隔板少（半径 " + radius + "：" + columns + " vs " + panels
                            + "）✗ —— 挤那么近就又变成一堵墙了 ✗");
            double chord = 2 * radius * Math.sin(Math.PI / columns);
            assertTrue(chord > 2.6D, "柱距太小（半径 " + radius + " ⇒ " + chord + " 格），读不出'柱廊'✗");
            // 粗细：既不能细成旗杆，也不能粗成水塔（见 WaterGeometry#columnDiameter ✓）
            double diameter = WaterGeometry.columnDiameter(radius, 7.0 + radius * 0.75, columns);
            assertTrue(diameter >= 0.55D && diameter <= 2.6D, "柱子粗细越界：" + diameter);
            assertTrue(diameter < chord, "柱子比柱距还粗，会互相穿模：" + diameter + " vs " + chord);
        }
    }

    @Test
    void theWholeColonnadeStaysWithinItsFaceBudget() throws Exception {
        // 一圈 22 根 × (柱础 + 柱身 + 柱头) 个方块 × 6 面 —— 水牢最长要挂 24 秒 ✗
        int base = load("roman_column_base").getAsJsonArray("elements").size();
        int shaft = load("roman_column_shaft").getAsJsonArray("elements").size();
        int capital = load("roman_column_capital").getAsJsonArray("elements").size();
        int ornament = load("roman_column_ornament").getAsJsonArray("elements").size();
        int columns = WaterGeometry.columnCount(26);
        int farFaces = (base + shaft + capital) * 6 * columns;
        // 近处：假设身边有 6 根带浮雕（18 格内 ✓），其余按远的算
        int nearFaces = farFaces + ornament * 6 * Math.min(6, columns);
        System.out.println("TN-C water cost: prison colonnade = " + columns + " columns, far=" + farFaces
                + " quads, near=" + nearFaces + " quads (ornament " + ornament + " boxes x6)");
        // 老的"隔板"是 48 片 × 一个 ~20 块的模型 ⇒ 更贵 ✗
        int oldFaces = WaterGeometry.panelCount(26) * 20 * 6;
        assertTrue(farFaces < oldFaces, "柱廊应该比老隔板省（" + farFaces + " vs " + oldFaces + " 面）");
        assertTrue(farFaces <= 3_600, "远处柱廊面数超预算：" + farFaces);
        assertTrue(nearFaces <= 5_400, "近处柱廊（含柱头浮雕）面数超预算：" + nearFaces);
        assertTrue(nearFaces < oldFaces, "就算画满浮雕也不该比老隔板贵（" + nearFaces + " vs " + oldFaces + "）");
    }

    private static double maxY(JsonArray parts) {
        double max = 0;
        for (int i = 0; i < parts.size(); i++) {
            max = Math.max(max, parts.get(i).getAsJsonObject().getAsJsonArray("to").get(1).getAsDouble());
        }
        return max;
    }

    private static double minX(JsonArray parts) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < parts.size(); i++) {
            min = Math.min(min, parts.get(i).getAsJsonObject().getAsJsonArray("from").get(0).getAsDouble());
        }
        return min;
    }

    private static double maxX(JsonArray parts) {
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < parts.size(); i++) {
            max = Math.max(max, parts.get(i).getAsJsonObject().getAsJsonArray("to").get(0).getAsDouble());
        }
        return max;
    }

    private static double minZ(JsonArray parts) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < parts.size(); i++) {
            min = Math.min(min, parts.get(i).getAsJsonObject().getAsJsonArray("from").get(2).getAsDouble());
        }
        return min;
    }

    private static double maxZ(JsonArray parts) {
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < parts.size(); i++) {
            max = Math.max(max, parts.get(i).getAsJsonObject().getAsJsonArray("to").get(2).getAsDouble());
        }
        return max;
    }

    private static double width(JsonArray parts) {
        return maxX(parts) - minX(parts);
    }
}
