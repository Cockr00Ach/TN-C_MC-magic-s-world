package com.tnc.tnc.life.pasture;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import com.tnc.tnc.life.routes.*;
/** Held capture and authoritative UUID transfer. Copies cannot release another animal. */
public final class PastureCageItem extends Item {
    private record Session(UUID target,ItemStack cage,int ticks,long started,float health){}
    private static final Map<UUID,Session> CHANNELS=new HashMap<>();
    public final int tier;public PastureCageItem(){this(1);}public PastureCageItem(int tier){super(new Properties().stacksTo(1));this.tier=tier;}
    public static int tierFor(String id){return switch(id){case "dewbound_whale","wirecall_lizard","papersail_ray","bowlhorn_rhino","springhoof_strider","prismatic_antelope"->3;case "emberback_hog","tideback_newt","apiary_toad","dusk_lantern_deer","post_heron","watch_mantis","forgegill_tapir","mistbelly_otter","ringstone_tortoise","patternbuild_beaver","drumbelly_otter"->2;default->1;};}
    private static String species(LivingEntity e){return e instanceof PastureAnimal a?a.speciesId():e instanceof com.tnc.tnc.life.fauna.BellwoolSheepEntity?"bellwool_sheep":"";}
    private boolean valid(ServerPlayer p,ItemStack cage,LivingEntity target){if(cage.getItem()!=this||cage.getCount()!=1||cage.getOrCreateTag().hasUUID("Ticket")||!target.isAlive()||target.isVehicle()||target.isPassenger()||p.distanceToSqr(target)>25||!p.hasLineOfSight(target)||!RouteAccess.allowed(p.serverLevel(),target.blockPosition(),p.getUUID()))return false;String id=species(target);if(id.isEmpty()||tier<tierFor(id))return false;
        if(target instanceof PastureAnimal a){if(a.owner()!=null&&!a.owner().equals(p.getUUID()))return false;if(!p.isCreative()&&!a.captureTrust(p))return false;if(tierFor(id)==3&&!p.isCreative()&&!a.isCalmed())return false;}
        if(target instanceof com.tnc.tnc.life.fauna.BellwoolSheepEntity sheep&&!p.isCreative()&&!p.getUUID().equals(sheep.caretaker()))return false;return true;}
    public InteractionResult capture(ServerPlayer player,ItemStack cage,PastureAnimal target){return begin(player,cage,target);}
    private InteractionResult begin(ServerPlayer p,ItemStack cage,LivingEntity target){if(!valid(p,cage,target)){PastureAnimal.message(p,"先喂喜食取得信任；稀有兽需安抚，且笼级必须够。保持五格内视线。");return InteractionResult.FAIL;}int required=new int[]{0,60,100,160}[tierFor(species(target))];CHANNELS.put(p.getUUID(),new Session(target.getUUID(),cage,required,p.level().getGameTime(),p.getHealth()));p.startUsingItem(p.getMainHandItem()==cage?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND);return InteractionResult.CONSUME;}
    @Override public InteractionResult interactLivingEntity(ItemStack cage,Player p,LivingEntity target,InteractionHand hand){return p instanceof ServerPlayer sp?begin(sp,cage,target):InteractionResult.SUCCESS;}
    @Override public int getUseDuration(ItemStack s){return 200;}
    @Override public UseAnim getUseAnimation(ItemStack s){return UseAnim.BOW;}
    @Override public void onUseTick(net.minecraft.world.level.Level l,LivingEntity actor,ItemStack stack,int remaining){if(!(actor instanceof ServerPlayer p))return;Session session=CHANNELS.get(p.getUUID());var raw=session==null?null:p.serverLevel().getEntity(session.target);if(session==null||session.cage!=stack||!(raw instanceof LivingEntity target)||!valid(p,stack,target)||p.getHealth()<session.health||p.hurtTime>0){p.stopUsingItem();CHANNELS.remove(p.getUUID());return;}
        int elapsed=(int)(l.getGameTime()-session.started);if(elapsed%10==0)PastureAnimal.message(p,"捕获 "+species(target)+" · "+Math.min(100,elapsed*100/session.ticks)+"%");if(elapsed<session.ticks)return;
        if(!valid(p,stack,target))return;CompoundTag saved=new CompoundTag();if(!target.save(saved))return;saved.putUUID("CageCaptor",p.getUUID());UUID ticket=PastureCageLedger.get(p.server).store(saved);if(ticket==null)return;String id=species(target);stack.getOrCreateTag().putUUID("Ticket",ticket);stack.getOrCreateTag().putString("Species",id);target.discard();CHANNELS.remove(p.getUUID());p.stopUsingItem();RouteProgress.award(p,"capture/"+id);PastureAnimal.message(p,"已封存这一只；右键合法空地释放，空笼可重复使用。");}
    @Override public void releaseUsing(ItemStack s,net.minecraft.world.level.Level l,LivingEntity actor,int remaining){CHANNELS.remove(actor.getUUID());}
    @Override public InteractionResult useOn(UseOnContext c){if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;if(!(c.getPlayer() instanceof ServerPlayer p))return InteractionResult.PASS;var tag=c.getItemInHand().getTag();if(tag==null||!tag.hasUUID("Ticket"))return InteractionResult.PASS;var ledger=PastureCageLedger.get(p.server);UUID ticket=tag.getUUID("Ticket");CompoundTag saved=ledger.peek(ticket);if(saved==null)return InteractionResult.FAIL;
        UUID authorized=saved.hasUUID("CageCaptor")?saved.getUUID("CageCaptor"):saved.hasUUID("PastureOwner")?saved.getUUID("PastureOwner"):null;if(!p.getUUID().equals(authorized))return InteractionResult.FAIL;BlockPos pos=c.getClickedPos().relative(c.getClickedFace());if(saved.hasUUID("UUID"))for(var world:p.server.getAllLevels())if(world.getEntity(saved.getUUID("UUID"))!=null)return InteractionResult.FAIL;
        Entity entity=EntityType.loadEntityRecursive(saved,p.serverLevel(),e->{e.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,p.getYRot(),0);return e;});if(!(entity instanceof PastureAnimal)&&!(entity instanceof com.tnc.tnc.life.fauna.BellwoolSheepEntity))return InteractionResult.FAIL;if(!p.serverLevel().noCollision(entity,entity.getBoundingBox()))return InteractionResult.FAIL;
        for(BlockPos q:BlockPos.betweenClosed(BlockPos.containing(entity.getBoundingBox().minX,entity.getBoundingBox().minY,entity.getBoundingBox().minZ),BlockPos.containing(entity.getBoundingBox().maxX,entity.getBoundingBox().maxY,entity.getBoundingBox().maxZ)))if(!RouteAccess.allowed(p.serverLevel(),q,p.getUUID()))return InteractionResult.FAIL;
        if(entity instanceof PastureAnimal a)a.afterCageRelease();else entity.getPersistentData().putLong("RouteNest",pos.asLong());if(!p.serverLevel().addFreshEntity(entity))return InteractionResult.FAIL;ledger.consume(ticket);tag.remove("Ticket");tag.remove("Species");return InteractionResult.SUCCESS;}
    @Override public void appendHoverText(ItemStack s,net.minecraft.world.level.Level l,List<net.minecraft.network.chat.Component> lines,TooltipFlag flags){lines.add(net.minecraft.network.chat.Component.literal("笼级 "+tier+" · 信任后长按捕获，松开或受伤会中止"));if(s.hasTag()&&s.getTag().contains("Species"))lines.add(net.minecraft.network.chat.Component.literal("已封存："+s.getTag().getString("Species")));}
    public static void clearSessions(){CHANNELS.clear();}
}
