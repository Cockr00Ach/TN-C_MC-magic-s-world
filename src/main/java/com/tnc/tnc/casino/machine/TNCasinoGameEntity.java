package com.tnc.tnc.casino.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class TNCasinoGameEntity extends TNMachineEntity {
    public enum Game { WHEEL, CARDS }
    private final Game game;
    public TNCasinoGameEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Game game) {
        super(type, pos, state); this.game = game;
    }
    public Game game() { return game; }
    public int spinTicks() { return game == Game.WHEEL ? 100 : 50; }
    public int clientSpinTicks() { return spinTicks(); }
    public int resultTicks() { return 80; }
    protected int maxStake() { return 1; }
    protected int sanitizeResult(int slot, int raw) { return Math.floorMod(raw, game == Game.WHEEL ? 12 : slot==2 ? 16 : 13); }
    public int cardSuit(int card) { return (result(2) >> (Math.floorMod(card,2)*2)) & 3; }
    protected String[] betNames() { return game == Game.WHEEL ? new String[]{"固定倍率转轮"} : CasinoRules.CARD_BETS; }
    protected int payoutFor(int[] r, int stake) {
        return game == Game.WHEEL ? stake * CasinoRules.WHEEL_MULTIPLIERS[r[0]]
                : CasinoRules.cards(r[0]+1, r[1]+1, selectedBet, stake);
    }
    public void interact(ServerPlayer player) {
        if (!prepareRound(player)) return;
        ServerLevel server = (ServerLevel) level;
        for (int i=0;i<3;i++) result[i] = server.random.nextInt(game == Game.WHEEL ? 12 : i==2 ? 16 : 13);
        beginSpin(server, worldPosition, getBlockState());
        tell(player, game == Game.WHEEL ? "幸运转轮开转，指针倍率包含本金：0/1/2/4 倍。"
                : "比牌开局：" + betName() + "，A 最小、K 最大；赢返回 2 倍，同点返本。");
    }
    protected void onSettled(ServerLevel level, BlockPos pos, int payout) {
        level.playSound(null, pos, payout > 0 ? net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP
                : net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
    }
}
