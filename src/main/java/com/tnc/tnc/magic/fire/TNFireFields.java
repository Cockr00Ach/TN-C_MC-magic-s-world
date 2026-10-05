package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 火球链里<b>不是"朝准星扔火球"</b>的那两档的入口：熔岳天倾（t4）与炎葬（t5）。
 *
 * <p>为什么单独一个类：{@code TNFireBoltEntity.cast} 只处理"扔出去的火球"，
 * 而这两档是<b>以自己为原点的法阵</b>（一个在头顶、一个在脚下），形态完全不同。
 * 约定和别处一样：<b>不是本链的法术返回 false</b>，派发处可以无脑全调一遍 ✓
 */
public final class TNFireFields {

    private TNFireFields() {
    }

    /**
     * @return 真的放出去了才 true
     */
    public static boolean cast(ServerPlayer player, ResourceLocation spellId) {
        if (player == null || spellId == null || !spellId.getNamespace().equals(TNMod.MODID)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        switch (spellId.getPath()) {
            case "molten_skyfall" -> {
                // 作者要求"在视角上方生成" —— 就放在施法者正上方，法阵会一直跟着他走
                Vec3 above = player.position().add(0.0D, FireSpellRules.SKYFALL_HEIGHT, 0.0D);
                TNSkyfallEntity.cast(level, player, above);
                return true;
            }
            case "flame_burial" -> {
                // 灼烧基数 = 系数 × 绝对基准 × 火法强（炎葬不是一个"命中"，没有命中伤害可用）
                TNBurialEntity.cast(level, player,
                        FireSpellRules.burialScorchBase(FireSpellRules.power(player)));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** 这个法术是不是由本类负责（命令/自检用）。 */
    public static boolean handles(String path) {
        return "molten_skyfall".equals(path) || "flame_burial".equals(path);
    }
}
