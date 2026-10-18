package com.tnc.tnc.casino.machine;

/** Four by two table; shape rotates around the same centre as its renderer. */
public final class TNGambleTableBlock extends TNCasinoMachineBlock<TNGambleTableEntity> {
    public TNGambleTableBlock() { super(TNGambleTableEntity::new, 8.0D, 1.15D, 4.0D); }
}
