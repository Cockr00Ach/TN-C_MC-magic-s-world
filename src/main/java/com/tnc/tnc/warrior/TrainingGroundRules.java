package com.tnc.tnc.warrior;

/**
 * 天空岛共用演武场的几何：一块 61×61 的石台，四角各一个演武位，正中一台演武终端。
 *
 * <p>演武位是「按需占用」的：谁来练就占一个空的，走了就释放；所以同一座台子
 * 可以四个人同时练，谁也不挡谁。
 */
public final class TrainingGroundRules {
    /** 台面半径：61×61。 */
    public static final int HALF = 30;
    /** 演武位中心到台心的距离。 */
    public static final int PAD_OFFSET = 17;
    /** 演武位半宽：13×13。 */
    public static final int PAD_HALF = 6;
    /** 演武位数量。 */
    public static final int PAD_COUNT = 4;

    private TrainingGroundRules() {
    }

    /** 演武位中心的 X（世界坐标）。 */
    public static int padX(int pad, int centerX) {
        return centerX + (((pad & 1) == 0) ? -PAD_OFFSET : PAD_OFFSET);
    }

    /** 演武位中心的 Z（世界坐标）。 */
    public static int padZ(int pad, int centerZ) {
        return centerZ + ((pad < 2) ? -PAD_OFFSET : PAD_OFFSET);
    }

    /** 某点是否落在某个演武位里（用于判断「站在自己的位子上」）。 */
    public static boolean padContains(int pad, int centerX, int centerZ, double x, double z) {
        return Math.abs(x - (padX(pad, centerX) + 0.5)) <= PAD_HALF + 1
            && Math.abs(z - (padZ(pad, centerZ) + 0.5)) <= PAD_HALF + 1;
    }

    /** 某点是否在演武场里（含台面上方一层空间，够跳、够被击退）。 */
    public static boolean contains(int centerX, int deckY, int centerZ, double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Math.abs(x - (centerX + 0.5)) <= HALF + 2
            && Math.abs(z - (centerZ + 0.5)) <= HALF + 2
            && y >= deckY - 8 && y <= deckY + 30;
    }
}
