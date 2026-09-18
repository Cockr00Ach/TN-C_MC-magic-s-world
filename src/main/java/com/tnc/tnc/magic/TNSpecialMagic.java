package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 领域魔法（设计总纲 §12.F 的特殊魔法）：<b>强度只与亲和力有关</b>。
 *
 * <h2>文档的三条规则怎么落</h2>
 * <ul>
 *   <li>"无法升级" —— 它不是链，学会就是学会（见 {@link SpellCatalog.Special}）。</li>
 *   <li>"获得路线即获得" —— {@code /tnc special grant}（委托系统以后接上）。</li>
 *   <li><b>"等级只与亲和力有关"</b> —— 就是本类：按亲和力给该元素学派属性的加成。</li>
 * </ul>
 *
 * <h2>为什么这么做</h2>
 * 法术伤害走的是 {@code spell_power:<school>} 属性，所以"亲和力 → 强度"最自然的表达
 * 就是往那个属性上挂修饰符。这样引擎自己会算伤害，我们不需要碰伤害管线。
 *
 * <h2>两个容易踩的坑（都在这里处理了）</h2>
 * <ol>
 *   <li><b>亲和力变了要重算</b>：玩家用 {@code /tnc affinity set} 改亲和力之后，
 *       加成必须跟着变。所以这里定期刷新，而不是"学会那一刻算一次"。</li>
 *   <li><b>用 transient 修饰符</b>：永久修饰符会写进玩家 NBT，改了亲和力清不干净 ✗；
 *       transient 的只活在内存里，登录后重算一次即可，而且用<b>固定 UUID</b>，
 *       重复 add 会替换同一条，天然幂等。</li>
 * </ol>
 *
 * <p>数值调这里：{@link #BONUS_PER_POINT}（每点亲和力给多少）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNSpecialMagic {

    /** 每点亲和力给多少加成（亲和力 0~6 → 0%~30%）。 */
    private static final double BONUS_PER_POINT = 0.05D;

    /** 多久重算一次（tick）。5 秒一次，开销可以忽略，又能及时反映亲和力变化。 */
    private static final int REFRESH_INTERVAL_TICKS = 100;

    private TNSpecialMagic() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        if (player.level().getGameTime() % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        MagicStone.get(player).ifPresent(data -> refresh(player, data));
    }

    /** 按当前亲和力与已获得的领域魔法，重算七个学派属性上的修饰符。 */
    public static void refresh(ServerPlayer player, MagicStoneData data) {
        for (Element element : Element.values()) {
            // Element 里已经写好对应关系（雷=lightning、光=healing、暗=soul …）
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(
                    ResourceLocation.fromNamespaceAndPath("spell_power", schoolOf(element)));
            if (attribute == null) {
                continue;                       // 这个学派没装 spell_power，跳过
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            UUID id = uuidFor(element);
            AttributeModifier existing = instance.getModifier(id);
            double amount = data.hasSpecial(element)
                    ? data.getAffinity(element) * BONUS_PER_POINT
                    : 0.0D;
            if (amount <= 0.0D) {
                if (existing != null) {
                    instance.removeModifier(id);
                }
                continue;
            }
            if (existing != null && Math.abs(existing.getAmount() - amount) < 1e-6D) {
                continue;                       // 数值没变就别动，省得每 5 秒改一次属性
            }
            if (existing != null) {
                instance.removeModifier(id);
            }
            instance.addTransientModifier(new AttributeModifier(id,
                    "tnc_special_" + element.id(), amount, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    /**
     * 元素 → 引擎学派 id。
     *
     * <p>⚠️ 不能直接用 Element.id()：光和暗的写法不一样 ——
     * 光对应 {@code spell_power:healing}、暗对应 {@code spell_power:soul}
     * （不是 light / dark ✗，写错就是"属性取不到、加成静默失效"）。
     */
    private static String schoolOf(Element element) {
        return switch (element) {
            case LIGHTNING -> "lightning";
            case FIRE -> "fire";
            case WIND -> "air";
            case WATER -> "water";
            case EARTH -> "earth";
            case LIGHT -> "healing";
            case DARK -> "soul";
        };
    }

    /** 每个元素一个固定 UUID（同一个元素反复施加只会替换，不会叠加）。 */
    private static UUID uuidFor(Element element) {
        return UUID.nameUUIDFromBytes(("tnc:special:" + element.id()).getBytes(StandardCharsets.UTF_8));
    }

    /** 供自检/命令查询：当前这个玩家从这个元素拿到多少加成。 */
    public static double bonusFor(ServerPlayer player, MagicStoneData data, Element element) {
        return data.hasSpecial(element) ? data.getAffinity(element) * BONUS_PER_POINT : 0.0D;
    }
}
