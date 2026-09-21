package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * TN-C 自己的 NPC 实体注册表。
 *
 * <p><b>为什么我们自己注册，而不是用原作者那套</b>：原作者的 {@code ysjxmodel} 是闭源
 * （All Rights Reserved），而他的皮肤 NPC（{@code PlayerSkinNpcEntity}）把每个 NPC 的贴图
 * <b>硬编码在静态字段里</b> —— 既改不了 jar，也没法加人。所以 TN-C 的 NPC 一律自己注册。
 *
 * <p>这套写法就是后面 cava（铁匠）/ riggen（黑市商人）的模板：加一个实体 = 这里加一行。
 */
public final class TNNpcs {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TNMod.MODID);

    /**
     * A. Self —— 酒馆老板（中立派系）。
     *
     * <p>参数说明（Forge 1.20.1）：
     * <ul>
     *   <li>{@code sized(0.6F, 1.8F)} —— 玩家尺寸（宽 0.6 / 高 1.8），皮肤模型才不会被拉伸；</li>
     *   <li>{@code clientTrackingRange(10)} —— 10 个区块内客户端会收到它（要看得见人）；</li>
     *   <li>{@code MobCategory.MISC} —— 不参与任何刷怪规则。他是剧情 NPC，只能被我们放出来，
     *       不会自然生成，也不会因为玩家走远而消失（要不要消失由实体类里
     *       {@code removeWhenFarAway} 决定，我们让它不消失）。</li>
     * </ul>
     */
    public static final RegistryObject<EntityType<SelfNpcEntity>> SELF =
            ENTITY_TYPES.register("self", () -> EntityType.Builder
                    .of(SelfNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("tnc:self"));

    private TNNpcs() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
