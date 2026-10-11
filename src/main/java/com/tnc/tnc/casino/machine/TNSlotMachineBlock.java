package com.tnc.tnc.casino.machine;

/**
 * 老虎机的方块 ✓ —— 形状、朝向、交互、状态同步、拆机退币全部继承自
 * {@link TNCasinoMachineBlock} ✓；这个子类只负责两件事：<b>占地尺寸</b> 与 <b>造哪种方块实体</b> ✓。
 *
 * <h2>★ 占地 1 格宽 × 2 格高 × 1 格深（2026-10-08 作者要求 ✗）</h2>
 * 作者原话：「老虎机我要<b>高 2 宽 1</b> 的那种，右边有一个红红的拉杆的那种经典老虎机」✓。
 * 所以碰撞箱从 2×1×2 改成 <b>1×2×1</b> ✓ ——
 * 立式老虎机本来就该又高又窄 ✓，2×2 的矮柜子不像 ✓。
 *
 * <p>⚠️ 改占地是**存档相关**的 ✗：老存档里已经放下的机器会按新碰撞箱重新计算 ✓
 * （方块状态本身没变，只是形状变了 ✓），一般不会出问题 ✓；
 * 但如果之前贴着它放了别的东西，可能出现轻微重叠 ✓ —— 这个由作者验收时看 ✓。
 */
public final class TNSlotMachineBlock extends TNCasinoMachineBlock<TNSlotMachineEntity> {

    public TNSlotMachineBlock() {
        // 宽 1.0 × 高 2.0 × 深 1.0 ✓
        super(TNSlotMachineEntity::new, 1.0D, 2.0D, 1.0D);
    }
}
