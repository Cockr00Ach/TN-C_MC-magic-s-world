package com.tnc.tnc.tavern;

import com.tnc.tnc.dialogue.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Persistent, stationary patrons. The rendered hip and interaction box start at the seat. */
public final class TavernGuestEntity extends Villager {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> LOW_SEAT=net.minecraft.network.syncher.SynchedEntityData.defineId(TavernGuestEntity.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private String seatId="";
    private float fixedYaw;
    private Vec3 seat=Vec3.ZERO;
    private int persona;
    private List<String> lines=List.of("今晚的热汤很香，坐一会儿吧。");
    public TavernGuestEntity(EntityType<? extends Villager> type,Level level) {
        super(type,level);setNoAi(true);setNoGravity(true);setInvulnerable(true);setPersistenceRequired();setAge(0);
    }
    public String seatId(){return seatId;}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(LOW_SEAT,false);}
    public boolean lowSeat(){return entityData.get(LOW_SEAT);}
    public void configure(TavernAtmosphere.Seat spec,BlockPos origin) {
        seatId=spec.id();fixedYaw=spec.yaw();persona=spec.persona();lines=spec.lines();
        entityData.set(LOW_SEAT,spec.height()<.35);
        setCustomName(Component.literal(spec.name()));setCustomNameVisible(false);
        VillagerType[] types={VillagerType.PLAINS,VillagerType.TAIGA,VillagerType.DESERT,VillagerType.SAVANNA,VillagerType.SNOW,VillagerType.SWAMP,VillagerType.JUNGLE};
        VillagerProfession[] jobs={VillagerProfession.FARMER,VillagerProfession.LIBRARIAN,VillagerProfession.FISHERMAN,VillagerProfession.SHEPHERD,VillagerProfession.TOOLSMITH,VillagerProfession.CARTOGRAPHER,VillagerProfession.CLERIC,VillagerProfession.BUTCHER,VillagerProfession.ARMORER,VillagerProfession.MASON,VillagerProfession.LEATHERWORKER};
        setVillagerData(new VillagerData(types[Math.floorMod(persona,types.length)],jobs[Math.floorMod(persona/2,jobs.length)],1+Math.floorMod(persona,3)));
        var p=origin.offset(spec.local());var offset=level().getBlockState(p).getOffset(level(),p);
        seat=new Vec3(p.getX()+.5+offset.x,p.getY()+spec.height()+offset.y,p.getZ()+.5+offset.z);anchor();
    }
    private void anchor(){setPos(seat.x,seat.y,seat.z);setYRot(fixedYaw);setYHeadRot(fixedYaw);setYBodyRot(fixedYaw);setDeltaMovement(Vec3.ZERO);}
    @Override public void tick(){super.tick();if(!level().isClientSide)anchor();}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canChangeDimensions(){return false;}
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){return null;}
    @Override public InteractionResult mobInteract(net.minecraft.world.entity.player.Player player,InteractionHand hand) {
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(player instanceof ServerPlayer serverPlayer&&distanceToSqr(player)<=36) {
            var name=getCustomName()==null?"酒馆客人":getCustomName().getString();
            var script=new DialogueScript(ResourceLocation.fromNamespaceAndPath("tnc","tavern/"+(seatId.isEmpty()?"visitor":seatId)),null,null,List.of(),null,List.of(),List.of(),lines.stream().map(line->new DialogueScript.Line(name,line)).toList());
            DialogueNetwork.openFor(serverPlayer,DialogueLoader.get(serverPlayer.server.getResourceManager(),script.id()).orElse(script),getUUID());
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putString("TavernSeat",seatId);tag.putFloat("TavernYaw",fixedYaw);tag.putInt("TavernPersona",persona);
        tag.putBoolean("TavernLowSeat",lowSeat());
        tag.putDouble("SeatX",seat.x);tag.putDouble("SeatY",seat.y);tag.putDouble("SeatZ",seat.z);
        var list=new net.minecraft.nbt.ListTag();lines.forEach(line->list.add(net.minecraft.nbt.StringTag.valueOf(line)));tag.put("TavernLines",list);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);seatId=tag.getString("TavernSeat");fixedYaw=tag.getFloat("TavernYaw");persona=tag.getInt("TavernPersona");
        entityData.set(LOW_SEAT,tag.getBoolean("TavernLowSeat"));
        seat=new Vec3(tag.contains("SeatX")?tag.getDouble("SeatX"):getX(),tag.contains("SeatY")?tag.getDouble("SeatY"):getY(),tag.contains("SeatZ")?tag.getDouble("SeatZ"):getZ());
        var list=tag.getList("TavernLines",8);if(!list.isEmpty())lines=list.stream().map(net.minecraft.nbt.Tag::getAsString).limit(2).toList();
        setNoAi(true);setNoGravity(true);setInvulnerable(true);setPersistenceRequired();setAge(0);
    }
}
