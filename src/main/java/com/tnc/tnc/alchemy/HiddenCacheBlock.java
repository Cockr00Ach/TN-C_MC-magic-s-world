package com.tnc.tnc.alchemy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.item.*;
import com.tnc.tnc.dungeon.*;
public final class HiddenCacheBlock extends BaseEntityBlock {
    public HiddenCacheBlock(){super(BlockBehaviour.Properties.copy(Blocks.BEDROCK).noOcclusion().noCollission().noLootTable());}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new HiddenCacheEntity(p,s);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return l instanceof Level level&&level.isClientSide&&!Client.visible()?Shapes.empty():Block.box(2,0,3,14,11,13);}
    private static final class Client{static boolean visible(){return com.tnc.tnc.mana.sight.client.ManaSightClient.enabled();}}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player player,InteractionHand h,BlockHitResult hit){if(player instanceof net.minecraft.server.level.ServerPlayer p){
        var r=DungeonService.run(p);var room=DungeonService.room(p);if(!com.tnc.tnc.mana.sight.ManaSightServer.enabled(p)||r==null||room==null||!DungeonRealm.inside(l)||!DungeonService.near(p,pos,5)||!DungeonService.validateRoom(p,r,room))return InteractionResult.FAIL;
        if(!r.state().clear(room.id())){DungeonService.message(p,"先清理这间房，再解开隐匣的封印。");return InteractionResult.SUCCESS;}
        String key="Hidden:"+room.id()+":"+p.getUUID();if(r.state().events.getBoolean(key)){DungeonService.message(p,"你已取走这一隐匣的馈赠。");return InteractionResult.SUCCESS;}
        var tx=new com.tnc.tnc.adventure.InventoryTransaction(p.getInventory());if(!tx.add(new ItemStack(DungeonRewards.material(r.theme),2))||!tx.add(new ItemStack(AlchemyContent.POTIONS.get(Elixir.SPRING)))){DungeonService.message(p,"请留出背包空间，隐匣仍为你保留。");return InteractionResult.SUCCESS;}
        tx.commit();r.state().events.putBoolean(key,true);DungeonData.get(p.server).setDirty();p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,pos.getX()+.5,pos.getY()+.8,pos.getZ()+.5,28,.3,.5,.3,.05);DungeonService.message(p,"隐匣开启：主题结晶×2、凝泉饮×1。");
    }return InteractionResult.sidedSuccess(l.isClientSide);}
}
