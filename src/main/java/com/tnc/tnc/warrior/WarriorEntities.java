package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 战士系统的实体注册表（目前只有演武场教官）。 */
public final class WarriorEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TNMod.MODID);

    public static final RegistryObject<EntityType<InstructorEntity>> INSTRUCTOR =
        ENTITY_TYPES.register("instructor", () -> EntityType.Builder
            .of(InstructorEntity::new, MobCategory.MISC)
            .sized(0.7F, 1.95F)
            .clientTrackingRange(10)
            .updateInterval(2)
            .build("tnc:instructor"));

    public static final RegistryObject<EntityType<TestDummyEntity>> TEST_DUMMY =
        ENTITY_TYPES.register("test_dummy", () -> EntityType.Builder.of(TestDummyEntity::new,MobCategory.MISC)
            .sized(.8f,2f).clientTrackingRange(10).updateInterval(2).build("tnc:test_dummy"));

    public static final RegistryObject<EntityType<WarriorArrowEntity>> ORNATE_ARROW =
        ENTITY_TYPES.register("ornate_arrow",()->EntityType.Builder.<WarriorArrowEntity>of(WarriorArrowEntity::new,MobCategory.MISC)
            .sized(.5f,.5f).clientTrackingRange(8).updateInterval(20).build("tnc:ornate_arrow"));

    private WarriorEntities() {
    }

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
        bus.addListener(WarriorEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(TEST_DUMMY.get(),TestDummyEntity.attributes().build());
        event.put(INSTRUCTOR.get(), InstructorEntity.createAttributes().build());
    }
}
