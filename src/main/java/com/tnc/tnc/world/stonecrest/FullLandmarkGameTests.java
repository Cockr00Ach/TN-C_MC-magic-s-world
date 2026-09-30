package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

/** Opt-in real-size test, never registered in normal clients or ordinary smoke tests. */
@GameTestHolder("tnc")
public final class FullLandmarkGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!Boolean.getBoolean("tnc.fullLandmarkTests")) return List.of();
        var result=new ArrayList<TestFunction>(); int index=0;
        for (String asset:List.of("gothic_cathedral","heroskand_complex","heroskand_estate_v2","elden_coastal_castle","end_pvp_island")) {
            if (asset.startsWith("heroskand_") && !net.minecraftforge.fml.ModList.get().isLoaded("conquest")) {
                com.mojang.logging.LogUtils.getLogger().warn("[TN-C Full Test] SKIPPED {}: Conquest registry unavailable; never test a palace with its blocks substituted by air",asset);
                continue;
            }
            int offset=index++*2048;
            result.add(new TestFunction("full_landmarks","full_"+asset,"tnc:building_test_empty",
                    net.minecraft.world.level.block.Rotation.NONE,160000,0,true,h->landmark(h,asset,offset)));
        }
        return result;
    }

    private static void landmark(GameTestHelper helper,String asset,int offset) {
        var level=helper.getLevel(); var m=StonecrestManifest.get(asset);
        var origin=new BlockPos(8192+offset,m.sunken()?-42:m.floating()?128:-32,8192);
        // Force a known pre-existing facility into the real building's write volume.
        BlockPos chest=null;
        for (int z=m.dimensions().getZ()/2;chest==null && z<m.dimensions().getZ();z++)
            for (int x=m.dimensions().getX()/2;x<m.dimensions().getX();x++) if (m.buildingAt(x,z)) {
                chest=origin.offset(x,80,z); break;
            }
        if (chest==null) throw new IllegalStateException("No occupied cathedral column");
        final var protectedChest=chest;
        if (!m.floating()) level.setBlock(protectedChest,Blocks.CHEST.defaultBlockState(),18);
        LargeLandmarkJobs.request(level,asset,origin);
        var key=new LandmarkData.Job(asset,origin).key();
        int[] checked={0}; int[] matches={0};
        helper.succeedWhen(()->{
            var j=LandmarkData.get(level).jobs.get(key);
            if (j!=null && j.phase<0) throw new IllegalStateException("Full "+asset+" failed: "+j.error);
            if (j==null || j.phase!=3) throw new GameTestAssertException("Waiting for FULL "+asset+" "+(j==null?"queued":j.phase+":"+j.tile+":"+j.piece));
            if (!m.floating() && !level.getBlockState(protectedChest).is(Blocks.CHEST)) throw new IllegalStateException("Full build destroyed original facility");
            // Sample real states from EVERY piece after completion, not just a mock fixture or marker.
            for (int n=0;n<8 && checked[0]<m.pieces().size();n++,checked[0]++) {
                var p=m.pieces().get(checked[0]); var t=level.getStructureManager().get(p.resource()).orElseThrow();
                var tag=t.save(new net.minecraft.nbt.CompoundTag());
                var palette=tag.getList("palette",net.minecraft.nbt.Tag.TAG_COMPOUND);
                var blocks=tag.getList("blocks",net.minecraft.nbt.Tag.TAG_COMPOUND);
                try {
                    for (int i=0;i<blocks.size();i+=Math.max(1,blocks.size()/5)) {
                        var b=blocks.getCompound(i); var xyz=b.getList("pos",net.minecraft.nbt.Tag.TAG_INT);
                        var destination=origin.offset(p.offset()).offset(xyz.getInt(0),xyz.getInt(1),xyz.getInt(2));
                        if (j.preserved.contains(destination)) continue;
                        var expected=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),palette.getCompound(b.getInt("state")));
                        // Fluid, plant and falling-block physics can legitimately change states; structural solids may not vanish.
                        if (expected.isAir() || !expected.isSolidRender(level,destination)) continue;
                        if (level.getBlockState(destination).isAir()) throw new IllegalStateException("Missing source architecture at "+destination+" in "+p.resource());
                        matches[0]++;
                    }
                } finally {level.getStructureManager().remove(p.resource());}
            }
            if (checked[0]<m.pieces().size()) throw new GameTestAssertException("Auditing full "+asset+" "+checked[0]);
            if (matches[0]<1000) throw new IllegalStateException("Insufficient geometry verification: "+matches[0]);
            com.mojang.logging.LogUtils.getLogger().info("[TN-C Full Test] {} complete: {} pieces, {} structural samples present, preservation checked",asset,checked[0],matches[0]);
        });
    }
}
