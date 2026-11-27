package com.tnc.tnc.life.seawild;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import java.util.EnumMap;

@Mod.EventBusSubscriber(modid=TNMod.MODID, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SeaWildRegistry {
    public static final EnumMap<SeaKind,EntityType<SeaCreature>> TYPES = new EnumMap<>(SeaKind.class);
    private SeaWildRegistry() {}

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, registry -> {
            for (var kind : SeaKind.values()) {
                var id = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, kind.id);
                var type = EntityType.Builder.<SeaCreature>of((t,l) -> new SeaCreature(t,l,kind),kind.category)
                        .sized(kind.width,kind.height).clientTrackingRange(6).updateInterval(3).build(id.toString());
                registry.register(id, type);
                TYPES.put(kind,type);
            }
        });
    }

    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent e) {
        TYPES.forEach((kind,type) -> e.put(type,Mob.createMobAttributes().add(Attributes.MAX_HEALTH,kind.school()?8:5)
                .add(Attributes.MOVEMENT_SPEED,.20).build()));
    }

    @SubscribeEvent public static void spawn(SpawnPlacementRegisterEvent e) {
        TYPES.forEach((kind,type) -> e.register(type,SpawnPlacements.Type.IN_WATER,Heightmap.Types.OCEAN_FLOOR,
                (t,level,reason,pos,rng) -> canSpawn(kind,level,pos), SpawnPlacementRegisterEvent.Operation.REPLACE));
    }

    static boolean canSpawn(SeaKind kind, ServerLevelAccessor l, BlockPos pos) {
        if (l.getLevel().dimension()!=Level.OVERWORLD || !l.hasChunkAt(pos)
                || !l.getBiome(pos).is(BiomeTags.IS_OCEAN) || !l.getFluidState(pos).is(FluidTags.WATER)) return false;
        if (!l.getBlockState(pos).getCollisionShape(l,pos).isEmpty()) return false;
        int floor=l.getHeight(Heightmap.Types.OCEAN_FLOOR,pos.getX(),pos.getZ());
        int aboveFloor=pos.getY()-floor, depth=l.getSeaLevel()-pos.getY();
        if (depth<2 || aboveFloor<0) return false;
        boolean habitat = switch(kind) {
            case REEF -> aboveFloor<=13 && depth<=20;
            case SILVER -> aboveFloor>=4 && depth>=3;
            case JELLY -> aboveFloor>=1 && depth>=4 && depth<=27;
            case SLUG -> aboveFloor<=3;
        };
        if (!habitat || kind!=SeaKind.SLUG && !l.getFluidState(pos.above()).is(FluidTags.WATER)) return false;
        var group=new AABB(pos).inflate(kind.school()?22:12,8,kind.school()?22:12);
        return l.getLevel().getEntitiesOfClass(SeaCreature.class,group,e -> e.kind==kind).size() < (kind.school()?2:5);
    }
}
