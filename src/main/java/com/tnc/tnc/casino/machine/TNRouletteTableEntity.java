package com.tnc.tnc.casino.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/** 37 numbered pockets. Players select colour, parity, size or zero; decorative dice do not affect payout. */
public final class TNRouletteTableEntity extends TNMachineEntity {

    public static final int POCKETS = 37;

    public static final int DIE_FACES = 6;

    private static final int SPIN_TICKS = 80;

    private static final int CLIENT_SPIN_TICKS = 108;

    private static final int RESULT_TICKS = 70;

    private static final int MAX_STAKE = 8;

    public TNRouletteTableEntity(BlockPos pos, BlockState state) {
        super(TNCasinoRegistry.ROULETTE_ENTITY, pos, state);
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
    protected int sanitizeResult(int slot, int raw) {
        return slot == 0 ? Math.floorMod(raw, POCKETS) : Math.floorMod(raw, DIE_FACES);
    }

    @Override
    protected int payoutFor(int[] result, int staked) {
        return CasinoRules.roulette(result[0], selectedBet, staked);
    }

    @Override
    protected String[] betNames() { return CasinoRules.ROULETTE_BETS; }

    public void chooseColour(ServerPlayer player,int bet) {
        if(bet!=0&&bet!=1&&bet!=6) return;
        if(spinning()) {tell(player,"轮盘正在转，本局押注已锁定。");return;}
        if(!(level instanceof ServerLevel server)) return;
        selectedBet=bet;setChanged();sync(server,worldPosition,getBlockState(),false);
        tell(player,"已选"+betName()+"；手持铜币右键桌面开局。绿零只押号码0，返回36倍。");
    }

    @Override
    public void interact(ServerPlayer player) {
        if (!prepareRound(player)) return;
        ServerLevel server = (ServerLevel) level;
        result[0] = server.random.nextInt(POCKETS);
        result[1] = server.random.nextInt(DIE_FACES);
        result[2] = server.random.nextInt(DIE_FACES);
        beginSpin(server, worldPosition, getBlockState());
        tell(player, "投入 1 枚铜币，押" + betName() + "：普通押注返回 2 倍，绿零返回 36 倍。");
    }

    @Override
    protected void onSettled(ServerLevel level, BlockPos pos, int payout) {
        level.playSound(null, pos,
                payout > 0 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.BLOCKS, 0.8F, payout > 0 ? 1.3F : 0.8F);
    }

}
