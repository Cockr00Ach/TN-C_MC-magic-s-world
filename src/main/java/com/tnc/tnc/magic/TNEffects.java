package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * TN-C 自己的状态效果（雷速 / 极速雷风 / 环绕雷球 / 闪电登神）。
 *
 * <h2>为什么要自己注册效果</h2>
 * 原版速度效果每级固定 +20%，拿不到我们要的 +25% / +70%。自定义效果可以在构造时
 * {@code addAttributeModifier} 精确指定数值；而且 {@code MobEffectInstance} 会把
 * 修饰符数值再乘 {@code (amplifier + 1)}，所以<b>一个效果就能覆盖同一类 buff 的多档强度</b>
 * （例如雷速 amp0 = +25%、amp1 = +50%）。
 *
 * <p>法术 JSON 里通过 {@code impact[].action.status_effect.effect_id = "tnc:xxx"} 引用它们，
 * 所以数据层不需要知道任何数值。
 *
 * <h2>不归这里管的部分</h2>
 * "环绕雷球的持续电击""极速雷风的雷电拖尾""闪电登神的无冷却""闪电降低冷却的回蓝"
 * 都是每 tick 或施法瞬间的<b>行为</b>，放在 {@link TnSpellMechanics} 里；
 * 这里只负责"挂一个带数值的效果"。
 */
public final class TNEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TNMod.MODID);

    /** 雷速线统一用的紫色（和魔力条一套配色）。 */
    private static final int COLOR_LIGHTNING = 0x7C5CE0;

    /** 雷速：+25% 移速（amp1 = +50%，被"闪电降低冷却"复用）。 */
    public static final RegistryObject<MobEffect> LIGHTNING_HASTE =
            EFFECTS.register("lightning_haste", () -> new AttributeBuff(
                    COLOR_LIGHTNING, Attributes.MOVEMENT_SPEED, 0.25D,
                    AttributeModifier.Operation.MULTIPLY_BASE));

    /** 极速雷风：+70% 移速（拖尾伤害由机制层处理）。 */
    public static final RegistryObject<MobEffect> LIGHTNING_WIND =
            EFFECTS.register("lightning_wind", () -> new AttributeBuff(
                    0x9C86F5, Attributes.MOVEMENT_SPEED, 0.70D,
                    AttributeModifier.Operation.MULTIPLY_BASE));

    /** 环绕雷球：本身不加属性，只是个"光环开着"的标记（电击逻辑在机制层）。 */
    public static final RegistryObject<MobEffect> ORBITING_THUNDER_ORB =
            EFFECTS.register("orbiting_thunder_orb", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, COLOR_LIGHTNING) {
            });

    /**
     * 闪电登神：所有伤害提升（+30% 攻击力）+ 期间无冷却（无冷却在机制层）。
     *
     * <p>另外把 spell_power 的 12 个学派属性也一起加（如果装了 spell_power）——
     * 法术伤害走的是那些属性，只加攻击力的话"所有伤害"就只算近战了。
     */
    public static final RegistryObject<MobEffect> LIGHTNING_ASCENSION =
            EFFECTS.register("lightning_ascension", () -> AscensionEffect.create());

    private TNEffects() {
    }

    /** 带一个属性修饰符的增益效果。 */
    private static class AttributeBuff extends MobEffect {

        AttributeBuff(int color, Attribute attribute, double amount, AttributeModifier.Operation op) {
            super(MobEffectCategory.BENEFICIAL, color);
            // 1.20.1 的 addAttributeModifier 收的是"UUID 字符串"
            // 固定 UUID：同一个效果重复上不该叠加出多个修饰符
            addAttributeModifier(attribute, uuidFor("tnc:" + attribute.getDescriptionId()), amount, op);
        }
    }

    /** 由名字派生一个稳定的 UUID 字符串。 */
    private static String uuidFor(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    /** 闪电登神：攻击力 + 法术强度一起加。 */
    private static final class AscensionEffect {

        static MobEffect create() {
            MobEffect effect = new AttributeBuff(0xE4DCFF, Attributes.ATTACK_DAMAGE, 0.30D,
                    AttributeModifier.Operation.MULTIPLY_BASE);
            // spell_power 的学派属性（软依赖：没有那个 mod 就跳过）
            for (String school : new String[]{"arcane", "fire", "frost", "healing", "lightning", "soul",
                    "air", "earth", "water", "physical_melee", "physical_ranged", "berserker_melee"}) {
                Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("spell_power", school));
                if (attribute != null) {
                    effect.addAttributeModifier(attribute, uuidFor("tnc:ascension:" + school),
                            0.30D, AttributeModifier.Operation.MULTIPLY_BASE);
                }
            }
            return effect;
        }
    }

    /** MOD 总线上的注册（由 {@link TNMod} 的构造器调用一次）。 */
    public static void register(net.minecraftforge.eventbus.api.IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
        // 火系燃烧线那 5 个效果在自己的类里（TNFireMechanics），一并挂上：
        // DeferredRegister 必须在模组构造期挂到总线上，晚一步就注册不进去了
        TNFireMechanics.register(modEventBus);
        // 风系的效果与飞行机制同理
        TNWindMechanics.register(modEventBus);
    }
}
