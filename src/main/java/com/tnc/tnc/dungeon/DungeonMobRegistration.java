package com.tnc.tnc.dungeon;

import com.tnc.tnc.TNMod;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 12 只地下城小怪 + 弹体的注册点（实体类型 / 属性表 / 刷怪蛋三样）。
 *
 * <p>两个会"进游戏才炸"的坑，这里都堵上了：
 * <ul>
 *   <li><b>属性表</b>必须在 {@link EntityAttributeCreationEvent}（MOD 总线）交上去，
 *       漏了就是 {@code Entity tnc:xxx has no attributes} —— 编译期完全看不出来 ✗；</li>
 *   <li>{@link com.tnc.tnc.TNMod} 的构造函数里必须调用 {@link #register(IEventBus)}，
 *       否则 DeferredRegister 根本没挂到总线上，12 个实体全都不存在 ✗。</li>
 * </ul>
 *
 * <p>刷怪蛋只做调试用（剧情/地下城正式投放靠刷怪表与任务发放），
 * 但有了它，"把某只怪单独摆出来看动画"就从改代码变成一句话 ✓。
 *
 * <p>★ 刷怪蛋的配色目前统一（暗棕壳 + 铜黄点）；等美术定稿再逐只调。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DungeonMobRegistration {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TNMod.MODID);

    public static final DeferredRegister<Item> SPAWN_EGGS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TNMod.MODID);

    /** 远程怪共用的弹体（弩箭 / 火罐）。 */
    public static final RegistryObject<EntityType<DungeonBoltEntity>> BOLT = ENTITY_TYPES.register(
            "dungeon_bolt",
            () -> EntityType.Builder
                    .<DungeonBoltEntity>of(DungeonBoltEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(6)
                    .build("tnc:dungeon_bolt"));

    private static final Map<DungeonSpecies, RegistryObject<EntityType<TNDungeonMob>>> MOB_TYPES =
            new EnumMap<>(DungeonSpecies.class);
    private static final Map<DungeonSpecies, RegistryObject<Item>> EGGS =
            new EnumMap<>(DungeonSpecies.class);

    static {
        for (DungeonSpecies species : DungeonSpecies.values()) {
            MOB_TYPES.put(species, ENTITY_TYPES.register(species.id,
                    () -> EntityType.Builder
                            .<TNDungeonMob>of(TNDungeonMob::new, MobCategory.MONSTER)
                            .sized(species.width, species.height)
                            .clientTrackingRange(10)
                            .build("tnc:" + species.id)));
        }
        for (DungeonSpecies species : DungeonSpecies.values()) {
            EGGS.put(species, SPAWN_EGGS.register(species.id + "_spawn_egg",
                    () -> new ForgeSpawnEggItem(MOB_TYPES.get(species), 0x3A2E26, 0xC8A25A,
                            new Item.Properties())));
        }
        for (DungeonSpecies species : DungeonSpecies.values()) {
            species.bind(MOB_TYPES.get(species));
        }
    }

    private DungeonMobRegistration() {
    }

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
        SPAWN_EGGS.register(bus);
    }

    public static RegistryObject<EntityType<TNDungeonMob>> type(DungeonSpecies species) {
        return MOB_TYPES.get(species);
    }

    public static RegistryObject<Item> egg(DungeonSpecies species) {
        return EGGS.get(species);
    }

    /** 12 张属性表一次交上去；漏掉任何一张，那只怪一生成就抛"没有属性"。 */
    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (DungeonSpecies species : DungeonSpecies.values()) {
            event.put(MOB_TYPES.get(species).get(), TNDungeonMob.createAttributes(species).build());
        }
    }

    /** 刷怪蛋进 TN-C 自己的创造标签页（否则只能在 JEI/指令里看到，等于没法验收动画）。 */
    @SubscribeEvent
    public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TNMod.TNC_TAB.getKey())) {
            for (DungeonSpecies species : DungeonSpecies.values()) {
                event.accept(EGGS.get(species));
            }
        }
    }
}
