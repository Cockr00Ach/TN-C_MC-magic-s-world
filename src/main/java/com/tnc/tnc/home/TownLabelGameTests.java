package com.tnc.tnc.home;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.*;
@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class TownLabelGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void doorLabelTransformRoundTripsWithStableIdentity(GameTestHelper h){
        var entity=EntityType.TEXT_DISPLAY.create(h.getLevel());var id=entity.getUUID();
        TownLabels.configure(entity,new TownLabels.Label("fixture","19","归航银行",BlockPos.ZERO));
        var saved=entity.saveWithoutId(new CompoundTag());var t=saved.getCompound("transformation");
        h.assertTrue(entity.getUUID().equals(id)&&t.getList("scale",5).getFloat(0)==.5f&&t.getList("left_rotation",5).size()==4&&t.getList("translation",5).size()==3,"Full transform decodes instead of falling back to identity");
        var restored=EntityType.TEXT_DISPLAY.create(h.getLevel());restored.load(saved);var after=restored.saveWithoutId(new CompoundTag());h.assertTrue(after.getCompound("transformation").getList("scale",5).getFloat(0)==.5f,"Saved labels keep readable size after world reload");h.succeed();
    }
}
