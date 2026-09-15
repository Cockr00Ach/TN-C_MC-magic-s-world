package net.spell_engine.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.casting.SpellCast;

import java.util.List;

/**
 * 【开发用桩】对应真引擎的 {@code net.spell_engine.api.event.CombatEvents}。
 *
 * <p>存在的意义：{@code SpellEngineManaHook} 的注册代码整段包在 try/catch 里，
 * 所以**字段名/签名对不上会被静默吞掉**，表现就是"施法不扣魔力"却毫无提示。
 * 有了这个桩，dev 环境就能真的走一遍
 * {@code CombatEvents.SPELL_CAST.register(我们的监听器)} ——
 * 证明类名、字段名、泛型、{@code register} 签名、以及我们 lambda 的形状都是对的。
 *
 * <p>真引擎里还有 ENTITY_ATTACK / ITEM_USE，我们用不到就不桩了。
 * 构造函数保持 public（javap 实测真引擎就是 public），不做无谓的偏离。
 */
public class CombatEvents {

    public static final Event<SpellCast> SPELL_CAST = new Event<>();

    /** 对应真引擎里的 {@code CombatEvents$SpellCast} 函数式接口。 */
    public interface SpellCast {
        void onSpellCast(Args args);
    }

    /** 对应真引擎里的 {@code CombatEvents$SpellCast$Args}（record，5 个分量）。 */
    public record Args(Player caster, SpellInfo spell, List<Entity> targets,
                       net.spell_engine.internals.casting.SpellCast.Action action, float progress) {
    }
}
