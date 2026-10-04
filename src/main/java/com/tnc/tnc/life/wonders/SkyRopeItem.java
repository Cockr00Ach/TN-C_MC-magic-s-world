package com.tnc.tnc.life.wonders;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
public final class SkyRopeItem extends Item {
    public SkyRopeItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResult useOn(UseOnContext c){return WonderRopes.use(c,24);}
}
