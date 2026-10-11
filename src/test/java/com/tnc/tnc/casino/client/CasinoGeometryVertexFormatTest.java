package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>「赌场机器一渲染就崩游戏」的离线闸门</b> ✓ —— 2026-10-08 那次真崩之后补的 ✗。
 *
 * <h2>崩的是什么</h2>
 * 实机崩报 {@code crash-2026-10-08_20.47.58-client.txt} ✓：
 * <pre>
 * Description: Rendering Block Entity
 * java.lang.IllegalStateException: Not filled all elements of the vertex
 *   at com.mojang.blaze3d.vertex.BufferBuilder.endVertex(BufferBuilder.java:264)
 *   at com.tnc.tnc.casino.client.TNCasinoGeometry.box(TNCasinoGeometry.java:94)
 *   at com.tnc.tnc.casino.client.TNSlotMachineRenderer.render(TNSlotMachineRenderer.java:164)
 * </pre>
 * 根因：{@code TNCasinoGeometry.box} 把顶点按 {@code POSITION_COLOR} 那样发
 * （position / color / overlay / uv2 / normal ✓）<b>却漏了 uv0</b> ✗，
 * 而缓冲是 {@code RenderType.entityCutoutNoCull} ⇒ <b>实体顶点格式</b>，
 * 本来就<b>要求 uv0</b> ✗。
 *
 * <h2>为什么"编译过 + verify 过"却照样崩</h2>
 * {@code BufferBuilder} 的每个元素方法（{@code color/uv/overlayCoords/uv2/normal}）
 * 都会先比对自己是不是"当前该填的那个元素"，不是就<b>静默 return</b> ✗ ——
 * 少发一个元素时<b>不报错、不提示</b> ✗，只在最后 {@code endVertex()} 抛一次异常 ✗。
 * 这类"静默失效"正是本项目最贵的一类 bug ✓。
 *
 * <h2>这条测试怎么把关</h2>
 * 不去解析源码文本 ✗，而是<b>真把几何跑一遍</b> ✓，用
 * {@link StrictFormatVertexConsumer} 在离线环境复刻 {@code BufferBuilder} 的两条行为 ——
 * ① 元素必须<b>按格式顺序</b>发（顺序不对就静默丢弃 ✗）；
 * ② {@code endVertex()} 时元素必须发满，否则抛（就是崩报里那一句 ✗）。
 *
 * <p>★ 期望的格式<b>不写死元素清单</b> ✓ —— 直接读
 * {@code DefaultVertexFormat.NEW_ENTITY}（实体渲染类型用的就是它 ✓，
 * 已核 {@code RenderType} 里那一串实体工厂全部读同一个字段 ✓），
 * 再把它的元素逐个翻译成"该调哪个 {@code VertexConsumer} 方法" ✓。
 * 于是"漏发 uv0"以及"顺序写错"这两件事<b>在 {@code gradlew test} 阶段就红</b> ✓，
 * 不必等作者进游戏崩一次 ✗。
 */
class CasinoGeometryVertexFormatTest {

    /**
     * 实体顶点格式 ✓ —— 就是 {@code RenderType.entityCutoutNoCull} 用的那一份
     * （{@code DefaultVertexFormat.NEW_ENTITY} ✓）。
     */
    private static VertexFormat entityFormat() {
        return DefaultVertexFormat.NEW_ENTITY;
    }

    /** 真实顶点链：{@code vertex → color → uv → overlayCoords → uv2 → normal → endVertex} ✓。 */
    private static final List<String> GEOMETRY_SEQUENCE =
            List.of("position", "color", "uv0", "overlay", "uv2", "normal");

    /** 一个长方体 = 6 面 × 4 角 ✓。 */
    private static final int VERTICES_PER_BOX = 6 * 4;

