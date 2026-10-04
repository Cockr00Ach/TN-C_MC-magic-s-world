package com.tnc.tnc.magic;

import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class LoadoutNormalizationGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void normalizationKeepsExistingTopSlotAndIndependentSpellAndIsIdempotent(GameTestHelper h){
        var data=new MagicStoneData();var low=ResourceLocation.fromNamespaceAndPath("tnc","water_ball");var top=ResourceLocation.fromNamespaceAndPath("tnc","water_cannon");var independent=ResourceLocation.fromNamespaceAndPath("tnc","chaos_magic");
        data.learn(low);data.learn(top);data.learn(independent);for(int i=0;i<MagicStoneData.LOADOUT_SLOTS;i++)data.clearSlot(i);
        data.setSlot(0,low);data.setSlot(8,top);data.setSlot(3,independent);data.setSlot(10,ResourceLocation.fromNamespaceAndPath("tnc","not_learned"));data.normalizeLoadout();
        h.assertTrue(data.getSlot(0)==null&&top.equals(data.getSlot(8))&&independent.equals(data.getSlot(3))&&data.getSlot(10)==null,"Normalize promotes each chain once, retains the already assigned top key and independent spell, and removes unlearned bindings");
        data.normalizeLoadout();h.assertTrue(data.loadoutCount()==2&&top.equals(data.getSlot(8))&&independent.equals(data.getSlot(3)),"Repeated wand synchronization cannot reorder or duplicate bindings");h.succeed();
    }
}
