package com.tnc.tnc.home;

import com.google.gson.*;
import com.tnc.tnc.adventure.*;
import com.tnc.tnc.npc.SkyIslandAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Sale is a real marked source cottage. Unexpected changes stop purchase, never erase player work. */
public final class HousingService {
    public static final String ID="south_cottage";
    public record Cell(BlockPos local,BlockState state,boolean clear){}
    public record Blueprint(BlockPos min,BlockPos max,BlockPos entry,List<Cell> cells){}
    private static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    public static Blueprint blueprint(ServerLevel level) throws Exception {
        var resource=level.getServer().getResourceManager().getResource(ResourceLocation.parse("tnc:housing/south_cottage.json")).orElseThrow();
        try(var reader=new InputStreamReader(resource.open(),StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();List<BlockState> palette=new ArrayList<>();
            for(var e:root.getAsJsonArray("palette")) {
                var entry=e.getAsJsonObject();var block=ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(entry.get("name").getAsString()));if(block==null)throw new IllegalStateException("Missing house block");var state=block.defaultBlockState();
                for(var p:entry.getAsJsonObject("properties").entrySet()){var prop=state.getBlock().getStateDefinition().getProperty(p.getKey());if(prop!=null)state=with(state,prop,p.getValue().getAsString());}palette.add(state);
            }
            List<Cell> cells=new ArrayList<>();for(var e:root.getAsJsonArray("blocks")){var c=e.getAsJsonObject();cells.add(new Cell(pos(c.getAsJsonArray("pos")),palette.get(c.get("state").getAsInt()),c.get("clear").getAsBoolean()));}
            return new Blueprint(pos(root.getAsJsonArray("min")),pos(root.getAsJsonArray("max")),pos(root.getAsJsonArray("entry")),List.copyOf(cells));
        }
    }
    private static <T extends Comparable<T>> BlockState with(BlockState s,Property<T> p,String v){return p.getValue(v).map(value->s.setValue(p,value)).orElseThrow();}
    public static CompoundTag home(net.minecraft.server.MinecraftServer s){return AdventureSavedData.get(s).housing.getCompound(ID);}
    public static BlockPos sourceOrigin(ServerLevel level){return SkyIslandAnchors.resolve(level,SkyIslandAnchors.Anchor.ORIGIN);}
    public static boolean inside(BlockPos local){return local.getX()>=182&&local.getX()<=199&&local.getY()>=90&&local.getY()<=117&&local.getZ()>=384&&local.getZ()<=398;}
    public static boolean owned(ServerPlayer p){var t=home(p.server);return t.hasUUID("Owner")&&t.getUUID("Owner").equals(p.getUUID())&&!t.getBoolean("Preparing");}
    public static boolean atSale(ServerPlayer p){var origin=sourceOrigin(p.server.overworld());return p.serverLevel()==p.server.overworld()&&p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p)&&origin!=null&&origin.offset(189,92,394).closerToCenterThan(p.position(),16);}
    public static boolean isOwnedPosition(ServerLevel level,BlockPos pos){var t=home(level.getServer());return level==level.getServer().overworld()&&t.hasUUID("Owner")&&inside(pos.subtract(BlockPos.of(t.getLong("Origin"))));}
    public static boolean mayDecorate(ServerPlayer p,BlockPos pos) {
        var t=home(p.server);if(!isOwnedPosition(p.serverLevel(),pos))return false;
        return !t.getBoolean("Preparing")&&(t.getUUID("Owner").equals(p.getUUID())||t.getList("Guests",8).stream().anyMatch(v->v.getAsString().equals(p.getUUID().toString())));
    }
    public static String guest(ServerPlayer owner,ServerPlayer friend,boolean allow){
        if(!owned(owner))return "只有房主可以授权装修伙伴。";
        var t=home(owner.server);var guests=t.getList("Guests",8);String id=friend.getUUID().toString();
        guests.removeIf(v->v.getAsString().equals(id));if(allow){if(guests.size()>=8)return "最多八位装修伙伴。";if(!friend.getUUID().equals(owner.getUUID()))guests.add(net.minecraft.nbt.StringTag.valueOf(id));}
        t.put("Guests",guests);AdventureSavedData.get(owner.server).setDirty();return (allow?"已授权":"已撤销")+friend.getGameProfile().getName()+"的装修权限；不会转移产权。";
    }
    public static String buy(ServerPlayer p) {
        if(p.level()!=p.server.overworld())return "住宅位于主世界天空岛。";
        var origin=sourceOrigin(p.serverLevel());if(origin==null||!SkyIslandAnchors.isComplete(p.serverLevel()))return "天空岛还未完成，尚无可售房源。";
        try{return buyAt(p,origin,blueprint(p.serverLevel()));}catch(Exception e){var t=home(p.server);return t.hasUUID("Owner")&&t.getUUID("Owner").equals(p.getUUID())?"住宅已预留；整理遇到问题，产权与扣款已保存："+e.getMessage():"住宅预检停止，未扣款："+e.getMessage();}
    }
    static String buyAt(ServerPlayer p,BlockPos origin,Blueprint blueprint) throws Exception {
        var store=AdventureSavedData.get(p.server);var existing=home(p.server);
        if(existing.hasUUID("Owner"))return "这间住宅已有房主。";
        var account=AdventureService.profile(p);if(account.coins()<500)return "需要5银（500铜）。";
        var level=p.serverLevel();for(var cell:blueprint.cells) {
            var world=origin.offset(cell.local);if(!level.hasChunkAt(world))return "请先到南街空屋看房，让房屋区块加载；未扣款。";
            var state=level.getBlockState(world);
            if(!stable(state,cell.state))return "房屋已有改动（"+world.toShortString()+"），停止清内饰和出售，保留现场；未扣款。";
            if(level.getBlockEntity(world) instanceof Container container&&!container.isEmpty())return "箱内物品已保留（"+world.toShortString()+"）。请先检查并取出要保管的物品，再购买；未扣款。";
        }
        // Ownership and debit are saved in the same account file. Journal prevents repeat cleanup.
        var t=new CompoundTag();t.putUUID("Owner",p.getUUID());t.putLong("Origin",origin.asLong());t.putBoolean("Preparing",true);t.putInt("Cursor",0);
        store.housing.put(ID,t);account.debit(500,"购买南街空屋");store.setDirty();
        level.getServer().overworld().getDataStorage().save();
        finish(level,origin,blueprint,t);
        if(t.getBoolean("Preparing"))return "住宅已预留，整理尚未完成。请保持房屋区块加载；不会再次扣款。";
        AdventureService.milestone(p,"home_bought");return "已购南街空屋。室内用品已移除，请自行装修。产权和装修长期保存。";
    }
    static boolean stable(BlockState a,BlockState b){
        if(a.getBlock()!=b.getBlock())return false;
        for(var property:a.getProperties())if(!Set.of("open","powered","waterlogged","distance","persistent").contains(property.getName())&&!derived(a,property)&&!a.getValue(property).equals(b.getValue(property)))return false;return true;
    }
    private static boolean derived(BlockState state,Property<?> property){
        var block=state.getBlock();
        // Template placement recomputes these from neighbors. Placement properties
        // (facing, half, bed part, hinge, etc.) remain strict, as does every block ID.
        if(block instanceof IronBarsBlock||block instanceof FenceBlock)return property==BlockStateProperties.NORTH||property==BlockStateProperties.EAST||property==BlockStateProperties.SOUTH||property==BlockStateProperties.WEST;
        if(block instanceof WallBlock)return property==WallBlock.NORTH_WALL||property==WallBlock.EAST_WALL||property==WallBlock.SOUTH_WALL||property==WallBlock.WEST_WALL||property==WallBlock.UP;
        if(block instanceof StairBlock)return property==StairBlock.SHAPE;
        if(block instanceof FenceGateBlock)return property==FenceGateBlock.IN_WALL;
        return block instanceof BedBlock&&property==BedBlock.OCCUPIED;
    }
    public static void finish(ServerLevel level,BlockPos origin,Blueprint b,CompoundTag t) {
        if(!t.getBoolean("Preparing"))return;
        // Re-check every removal when recovering: a saved cursor may precede chunk durability.
        for(int i=0;i<b.cells.size();i++){
            var cell=b.cells.get(i);var pos=origin.offset(cell.local);
            if(!level.hasChunkAt(pos)){t.putString("Status","等待区块加载："+pos.toShortString());AdventureSavedData.get(level.getServer()).setDirty();return;}
            if(cell.clear) {
                var state=level.getBlockState(pos);
                if(!state.isAir()&&!stable(state,cell.state)){t.putString("Status","现场改动待检查："+pos.toShortString());AdventureSavedData.get(level.getServer()).setDirty();return;}
                if(level.getBlockEntity(pos) instanceof Container container&&!container.isEmpty()){t.putString("Status","容器有物品，保留现场："+pos.toShortString());AdventureSavedData.get(level.getServer()).setDirty();return;}
                if(!state.isAir())level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);
            }t.putInt("Cursor",i+1);
        }level.getChunkSource().save(true);t.putBoolean("Preparing",false);t.putString("Status","交付完成");AdventureSavedData.get(level.getServer()).setDirty();level.getServer().overworld().getDataStorage().save();
        if(t.hasUUID("Owner")){var owner=level.getServer().getPlayerList().getPlayer(t.getUUID("Owner"));if(owner!=null)AdventureService.milestone(owner,"home_bought");}
    }
    public static CompoundTag snapshot(ServerPlayer p){
        var t=home(p.server).copy();var origin=t.hasUUID("Owner")?BlockPos.of(t.getLong("Origin")):sourceOrigin(p.server.overworld());
        if(origin!=null){t.putString("Entry",origin.offset(189,92,394).toShortString());t.putString("Boundary",origin.offset(182,90,384).toShortString()+" 至 "+origin.offset(199,117,398).toShortString());}
        t.putBoolean("Mine",owned(p));return t;
    }
    public static boolean intersectsOwned(ServerLevel level,BlockPos min,net.minecraft.core.Vec3i size) {
        var t=home(level.getServer());if(level!=level.getServer().overworld()||!t.hasUUID("Owner"))return false;
        var o=BlockPos.of(t.getLong("Origin"));var low=o.offset(182,90,384);var high=o.offset(199,117,398);var max=min.offset(size).offset(-1,-1,-1);
        return min.getX()<=high.getX()&&max.getX()>=low.getX()&&min.getY()<=high.getY()&&max.getY()>=low.getY()&&min.getZ()<=high.getZ()&&max.getZ()>=low.getZ();
    }
    public static BlockPos furnishedBed(ServerPlayer p) {
        if(!owned(p))return null;var t=home(p.server);var origin=BlockPos.of(t.getLong("Origin"));int beds=0;BlockPos selected=null;
        for(var pos:BlockPos.betweenClosed(origin.offset(183,91,385),origin.offset(198,116,397)))if(p.server.overworld().hasChunkAt(pos)) {
            var state=p.server.overworld().getBlockState(pos);
            if(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock&&state.getValue(net.minecraft.world.level.block.BedBlock.PART)==net.minecraft.world.level.block.state.properties.BedPart.HEAD){beds++;selected=pos.immutable();}
        }return beds>=2?selected:null;
    }
}
