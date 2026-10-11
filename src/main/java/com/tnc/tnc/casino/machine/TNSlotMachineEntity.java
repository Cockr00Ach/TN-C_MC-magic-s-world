package com.tnc.tnc.casino.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/** Three independent uniform reels; pairs refund and triples return the listed multiplier. */
public final class TNSlotMachineEntity extends TNMachineEntity {

    public static final int SYMBOLS = 6;

    public static final int REELS = RESULT_SLOTS;

    private static final int SPIN_TICKS = 70;

    private static final int CLIENT_SPIN_TICKS = 88;

    private static final int RESULT_TICKS = 60;

    private static final int MAX_STAKE = 8;

    public TNSlotMachineEntity(BlockPos pos, BlockState state) {
        super(TNCasinoRegistry.SLOT_ENTITY, pos, state);
    }

    @Override
    public int spinTicks() { return SPIN_TICKS; }

    @Override
    public int clientSpinTicks() { return CLIENT_SPIN_TICKS; }

    @Override
    public int resultTicks() { return RESULT_TICKS; }

    @Override
    protected int maxStake() { return MAX_STAKE; }

    @Override
    protected int sanitizeResult(int slot, int raw) { return Math.floorMod(raw, SYMBOLS); }

    @Override
    protected int payoutFor(int[] symbols, int staked) {
        return CasinoRules.slots(symbols[0], symbols[1], symbols[2], staked);
    }

    public static int symbolMultiplier(int symbol) {
        return switch (symbol) {
            case 0 -> 3;
            case 1 -> 4;
            case 2 -> 6;
            case 3 -> 10;
            case 4 -> 20;
            default -> 50;
        };
    }

    @Override
    public void interact(ServerPlayer player) {
        if (!prepareRound(player)) return;
        ServerLevel server = (ServerLevel) level;
        for (int slot = 0; slot < RESULT_SLOTS; slot++) result[slot] = server.random.nextInt(SYMBOLS);
        beginSpin(server, worldPosition, getBlockState());
        tell(player, "投入 1 枚铜币：对子返本，三同按符号返回 3/4/6/10/20/50 倍。");
    }

    @Override
    protected void onSettled(ServerLevel level, BlockPos pos, int payout) {
        level.playSound(null, pos,
                payout > 0 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.BLOCKS, 0.8F, payout > 0 ? 1.4F : 0.7F);
    }

}