    /**
     * 把一个顶点格式元素翻译成"该调哪个 {@code VertexConsumer} 方法" ✓。
     *
     * <p>{@code VertexFormatElement.getUsage()} 只把三套 uv 都归成 {@code UV} ✗，
     * 所以再按 {@code getIndex()} 分开 ✓：{@code 0} = {@code .uv(float,float)}（uv0 ✓）、
     * {@code 1} = {@code .overlayCoords(int,int)}（overlay ✓）、
     * {@code 2} = {@code .uv2(int)}（lightmap ✓）——
     * 这正是原版实体顶点格式的排法 ✓，也正是
     * {@code TNDragonModel} / {@code TNMagicCircleRenderer} / {@code TNLightningStrikeRenderer} /
     * {@code TNFireBoltRenderer} / {@code TNLightBeamRenderer} / {@code TNLightWingsModel}
     * 六处已经在跑的代码实际发的顺序 ✓。
     */
    private static String methodFor(VertexFormatElement element) {
        return switch (element.getUsage()) {
            case POSITION -> "position";
            case COLOR -> "color";
            case NORMAL -> "normal";
            case UV -> uvMethod(element);
            case PADDING -> "padding";
            case GENERIC -> "generic" + element.getIndex();
        };
    }

    /** 三套 uv 靠下标分开 ✓（见 {@link #methodFor} 的说明 ✓）。 */
    private static String uvMethod(VertexFormatElement element) {
        return switch (element.getIndex()) {
            case 0 -> "uv0";
            case 1 -> "overlay";
            default -> "uv2";
        };
    }

    /** 实体顶点格式要求的元素序列 ✓（读出来、不写死 ✓）。 */
    private static List<String> expectedSequence() {
        List<String> out = new ArrayList<>();
        for (VertexFormatElement element : entityFormat().getElements()) {
            out.add(methodFor(element));
        }
        return out;
    }

    /**
     * 闸门的<b>地基</b> ✓ —— 先证明"读出来的实体格式"就是
     * {@code TNCasinoGeometry} 实际走的那条链 ✓。
     *
     * <p>这一条红了，下面的断言就全都失去意义 ✗
     * （说明格式取错了，或者那套格式换了元素 ✓ —— 换格式 = 顶点元素一个都不能省 ✗）。
     */
    @Test
    void theEntityFormatMatchesTheChainTheGeometryEmits() {
        List<String> actual = expectedSequence();
        // padding 是格式自带的占位对齐 ✓，顶点链从不显式发它 ✗ ⇒ 比之前先摘掉 ✓
        List<String> supplied = actual.stream().filter(label -> !"padding".equals(label)).toList();
        assertEquals(GEOMETRY_SEQUENCE, supplied,
                "实体顶点格式的元素序列与 TNCasinoGeometry 发的链对不上 ✗；实际读到：" + actual);
    }

    @Test
    void boxEmitsEveryElementOfTheEntityVertexFormat() {
        List<String> expected = GEOMETRY_SEQUENCE;
        StrictFormatVertexConsumer consumer = new StrictFormatVertexConsumer(expectedSequence());

        // 一个 0.5 格见方的盒子；位置/颜色随便给（本测试只关心"元素发全了没有"✓）
        TNCasinoGeometry.box(new PoseStack(), consumer,
                new Vector3f(0.25F, 0.25F, 0.25F), new Vector3f(0.5F, 0.5F, 0.5F),
                0xC0A060, 0x00F000F0);

        assertEquals(VERTICES_PER_BOX, consumer.vertices,
                "一个盒子的顶点数不对（应该是 6 面 × 4 角）✗");
        for (List<String> sequence : consumer.sequences) {
            assertEquals(expected, sequence,
                    "有顶点的元素序列不对 ✗（漏发或顺序错都会被 BufferBuilder 静默丢弃，"
                            + "最后抛 \"Not filled all elements of the vertex\" ✗）");
        }
    }

    @Test
    void rotatedBoxEmitsEveryElementOfTheEntityVertexFormat() {
        StrictFormatVertexConsumer consumer = new StrictFormatVertexConsumer(expectedSequence());

        TNCasinoGeometry.boxRotated(new PoseStack(), consumer,
                new Vector3f(0.5F, 0.5F, 0.5F), new Vector3f(0.25F, 0.25F, 0.25F),
                37.0F, 0x4488CC, 0x00F000F0);

        assertEquals(VERTICES_PER_BOX, consumer.vertices, "旋转盒子的顶点数不对 ✗");
        for (List<String> sequence : consumer.sequences) {
            assertEquals(GEOMETRY_SEQUENCE, sequence, "旋转盒子的元素序列不对 ✗");
        }
    }

