package com.tnc.tnc.life.seagarden;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncroute") @PrefixGameTestTemplate(false)
public final class SeaGardenGameTests {
    private static FakePlayer diver(ServerLevel level, String name) {
        return new FakePlayer(level,new GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name)) {
            @Override public boolean isUnderWater() {return true;}
        };
    }
    private static BlockHitResult hit(BlockPos pos) {return new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);}
    private static void tank(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(),Blocks.SAND.defaultBlockState(),18);
        for (int dy=0;dy<=6;dy++) level.setBlock(pos.above(dy),Blocks.WATER.defaultBlockState(),18);
    }

    @GameTest(template="empty",timeoutTicks=160)
    public static void seagardenBreathIsSharedAndRefills(GameTestHelper h) {
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(1,2,1));
        var plant=SeaGarden.Kind.LANTERN.block();
        tank(level,pos);
        level.setBlock(pos,plant.defaultBlockState(),18);
        var first=diver(level,"FirstDiver");
        var second=diver(level,"SecondDiver");
        first.setAirSupply(40);second.setAirSupply(50);
        plant.use(level.getBlockState(pos),level,pos,first,InteractionHand.MAIN_HAND,hit(pos));
        h.assertTrue(first.getAirSupply()==140 && level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==1,
                "Diver gains exactly one breath and lantern visibly discharges");
        plant.use(level.getBlockState(pos),level,pos,second,InteractionHand.MAIN_HAND,hit(pos));
        h.assertTrue(second.getAirSupply()==50,"A second diver cannot draw from a discharged lantern");
        plant.tick(level.getBlockState(pos),level,pos,level.random);
        h.assertTrue(level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==2 && level.getFluidState(pos).isSource(),"Deflated hold underwater");
        plant.tick(level.getBlockState(pos),level,pos,level.random);
        h.assertTrue(level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==0,"Recharges after cooldown");
        level.destroyBlock(pos,false);
        h.assertTrue(level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource(),"Harvest leaves source water");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void seagardenBreathColumnNeedsARealWaterPath(GameTestHelper h) {
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(1,2,1));
        tank(level,pos);
        var lantern=SeaGarden.Kind.LANTERN.block();
        level.setBlock(pos,lantern.defaultBlockState(),18);
        var swimmer=diver(level,"PlumeDiver");
        swimmer.tickCount=Math.floorMod(-swimmer.getId(),12);
        swimmer.setAirSupply(62);
        var event=new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,swimmer);
        swimmer.moveTo(pos.getX()+1.2,pos.getY()+2,pos.getZ()+.5,0,0);
        SeaGardenSense.swimmingPast(event);
        h.assertTrue(swimmer.getAirSupply()==62,"Passing outside the plume keeps the plant charged");
        swimmer.moveTo(pos.getX()+.5,pos.getY()+2,pos.getZ()+.5,0,0);
        level.setBlock(pos.above(),Blocks.GLASS.defaultBlockState(),18);
        SeaGardenSense.swimmingPast(event);
        h.assertTrue(swimmer.getAirSupply()==62,"Bubbles cannot cross glass");
        level.setBlock(pos.above(),Blocks.WATER.defaultBlockState(),18);
        SeaGardenSense.swimmingPast(event);
        h.assertTrue(swimmer.getAirSupply()==162 && level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==1,"Swimming through the two-block bubble plume grants 100 air");
        lantern.use(level.getBlockState(pos),level,pos,swimmer,InteractionHand.MAIN_HAND,hit(pos));
        h.assertTrue(swimmer.getAirSupply()==162,"Manual and automatic collection share the same charge");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void seagardenWarnsBeforeContactAndAcceptsShears(GameTestHelper h) {
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(1,2,1));
        var player=diver(level,"ReefDiver");
        var bind=SeaGarden.Kind.BINDWEED.block();
        tank(level,pos);
        level.setBlock(pos,bind.defaultBlockState(),18);
        bind.entityInside(level.getBlockState(pos),level,pos,player);
        h.assertTrue(level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==1,"Vine shows a warning before it binds");
        bind.tick(level.getBlockState(pos),level,pos,level.random);
        h.assertTrue(level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==0,"Stepping out during warning cancels the bind");
        level.setBlock(pos,bind.defaultBlockState().setValue(SeaPlantBlock.STAGE,2),18);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));
        bind.use(level.getBlockState(pos),level,pos,player,InteractionHand.MAIN_HAND,hit(pos));
        h.assertTrue(level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==0 && player.getMainHandItem().getDamageValue()==1,"Shears open the clasp");
        var blossom=SeaGarden.Kind.BLOSSOM.block();
        level.setBlock(pos,blossom.defaultBlockState(),18);
        blossom.tick(level.getBlockState(pos),level,pos,level.random);
        h.assertTrue(SeaPlantBlock.moonStage(6000)==0 && SeaPlantBlock.moonStage(13000)==1 && SeaPlantBlock.moonStage(18000)==2
                && level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==SeaPlantBlock.moonStage(level.getDayTime()),"Moonreef opens at night and visibly changes at dusk");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=120)
    public static void seagardenDiverTriggersFanSporeAndClasp(GameTestHelper h) {
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(1,2,1));
        tank(level,pos);
        var player=diver(level,"ShoalDiver");
        var v=Vec3.atBottomCenterOf(pos);
        player.moveTo(v.x,v.y,v.z,0,0);
        level.addNewPlayer(player);
        try {
            var fan=SeaGarden.Kind.FAN.block();
            level.setBlock(pos,fan.defaultBlockState(),18);
            player.setSwimming(true);
            player.tickCount=Math.floorMod(-player.getId(),12);
            SeaGardenSense.swimmingPast(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
            h.assertTrue(level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==1,"Nearby swimmer opens the fan before the current begins");
            fan.tick(level.getBlockState(pos),level,pos,level.random);
            h.assertTrue(level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==2 && player.hasEffect(net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE),"Active swimmer receives the current without a forced teleport");
            var blossom=SeaGarden.Kind.ANEMONE.block();
            level.setBlock(pos,blossom.defaultBlockState(),18);
            blossom.entityInside(level.getBlockState(pos),level,pos,player);
            blossom.tick(level.getBlockState(pos),level,pos,level.random);
            h.assertTrue(level.getBlockState(pos).getValue(SeaPlantBlock.STAGE)==2,"Filter flower folds and emits spores on contact");
            var vine=SeaGarden.Kind.BINDWEED.block();
            level.setBlock(pos,vine.defaultBlockState(),18);
            vine.entityInside(level.getBlockState(pos),level,pos,player);
            vine.tick(level.getBlockState(pos),level,pos,level.random);
            var clasp=level.getBlockState(pos);
            h.assertTrue(clasp.getValue(SeaPlantBlock.STAGE)==2,"The warned clasp closes on the swimmer");
            player.setSprinting(true);
            var stroke=new Vec3(.25,.14,0);
            var start=player.position();
            vine.entityInside(clasp,level,pos,player);
            player.move(net.minecraft.world.entity.MoverType.SELF,stroke);
            var held=player.position().subtract(start);
            h.assertTrue(held.x>stroke.x*.4 && held.x<stroke.x*.8 && held.y>stroke.y*.5 && held.y<stroke.y*.85,
                    "A sprint stroke still advances while the clasp actually slows horizontal and vertical displacement");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));
            vine.use(clasp,level,pos,player,InteractionHand.MAIN_HAND,hit(pos));
            var released=player.position();
            player.move(net.minecraft.world.entity.MoverType.SELF,stroke);
            h.assertTrue(player.position().subtract(released).distanceToSqr(stroke)<1E-8,
                    "Cutting restores full displacement on the very next stroke");
        } finally {
            level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=120)
    public static void seagardenKelpLivesInOneContinuousWaterColumn(GameTestHelper h) {
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(1,2,1));
        var chunk=level.getChunkAt(pos);
        tank(level,pos);
        level.setBlock(pos.above(2),Blocks.GLASS.defaultBlockState(),18);
        h.assertTrue(!SeaGardenShoals.place(level,chunk,pos,SeaGarden.Kind.KELP,RandomSource.create(331)) && level.getBlockState(pos).is(Blocks.WATER),"Obstructed kelp leaves the entire column untouched");
        tank(level,pos);
        var kind=SeaGarden.Kind.KELP;
        var kelp=(TidekelpBlock)kind.block();
        h.assertTrue(SeaGardenShoals.place(level,chunk,pos,kind,RandomSource.create(331)),"Living kelp planted on a submerged bed");
        int n=0;
        while(n<6 && level.getBlockState(pos.above(n)).is(kelp)) n++;
        var tip=pos.above(n-1);
        h.assertTrue(n>=3 && n<=5 && level.getBlockState(tip).getValue(TidekelpBlock.PART)==TidekelpBlock.Part.TIP,"Three to five segments, one crown");
        for(int i=0;i<n-1;i++) h.assertTrue(level.getBlockState(pos.above(i)).getValue(TidekelpBlock.PART)==TidekelpBlock.Part.STEM
                && level.getFluidState(pos.above(i)).isSource(),"Submerged living stem");
        var player=diver(level,"KelpDiver");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));
        kelp.use(level.getBlockState(tip),level,tip,player,InteractionHand.MAIN_HAND,hit(tip));
        h.assertTrue(player.getInventory().countItem(kelp.asItem())==1 && level.getBlockState(tip).getValue(SeaPlantBlock.STAGE)==0,"One live cutting and a regenerating canopy");
        kelp.use(level.getBlockState(tip),level,tip,player,InteractionHand.MAIN_HAND,hit(tip));
        h.assertTrue(player.getInventory().countItem(kelp.asItem())==1,"Cannot harvest an immature cutting twice");
        level.destroyBlock(pos.below(),false);
        for (int i=0;i<n;i++) h.assertTrue(level.getFluidState(pos.above(i)).is(Fluids.WATER) && level.getBlockState(pos.above(i)).is(Blocks.WATER),"Removing support returns the entire stalk to water");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void seagardenSamplesNaturalOceanFloor(GameTestHelper h) {
        var level=h.getLevel();
        var source=level.getChunkSource();
        var biomes=source.getGenerator().getBiomeSource();
        var sampler=source.randomState().sampler();
        var seen=java.util.EnumSet.noneOf(SeaGarden.Kind.class);
        var dice=RandomSource.create(18);
        for(var zone:SeaGardenShoals.Zone.values()) for(int i=0;i<120;i++) seen.add(SeaGardenShoals.pick(zone,dice));
        h.assertTrue(seen.size()==6,"All six plants have a submarine habitat");
        int inspected=0;
        for (int ring=1;ring<=40;ring++) for(int side=0;side<16;side++) {
            double angle=side*Math.PI/8;
            int cx=(int)Math.round(Math.cos(angle)*ring*6),cz=(int)Math.round(Math.sin(angle)*ring*6);
            var ocean=biomes.getNoiseBiome(QuartPos.fromBlock(cx*16+8),QuartPos.fromBlock(43),QuartPos.fromBlock(cz*16+8),sampler);
            if(!ocean.is(BiomeTags.IS_OCEAN)) continue;
            var chunk=level.getChunk(cx,cz);
            int count=SeaGardenShoals.populate(level,chunk);
            inspected++;
            if(count>0) {
                int roots=0,segments=0;
                var cp=chunk.getPos();
                for(int x=2;x<=13;x++) for(int z=2;z<=13;z++) {
                    for(int y=level.getMinBuildHeight()+1;y<=level.getSeaLevel()+2;y++) {
                        var p=new BlockPos(cp.getMinBlockX()+x,y,cp.getMinBlockZ()+z);
                        var s=chunk.getBlockState(p);
                        if (!(s.getBlock() instanceof SeaPlantBlock)) continue;
                        segments++;
                        h.assertTrue(chunk.getFluidState(p).isSource() && s.canSurvive(level,p),"Valid natural submerged segment");
                        if (!chunk.getBlockState(p.below()).is(s.getBlock())) roots++;
                    }
                }
                h.assertTrue(roots>=count && count<=4,"Cluster count agrees with physical seabed plants");
                com.mojang.logging.LogUtils.getLogger().info("[TN-C Sea Garden] actual ocean {}, chunk {}, {} new seafloor roots, {} segments, {} terrain chunks inspected",ocean.unwrapKey().orElseThrow().location(),cp,count,segments,inspected);
                h.succeed();return;
            }
            if(inspected>=12) {h.fail("12 natural ocean samples had no sea garden");return;}
        }
        h.fail("No natural ocean within the sample radius");
    }
    private SeaGardenGameTests() {}
}
