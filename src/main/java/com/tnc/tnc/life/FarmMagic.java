package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.adventure.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class FarmMagic {
    private static final TagKey<net.minecraft.world.level.block.Block> CROPS=TagKey.create(Registries.BLOCK,ResourceLocation.parse("tnc:farm_crops"));
    private record Field(UUID owner,ResourceLocation dimension,BlockPos center,int radius,long next,int remaining,int interval){}
    private static final Map<UUID,Field> FIELDS=new HashMap<>();
    public static void learn(ServerPlayer p) {
        var a=LifeSavedData.get(p.server).account(p.getUUID());
        if(a.farmTier==0)a.farmTier=1;
        if(!p.getInventory().contains(new ItemStack(TNMod.FARM_FOCUS.get()))){var stack=new ItemStack(TNMod.FARM_FOCUS.get());var tx=new InventoryTransaction(p.getInventory());if(tx.add(stack))tx.commit();else p.drop(stack,false);}
        LifeSavedData.get(p.server).setDirty();AdventureService.milestone(p,"farm_learned");
    }
    public static class FocusItem extends Item {
        public FocusItem(){super(new Properties().stacksTo(1));}
        @Override public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player p,InteractionHand hand) {
            var stack=p.getItemInHand(hand);if(p instanceof ServerPlayer s) {
                var a=LifeSavedData.get(s.server).account(s.getUUID());
                a.farmTier=Math.max(a.farmTier,a.farmTier>0?(AdventureService.profile(s).level()>=30?3:AdventureService.profile(s).level()>=10?2:1):0);
                if(s.isShiftKeyDown()){int mode=stack.getOrCreateTag().getInt("FarmMode")%Math.max(1,a.farmTier)+1;stack.getOrCreateTag().putInt("FarmMode",mode);s.displayClientMessage(Component.literal("农事魔法："+name(mode)),true);}
                else s.displayClientMessage(Component.literal(cast(s,Math.max(1,stack.getOrCreateTag().getInt("FarmMode")))),true);
            }return InteractionResultHolder.sidedSuccess(stack,level.isClientSide());
        }
    }
    private static String name(int tier){return tier==1?"初芽术":tier==2?"田园之息":"丰穰法阵";}
    public static String cast(ServerPlayer p,int tier) {
        if(p.isSpectator()||!p.isAlive()||com.tnc.tnc.combat.DownedCombat.isDowned(p))return "现在不能施放农事魔法。";
        var data=LifeSavedData.get(p.server);var a=data.account(p.getUUID());long now=AdventureSavedData.get(p.server).activeTicks;
        if(tier<1||tier>3||tier>a.farmTier)return "先完成小麦委托学习初芽术；Lv10/30开放后两阶。";
        if(now<a.farmCooldown)return "农事魔法冷却中。";
        var hit=p.pick(12,1,false);if(!(hit instanceof BlockHitResult block)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return "请瞄准12格内的农田。";
        var center=block.getBlockPos();int radius=tier==1?1:tier==2?3:4;
        if(!fullyLoaded(p,center,radius))return "田块尚未完全加载，请靠近后施放。";
        if(FIELDS.containsKey(p.getUUID())||FIELDS.values().stream().anyMatch(f->f.dimension.equals(p.level().dimension().location())&&Math.abs(f.center.getX()-center.getX())<=f.radius+radius&&Math.abs(f.center.getZ()-center.getZ())<=f.radius+radius))return "附近已有农事法阵，请等它结束再施放。";
        if(!hasCrop(p,center,radius))return "范围内没有白名单作物；不会催生树木、矿物或自动收割。";
        var magic=MagicStone.getOrNull(p);int cost=tier==1?15:tier==2?45:120;if(magic==null||!magic.spendMana(cost))return "魔力不足，需要 "+cost+"。";
        a.farmCooldown=now+(tier==1?600:tier==2?1200:3600);data.setDirty();
        if(tier==1)grow(p,center,radius);
        else FIELDS.put(p.getUUID(),new Field(p.getUUID(),p.level().dimension().location(),center,radius,now+(tier==2?200:300),tier==2?3:4,tier==2?200:300));
        com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);AdventureService.milestone(p,"farm_cast");return name(tier)+"已施放。";
    }
    private static boolean hasCrop(ServerPlayer p,BlockPos center,int radius){
        for(var pos:BlockPos.betweenClosed(center.offset(-radius,-1,-radius),center.offset(radius,2,radius)))if(p.serverLevel().hasChunkAt(pos)&&p.serverLevel().getBlockState(pos).is(CROPS))return true;return false;
    }
    private static boolean fullyLoaded(ServerPlayer p,BlockPos center,int radius){
        for(int x=(center.getX()-radius)>>4;x<=(center.getX()+radius)>>4;x++)for(int z=(center.getZ()-radius)>>4;z<=(center.getZ()+radius)>>4;z++)if(!p.serverLevel().hasChunk(x,z))return false;return true;
    }
    private static void grow(ServerPlayer p,BlockPos center,int radius) {
        var level=p.serverLevel();for(var pos:BlockPos.betweenClosed(center.offset(-radius,-1,-radius),center.offset(radius,2,radius))) {
            if(!level.hasChunkAt(pos)||!level.mayInteract(p,pos))continue;
            var state=level.getBlockState(pos);if(!state.is(CROPS))continue;
            // Respect the same real protection event as destructive magic and player edits.
            if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,pos,state,p)))continue;
            for(var property:state.getProperties())if(property instanceof IntegerProperty age&&age.getName().equals("age")) {
                int value=state.getValue(age),max=Collections.max(age.getPossibleValues());if(value<max) {
                    level.setBlock(pos,state.setValue(age,value+1),3);level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,pos.getX()+.5,pos.getY()+.6,pos.getZ()+.5,2,.2,.2,.2,0);
                }break;
            }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null||server.getTickCount()%20!=0)return;
        long now=AdventureSavedData.get(server).activeTicks;
        for(var field:List.copyOf(FIELDS.values()))if(now>=field.next) {
            var p=server.getPlayerList().getPlayer(field.owner);if(p==null||!p.level().dimension().location().equals(field.dimension)||!fullyLoaded(p,field.center,field.radius))continue;
            grow(p,field.center,field.radius);if(field.remaining<=1)FIELDS.remove(field.owner);else FIELDS.put(field.owner,new Field(field.owner,field.dimension,field.center,field.radius,now+field.interval,field.remaining-1,field.interval));
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e){FIELDS.clear();}
}
