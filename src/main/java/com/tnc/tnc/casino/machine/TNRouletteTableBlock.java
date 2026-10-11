package com.tnc.tnc.casino.machine;

/**
 * 转盘桌的方块 ✓ —— 形状、朝向、交互、状态同步、拆机退币全部继承自
 * {@link TNCasinoMachineBlock} ✓；这个子类<b>只负责"造哪种方块实体"</b> ✓。
 */
public final class TNRouletteTableBlock extends TNCasinoMachineBlock<TNRouletteTableEntity> {

    public TNRouletteTableBlock() {
        super(TNRouletteTableEntity::new,8,1.05,4);
    }
}
