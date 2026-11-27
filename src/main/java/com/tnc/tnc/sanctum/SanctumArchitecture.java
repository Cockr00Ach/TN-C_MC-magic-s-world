package com.tnc.tnc.sanctum;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The finished hall is a basilica built around a clear floor, with a pointed rib vault and clerestory. */
final class SanctumArchitecture {
    static final int FINAL_Z = 224;
    static final int[] DOORS = {17, 54, 74, 129, 152};
    static final int[] BELLS = {0, -6, 6};
    static final int ANCHOR_Z = 100;
    static final int ALTAR_Z = 197;
    private static final TicketType<ChunkPos> CONSTRUCTION = TicketType.create("tnc_sanctum_masonry", Comparator.comparingLong(ChunkPos::toLong));
    private static final Block ASH = Blocks.DEEPSLATE_TILES, RIB = Blocks.POLISHED_BLACKSTONE_BRICKS;
    private static final Block SEAM = Blocks.CHISELED_POLISHED_BLACKSTONE, GOLD = Blocks.CUT_COPPER;
    private static final Block BONE = Blocks.SMOOTH_BASALT, GLASS = Blocks.PURPLE_STAINED_GLASS;
    private static final Block COLD = Blocks.OCHRE_FROGLIGHT, BLUE = Blocks.PEARLESCENT_FROGLIGHT;
    private final ServerLevel level;
    private final BlockPos origin;
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final ArrayDeque<Cell> pending = new ArrayDeque<>();
    private ChunkPos ticket;
    private int chunkIndex, columnIndex;
    private final IntPredicate gateOpen;
    private final Consumer<ChunkPos> onChunk;
    private record Cell(BlockPos pos, Block block) {}

    SanctumArchitecture(ServerLevel level, BlockPos origin, IntPredicate gateOpen, Consumer<ChunkPos> onChunk) {
        this.level = level;
        this.origin = origin;
        this.gateOpen = gateOpen;
        this.onChunk = onChunk;
        ChunkPos start = new ChunkPos(origin.offset(-72,0,-8));
        ChunkPos end = new ChunkPos(origin.offset(72,0,296));
        for (int z=start.z; z<=end.z; z++) for (int x=start.x; x<=end.x; x++) {
            ChunkPos c = new ChunkPos(x,z);
            int nearZ = Math.max(-8,c.getMinBlockZ()-origin.getZ());
            int farZ = Math.min(296,c.getMaxBlockZ()-origin.getZ());
            int width = 0;
            for (int localZ=nearZ; localZ<=farZ; localZ++) width = Math.max(width,halfWidth(localZ));
            if (c.getMaxBlockX() >= origin.getX()-width && c.getMinBlockX() <= origin.getX()+width) chunks.add(c);
        }
        for (int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) put(origin.offset(x,-1,z), ASH);
    }

    static int halfWidth(int z) {
        if (z<=17) return 11;
        if (z<=54) return 9;
        if (z<=74) return 14;
        if (z<=129) return 38;
        if (z<152) return 13;
        return 72;
    }

    private static int height(int z) {
        if (z<=17) return 15;
        if (z<=54) return 13;
        if (z<=74) return 18;
        if (z<=129) return 59;
        if (z<152) return 18;
        return 92;
    }

    boolean built() { return chunkIndex >= chunks.size(); }
    int percent() { return 100*chunkIndex/chunks.size(); }
    void close() {
        if (ticket != null) level.getChunkSource().removeRegionTicket(CONSTRUCTION,ticket,2,ticket);
        ticket=null;
    }

    void tick() {
        if (built()) return;
        long deadline = System.nanoTime() + 7_000_000L;
        int writes = 0, scanned = 0;
        while (!built() && writes < 1400 && scanned < 768 && System.nanoTime()<deadline) {
            ChunkPos chunk=chunks.get(chunkIndex);
            if (ticket==null) {
                ticket=chunk;
                level.getChunkSource().addRegionTicket(CONSTRUCTION,chunk,2,chunk);
            }
            if (level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null) return;
            if (columnIndex==0 && pending.isEmpty()) onChunk.accept(chunk);
            if (!pending.isEmpty()) {
                Cell cell=pending.removeFirst();
                put(cell.pos, cell.block);
                writes++;
            } else if (columnIndex<256) {
                int x=chunk.getMinBlockX()+(columnIndex & 15)-origin.getX();
                int z=chunk.getMinBlockZ()+(columnIndex >> 4)-origin.getZ();
                columnIndex++; scanned++;
                if (z>=-8 && z<=296 && Math.abs(x)<=halfWidth(z)) column(x,z);
            } else {
                close();
                chunkIndex++;
                columnIndex=0;
            }
        }
    }

