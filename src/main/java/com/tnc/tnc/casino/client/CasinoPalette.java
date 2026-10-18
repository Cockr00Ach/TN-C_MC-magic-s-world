package com.tnc.tnc.casino.client;

/**
 * 赌场机器的配色表 —— <b>全机唯一一处</b> ✓。
 *
 * <p>现状是<b>纯色自绘</b>（每一面按朝向给不同明暗 ✓）—— 这样先保证
 * "几何对不对、动画动不动"能看清楚 ✓，而且<b>完全不受资源包影响</b> ✓。
 * 等作者确认造型之后再往这套盒子外面贴正式贴图 ✓（替换点只有这一个文件 ＋ 渲染器里的盒子表 ✓）。
 *
 * <p>配色跟着小镇既有家具走（木构深棕 ＋ 黄铜 ＋ 深绿台呢 ✓），免得这台机器像外来物 ✗。
 */
public final class CasinoPalette {

    private CasinoPalette() {
    }

    // ---- 木壳（转盘桌用 ✓：小镇既有家具的深棕 ＋ 黄铜 ✓）----
    /** 柜体主色 ✓。 */
    public static final int WOOD = 0x493126;
    /** 柜体深一档（侧板/内衬 ✓）。 */
    public static final int WOOD_DARK = 0x281C19;
    /** 台面/顶盖（浅一档 ✓）。 */
    public static final int WOOD_TOP = 0x684A37;

    // ---- 统一的胡桃木柜体与酒红皮革----
    /**
     * 机身使用与赌桌一致的胡桃木，顶部以酒红软包点缀。
     * 所有设备共享香槟金饰条、墨绿台呢和象牙白细节。
     */
    public static final int CABINET = 0x493126;
    /** 机身高光（顶盖/腰线 ✓）。 */
    public static final int CABINET_TOP = 0x68303A;
    /** 机身暗部（底座/内侧 ✓）。 */
    public static final int CABINET_DARK = 0x281C19;

    // ---- 金属 ----
    /** 黄铜装饰 ✓。 */
    public static final int BRASS = 0xC2AA72;
    /** 黄铜暗面 ✓。 */
    public static final int BRASS_DARK = 0x897449;
    /** 深铁（铰链、拉杆杆身 ✓）。 */
    public static final int IRON = 0x3A3A3E;

    // ---- 台呢与滚轮 ----
    /** 深绿台呢 ✓。 */
    public static final int FELT = 0x174C40;
    /** 滚轮窗口的内部阴影 ✓（凹进去的感觉 ✓）。 */
    public static final int WELL = 0x181412;
    /** 滚轮带底色 ✓。 */
    public static final int DRUM = 0xEFE6D2;
    /** 滚轮带上的一格分隔线 ✓。 */
    public static final int DRUM_SEAM = 0xB9AE95;

    // ---- 符号 ----
    /** 六个滚轮符号的颜色 ✓（下标必须与 {@code TNSlotMachineEntity.SYMBOLS} 的数量一致 ✗）。 */
    public static final int[] SYMBOL = {
            0x526A80, // 0 蓝（最常见）
            0x863C46, // 1 红
            0x6E506A, // 2 紫
            0x426C55, // 3 绿
            0xA8764D, // 4 橙
            0xC2AA72, // 5 金（最稀有）
    };

    // ---- 灯 ----
    /** 结果灯：亮 ✓。 */
    public static final int LAMP_ON = 0xE1C58E;
    /** 结果灯：灭 ✓。 */
    public static final int LAMP_OFF = 0x514437;
    /** 中奖时的爆闪灯 ✓。 */
    public static final int LAMP_WIN = 0xF4E4BD;

    // ---- 4×2 赌博桌上的摆件（★ 把生成器里的素材区换成纯色 ✓）----
    /**
     * 牌面（米白 ✓）—— 对应生成器里的 {@code card face} 素材区 ✓。
     * 纯色自绘没有 UV ✗，所以"牌面/牌背"的区分只能靠<b>两种颜色</b> ✓，
     * 而不能靠同一张图上不同的格子 ✓。
     */
    public static final int CARD_FACE = 0xEFE9DA;
    /** 牌背（灰蓝 ✓）—— 同时用作"牌靴"的料 ✓。 */
    public static final int CARD_BACK = 0x68303A;
    /** 骰身（象牙白 ✓）。 */
    public static final int DICE = 0xF4EFE2;
    /** 骰子上的点（近黑 ✓）。 */
    public static final int PIP = 0x1A1A1E;
    /** 筹码：红 ✓。 */
    public static final int CHIP_RED = 0x863C46;
    /** 筹码：蓝 ✓。 */
    public static final int CHIP_BLUE = 0x526A80;
    /** 筹码：白 ✓。 */
    public static final int CHIP_WHITE = 0xECE6D8;

    /** 取符号颜色 ✓（越界自动绕回，绝不抛 ✗）。 */
    public static int symbol(int index) {
        return SYMBOL[Math.floorMod(index, SYMBOL.length)];
    }

    /** 按整数颜色取红分量 ✓。 */
    public static float red(int rgb) { return ((rgb >> 16) & 0xFF) / 255.0F; }

    public static float green(int rgb) { return ((rgb >> 8) & 0xFF) / 255.0F; }

    public static float blue(int rgb) { return (rgb & 0xFF) / 255.0F; }
}
