package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureService;
import com.tnc.tnc.combat.DownedCombat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Private, protected practice plots. No borrowed inventory and no real-combat escape portal. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class TrainingGround {
    public static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(TNMod.MODID,"training_ground"));
    private static final String OWNER="TncPracticeOwner";
    private static final Map<UUID,ServerPlayer> PENDING=new HashMap<>();
    private static final Map<UUID,Husk> TEACHERS=new HashMap<>();
    private TrainingGround(){}
    public static boolean inside(ServerPlayer p){return p.level().dimension().equals(DIMENSION);}
    public static boolean trainer(Entity entity){return entity!=null&&entity.getPersistentData().hasUUID(OWNER);}
    private static boolean owns(Entity entity,ServerPlayer p){return trainer(entity)&&entity.getPersistentData().getUUID(OWNER).equals(p.getUUID());}
    static boolean qualified(ServerPlayer p){return p.isAlive()&&!p.isRemoved()&&!p.isCreative()&&!p.isSpectator()&&!DownedCombat.isDowned(p)&&TemperingData.get(p.server).enrolled(p.getUUID());}
    public static boolean enter(ServerPlayer p){
        if(inside(p))return true;
        if(!qualified(p)){p.sendSystemMessage(Component.literal("进入训练场需要战士天赋，且处于未倒地的生存状态。"));return false;}
        if(!AdventureService.atService(p)||!p.onGround()||com.tnc.tnc.dungeon.DungeonService.active(p)){
            p.sendSystemMessage(Component.literal("请先回到冒险者协会接待员艾琳身边，并站稳后进入训练场。"));return false;
        }
        var target=p.server.getLevel(DIMENSION);
        if(target==null){p.sendSystemMessage(Component.literal("训练场维度尚未载入，请重新进入存档。"));return false;}
        if(PENDING.putIfAbsent(p.getUUID(),p)!=null)return false;
        int plot;
        try{plot=TrainingGroundData.get(p.server).allocate(p.getUUID());}catch(IllegalStateException e){PENDING.remove(p.getUUID(),p);p.sendSystemMessage(Component.literal("训练场暂时没有空余场地。"));return false;}
        int cx=TrainingGroundRules.centerX(plot),cz=TrainingGroundRules.centerZ(plot);var futures=new ArrayList<CompletableFuture<?>>();
        try{for(int x=(cx-12)>>4;x<=(cx+12)>>4;x++)for(int z=(cz-12)>>4;z<=(cz+12)>>4;z++)futures.add(target.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true));}
        catch(RuntimeException e){PENDING.remove(p.getUUID(),p);p.sendSystemMessage(Component.literal("训练场区块暂不可用，请稍后重试。"));return false;}
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenCompleteAsync((ignored,error)->{
            try{
                if(error!=null||PENDING.get(p.getUUID())!=p||!qualified(p)||!AdventureService.atService(p)||!p.onGround())return;
                for(int x:new int[]{cx-12,cx+12})for(int z:new int[]{cz-12,cz+12})if(!target.hasChunkAt(new BlockPos(x,1,z)))return;
                build(target,plot);TrainingGroundData.get(p.server).begin(p);TemperingTraining.cancel(p);
                p.teleportTo(target,cx+.5,2,cz+.5,0,0);settle(p);spawnTeacher(p,plot);TemperingEvents.sync(p);
                p.sendSystemMessage(Component.literal("已进入个人训练场。按修习快捷键开始守势反击；右键蓝色盾卫、金色剑术、青色风刃教官可专项训练。场内保护生命，武器攻击仍消耗耐久。面板可返回，也可在绿色归途台上潜行离场。"));
            }catch(RuntimeException e){com.mojang.logging.LogUtils.getLogger().error("Training ground entry failed",e);p.sendSystemMessage(Component.literal("训练场准备失败，请稍后重试。"));}
            finally{PENDING.remove(p.getUUID(),p);}
        },p.server);
        p.sendSystemMessage(Component.literal("正在准备个人训练场，请保持在协会附近。"));
        return true;
    }
    static void build(ServerLevel level,int plot){
        int cx=TrainingGroundRules.centerX(plot),cz=TrainingGroundRules.centerZ(plot);
        for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++){
            boolean edge=Math.abs(dx)==12||Math.abs(dz)==12;
            level.setBlock(new BlockPos(cx+dx,1,cz+dz),(edge?Blocks.DARK_OAK_PLANKS:(dx==0||dz==0)?Blocks.SMOOTH_STONE:Blocks.POLISHED_ANDESITE).defaultBlockState(),3);
            for(int y=2;y<=7;y++)level.setBlock(new BlockPos(cx+dx,y,cz+dz),(edge&&y<=3?Blocks.STONE_BRICKS:Blocks.AIR).defaultBlockState(),3);
        }
        for(int x:new int[]{-10,10})for(int z:new int[]{-10,10}){
            for(int y=2;y<=5;y++)level.setBlock(new BlockPos(cx+x,y,cz+z),Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(),3);
            level.setBlock(new BlockPos(cx+x,6,cz+z),Blocks.LANTERN.defaultBlockState(),3);
        }
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.setBlock(new BlockPos(cx+dx,1,cz-8+dz),Blocks.EMERALD_BLOCK.defaultBlockState(),3);
        for(int x:new int[]{-6,6})level.setBlock(new BlockPos(cx+x,2,cz+5),Blocks.TARGET.defaultBlockState(),3);
        int[] lessonX={-6,6,-6},lessonZ={-2,-4,7};Block[] pads={Blocks.BLUE_TERRACOTTA,Blocks.YELLOW_TERRACOTTA,Blocks.CYAN_TERRACOTTA};
        for(int i=0;i<3;i++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.setBlock(new BlockPos(cx+lessonX[i]+dx,1,cz+lessonZ[i]+dz),pads[i].defaultBlockState(),3);
    }
    private static void settle(ServerPlayer p){p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.clearFire();}
    static Husk teacher(ServerPlayer p){
        var entity=TEACHERS.get(p.getUUID());return entity!=null&&entity.isAlive()&&!entity.isRemoved()&&entity.level()==p.level()?entity:null;
    }
    private static void disposeTeacher(ServerPlayer p){
        SpecialistTrainers.remove(p);
        var level=p.server.getLevel(DIMENSION);var existing=TEACHERS.remove(p.getUUID());
        if(existing!=null)existing.discard();if(level==null)return;
        int plot=TrainingGroundData.get(p.server).plot(p.getUUID());if(plot<0)return;
        int x=TrainingGroundRules.centerX(plot),z=TrainingGroundRules.centerZ(plot);
        level.getEntitiesOfClass(Husk.class,new AABB(x-13,0,z-13,x+13,9,z+13),e->owns(e,p)).forEach(Entity::discard);
    }
    private static void spawnTeacher(ServerPlayer p,int plot){
        disposeTeacher(p);var teacher=EntityType.HUSK.create(p.serverLevel());if(teacher==null)return;
        teacher.setNoAi(true);teacher.setPersistenceRequired();teacher.setCanPickUpLoot(false);
        teacher.getPersistentData().putUUID(OWNER,p.getUUID());teacher.getPersistentData().putLong("NextStrike",p.server.overworld().getGameTime()+50);
        teacher.setCustomName(Component.literal("协会陪练傀儡 · 守势反击"));teacher.setCustomNameVisible(true);
        teacher.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);teacher.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);teacher.setHealth(200);
        teacher.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CARVED_PUMPKIN));teacher.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.LEATHER_CHESTPLATE));
        teacher.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_SWORD));for(var slot:EquipmentSlot.values())teacher.setDropChance(slot,0);
        teacher.moveTo(TrainingGroundRules.centerX(plot)+.5,2,TrainingGroundRules.centerZ(plot)+3.5,180,0);
        if(!p.serverLevel().addFreshEntity(teacher))throw new IllegalStateException("Could not add practice teacher");
        TEACHERS.put(p.getUUID(),teacher);
        SpecialistTrainers.spawn(p,plot);
    }
    public static boolean leave(ServerPlayer p){
        if(!inside(p))return false;
        var data=TrainingGroundData.get(p.server);var origin=data.origin(p.getUUID());ServerLevel destination=p.server.overworld();
        if(origin!=null){var found=p.server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(origin.dimension())));if(found!=null&&!found.dimension().equals(DIMENSION))destination=found;}
        Vec3 requested=origin==null||!destination.dimension().location().toString().equals(origin.dimension())?
            Vec3.atBottomCenterOf(destination.getSharedSpawnPos()):new Vec3(origin.x(),origin.y(),origin.z());
        var landing=safeLanding(p,destination,requested);
        if(landing==null){p.sendSystemMessage(Component.literal("返回落点暂时受阻，已保留归途记录，请稍后重试。"));return false;}
        disposeTeacher(p);TemperingTraining.cancel(p);
        p.teleportTo(destination,landing.x,landing.y,landing.z,origin==null?0:origin.yaw(),origin==null?0:origin.pitch());
        data.finish(p.getUUID());settle(p);TemperingEvents.sync(p);p.sendSystemMessage(Component.literal("已离开训练场，修习成果已保留。"));return true;
    }
    private static Vec3 safeLanding(ServerPlayer p,ServerLevel level,Vec3 requested){
        double x=Math.max(level.getWorldBorder().getMinX()+2,Math.min(level.getWorldBorder().getMaxX()-2,requested.x));
        double z=Math.max(level.getWorldBorder().getMinZ()+2,Math.min(level.getWorldBorder().getMaxZ()-2,requested.z));
        level.getChunk(net.minecraft.util.Mth.floor(x)>>4,net.minecraft.util.Mth.floor(z)>>4);
        if(requested.y>=level.getMinBuildHeight()+1&&requested.y<level.getMaxBuildHeight()-2){
            var exact=new Vec3(x,requested.y,z);var below=BlockPos.containing(exact.subtract(0,.05,0));
            var box=p.getBoundingBox().move(exact.subtract(p.position()));
            if(level.getBlockState(below).getFluidState().isEmpty()&&!level.getBlockState(below).getCollisionShape(level,below).isEmpty()
                &&level.noCollision(p,box)&&!level.containsAnyLiquid(box))return exact;
        }
        for(int dx:new int[]{0,2,-2,4,-4})for(int dz:new int[]{0,2,-2,4,-4}){
            int px=net.minecraft.util.Mth.floor(x)+dx,pz=net.minecraft.util.Mth.floor(z)+dz;if(!level.getWorldBorder().isWithinBounds(new BlockPos(px,level.getMinBuildHeight(),pz)))continue;level.getChunk(px>>4,pz>>4);
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,px,pz);var feet=new Vec3(px+.5,y,pz+.5);var below=new BlockPos(px,y-1,pz);
            if(y>level.getMinBuildHeight()&&y<level.getMaxBuildHeight()-2&&level.getBlockState(below).getFluidState().isEmpty()
                &&!level.getBlockState(below).getCollisionShape(level,below).isEmpty()&&level.noCollision(p,p.getBoundingBox().move(feet.subtract(p.position())))
                &&!level.containsAnyLiquid(p.getBoundingBox().move(feet.subtract(p.position()))))return feet;
        }
        return null;
    }
    static void practiceTick(ServerPlayer p){
        if(!inside(p))return;
        int plot=TrainingGroundData.get(p.server).plot(p.getUUID());if(plot<0){leave(p);return;}
        if(!qualified(p)){leave(p);return;}
        if(!TrainingGroundRules.contains(plot,p.getX(),p.getY(),p.getZ())){
            p.connection.teleport(TrainingGroundRules.centerX(plot)+.5,2,TrainingGroundRules.centerZ(plot)+.5,0,0);settle(p);
        }
        if(p.isShiftKeyDown()&&Math.abs(p.getX()-(TrainingGroundRules.centerX(plot)+.5))<1.5
                &&Math.abs(p.getZ()-(TrainingGroundRules.centerZ(plot)-7.5))<1.5){leave(p);return;}
        var teacher=teacher(p);if(teacher==null){spawnTeacher(p,plot);teacher=teacher(p);if(teacher==null)return;}
        teacher.setHealth(teacher.getMaxHealth());teacher.clearFire();
        teacher.setPos(TrainingGroundRules.centerX(plot)+.5,2,TrainingGroundRules.centerZ(plot)+3.5);
        var delta=p.position().subtract(teacher.position());float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));teacher.setYRot(yaw);teacher.yBodyRot=yaw;teacher.setYHeadRot(yaw);
        long now=p.server.overworld().getGameTime();var t=teacher.getPersistentData();
        int mode=TemperingTraining.mode(p),period=PracticeDifficulty.period(mode),windup=PracticeDifficulty.windup(mode);
        boolean active=TemperingTraining.active(p);
        teacher.setCustomName(Component.literal("协会陪练 · "+PracticeDifficulty.name(mode)+" · "+(!active?"按修习键开始":TemperingTraining.blockCount(p)<3?"举盾 "+TemperingTraining.blockCount(p)+"/3":TemperingTraining.remaining(p,now)>0?"现在反击！":"再次格挡刷新窗口")));
        if(!active){t.putLong("NextStrike",now+period);t.putBoolean("Windup",false);return;}
        if(teacher.distanceToSqr(p)>12.25){
            t.putLong("NextStrike",now+period);t.putBoolean("Windup",false);
            if(now%40==0)p.displayClientMessage(Component.literal("请靠近陪练，正面举盾；盾挡后可向前补位。"),true);
            return;
        }
        long next=t.getLong("NextStrike");
        if(now>=next-windup&&!t.getBoolean("Windup")){
            t.putBoolean("Windup",true);p.serverLevel().sendParticles(ParticleTypes.CRIT,teacher.getX(),teacher.getY()+1.8,teacher.getZ(),5,.18,.15,.18,0);
            p.serverLevel().playSound(null,teacher.blockPosition(),SoundEvents.NOTE_BLOCK_HAT.value(),SoundSource.PLAYERS,.5f,.7f);
        }
        if(now>=next){
            teacher.swing(InteractionHand.MAIN_HAND);p.hurt(p.damageSources().mobAttack(teacher),4);
            t.putInt("Swings",t.getInt("Swings")+1);t.putLong("NextStrike",now+period);t.putBoolean("Windup",false);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p)practiceTick(p);}
    static void resetRhythm(ServerPlayer p){var t=teacher(p);if(t!=null){t.getPersistentData().putLong("NextStrike",p.server.overworld().getGameTime()+PracticeDifficulty.period(TemperingTraining.mode(p)));t.getPersistentData().putBoolean("Windup",false);}}
    static boolean cyclePractice(ServerPlayer p){
        if(!inside(p)||!qualified(p)||TemperingTraining.active(p))return false;
        TrainingGroundData.get(p.server).cycle(p.getUUID());resetRhythm(p);return true;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void safePlayer(LivingHurtEvent e){if(e.getEntity() instanceof ServerPlayer p&&inside(p)){e.setCanceled(true);if(e.getAmount()>0&&owns(e.getSource().getEntity(),p)){TemperingTraining.miss(p);p.displayClientMessage(Component.literal("未挡住这次挥击。观察前摇，正面举盾后再反击。"),true);}}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void trainerAttack(LivingAttackEvent e){
        if(trainer(e.getEntity())&&(!(e.getSource().getEntity() instanceof ServerPlayer p)||!owns(e.getEntity(),p)
            ||!inside(p)||!(e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                ||SpecialistTrainers.isWindTarget(e.getEntity())&&e.getSource().is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)&&WindInfusion.isWind(p.getMainHandItem()))))e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void trainerHurt(LivingHurtEvent e){if(trainer(e.getEntity()))e.setAmount(Math.min(1,e.getAmount()));}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void shield(ShieldBlockEvent e){if(e.getEntity() instanceof ServerPlayer p&&inside(p)&&owns(e.getDamageSource().getEntity(),p))e.setShieldTakesDamage(false);}
    @SubscribeEvent public static void experience(LivingExperienceDropEvent e){if(trainer(e.getEntity()))e.setDroppedExperience(0);}
    @SubscribeEvent public static void drops(LivingDropsEvent e){if(trainer(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e){if(e.getPlayer() instanceof ServerPlayer p&&inside(p))e.setCanceled(true);}
    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent e){if(e.getEntity() instanceof ServerPlayer p&&inside(p))e.setCanceled(true);}
    private static boolean practiceItem(ItemStack stack){return stack.isEmpty()||WindInfusion.weapon(stack)||stack.getItem() instanceof ShieldItem||stack.isEdible();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void useItem(PlayerInteractEvent.RightClickItem e){if(e.getEntity() instanceof ServerPlayer p&&inside(p)&&!practiceItem(e.getItemStack())){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void useBlock(PlayerInteractEvent.RightClickBlock e){if(e.getEntity() instanceof ServerPlayer p&&inside(p)){e.setUseBlock(Event.Result.DENY);if(!practiceItem(e.getItemStack()))e.setUseItem(Event.Result.DENY);}}
    @SubscribeEvent public static void tool(BlockEvent.BlockToolModificationEvent e){if(e.getPlayer() instanceof ServerPlayer p&&inside(p))e.setCanceled(true);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p&&inside(p))leave(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){disposeTeacher(p);PENDING.remove(p.getUUID(),p);}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p&&e.getFrom().equals(DIMENSION)){disposeTeacher(p);TrainingGroundData.get(p.server).finish(p.getUUID());}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){PENDING.clear();TEACHERS.clear();}
}
