package com.tnc.tnc.dungeon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
public final class DungeonInteractBlock extends Block {
    public final String kind;
    public DungeonInteractBlock(String kind){super(BlockBehaviour.Properties.copy(Blocks.CHISELED_DEEPSLATE).strength(-1,3600000).lightLevel(s->kind.equals("cache")?5:11));this.kind=kind;}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos at,Player player,InteractionHand hand,BlockHitResult hit){if(player instanceof ServerPlayer p){var r=DungeonService.run(p);if(r==null||!DungeonRealm.inside(l))return InteractionResult.FAIL;var room=DungeonService.room(p);
      if(kind.equals("waystone")){if(r.floor==0)DungeonService.leave(p);else DungeonService.next(p);}
      else if(kind.equals("cache")&&room!=null&&room.kind()==DungeonGraph.Kind.TREASURE){if(!DungeonService.cacheEncounter(p,r,room))DungeonNetwork.open(p,"cache",at);}
      else if(kind.equals("console"))DungeonNetwork.open(p,r.floor==0?"gate":room!=null&&room.kind()==DungeonGraph.Kind.REST?"rest":"event",at);
    }return InteractionResult.sidedSuccess(l.isClientSide);}
}
