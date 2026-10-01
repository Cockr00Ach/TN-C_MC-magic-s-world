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
    public static final long PRICE=3000,DOWN_PAYMENT=900;
    private static final TicketType<net.minecraft.world.level.ChunkPos> BANK_INSPECTION=TicketType.create("tnc_bank_house_inspection",Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong),200);
    public record Cell(BlockPos local,BlockState state,boolean clear){}
    public record Blueprint(BlockPos min,BlockPos max,BlockPos entry,List<Cell> cells){}
    private static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    public static Blueprint blueprint(ServerLevel level) throws Exception {
        return blueprint(level,ID);
    }
    public static Blueprint blueprint(ServerLevel level,String id) throws Exception {
        if(PlotCatalog.find(id)==null)throw new IllegalArgumentException("Unknown property");
        var resource=level.getServer().getResourceManager().getResource(ResourceLocation.parse("tnc:housing/"+id+".json")).orElseThrow();
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
    public static CompoundTag home(net.minecraft.server.MinecraftServer s,String id){return AdventureSavedData.get(s).housing.getCompound(id);}
    public static PlotCatalog.Plot mine(ServerPlayer p,PlotCatalog.Kind kind){return PlotCatalog.ALL.stream().filter(a->a.kind()==kind&&home(p.server,a.id()).hasUUID("Owner")&&home(p.server,a.id()).getUUID("Owner").equals(p.getUUID())).findFirst().orElse(null);}
    public static CompoundTag home(ServerPlayer p){var a=mine(p,PlotCatalog.Kind.HOME);return a==null?new CompoundTag():home(p.server,a.id());}
    public static BlockPos sourceOrigin(ServerLevel level){return SkyIslandAnchors.resolve(level,SkyIslandAnchors.Anchor.ORIGIN);}
    public static boolean inside(BlockPos local){return local.getX()>=182&&local.getX()<=199&&local.getY()>=90&&local.getY()<=117&&local.getZ()>=384&&local.getZ()<=398;}
    public static boolean owned(ServerPlayer p){var t=home(p);return t.hasUUID("Owner")&&!t.getBoolean("Preparing");}
    public static boolean atSale(ServerPlayer p){var origin=sourceOrigin(p.server.overworld());return p.serverLevel()==p.server.overworld()&&p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p)&&origin!=null&&origin.offset(189,92,394).closerToCenterThan(p.position(),16);}
    public static PlotCatalog.Plot ownedAt(ServerLevel level,BlockPos pos){if(level!=level.getServer().overworld())return null;return PlotCatalog.ALL.stream().filter(a->{var t=home(level.getServer(),a.id());return t.hasUUID("Owner")&&a.contains(pos.subtract(BlockPos.of(t.getLong("Origin"))));}).findFirst().orElse(null);}
    public static PlotCatalog.Plot plotAt(ServerLevel level,BlockPos pos){if(level!=level.getServer().overworld())return null;var origin=sourceOrigin(level);return origin==null?null:PlotCatalog.at(pos.subtract(origin));}
    public static boolean isOwnedPosition(ServerLevel level,BlockPos pos){return ownedAt(level,pos)!=null;}
    public static boolean mayDecorate(ServerPlayer p,BlockPos pos) {
        var plot=ownedAt(p.serverLevel(),pos);if(plot==null)return false;var t=home(p.server,plot.id());
        return !t.getBoolean("Preparing")&&(t.getUUID("Owner").equals(p.getUUID())||t.getList("Guests",8).stream().anyMatch(v->v.getAsString().equals(p.getUUID().toString())));
    }
    public static String guest(ServerPlayer owner,ServerPlayer friend,boolean allow){
        var owned=PlotCatalog.ALL.stream().filter(plot->{var t=home(owner.server,plot.id());return t.hasUUID("Owner")&&t.getUUID("Owner").equals(owner.getUUID())&&!t.getBoolean("Preparing");}).toList();
        if(owned.isEmpty())return "只有产权持有人可以授权伙伴。";String id=friend.getUUID().toString();
        if(allow&&owned.stream().anyMatch(plot->{var guests=home(owner.server,plot.id()).getList("Guests",8);return guests.size()>=8&&guests.stream().noneMatch(v->v.getAsString().equals(id));}))return "每处最多八位伙伴，未变更权限。";
        for(var plot:owned){var t=home(owner.server,plot.id());var guests=t.getList("Guests",8);guests.removeIf(v->v.getAsString().equals(id));if(allow&&!friend.getUUID().equals(owner.getUUID()))guests.add(net.minecraft.nbt.StringTag.valueOf(id));t.put("Guests",guests);}
        AdventureSavedData.get(owner.server).setDirty();return (allow?"已授权":"已撤销")+friend.getGameProfile().getName()+"在你现有房屋和农牧地上的协作权限；不会转移产权。";
    }
    public static String buy(ServerPlayer p) {return buy(p,false);}
    public static String buy(ServerPlayer p,boolean installment) {
        return buy(p,ID,installment,false);
    }
    public static String buy(ServerPlayer p,String id,boolean installment){return buy(p,id,installment,true);}
    private static String buy(ServerPlayer p,String id,boolean installment,boolean requireVisit) {
        com.tnc.tnc.adventure.BankService.settle(p);
        var plot=PlotCatalog.find(id);if(plot==null)return "没有这处房源或土地。";
        if(mine(p,plot.kind())!=null)return "你已有同类产权；先把自己的归处建设起来。";
        if(installment&&plot.kind()!=PlotCatalog.Kind.HOME)return "农田与牧场只需一次购买，不提供房贷。";
        if(requireVisit&&plot.kind()==PlotCatalog.Kind.HOME&&!p.isCreative()&&!AdventureService.profile(p).hasMilestone("viewed_"+id))return "先去"+plot.number()+"号实地看房，再回米洛这里决定。点击去看房可获得指引。";
        if(p.level()!=p.server.overworld())return "住宅位于主世界天空岛。";
        var origin=sourceOrigin(p.serverLevel());if(origin==null||!SkyIslandAnchors.isComplete(p.serverLevel()))return "天空岛还未完成，尚无可售房源。";
        var chunks=new HashSet<net.minecraft.world.level.ChunkPos>();boolean waiting=false;
        try{
            if(plot.kind()!=PlotCatalog.Kind.HOME)return buyLand(p,plot,origin);
            var plan=blueprint(p.serverLevel(),id);var level=p.serverLevel();
            for(var cell:plan.cells())chunks.add(new net.minecraft.world.level.ChunkPos(origin.offset(cell.local())));
            for(var chunk:chunks)level.getChunkSource().addRegionTicket(BANK_INSPECTION,chunk,2,chunk);
            if(chunks.stream().anyMatch(c->!level.hasChunk(c.x,c.z))){waiting=true;return "银行正在调取南街房源，请稍候再次确认购买；尚未扣款。";}
            return buyAt(p,origin,plan,installment,plot);
        }catch(Exception e){var t=home(p.server,id);return t.hasUUID("Owner")&&t.getUUID("Owner").equals(p.getUUID())?"住宅已预留；整理遇到问题，产权与扣款已保存："+e.getMessage():"住宅预检停止，未扣款："+e.getMessage();}
        finally{if(!waiting)for(var chunk:chunks)p.serverLevel().getChunkSource().removeRegionTicket(BANK_INSPECTION,chunk,2,chunk);}
    }
    static String buyAt(ServerPlayer p,BlockPos origin,Blueprint blueprint) throws Exception {return buyAt(p,origin,blueprint,false);}
    static String buyAt(ServerPlayer p,BlockPos origin,Blueprint blueprint,boolean installment) throws Exception {
        return buyAt(p,origin,blueprint,installment,PlotCatalog.find(ID));
    }
    static String buyAt(ServerPlayer p,BlockPos origin,Blueprint blueprint,boolean installment,PlotCatalog.Plot plot) throws Exception {
        var store=AdventureSavedData.get(p.server);var existing=home(p.server,plot.id());
        if(existing.hasUUID("Owner"))return "这间住宅已有房主。";
        if(mine(p,PlotCatalog.Kind.HOME)!=null)return "你已经有自己的住宅。";
        var account=AdventureService.profile(p);long charge=installment?plot.down():plot.price();
        if(installment&&!account.bank.canMortgage(account))return "分期需要协会登记、冒险Lv10且没有欠付账单。";
        if(account.coins()+account.bank.savings<charge)return "钱袋和存款合计需要"+charge+"铜，尚未扣款。";
        var level=p.serverLevel();for(var cell:blueprint.cells) {
            var world=origin.offset(cell.local);if(!level.hasChunkAt(world))return "请先到南街空屋看房，让房屋区块加载；未扣款。";
            var state=level.getBlockState(world);
            // Imported support-dependent decorations may have dropped; already-empty
            // removable cells are harmless. The shell and any replacement remain strict.
            if(!(cell.clear&&state.isAir())&&!stable(state,cell.state)&&!restorableSeamDoor(level,origin,blueprint,cell))return "房屋已有改动（"+world.toShortString()+"），停止清内饰和出售，保留现场；未扣款。";
            if(level.getBlockEntity(world) instanceof Container container&&!container.isEmpty())return "箱内物品已保留（"+world.toShortString()+"）。请先检查并取出要保管的物品，再购买；未扣款。";
        }
        // Ownership and debit are saved in the same account file. Journal prevents repeat cleanup.
        var t=new CompoundTag();t.putUUID("Owner",p.getUUID());t.putLong("Origin",origin.asLong());t.putBoolean("Preparing",true);t.putInt("Cursor",0);
        if(!account.bank.spend(account,charge,"购买南街空屋"))return "余额变化，尚未扣款。";
        if(installment){account.bank.startMortgage(plot.price()-plot.down());t.putBoolean("Mortgage",true);}
        t.putLong("Price",plot.price());t.putString("PlotId",plot.id());store.housing.put(plot.id(),t);store.setDirty();
        level.getServer().overworld().getDataStorage().save();
        finish(level,origin,blueprint,t);
        if(t.getBoolean("Preparing"))return "住宅已预留，整理尚未完成。请保持房屋区块加载；不会再次扣款。";
        AdventureService.milestone(p,"home_bought");return "已购"+plot.number()+"号"+plot.name()+"。室内用品已移除，房屋边界内可挖、可放，请自行装修。";
    }
    private static String buyLand(ServerPlayer p,PlotCatalog.Plot plot,BlockPos origin){
        var store=AdventureSavedData.get(p.server);if(home(p.server,plot.id()).hasUUID("Owner"))return "这块土地已有主人。";
        var a=AdventureService.profile(p);if(!a.bank.spend(a,plot.price(),"购买"+plot.number()+"土地"))return "钱袋和存款合计不足，未扣款。";
        var t=new CompoundTag();t.putUUID("Owner",p.getUUID());t.putLong("Origin",origin.asLong());t.putString("PlotId",plot.id());t.putLong("Price",plot.price());store.housing.put(plot.id(),t);store.setDirty();p.server.overworld().getDataStorage().save();AdventureService.milestone(p,"land_bought");return "已购"+plot.number()+plot.name()+"。土地原状保留，边界内可整理种养。";
    }
    static boolean stable(BlockState a,BlockState b){
        // Source plants can lose attachment faces or disappear during normal import
        // and growth. A different replacement block still fails the shell preflight.
        boolean plant=b.getBlock() instanceof BushBlock||b.getBlock() instanceof VineBlock;
        if(plant&&(a.isAir()||a.getBlock()==b.getBlock()))return true;
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
    /** Old imports lost doors split between Y=53+48n tiles. Only repair that exact
     * source pair, into empty cells, after the complete house has passed inspection. */
    private static boolean restorableSeamDoor(ServerLevel level,BlockPos origin,Blueprint b,Cell cell){
        if(cell.clear||!(cell.state.getBlock() instanceof DoorBlock)||!level.getBlockState(origin.offset(cell.local)).isAir())return false;
        boolean lower=cell.state.getValue(DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
        var bottom=lower?cell.local:cell.local.below();
        if(Math.floorMod(bottom.getY()-53,48)!=47)return false;
        var otherLocal=lower?cell.local.above():cell.local.below();
        var other=b.cells.stream().filter(c->c.local.equals(otherLocal)).findFirst().orElse(null);
        if(other==null||other.clear||other.state.getBlock()!=cell.state.getBlock()||other.state.getValue(DoorBlock.HALF)==cell.state.getValue(DoorBlock.HALF))return false;
        var otherActual=level.getBlockState(origin.offset(otherLocal));var floor=origin.offset(bottom.below());
        return (otherActual.isAir()||stable(otherActual,other.state))&&level.getBlockState(floor).isFaceSturdy(level,floor,net.minecraft.core.Direction.UP);
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
            }else if(restorableSeamDoor(level,origin,b,cell)){
                // Suppress neighbor recomputation until both authored halves exist.
                level.setBlock(pos,cell.state,18);
            }t.putInt("Cursor",i+1);
        }level.getChunkSource().save(true);t.putBoolean("Preparing",false);t.putString("Status","交付完成");AdventureSavedData.get(level.getServer()).setDirty();level.getServer().overworld().getDataStorage().save();
        if(t.hasUUID("Owner")){var owner=level.getServer().getPlayerList().getPlayer(t.getUUID("Owner"));if(owner!=null)AdventureService.milestone(owner,"home_bought");}
    }
    public static CompoundTag snapshot(ServerPlayer p){
        var mine=mine(p,PlotCatalog.Kind.HOME);var t=(mine==null?home(p.server):home(p)).copy();t.putLong("SalePrice",mine==null?PRICE:mine.price());t.putLong("DownPayment",mine==null?DOWN_PAYMENT:mine.down());var origin=t.hasUUID("Owner")?BlockPos.of(t.getLong("Origin")):sourceOrigin(p.server.overworld());
        var selected=mine==null?PlotCatalog.find(ID):mine;
        if(origin!=null){t.putString("Entry",origin.offset(selected.entry()).toShortString());t.putString("Boundary",origin.offset(selected.min()).toShortString()+" 至 "+origin.offset(selected.max()).toShortString());}
        var list=new net.minecraft.nbt.ListTag();for(var plot:PlotCatalog.ALL){var item=home(p.server,plot.id()).copy();item.putString("Id",plot.id());item.putString("Number",plot.number());item.putString("Name",plot.name());item.putString("Kind",plot.kind().name());item.putString("Detail",plot.detail());item.putLong("Price",plot.price());item.putLong("Down",plot.down());item.putLong("Total",plot.price()+plot.mortgageTotal()-(plot.price()-plot.down()));item.putLong("MortgageTotal",plot.mortgageTotal());item.putBoolean("Viewed",AdventureService.profile(p).hasMilestone("viewed_"+plot.id()));item.putBoolean("Mine",item.hasUUID("Owner")&&item.getUUID("Owner").equals(p.getUUID()));if(origin!=null)item.putString("Entry",origin.offset(plot.entry()).toShortString());list.add(item);}
        t.put("Catalog",list);t.putBoolean("Mine",owned(p));return t;
    }
    public static boolean intersectsOwned(ServerLevel level,BlockPos min,net.minecraft.core.Vec3i size) {
        if(level!=level.getServer().overworld())return false;var max=min.offset(size).offset(-1,-1,-1);return PlotCatalog.ALL.stream().anyMatch(plot->{var t=home(level.getServer(),plot.id());if(!t.hasUUID("Owner"))return false;var o=BlockPos.of(t.getLong("Origin"));return plot.intersects(min.subtract(o),max.subtract(o));});
    }
    public static BlockPos furnishedBed(ServerPlayer p) {
        if(!owned(p))return null;var plot=mine(p,PlotCatalog.Kind.HOME);var t=home(p);var origin=BlockPos.of(t.getLong("Origin"));int beds=0;BlockPos selected=null;
        for(var pos:BlockPos.betweenClosed(origin.offset(plot.min()),origin.offset(plot.max())))if(p.server.overworld().hasChunkAt(pos)) {
            var state=p.server.overworld().getBlockState(pos);
            if(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock&&state.getValue(net.minecraft.world.level.block.BedBlock.PART)==net.minecraft.world.level.block.state.properties.BedPart.HEAD){beds++;selected=pos.immutable();}
        }return beds>=2?selected:null;
    }
}
