package com.tnc.tnc.dungeon.mirror;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MirrorEntities {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "tnc");
    public static final RegistryObject<EntityType<EchoDouble>> DOUBLE = TYPES.register("echo_double", () ->
            EntityType.Builder.of(EchoDouble::new, MobCategory.MISC).sized(.6f, 1.8f).clientTrackingRange(12)
                    .updateInterval(1).build("tnc:echo_double"));
    public static final RegistryObject<EntityType<EchoCore>> CORE = TYPES.register("echo_core", () ->
            EntityType.Builder.of(EchoCore::new, MobCategory.MISC).sized(.60f, .56f).fireImmune()
                    .clientTrackingRange(12).build("tnc:echo_core"));

    private MirrorEntities() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(MirrorEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(DOUBLE.get(), Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 36)
                .add(Attributes.ATTACK_DAMAGE, 1).add(Attributes.FOLLOW_RANGE, 42)
                .add(Attributes.MOVEMENT_SPEED, .30).build());
        event.put(CORE.get(), Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).build());
    }
}
