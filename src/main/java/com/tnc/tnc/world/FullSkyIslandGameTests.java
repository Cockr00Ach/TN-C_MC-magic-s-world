package com.tnc.tnc.world;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import java.nio.file.*;
import java.util.*;

/** Opt-in complete production island test. Never uses or edits a launcher's saved world. */
@GameTestHolder("tnc")
public final class FullSkyIslandGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!Boolean.getBoolean("tnc.fullSkyIslandTests")) return List.of();
        return List.of(new TestFunction("full_sky", "actual_301_pieces_and_landscape",
                "tnc:building_test_empty", Rotation.NONE, 20000, 0, true, FullSkyIslandGameTests::generate));
    }

    private static void generate(GameTestHelper helper) {
        try {
            var level=helper.getLevel();
            var source=Path.of(System.getProperty("tnc.skyIslandSource"));
            SkyIslandManifest manifest;
            try (var reader=Files.newBufferedReader(source.resolve("sky_island/manifest.json"))) {
                manifest=SkyIslandManifest.parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
            if (manifest.pieces().size()!=301) throw new IllegalStateException("Wrong production manifest");
            for (var portal:List.of(manifest.groundPortal(),manifest.islandPortal()))
                loadSourceTemplate(level,source,portal);
            var data=new SkyIslandSavedData(); data.version=manifest.version(); data.layoutReady=true;
            data.phase=SkyIslandSavedData.Phase.BUILDING;
            data.originX=10000; data.originY=manifest.worldOriginY(); data.originZ=10000;
            var origin=new BlockPos(data.originX,data.originY,data.originZ);
            var arrival=origin.offset(manifest.arrivalLocal());
            data.arrivalX=arrival.getX(); data.arrivalY=arrival.getY(); data.arrivalZ=arrival.getZ();
            data.groundPortalX=9900; data.groundPortalY=200; data.groundPortalZ=10000;
            var ground=new BlockPos(data.groundPortalX,data.groundPortalY,data.groundPortalZ);
            var caveResident=new ArmorStand[1];
            var plan=SkyLandscapeUpgrade.load(level);
            var landscape=new SkyLandscapeUpgrade.State(); landscape.origin=origin; landscape.hash=plan.hash();
            var loaded=new int[]{-1}; var samples=new int[1]; var audited=new boolean[1]; var foundationChecked=new boolean[1];
            // Disable natural grass/leaf random updates; source architecture and portal placement
            // still use the real production methods and native block/entity chunk loading.
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(0,level.getServer());
            helper.succeedWhen(()->{
                try {
                    if (data.phase==SkyIslandSavedData.Phase.BUILDING) {
                        if (data.nextPiece<manifest.pieces().size() && loaded[0]!=data.nextPiece) {
                            var piece=manifest.pieces().get(data.nextPiece);
                            loadSourceTemplate(level,source,piece.resource());
                            loaded[0]=data.nextPiece;
                        }
                        int previous=data.nextPiece;
                        SkyIslandManager.buildStep(level,data,manifest);
                        if (data.nextPiece>previous) {
                            var piece=manifest.pieces().get(previous);
                            var tag=level.getStructureManager().get(piece.resource()).orElseThrow().save(new net.minecraft.nbt.CompoundTag());
                            var palette=tag.getList("palette",Tag.TAG_COMPOUND); var blocks=tag.getList("blocks",Tag.TAG_COMPOUND);
                            for (int i=0;i<blocks.size();i+=Math.max(1,blocks.size()/8)) {
                                var b=blocks.getCompound(i); var xyz=b.getList("pos",Tag.TAG_INT);
                                var expected=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),palette.getCompound(b.getInt("state")));
                                var pos=origin.offset(piece.offset()).offset(xyz.getInt(0),xyz.getInt(1),xyz.getInt(2));
                                if (expected.isSolidRender(level,pos)) {
                                    if (!level.getBlockState(pos).equals(expected)) throw new IllegalStateException("Missing source structure at "+pos);
                                    samples[0]++;
                                }
                            }
                            level.getStructureManager().remove(piece.resource());
                        }
                    } else if (data.phase==SkyIslandSavedData.Phase.FINALIZING) {
                        if (!data.activeChunks.isEmpty() && SkyIslandManager.portalEntitiesReady(level,data) && caveResident[0]==null) {
                            caveResident[0]=new ArmorStand(level,ground.getX()+7.5,ground.getY()-3,ground.getZ()+7.5);
                            caveResident[0].setNoGravity(true);
                            if (!level.addFreshEntity(caveResident[0])) throw new IllegalStateException("Could not load cave resident fixture");
                        }
                        SkyIslandManager.finalizeBuild(level,data,manifest);
                        if (data.phase==SkyIslandSavedData.Phase.COMPLETE) {
                            if (caveResident[0]==null || level.getEntity(caveResident[0].getUUID())!=caveResident[0]
                                    || !caveResident[0].isAlive() || !level.getBlockState(ground.offset(7,-2,7)).isAir())
                                throw new IllegalStateException("Finalization overwrote or removed loaded foundation resident");
                            foundationChecked[0]=true;
                        }
                    } else if (data.phase==SkyIslandSavedData.Phase.COMPLETE) {
                        SkyLandscapeUpgrade.step(level,landscape,plan);
                    } else throw new IllegalStateException("Unexpected island phase "+data.phase);
                    if (landscape.phase!=2) throw new GameTestAssertException("Full island "+data.phase+":"+data.nextPiece+" landscape "+landscape.phase+":"+landscape.tile);
                    if (!audited[0]) {
                        if (data.nextPiece!=301 || data.portalRevision!=4 || !data.activeChunks.isEmpty()
                                || samples[0]<500 || !foundationChecked[0])
                            throw new IllegalStateException("Incomplete production island or occupied foundation overwritten");
                        int checked=0;
                        for (var tile:plan.tiles()) for (var change:tile.changes()) {
                            var pos=origin.offset(change.local());
                            if (!SkyLandscapeUpgrade.sameStableState(level.getBlockState(pos),change.after()))
                                throw new IllegalStateException("Incomplete landscape at "+pos);
                            checked++;
                        }
                        // Production releases the ground site's tickets after finalization. The
                        // resident may now be normally saved/unloaded; don't require it to tick
                        // throughout construction of the remote island's landscape.
                        audited[0]=true;
                        LogUtils.getLogger().info("[TN-C Full Sky Test] completed: 301 source pieces, {} structural samples, {} landscape changes verified; occupied foundation preserved",samples[0],checked);
                    }
                } catch (GameTestAssertException waiting) {throw waiting;}
                catch (Exception failure) {throw new IllegalStateException("Full production island failed",failure);}
            });
        } catch (Exception failure) {helper.fail("Production fixture setup: "+failure);}
    }

    private static void loadSourceTemplate(net.minecraft.server.level.ServerLevel level,Path source,ResourceLocation resource) throws Exception {
        var tag=NbtIo.readCompressed(source.resolve("structures/"+resource.getPath()+".nbt").toFile());
        for (var raw:tag.getList("palette",Tag.TAG_COMPOUND)) {
            var id=ResourceLocation.tryParse(((net.minecraft.nbt.CompoundTag)raw).getString("Name"));
            if (id==null || !level.registryAccess().registryOrThrow(Registries.BLOCK).containsKey(id))
                throw new IllegalStateException("Missing production block "+id);
        }
        level.getStructureManager().getOrCreate(resource).load(level.holderLookup(Registries.BLOCK),tag);
    }
}
