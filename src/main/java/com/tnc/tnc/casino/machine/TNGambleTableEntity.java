package com.tnc.tnc.casino.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Three physical dice; preserves the original gamble_table save identity. */
public final class TNGambleTableEntity extends TNMachineEntity {
    public TNGambleTableEntity(BlockPos pos, BlockState state) { super(TNCasinoRegistry.GAMBLE_ENTITY, pos, state); }
    public int spinTicks() { return 64; }
    public int clientSpinTicks() { return 64; }
    public int resultTicks() { return 80; }
    protected int maxStake() { return 1; }
    protected int sanitizeResult(int slot, int raw) { return Math.floorMod(raw, 6); }
    protected String[] betNames() { return CasinoRules.DICE_BETS; }
    protected int payoutFor(int[] r, int stake) { return CasinoRules.dice(r[0]+1, r[1]+1, r[2]+1, selectedBet, stake); }
    public void interact(ServerPlayer player) {
        if (!prepareRound(player)) return;
        ServerLevel server = (ServerLevel) level;
        for (int i=0;i<3;i++) result[i] = server.random.nextInt(6);
        beginSpin(server, worldPosition, getBlockState());
        tell(player, "摇骰开局，押" + betName() + "；普通返回 2 倍，豹子返回 24 倍。豹子使普通押注落空。");
    }
    protected void onSettled(ServerLevel level, BlockPos pos, int payout) {
        net.minecraft.sounds.SoundEvent sound = payout > 0 ? net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP : net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value();
        level.playSound(null, pos, sound, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
    }
}
