package com.tnc.tnc.client;

/**
 * 「寻人点」的屏幕几何 —— 纯数学，不碰 Minecraft 的客户端对象，方便单测。
 *
 * <h2>为什么单独拎出来</h2>
 * 世界坐标 → 屏幕像素这件事，最怕"看起来差不多"。之前 {@link QuestMarkerHud}
 * 里自己写了一套绕 X 再绕 Y 的旋转，少了一个分量、还转反了方向，结果：
 * <ul>
 *   <li>目标在视野里时，屏幕上的菱形跑到**左右相反**的那一侧 ✗；</li>
 *   <li>目标在视野外时，边缘箭头的方向只有在 {@code yaw≈0}（朝南）时才是对的，
 *       朝西/朝北时指向完全错乱 ✗。</li>
 * </ul>
 * 所以这里改成**先算摄像机基向量、再点乘**的写法 —— 每一项都能对着原版约定念出来，
 * 也不会再有"漏掉 dx 对上下分量的贡献"这种隐性错误。
 *
 * <h2>摄像机约定（原版 {@code Entity#calculateViewVector} / {@code GameRenderer}）</h2>
 * <pre>
 *   forward f = ( -sin(yaw)·cos(pitch),  -sin(pitch),  cos(yaw)·cos(pitch) )
 *   right   r = ( -cos(yaw),              0,           -sin(yaw)           )
 *   up      u = ( -sin(yaw)·sin(pitch),   cos(pitch),   cos(yaw)·sin(pitch) )
 * </pre>
 * {@code yaw} 0 朝南（+Z）、90 朝西（-X）；{@code pitch} 为正表示**低头**。
 * 屏幕坐标以左上角为原点、x 向右、y 向下，所以 {@code screenY = 中心 - up·scale/depth}。
 */
final class QuestMarkerGeometry {

    /** 目标相对摄像机的偏移（与摄像机同一套基向量）。 */
    record CameraSpace(double right, double up, double depth) {
    }

    /** GUI 像素坐标（左上角原点）。 */
    record ScreenPoint(double x, double y) {
    }

    private QuestMarkerGeometry() {
    }

    /**
     * 世界坐标偏移 → 摄像机空间。
     *
     * @param dx/dy/dz 目标减摄像机位置
     * @param yawDeg   摄像机 {@code getYRot()}
     * @param pitchDeg 摄像机 {@code getXRot()}
     */
    static CameraSpace cameraSpace(double dx, double dy, double dz, double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);

        double right = dx * -cosYaw + dz * -sinYaw;
        double up = dx * -sinYaw * sinPitch + dy * cosPitch + dz * cosYaw * sinPitch;
        double depth = dx * -sinYaw * cosPitch + dy * -sinPitch + dz * cosYaw * cosPitch;
        return new CameraSpace(right, up, depth);
    }

    /**
     * 摄像机空间 → 屏幕像素（与 FOV 同一套竖直视场；方块像素是正方形，横竖共用 scale）。
     */
    static ScreenPoint project(CameraSpace space, double fovDeg, int width, int height) {
        double scale = (height / 2.0D) / Math.tan(Math.toRadians(fovDeg) / 2.0D);
        double x = width / 2.0D + space.right() * scale / space.depth();
        double y = height / 2.0D - space.up() * scale / space.depth();
        return new ScreenPoint(x, y);
    }

    /**
     * 目标在视野外（或背后）时，贴边箭头该指向哪 —— 返回**单位向量**，
     * {@code {x, y}}，x 向右、y 向下（GUI 坐标）。
     *
     * <p>用方位角 {@code atan2(right, depth)}：0 表示正前（箭头朝上），
     * +90° 表示正右，±180° 表示正后（箭头朝下）。这样目标在背后时箭头会落到屏幕
     * 下缘、并偏向它实际所在的那一侧，而不是镜像到另一边 ✗。
     */
    static double[] edgeDirection(CameraSpace space) {
        double bearing = Math.atan2(space.right(), space.depth());
        return new double[]{Math.sin(bearing), -Math.cos(bearing)};
    }
}
