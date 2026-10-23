package com.tnc.tnc.dungeon.leyline;

import com.tnc.tnc.alchemy.AlchemyBlock;
import com.tnc.tnc.alchemy.AlchemyContent;
import com.tnc.tnc.dungeon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Physical, bounded room architecture. The persisted nodes are also the server's interaction map. */
public final class LeylineRooms {
    private LeylineRooms() {}

    public static void build(List<DungeonRealm.Cell> cells, DungeonData.Run run,
                             DungeonGraph.Room room, LeylineKind kind, CompoundTag state) {
        new Builder(cells, DungeonRealm.center(run, room), state).build(kind);
    }

    private static final class Builder {
        final List<DungeonRealm.Cell> cells;
        final BlockPos origin;
        final CompoundTag state;
        final ListTag nodes = new ListTag();
        Builder(List<DungeonRealm.Cell> cells, BlockPos origin, CompoundTag state) {
            this.cells=cells; this.origin=origin; this.state=state;
            // Circuit nodes belong to the regional ecology and survive a room rebuild.
            for (Tag value : state.getList("Nodes", Tag.TAG_COMPOUND)) {
                CompoundTag n=(CompoundTag)value;
                if (Set.of("source","nest","exit").contains(n.getString("Role"))) nodes.add(n.copy());
            }
        }
        void put(int x,int y,int z,Block block){put(x,y,z,block.defaultBlockState());}
        void put(int x,int y,int z,BlockState block){cells.add(new DungeonRealm.Cell(origin.offset(x,y,z),block));}
        void box(int x0,int y0,int z0,int x1,int y1,int z1,Block b){
            for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)put(x,y,z,b);
        }
        void node(String role,int index,int x,int y,int z,Block block){
            CompoundTag n=new CompoundTag();n.putString("Role",role);n.putInt("Index",index);
            n.putLong("Pos",origin.offset(x,y,z).asLong());nodes.add(n);
            if(block!=null)put(x,y,z,block);
        }
        void podium(int x,int z,Block top){put(x,1,z,Blocks.CHISELED_STONE_BRICKS);put(x,2,z,top);}
        void column(int x,int z,int h,Block b){box(x,1,z,x,h,z,b);}
        void ring(int r,int y,Block b){for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(Math.max(Math.abs(x),Math.abs(z))==r)put(x,y,z,b);}
        void defaultInt(String key,int value){if(!state.contains(key))state.putInt(key,value);}
        void build(LeylineKind kind){
            int high=kind==LeylineKind.GRAVITY?28:kind==LeylineKind.RAPIDS?16:kind==LeylineKind.LENS?14:kind==LeylineKind.PUPPETS?14:10;
            // Remove the generic combat furniture and low roof before installing authored space.
            box(-10,1,-10,10,high-1,10,Blocks.AIR);
            if(high>10){
                box(-11,10,-11,11,10,11,Blocks.AIR);
                for(int y=10;y<high;y++)for(int x=-11;x<=11;x++)for(int z=-11;z<=11;z++)
                    if(Math.abs(x)==11||Math.abs(z)==11)put(x,y,z,Blocks.DEEPSLATE_BRICKS);
                box(-11,high,-11,11,high,11,Blocks.DEEPSLATE_TILES);
            }
            // A restrained common ruin vocabulary, outside all doorway axes.
            for(int x:new int[]{-9,9})for(int z:new int[]{-9,9}){
                column(x,z,high-2,Blocks.POLISHED_DEEPSLATE);
                put(x,3,z,Blocks.SEA_LANTERN);put(x,high-2,z,Blocks.CHISELED_DEEPSLATE);
            }
            switch(kind){
                case BRIDGE -> bridge(false);
                case DUAL_BRIDGE -> bridge(true);
                case FALSE_VAULT -> vault();
                case TIDE -> tide();
                case ECHO -> echo();
                case LENS -> lens();
                case SUNDIAL -> sundial();
                case FACTIONS -> factions();
                case STOMACH -> stomach();
                case VINES -> vines(false);
                case PUPPETS -> puppets();
                case CHESS -> chess();
                case REFRACTION -> refraction();
                case ALCHEMY -> alchemy();
                case MEAL -> meal();
                case MUSIC -> music();
                case POLLUTION -> vines(true);
                case GRAVITY -> gravity();
                case RAPIDS -> rapids();
                case SCALE -> scale();
                case CAULDRON -> hungry();
                case EXHIBITION -> exhibition();
                case PAWN -> pawn();
            }
            // Registered doors are carved after room furniture, never obstructed by trim.
            for(int s:new int[]{-1,1}){
                box(-1,1,s*10,1,4,s*10,Blocks.AIR);
                box(s*10,1,-1,s*10,4,1,Blocks.AIR);
            }
            box(-1,1,-9,1,3,-8,Blocks.AIR);
            box(-1,0,-9,1,0,-8,Blocks.DEEPSLATE_TILES);
            state.put("Nodes",nodes);state.putLong("Origin",origin.asLong());state.putInt("Height",high);
        }
        void bridge(boolean dual){
            box(-8,-8,-6,8,-1,6,Blocks.AIR);box(-8,-9,-6,8,-9,6,Blocks.OBSIDIAN);
            box(-8,0,-6,8,0,6,Blocks.AIR);
            // Two broad landings connect each doorway; a broken axial span prevents a simple walk-through.
            box(-8,0,-8,8,0,-7,Blocks.CRACKED_DEEPSLATE_BRICKS);
            box(-8,0,7,8,0,8,Blocks.CRACKED_DEEPSLATE_BRICKS);
            for(int i=0;i<7;i++){
                int x=i%2==0?-1:1,z=-6+i*2;
                Block b=dual?(i%2==0?LeylineContent.PHASE_MATERIAL:LeylineContent.PHASE_AETHER):LeylineContent.VEIL;
                for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++)put(x+dx,0,z+dz,b);
                node(dual?(i%2==0?"phase_a":"phase_b"):"bridge",i,x,0,z,null);
            }
            for(int s:new int[]{-1,1}){
                box(s<0?-8:6,0,-5,s<0?-6:8,0,5,Blocks.SMOOTH_STONE);
                node("drain",s+1,s*7,0,0,null);
            }
            node("goal",0,0,1,8,LeylineContent.NODE);
        }
        void vault(){
            defaultInt("TrueIndex",8);
            ring(8,0,Blocks.GILDED_BLACKSTONE);
            for(int i=0;i<9;i++){
                int x=(i%3-1)*5,z=(i/3-1)*5;
                put(x,0,z,Blocks.CHISELED_QUARTZ_BLOCK);node("vault",i,x,1,z,Blocks.CHEST);
            }
            node("prism",0,0,8,0,Blocks.AMETHYST_BLOCK);
            for(int y=9;y<10;y++)put(0,y,0,Blocks.CHAIN);
            put(-7,0,-7,Blocks.CHISELED_QUARTZ_BLOCK);node("mirror_base",0,-7,0,-7,null);
        }
        void tide(){
            for(int x=-4;x<=4;x++)for(int z=-8;z<=8;z++)put(x,0,z,(x%4==0)?Blocks.DARK_PRISMARINE:Blocks.PRISMARINE_BRICKS);
            for(int i=0;i<4;i++){
                int x=i%2==0?-5:5,z=-6+i*4;
                column(x,z,5,Blocks.PRISMARINE);put(x,4,z,Blocks.SEA_LANTERN);
                node("cover",i,x+(x<0?-1:1),1,z,null);
            }
            box(-2,0,7,2,0,9,Blocks.MOSS_BLOCK);
            put(0,0,8,Blocks.DIRT);
            put(0,1,8,com.tnc.tnc.life.routes.RouteContent.PLANTS.get("prism_crown").get().defaultBlockState()
                .setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,true).setValue(com.tnc.tnc.life.routes.RoutePlantBlock.AGE,3));
            node("plant",0,0,1,8,null);node("goal",0,0,1,8,null);
        }
        void echo(){
            box(-7,0,-7,7,0,7,Blocks.POLISHED_BLACKSTONE);
            for(int i=0;i<4;i++){
                int x=(i%2==0?-3:3),z=-5+i*3;
                put(x,0,z,Blocks.CRACKED_STONE_BRICKS);node("footprint",i,x,0,z,null);
            }
            for(int x=-5;x<=5;x++)for(int y=1;y<=6;y++)put(x,y,8,Blocks.CHISELED_STONE_BRICKS);
            node("echo_wall",0,0,2,8,null);put(-7,1,-5,Blocks.SKELETON_SKULL);
            put(0,1,9,Blocks.CHEST);
        }
        void lens(){
            defaultInt("CoreIndex",73);
            box(-8,0,4,8,0,7,Blocks.POLISHED_BASALT);
            box(-7,1,5,7,10,7,Blocks.STONE_BRICKS);
            for(int y=1;y<=10;y++)for(int x=-7;x<=7;x++)node("lens_wall",(y-1)*15+x+7,x,y,5,null);
            for(int x:new int[]{-9,9})column(x,6,11,Blocks.CHISELED_STONE_BRICKS);
            for(int x:new int[]{-5,0,5}){put(x,0,2,Blocks.SMOOTH_QUARTZ);node("mirror_base",x+5,x,0,2,null);}
            // A side stair and upper gallery let a survival player reach every row without building.
            for(int i=0;i<8;i++)box(-10,0,-6+i,-9,i,-6+i,Blocks.STONE_BRICKS);
            box(-10,7,2,8,7,3,Blocks.STONE_BRICK_SLAB);
            for(int x=-7;x<=7;x++)put(x,8,2,Blocks.IRON_BARS);
        }
        void sundial(){
            defaultInt("TreasureHour",3);ring(6,0,Blocks.SMOOTH_SANDSTONE);
            put(0,0,0,Blocks.DIRT);
            put(0,1,0,com.tnc.tnc.life.routes.RouteContent.PLANTS.get("shadow_hour_orchid").get().defaultBlockState()
                .setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,true).setValue(com.tnc.tnc.life.routes.RoutePlantBlock.AGE,3));
            column(2,0,3,Blocks.CHISELED_SANDSTONE);
            node("sun",0,0,7,0,Blocks.GLOWSTONE);
            int[][] doors={{0,-7},{8,0},{0,8},{-8,0}};
            for(int i=0;i<4;i++){
                int x=doors[i][0],z=doors[i][1];
                if(x==0)box(-2,1,z,2,5,z,Blocks.CUT_SANDSTONE);else box(x,1,-2,x,5,2,Blocks.CUT_SANDSTONE);
                node("dial_door",i,x,2,z,null);
            }
        }
        void factions(){
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)put(x,0,z,x<0?Blocks.POLISHED_BLACKSTONE:Blocks.PACKED_ICE);
            for(int z=-7;z<=7;z++)for(int y=1;y<=5;y++)node("barrier",(z+7)*5+y,0,y,z,Blocks.TINTED_GLASS);
            node("barrier_core",0,0,1,-7,LeylineContent.NODE);
            for(int z:new int[]{-6,6}){put(-7,1,z,Blocks.MAGMA_BLOCK);put(7,1,z,Blocks.BLUE_ICE);}
        }
        void stomach(){
            for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)put(x,0,z,(x*x+z*z)%7<2?Blocks.NETHER_WART_BLOCK:Blocks.CRIMSON_NYLIUM);
            for(int x=-9;x<=9;x+=6)for(int z:new int[]{-9,9}){
                column(x,z,5,Blocks.CRIMSON_HYPHAE);put(x,5,z,Blocks.SHROOMLIGHT);
            }
            node("vent",0,-8,2,-6,LeylineContent.NODE);box(-9,1,-7,-7,1,-5,Blocks.BONE_BLOCK);
            node("treasure",0,0,1,3,Blocks.CHEST);
            for(int x:new int[]{-3,3})put(x,1,3,Blocks.AMETHYST_CLUSTER);
        }
        void vines(boolean polluted){
            ring(7,0,polluted?Blocks.GILDED_BLACKSTONE:Blocks.MOSS_BLOCK);
            for(int x=-5;x<=5;x++)for(int y=1;y<=4;y++)if((x+y)%3!=0)
                node("vine",(x+5)*4+y,x,y,5,Blocks.MANGROVE_ROOTS);
            box(-8,0,-6,-4,0,-2,Blocks.POLISHED_DEEPSLATE);
            for(int x=-8;x<=-4;x++)for(int z=-6;z<=-2;z++)if(x==-8||x==-4||z==-6||z==-2)put(x,1,z,Blocks.STONE_BRICK_SLAB);
            node(polluted?"pool":"basin",0,-6,1,-4,LeylineContent.NODE);
            if(polluted)for(int x=-7;x<=-5;x++)for(int z=-5;z<=-3;z++)if(x!=-6||z!=-4)put(x,1,z,Blocks.WATER);
            node("treasure",0,0,1,8,Blocks.CHEST);
        }
        void puppets(){
            box(-8,0,2,8,0,8,Blocks.DARK_OAK_PLANKS);
            for(int x:new int[]{-8,8}){
                column(x,3,10,Blocks.POLISHED_BLACKSTONE_BRICKS);
                box(x<0?-10:7,7,3,x<0?-7:10,7,8,Blocks.DARK_OAK_PLANKS);
            }
            // Broad stair route makes the weak puppeteer physically reachable without building.
            for(int i=0;i<7;i++)box(-9,0,-5+i,-7,i,-5+i,Blocks.DARK_OAK_PLANKS);
            for(int i=0;i<3;i++){
                int x=(i-1)*5;node("string",i,x,6,5,LeylineContent.VEIL);
                put(x,1,5,Blocks.CHISELED_POLISHED_BLACKSTONE);put(x,2,5,Blocks.GOLD_BLOCK);
            }
            node("puppeteer",0,-8,8,6,LeylineContent.NODE);
            for(int x=-8;x<=8;x++)put(x,12,4,Blocks.RED_WOOL);
        }
        void chess(){
            for(int i=0;i<25;i++){
                int x=(i%5-2)*2,z=(i/5-2)*2;
                box(x,0,z,x+1,0,z+1,((i%5+i/5)%2==0)?Blocks.QUARTZ_BLOCK:Blocks.POLISHED_BLACKSTONE);
                node("chess_cell",i,x,0,z,null);
            }
            node("drain",0,2,0,2,null);
            column(-2,2,3,Blocks.CHISELED_QUARTZ_BLOCK);node("pillar",0,-2,1,2,null);
            column(2,-2,3,Blocks.CHISELED_QUARTZ_BLOCK);node("pillar",1,2,1,-2,null);
        }
        void refraction(){
            column(0,3,6,Blocks.CHISELED_STONE_BRICKS);column(-4,-3,6,Blocks.CHISELED_STONE_BRICKS);
            int[][] mirrors={{-4,-5},{4,-5},{4,3}};
            for(int i=0;i<3;i++){
                int x=mirrors[i][0],z=mirrors[i][1];put(x,0,z,Blocks.SMOOTH_QUARTZ);node("mirror_base",i,x,0,z,null);
            }
            node("beam_source",0,-8,1,-5,LeylineContent.NODE);
            box(-3,1,8,3,5,8,Blocks.CHISELED_DEEPSLATE);node("lock",0,0,2,8,LeylineContent.NODE);
        }
        void alchemy(){
            ring(5,0,Blocks.POLISHED_BLACKSTONE);box(-2,0,2,1,0,5,Blocks.MAGMA_BLOCK);
            for(int part=0;part<4;part++)put(-1+part%2,1,3+part/2,AlchemyContent.CAULDRON.defaultBlockState().setValue(AlchemyBlock.PART,part));
            node("cauldron",0,-1,1,3,null);node("igniter",0,4,1,3,LeylineContent.NODE);
            for(int x:new int[]{-7,7}){box(x,1,-5,x,1,5,Blocks.POLISHED_BLACKSTONE_SLAB);put(x,2,-5,Blocks.BREWING_STAND);put(x,2,5,Blocks.CAULDRON);}
        }
        void meal(){
            ring(6,0,Blocks.CHISELED_QUARTZ_BLOCK);
            for(int i=0;i<5;i++){
                double a=i*Math.PI*2/5-Math.PI/2;int x=(int)Math.round(Math.cos(a)*5),z=(int)Math.round(Math.sin(a)*5);
                put(x,1,z,Blocks.CHISELED_STONE_BRICKS);node("plate",i,x,2,z,LeylineContent.NODE);
            }
            put(0,1,0,Blocks.QUARTZ_PILLAR);put(0,3,0,Blocks.AMETHYST_CLUSTER);
        }
        void music(){
            ring(8,0,Blocks.CALCITE);
            for(int i=0;i<8;i++){
                double a=i*Math.PI/4;int x=(int)Math.round(Math.cos(a)*7),z=(int)Math.round(Math.sin(a)*7),h=2+i%4;
                column(x,z,h,Blocks.AMETHYST_BLOCK);node("crystal",i,x,h,z,null);
                put(x,h+1,z,Blocks.AMETHYST_CLUSTER);
            }
            node("treasure",0,0,1,0,Blocks.CHEST);
            for(int x:new int[]{-8,8})for(int z:new int[]{-8,8}){
                put(x,0,z,Blocks.DIRT);
                if(x==z)put(x,1,z,com.tnc.tnc.life.botanical.BotanicalContent.BLOCKS.get("echo_bean").defaultBlockState()
                    .setValue(com.tnc.tnc.life.botanical.BotanicalBlock.WILD,true));
                else put(x,1,z,com.tnc.tnc.life.routes.RouteContent.PLANTS.get("turnpage_fern").get().defaultBlockState()
                    .setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,true));
            }
        }
        void gravity(){
            box(-7,0,-7,7,0,7,Blocks.OBSIDIAN);
            for(int x:new int[]{-9,9}){
                for(int y=1;y<27;y++){put(x,y,-4,Blocks.PURPUR_PILLAR);put(x,y,4,Blocks.PURPUR_PILLAR);}
                node("gravity_node",x<0?0:1,x,2,0,LeylineContent.NODE);
            }
            box(-8,26,-8,8,26,8,Blocks.CHISELED_STONE_BRICKS);
            for(int x:new int[]{-5,5})for(int z:new int[]{-5,5}){
                put(x,25,z,Blocks.BOOKSHELF);put(x,24,z,Blocks.FLOWERING_AZALEA_LEAVES);
            }
            node("ceiling_goal",0,0,25,4,Blocks.CHEST);
        }
        void rapids(){
            box(-8,-8,-6,8,0,6,Blocks.AIR);box(-8,-9,-6,8,-9,6,Blocks.OBSIDIAN);
            for(int i=0;i<3;i++)node("current",i,(i-1)*4,2+i*3,-5+i*4,null);
            box(-7,9,7,7,9,9,Blocks.PRISMARINE_BRICKS);
            node("rapids_goal",0,0,10,8,LeylineContent.NODE);
        }
        void scale(){
            column(0,3,5,Blocks.POLISHED_BLACKSTONE);box(-5,5,3,5,5,3,Blocks.GOLD_BLOCK);
            for(int x:new int[]{-5,5}){
                box(x,2,3,x,4,3,Blocks.CHAIN);box(x-1,1,2,x+1,1,4,Blocks.POLISHED_BLACKSTONE_SLAB);
            }
            node("scale",0,-5,2,3,LeylineContent.NODE);node("treasure",0,5,2,3,Blocks.CHEST);
        }
        void hungry(){
            box(-3,0,2,3,0,8,Blocks.MAGMA_BLOCK);
            box(-2,1,3,2,3,7,Blocks.POLISHED_BLACKSTONE);box(-1,2,3,1,2,3,Blocks.AIR);
            put(-2,3,3,Blocks.SHROOMLIGHT);put(2,3,3,Blocks.SHROOMLIGHT);
            for(int x=-1;x<=1;x++){put(x,3,3,Blocks.QUARTZ_SLAB);put(x,1,3,Blocks.QUARTZ_SLAB);}
            box(-1,1,0,1,1,2,Blocks.RED_CARPET);node("mouth",0,0,2,3,LeylineContent.NODE);
        }
        void exhibition(){
            defaultInt("TreasureMask",(1<<2)|(1<<4));ring(9,0,Blocks.SMOOTH_QUARTZ);
            for(int i=0;i<6;i++){
                int x=i%2==0?-5:5,z=(i/2-1)*6;
                box(x-1,0,z-1,x+1,0,z+1,Blocks.CHISELED_QUARTZ_BLOCK);
                box(x-1,1,z-1,x+1,4,z+1,Blocks.ORANGE_STAINED_GLASS);
                put(x,1,z,Blocks.CHISELED_STONE_BRICKS);put(x,2,z,Blocks.STONE_BRICKS);
                node("amber",i,x,2,z-1,null);
                ((CompoundTag)nodes.get(nodes.size()-1)).putLong("Center",origin.offset(x,1,z).asLong());
            }
        }
        void pawn(){
            box(-8,1,4,-3,1,4,Blocks.DARK_OAK_PLANKS);
            for(int x=-8;x<=-3;x++)put(x,4,6,Blocks.BOOKSHELF);
            node("pawn",0,-5,2,5,LeylineContent.NODE);
            box(2,-1,1,8,-1,7,Blocks.PRISMARINE_BRICKS);
            for(int x=2;x<=8;x++)for(int z=1;z<=7;z++){
                boolean rim=x==2||x==8||z==1||z==7;put(x,0,z,rim?Blocks.SMOOTH_STONE:Blocks.WATER);
            }
            node("spring",0,5,0,4,null);node("spring_core",0,5,-1,4,LeylineContent.NODE);
            for(int x:new int[]{3,7})put(x,1,7,Blocks.SEA_LANTERN);
        }
    }
}
