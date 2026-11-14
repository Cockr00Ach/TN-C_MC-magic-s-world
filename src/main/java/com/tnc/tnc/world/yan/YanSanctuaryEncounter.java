package com.tnc.tnc.world.yan;

import com.tnc.tnc.boss.YanDarkBossEntity;
import com.tnc.tnc.npc.TNNpcs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = "tnc")
public final class YanSanctuaryEncounter extends SavedData {
    private static final TicketType<Integer> ARRIVAL_TICKET = TicketType.create("tnc_yan_altar", Integer::compare, 100);
    private static final String MARK = "TncYanSanctuaryBoss";
    private UUID boss;

    static YanSanctuaryEncounter get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(YanSanctuaryEncounter::load, YanSanctuaryEncounter::new, "tnc_yan_sanctuary");
    }

    private static YanSanctuaryEncounter load(CompoundTag tag) {
        var state = new YanSanctuaryEncounter();
        if (tag.hasUUID("boss")) state.boss = tag.getUUID("boss");
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (boss != null) tag.putUUID("boss", boss);
        return tag;
    }

    String summon(ServerLevel level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return "peaceful";
        if (boss != null) return "active";
        var at = YanSanctuaryLayout.BOSS;
        level.getChunkSource().addRegionTicket(ARRIVAL_TICKET, new ChunkPos(at), 2, 0);
        level.getChunkAt(at);
        var entity = TNNpcs.YAN_DARK.get().create(level);
        if (entity == null) return "unavailable";
        entity.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, 0, 0);
        if (!level.noCollision(entity)) return "obstructed";
        entity.setPersistenceRequired();
        entity.restrictTo(at, 64);
        entity.getPersistentData().putBoolean(MARK, true);
        boss = entity.getUUID();
        if (!level.addFreshEntity(entity)) {
            boss = null;
            return "unavailable";
        }
        setDirty();
        return "summoned";
    }

    @SubscribeEvent
    public static void loadBoss(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !YanSanctuary.KEY.equals(level.dimension())
                || !(event.getEntity() instanceof YanDarkBossEntity entity)
                || !entity.getPersistentData().getBoolean(MARK)) return;
        var state = get(level);
        if (state.boss == null) {
            state.boss = entity.getUUID();
            state.setDirty();
        } else if (!entity.getUUID().equals(state.boss)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void removed(EntityLeaveLevelEvent event) {
        var reason = event.getEntity().getRemovalReason();
        if (reason != null && reason.shouldDestroy()) clear(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void defeated(LivingDeathEvent event) {
        if (!event.isCanceled()) clear(event.getEntity());
    }

    private static void clear(net.minecraft.world.entity.Entity removed) {
        if (!(removed instanceof YanDarkBossEntity entity)
                || !(entity.level() instanceof ServerLevel level) || !YanSanctuary.KEY.equals(level.dimension())
                || !entity.getPersistentData().getBoolean(MARK)) return;
        var state = get(level);
        if (entity.getUUID().equals(state.boss)) {
            state.boss = null;
            state.setDirty();
        }
    }
}