    /**
     * <b>反证</b> ✓ —— 故意漏发 {@code .uv(...)}（就是原来那份代码的样子 ✗），
     * 这个假缓冲<b>必须</b>抛异常 ✓。
     *
     * <p>没有这一条，上面两条"绿"就说明不了任何问题 ✗ ——
     * 万一 {@link StrictFormatVertexConsumer} 写得太宽松（什么都能过 ✓），
     * 那这条闸门就是摆设 ✗。所以这里把"闸门真的会红"钉死 ✓。
     */
    @Test
    void theGateItselfFailsWhenUvIsMissing() {
        StrictFormatVertexConsumer consumer = new StrictFormatVertexConsumer(expectedSequence());
        PoseStack pose = new PoseStack();
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> consumer.vertex(m, 0.0F, 0.0F, 0.0F)
                        .color(1.0F, 1.0F, 1.0F, 1.0F)
                        // ★ 这里故意不写 .uv(...) ✗（原来 TNCasinoGeometry 就是这个毛病）
                        .overlayCoords(0)
                        .uv2(0x00F000F0)
                        .normal(n, 0.0F, 1.0F, 0.0F)
                        .endVertex(),
                "漏发 uv0 竟然没被拦下来 ✗ ⇒ 这条闸门是假的 ✗");

