package com.tnc.tnc.mount;

import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class LandMounts {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"tnc");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"tnc");
    public static final RegistryObject<EntityType<LandMount>> UNICORN=type("rainbow_unicorn",1.05f,1.7f);
    public static final RegistryObject<EntityType<LandMount>> GOAT=type("mountain_goat",1.0f,1.5f);
    public static final RegistryObject<EntityType<LandMount>> WOLF=type("snow_wolf",1.05f,1.45f);
    private static RegistryObject<EntityType<LandMount>> type(String id,float w,float h){return TYPES.register(id,()->EntityType.Builder.<LandMount>of(LandMount::new,MobCategory.CREATURE).sized(w,h).clientTrackingRange(10).updateInterval(1).build("tnc:"+id));}
    public static final RegistryObject<Item> UNICORN_EGG=ITEMS.register("rainbow_unicorn_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(UNICORN,0xf1ede4,0xb62fcc,new Item.Properties()));
    public static final RegistryObject<Item> GOAT_EGG=ITEMS.register("mountain_goat_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(GOAT,0xd7cbb0,0x665441,new Item.Properties()));
    public static final RegistryObject<Item> WOLF_EGG=ITEMS.register("snow_wolf_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(WOLF,0xb8c4cc,0x526f90,new Item.Properties()));
    public static void register(IEventBus bus){TYPES.register(bus);ITEMS.register(bus);bus.addListener(LandMounts::attributes);}
    private static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent e){for(var t:java.util.List.of(UNICORN,GOAT,WOLF))e.put(t.get(),LandMount.attributes().build());}
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.SPAWN_EGGS){e.accept(UNICORN_EGG.get());e.accept(GOAT_EGG.get());e.accept(WOLF_EGG.get());}}
    private LandMounts(){}
}
