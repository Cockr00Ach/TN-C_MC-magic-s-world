package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 「模型载体物品」——只为给投射物提供一个<b>引擎永远认识的 id</b>。
 *
 * <h2>为什么需要这些物品（一整晚排查的结论）</h2>
 * SpellEngine 渲染投射物时（反编译 {@code net.spell_engine.api.render.CustomModels#render}）：
 * <pre>
 *   BakedModel model = getModelManager().getModel(modelId);   // ① 查已烘焙模型
 *   if (model == null) {
 *       Item item = BuiltInRegistries.ITEM.get(modelId);      // ② ★ 把它当【物品 id】再查一次
 *       if (!item.getDefaultInstance().isEmpty()) {
 *           model = itemRenderer.getModel(stack, ...);        // ③ 用该物品的模型渲染
 *       }
 *   }
 * </pre>
 * 实测结论：
 * <ul>
 *   <li>我们自己的 {@code tnc:projectile/xxx} 这类<b>新模型号</b>引擎不认 ✗ → 紫黑方块
 *       （登记 API {@code CustomModels.registerModelIds} 调了也没用，登记时机也确认早于烘焙）；
 *       唯一能用的是历史上被它认过的号。</li>
 *   <li>而 <b>物品 id</b> 走的是 ② → ③ 的兜底路 ✓ —— 只要注册一个物品、把投射物模型挂在
 *       这个物品的 {@code models/item/<名字>.json} 上，引擎就一定渲染得出来 ✓✓。</li>
 * </ul>
 *
 * <p>所以这些物品<b>没有配方、不进创造栏、玩家永远拿不到</b>，纯粹是"模型 ID 的载体"。
 * 想给某系投射物换外观：改 {@code assets/tnc/models/item/<名字>.json} 即可。
 */
public final class TNProjectileCarriers {

    /** 物品注册表（挂到 mod 事件总线）。 */
    private static final DeferredRegister<Item> CARRIERS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TNMod.MODID);

    /**
     * 火球投射物的载体：法术里写 {@code "model_id": "tnc:fireball"}，
     * 引擎查不到模型时会退化成"取 tnc:fireball 这个物品的模型" → 渲染
     * {@code assets/tnc/models/item/fireball.json} ✓
     */
    public static final RegistryObject<Item> FIREBALL = CARRIERS.register("fireball",
            () -> new Item(new Item.Properties()));

    private TNProjectileCarriers() {
    }

    /** 在 mod 构造期挂到事件总线（TNMod 的静态块里调用）。 */
    public static void register() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        CARRIERS.register(bus);
    }
}
