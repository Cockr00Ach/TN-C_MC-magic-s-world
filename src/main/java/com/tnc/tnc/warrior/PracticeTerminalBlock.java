package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 演武终端：立在演武场正中的一台黑色机器。
 *
 * <p>右键打开「战士修习」面板的教官选择区；选谁、什么时候开始，全由面板发回服务器裁定。
 * 方块本身敲不坏（台子是场景的一部分，不给拆）。
 */
public class PracticeTerminalBlock extends Block {
    public PracticeTerminalBlock() {
        super(Properties.of()
            .mapColor(MapColor.METAL)
            .strength(-1.0F, 3600000.0F)
            .noOcclusion()
            .lightLevel(state -> 7));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) PracticeArena.openTerminal(sp);
        return InteractionResult.CONSUME;
    }
}
