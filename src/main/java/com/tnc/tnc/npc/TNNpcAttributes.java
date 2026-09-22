package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * NPC 的通用侧接线：属性 + 刷怪蛋。
 *
 * <p>{@code EntityAttributeCreationEvent} 是 <b>MOD 总线</b>事件，必须在注册之后、实体被使用之前
 * 把属性表交上去 —— 漏了的话实体一生成就抛
 * <i>"Entity tnc:self has no attributes"</i>，而且是**进游戏才炸**，编译期看不出来。
 *
 * <p>刷怪蛋只作为**调试与放置手段**（剧情正式上线后由生成逻辑/任务发放），
 * 但它让"把 Self 扔到岛上"变成一句话的事，所以先做上。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TNNpcAttributes {

    public static final DeferredRegister<Item> SPAWN_EGGS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TNMod.MODID);

    /** Self 的刷怪蛋（颜色是占位：深棕头发 + 米色围裙）。 */
    public static final RegistryObject<Item> SELF_SPAWN_EGG = SPAWN_EGGS.register("self_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    TNNpcs.SELF, 0x4A3424, 0xD6C8AC, new Item.Properties()));

    /** cava 的刷怪蛋（配色：炉火暗红 + 铁灰）。 */
    public static final RegistryObject<Item> CAVA_SPAWN_EGG = SPAWN_EGGS.register("cava_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    TNNpcs.CAVA, 0x5A2A18, 0x8A8A8A, new Item.Properties()));

    private TNNpcAttributes() {
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus modEventBus) {
        SPAWN_EGGS.register(modEventBus);
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(TNNpcs.SELF.get(), TnDialogueNpc.attributes().build());
        event.put(TNNpcs.CAVA.get(), TnDialogueNpc.attributes().build());
    }
}