    private void add(int x,int y,int z,Block block) { pending.addLast(new Cell(origin.offset(x,y,z),block)); }
    private void put(BlockPos at,Block block) {
        if (!level.getBlockState(at).is(block)) level.setBlock(at,block.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
    void set(int x,int y,int z,Block block) { put(origin.offset(x,y,z),block); }
    void door(int index, boolean open) {
        for(int x=-3;x<=3;x++) for(int y=0;y<7;y++) set(x,y,DOORS[index],open?Blocks.AIR:Blocks.POLISHED_BLACKSTONE_BRICKS);
    }

    private static Block floor(int x, int z, int w) {
        int ax=Math.abs(x);
        if(z>=152) {
            int dz=z-FINAL_Z;
            double radius=Math.hypot(x,dz);
            if (Math.abs(radius-32)<.5 || Math.abs(radius-12)<.48)
                return ((Math.abs(dz)+ax)%7==0)?COLD:GOLD;
            if ((ax==Math.abs(dz) || x==0 || dz==0) && radius>=12 && radius<=32)
                return Blocks.CHISELED_DEEPSLATE;
            if (radius<7 && (ax+Math.abs(dz))%4==0) return Blocks.GILDED_BLACKSTONE;
            if (ax==42 || ax==55 || Math.abs(dz)==54) return SEAM;
            if (ax==41 && Math.floorMod(z,10)==0) return BLUE;
        } else if (z>=75 && z<129) {
            if (z==ANCHOR_Z && ax<10) return GOLD;
            if (Math.abs(ax-8)<=2 && Math.abs(z-ANCHOR_Z)<=2) return Math.abs(ax-8)==2||Math.abs(z-ANCHOR_Z)==2?GOLD:SEAM;
        } else {
            if(ax==3 && Math.floorMod(z,8)==0) return COLD;
            if(ax==4) return SEAM;
            if(z==8 && x>=2 && x<=8) return x%2==0?BONE:SEAM;
            if(z>=60 && z<=63) for(int i=0;i<BELLS.length;i++)
                if(x==BELLS[i] && z>=63-i) return COLD;
        }
        if (ax==w-1) return RIB;
        return Math.floorMod(17*x+11*z,41)==0?Blocks.CRACKED_DEEPSLATE_TILES:ASH;
    }
    // A closed eye in a fractured rose window survives even at minimum particles or without shaders.
    private static Block apseGlass(int x,int y,Block stone) {
        int ax=Math.abs(x),dy=y-49;
        double r=Math.hypot(x,dy);
        if(r>29 || y<19)return stone;
        if(r>27 || r>24 && r<25.3)return x>8&&dy<0&&Math.abs(x+dy)<3?RIB:GOLD;
        if(r>21 && r<23 && Math.floorMod((int)(Math.atan2(dy,x)*72/Math.PI),12)<8)return COLD;
        if(r<21 && ax<19 && Math.abs(dy)<(18-ax)*.45+1){
            if(ax<=1)return Blocks.OBSIDIAN;
            return Math.abs(dy)<1.4?GOLD:(ax%5==0?BLUE:Blocks.BLACK_GLAZED_TERRACOTTA);
        }
        if(x==dy || x==-dy || ax==0 || dy==0)return SEAM;
        int shard=Math.floorMod(x+3*y,41);
        return shard==0?BLUE:shard==1?GLASS:ax%5==1?Blocks.BLACK_GLAZED_TERRACOTTA:Blocks.PURPLE_TERRACOTTA;
    }
    private void column(int x,int z) {
        int w=halfWidth(z), h=height(z), ax=Math.abs(x);
        add(x,-1,z,floor(x,z,w));
        add(x,h,z,ASH);
        int gate = -1;
        for(int i=0;i<DOORS.length;i++) if(z==DOORS[i]) gate=i;
        boolean wall = ax==w || z==-8 || z==296 || gate>=0;
        // Close the stepped shoulders where a narrow room meets a taller/wider volume.
        if (z>-8 && (ax>halfWidth(z-1) || height(z)>height(z-1))) {
            int start=ax>halfWidth(z-1)?0:height(z-1)+1;
            for(int y=start;y<h;y++) add(x,y,z,y%11==0?RIB:ASH);
        }
        if (z<296 && (ax>halfWidth(z+1) || height(z)>height(z+1))) {
            int start=ax>halfWidth(z+1)?0:height(z+1)+1;
            for(int y=start;y<h;y++) add(x,y,z,y%11==0?RIB:ASH);
        }
        if(wall) {
            for(int y=0;y<h;y++) {
                if(gate>=0 && ax<=3 && y<7) { add(x,y,z,gateOpen.test(gate)?Blocks.AIR:RIB); continue; }
                boolean window = ax==w && h>=18 && y>7 && y<h-7 && Math.floorMod(z,14)>=4
                        && Math.floorMod(z,14)<=8 && Math.abs(Math.floorMod(z,14)-6)<(h-y)/5+1;
                Block b=window?(Math.floorMod(z+y,21)==0?BLUE:Math.floorMod(z-y,17)==0?GLASS:Blocks.PURPLE_TERRACOTTA):((y==1||y==7||y==h-2)?SEAM:RIB);
                if ((gate>=0 || z==296) && (ax==4 && y<=8 || y>=7 && y<=12 && ax==12-y)) b=GOLD;
                if(z==296)b=apseGlass(x,y,b);
                add(x,y,z,b);
            }
            return;
        }
        // Paired compound piers, with progressively corbelled ribs meeting overhead.
        int bay=(w>=38)?14:8;
        int stripe=Math.floorMod(z,bay);
        int aisle=w>=38?w-10:w-2;
        int distance=Math.abs(ax-aisle);
        if (stripe<=1 && distance<=1) {
            for(int y=0;y<h-2;y++) add(x,y,z,(y==2||y==h/2||y==h-6)?SEAM:BONE);
            if(stripe==0 && ax==aisle-1) add(x,4,z,BLUE);
        } else if(stripe==0 && ax<aisle) {
            // The demon rises twelve blocks with a 112-block wingspan. Ribs bend down in the side aisles.
            int drop=w==72?Math.max(0,ax-56)*2+ax*4/aisle:(ax*ax*(h/3))/(aisle*aisle);
            int y=h-2-drop;
            add(x,y,z,SEAM);
            add(x,y+1,z,RIB);
            if(ax==0) add(x,y-1,z,GOLD);
        }
        if(w==72 && Math.floorMod(z-3,28)==0 && ax==66) {
            for(int y=12;y<h-16;y++) add(x,y,z,Blocks.CHAIN);
            add(x,11,z,BLUE);
            add(x,10,z,Blocks.CHISELED_DEEPSLATE);
        }
        if(w==72 && z>201 && z<278 && (ax>=38&&ax<=41 || ax>=49&&ax<=52) && Math.floorMod(z,6)<=1){
            add(x,0,z,Blocks.POLISHED_BLACKSTONE_SLAB);
            if(z%6==0)add(x,1,z,Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        }
        if(z==295 && ax>=10 && ax<=28){
            int top=49+(int)Math.sqrt(Math.max(0,29*29-x*x));
            if(ax%4==0)for(int y=8;y<top-40;y++)add(x,y,z,SEAM);
            add(x,top,z,GOLD);
        }
        fixtures(x,z);
    }

    private void fixtures(int x,int z) {
        if ((z==8 && Math.abs(x)<=1) || (z==7 || z==9) && Math.abs(x)<=1)
            for(int y=0;y<=7;y++) add(x,y,z,y==4?SEAM:BONE);
        int mx=Math.abs(x+7), mz=Math.abs(z-8);
        if(mx<=1&&mz<=1)add(x,0,z,mx+mz==0?Blocks.LODESTONE:Blocks.WAXED_CUT_COPPER_SLAB);
        if(mx==0&&mz==0){add(x,1,z,GOLD);add(x,2,z,COLD);add(x,4,z,Blocks.LIGHTNING_ROD);}
        if(mx==0&&mz==2){add(x,0,z,SEAM);for(int y=1;y<=3;y++)add(x,y,z,Blocks.POLISHED_BLACKSTONE_BRICK_WALL);}
        if(mx==0&&mz<=1)add(x,3,z,Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB);
        if(x==5&&z==49){add(x,0,z,SEAM);add(x,1,z,GOLD);add(x,2,z,BLUE);for(int y=4;y<=10;y++)add(x,y,z,Blocks.CHAIN);}
        if(Math.abs(x-5)+Math.abs(z-49)==1){add(x,3,z,Blocks.WAXED_CUT_COPPER_SLAB);add(x,4,z,Blocks.SOUL_LANTERN);}
        if(x==-5&&z==50){add(x,0,z,Blocks.CHISELED_BOOKSHELF); add(x,1,z,Blocks.LECTERN);}
        for(int i=0;i<BELLS.length;i++)bellBay(x,z,BELLS[i],65,i);
        wheel(x,z,4,71);wheel(x,z,0,126);
        if(Math.abs(x)<=2 && Math.abs(z-ALTAR_Z)<=1)
            for(int y=0;y<=6;y++){var block=tollBell(x,z-ALTAR_Z,y);if(block!=null)add(x,y,z,block);}
        if(Math.abs(x)==8&&z==ANCHOR_Z) add(x,-1,z,COLD);
        // Remove the first edition's pillar where the smaller warden now plants its feet over the lock.
        if(Math.abs(x)>=11&&Math.abs(x)<=13&&Math.abs(z-ANCHOR_Z-4)<=1)
            for(int y=0;y<=6;y++)add(x,y,z,Blocks.AIR);
        if(Math.abs(x)>=19&&Math.abs(x)<=21&&Math.abs(z-ANCHOR_Z-4)<=1)
            for(int y=0;y<=6;y++) add(x,y,z,y==1?SEAM:BONE);
        if(z>=133 && z<=143 && z%3==1 && Math.abs(x)>=3 && Math.abs(x)<=7){
            add(x,0,z,Blocks.DARK_OAK_SLAB); if(Math.abs(x)==7)add(x,1,z,Blocks.DARK_OAK_TRAPDOOR);
        }
        if(x==5&&z==144){add(x,0,z,SEAM);add(x,1,z,Blocks.LECTERN);}
        if(x==0&&z==148){add(x,0,z,SEAM);add(x,1,z,BLUE);add(x,2,z,Blocks.LIGHTNING_ROD);}
        if(Math.abs(x)==1&&z==148){add(x,0,z,Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB);add(x,1,z,Blocks.SOUL_LANTERN);}
        if(z==190 && Math.abs(x)>=8&&Math.abs(x)<=10) add(x,0,z,Blocks.POLISHED_BLACKSTONE_SLAB);
    }
    private void wheel(int x,int z,int cx,int cz){
        int dx=Math.abs(x-cx),dz=z-cz;
        if(dz==1&&dx<=2){add(x,0,z,Blocks.POLISHED_BLACKSTONE_SLAB);if(dx==0){add(x,1,z,SEAM);add(x,2,z,Blocks.LODESTONE);}}
        if(dz!=0 || dx>2)return;
        for(int y=0;y<=4;y++){
            int dy=Math.abs(y-2);int ring=dx*dx+dy*dy;
            if(ring>=3&&ring<=5)add(x,y,z,Blocks.WAXED_WEATHERED_CUT_COPPER);
            else if(dx==0||dy==0)add(x,y,z,dx==0&&dy==0?Blocks.LODESTONE:GOLD);
        }
    }
    private void bellBay(int x,int z,int cx,int cz,int note){
        int dx=Math.abs(x-cx),dz=Math.abs(z-cz),radius=note==2?2:1,span=radius+1,top=5+note;
        if(dz>1||dx>span)return;
        if(dx==span && dz==0){add(x,0,z,SEAM);for(int y=1;y<top;y++)add(x,y,z,Blocks.POLISHED_BLACKSTONE_BRICK_WALL);}
        if(dz==0)add(x,top,z,dx==0?SEAM:Blocks.DARK_OAK_WOOD);
        if(dx==0&&dz==0){add(x,0,z,Blocks.LODESTONE);add(x,1,z,Blocks.BELL);add(x,top-1,z,Blocks.CHAIN);}
        if(dx<=radius && (dz==1||dx==radius))add(x,2,z,Blocks.WAXED_CUT_COPPER_SLAB);
        if(dx<radius && dz<=1 && note>0)add(x,3,z,note==2?Blocks.WAXED_WEATHERED_CUT_COPPER:Blocks.WAXED_EXPOSED_CUT_COPPER);
        if(dx==0 && dz==0)for(int y=3;y<top-1;y++)add(x,y,z,y==top-2?Blocks.WAXED_CUT_COPPER_SLAB:GOLD);
    }
    private static Block tollBell(int x,int dz,int y){
        int ax=Math.abs(x),az=Math.abs(dz);
        if(az==0&&ax==2)return y==0?SEAM:y<=5?Blocks.POLISHED_BLACKSTONE_BRICK_WALL:Blocks.DARK_OAK_WOOD;
        if(az==0&&y==6)return Blocks.DARK_OAK_WOOD;
        if(x==0&&az==0)return switch(y){case 0->Blocks.LODESTONE;case 1->Blocks.BELL;case 2,3->GOLD;case 4->Blocks.WAXED_CUT_COPPER_SLAB;case 5->Blocks.CHAIN;default->null;};
        if(ax<=1&&az<=1&&y==2)return Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB;
        return null;
    }
    void tollBell(boolean raised){
        for(int x=-2;x<=2;x++)for(int z=-1;z<=1;z++)for(int y=0;y<=6;y++){
            Block b=tollBell(x,z,y);if(b!=null)set(x,y,ALTAR_Z+z,raised?b:Blocks.AIR);
        }
    }
}