        assertTrue(thrown.getMessage().contains("Not filled all elements of the vertex"),
                "抛的异常不是崩报里那一句 ✗：" + thrown.getMessage());
    }

    /**
     * <b>顺序写错也照样红</b> ✓ —— 元素发全了、但把 {@code overlay} 抢到 {@code uv} 前面 ✗，
     * {@code BufferBuilder} 会把 {@code uv} 静默丢掉 ⇒ 一样崩 ✗。
     */
    @Test
    void theGateAlsoFailsWhenElementOrderIsWrong() {
        StrictFormatVertexConsumer consumer = new StrictFormatVertexConsumer(expectedSequence());
        PoseStack pose = new PoseStack();
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();

        assertThrows(IllegalStateException.class,
                () -> consumer.vertex(m, 0.0F, 0.0F, 0.0F)
                        .color(1.0F, 1.0F, 1.0F, 1.0F)
                        // ★ 顺序错了：overlay 抢在 uv 前面 ✗
                        .overlayCoords(0)
                        .uv(0.0F, 0.0F)
                        .uv2(0x00F000F0)
                        .normal(n, 0.0F, 1.0F, 0.0F)
                        .endVertex(),
                "元素顺序写错竟然没被拦下来 ✗ ⇒ 这条闸门是假的 ✗");
    }

    /** 兜底体检 ✓：读到的确实是<b>实体那套</b>格式（几个关键元素都在 ✓）。 */
    @Test
    void theEntityFormatHasAllTheElementsWeRelyOn() {
        assertNotNull(entityFormat(), "拿不到实体顶点格式 ✗");
        List<String> labels = expectedSequence();
        assertTrue(labels.size() >= 6, "实体顶点格式的元素少于 6 个？✗ 读到的：" + labels);
        assertTrue(labels.contains("uv0"), "实体顶点格式里没有 uv0？✗ 读到的：" + labels);
        assertTrue(labels.contains("overlay"), "实体顶点格式里没有 overlay？✗ 读到的：" + labels);
        assertTrue(labels.contains("uv2"), "实体顶点格式里没有 uv2？✗ 读到的：" + labels);
        assertTrue(labels.contains("normal"), "实体顶点格式里没有 normal？✗ 读到的：" + labels);
    }

    /**
     * 离线复刻 {@code BufferBuilder} 与"实体顶点格式缓冲"相关的两条行为 ✓。
     *
     * <p>刻意只实现 {@code VertexConsumer} 的抽象方法 ✓ ——
     * {@code MultiBufferSource.getBuffer(...)} 实际交回来的是 {@code BufferBuilder}，
     * 走的就是下面这些"带校验"的方法 ✓（那几个 {@code default} 便利方法本来就只是转发 ✓）。
     */
    @Test
    void allAnimatedDiceFacesEmitValidEntityVerticesWithinBudget() {
        for(int face=1;face<=6;face++) for(int tick : new int[]{0,16,32,64}) {
            StrictFormatVertexConsumer consumer=new StrictFormatVertexConsumer(expectedSequence());
            CasinoTableDetails.die(new PoseStack(),consumer,0,1,0,face,tick,64,true,0xF000F0);
            assertEquals(528,consumer.vertices,"Six faces must contain 21 total pips plus one die body");
        }
    }

    @Test
    void solidWheelSectorEmitsCompleteEntityVertices() {
        StrictFormatVertexConsumer consumer=new StrictFormatVertexConsumer(expectedSequence());
        CasinoFurniture.sector(new PoseStack(),consumer,0.18F,1.3F,-0.09F,-0.025F,-14.5F,14.5F,CasinoPalette.FELT,15728880);
        assertEquals(144,consumer.vertices,"Six segments, each with six complete quad faces");
        assertEquals(0,consumer.overlayU,"Wheel must not sample the red hurt overlay");
        assertEquals(10,consumer.overlayV,"Neutral overlay row keeps the furniture palette unchanged");
        StrictFormatVertexConsumer disk=new StrictFormatVertexConsumer(expectedSequence());
        CasinoFurniture.sector(new PoseStack(),disk,0,1.43F,-0.02F,0.18F,0,360,CasinoPalette.WOOD_DARK,15728880);
        assertEquals(1440,disk.vertices,"Zero-radius centre has no invalid inner normal");
    }

    @Test
    void allFourCardSuitsEmitCompleteEntityVertices() {
        for(int suit=0;suit<4;suit++) {
            StrictFormatVertexConsumer consumer=new StrictFormatVertexConsumer(expectedSequence());
            CasinoCardFaces.suit(new PoseStack(),consumer,suit,0,0,0.025F,15728880);
            assertTrue(consumer.vertices>=500&&consumer.vertices<1000,"Legible bounded suit geometry");
            assertEquals(10,consumer.overlayV);
        }
    }

    private static final class StrictFormatVertexConsumer implements VertexConsumer {

        /** 本次缓冲的顶点格式元素序列（即校验顺序 ✓）。 */
        private final List<String> format;
        /** 已经<b>发满</b>元素的顶点数 ✓。 */
        private int vertices;
        private int overlayU, overlayV;
        /** 当前顶点已经收下的元素数（0 = 还没开始发 ✓）。 */
        private int pending;
        /** 当前顶点收到的元素（按收到的顺序 ✓）。 */
        private final List<String> current = new ArrayList<>();
        /** 每个完成顶点收到的元素序列 ✓。 */
        private final List<List<String>> sequences = new ArrayList<>();

        private StrictFormatVertexConsumer(List<String> format) {
            // ★ padding 不算"要发的元素" ✓ —— 原版 {@code BufferBuilder} 也从不显式发它
            //   （那是格式自带的占位对齐，不经过顶点链 ✗）。
            //   实测本仓的实体格式是 [position, color, uv0, overlay, uv2, normal, padding] ✓，
            //   顶点链只发前六个 ✓。
            this.format = format.stream().filter(label -> !"padding".equals(label)).toList();
        }

        /** 和 {@code BufferBuilder} 一样：不是"当前该填的元素"就<b>静默丢弃</b> ✗。 */
        private void offer(String label) {
            if (pending < format.size() && format.get(pending).equals(label)) {
                current.add(label);
                pending++;
            }
        }

        @Override
        public VertexConsumer vertex(Matrix4f pose, float x, float y, float z) {
            assertEquals(0, pending, "上一个顶点没发完就开始发下一个了 ✗");
            offer("position");
            return this;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            offer("position");
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            offer("color");
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            offer("uv0");
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            overlayU=u;overlayV=v;
            offer("overlay");
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            offer("uv2");
            return this;
        }

        @Override
        public VertexConsumer normal(Matrix3f pose, float x, float y, float z) {
            offer("normal");
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            offer("normal");
            return this;
        }

        /** {@code BufferBuilder.endVertex()} 的那三行 ✓ —— 少一个元素就在这里炸 ✗。 */
        @Override
        public void endVertex() {
            if (pending != format.size()) {
                throw new IllegalStateException("Not filled all elements of the vertex");
            }
            sequences.add(List.copyOf(current));
            current.clear();
            pending = 0;
            vertices++;
        }

        /** 这条测试用不到（只是接口要求 ✓）。 */
        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
