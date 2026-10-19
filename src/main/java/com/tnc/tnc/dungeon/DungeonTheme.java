package com.tnc.tnc.dungeon;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public enum DungeonTheme {
    DESERT("desert","黄沙遗庭",0xd6b677,Blocks.SMOOTH_SANDSTONE,Blocks.CUT_SANDSTONE,Blocks.CHISELED_SANDSTONE,Blocks.SEA_LANTERN,"wolf"),
    CRYPT("crypt","锁钟地牢",0xaf8872,Blocks.DEEPSLATE_BRICKS,Blocks.POLISHED_DEEPSLATE,Blocks.CRACKED_DEEPSLATE_BRICKS,Blocks.SHROOMLIGHT,"skull"),
    TIDE("tide","沉潮回廊",0x67d8cf,Blocks.PRISMARINE_BRICKS,Blocks.DARK_PRISMARINE,Blocks.PRISMARINE,Blocks.SEA_LANTERN,"guardian"),
    ASTRAL("astral","星眠幻庭",0x94baff,Blocks.QUARTZ_BRICKS,Blocks.POLISHED_BLACKSTONE_BRICKS,Blocks.BLUE_TERRACOTTA,Blocks.SEA_LANTERN,"unicorn");
    public final String id,name,gate; public final int color;
    private final Block floor,wall,trim,lamp;
    DungeonTheme(String id,String name,int color,Block floor,Block wall,Block trim,Block lamp,String gate){this.id=id;this.name=name;this.color=color;this.floor=floor;this.wall=wall;this.trim=trim;this.lamp=lamp;this.gate=gate;}
    public BlockState floor(){return floor.defaultBlockState();}public BlockState wall(){return wall.defaultBlockState();}public BlockState trim(){return trim.defaultBlockState();}public BlockState lamp(){return lamp.defaultBlockState();}
    public static DungeonTheme find(String id){for(var v:values())if(v.id.equals(id))return v;return DESERT;}
}
