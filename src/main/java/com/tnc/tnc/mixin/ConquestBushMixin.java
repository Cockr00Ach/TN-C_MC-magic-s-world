package com.tnc.tnc.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Conquest Reforged 1.6.0 can ask an air state to accept the bush-only layers property after
 * vanilla shape updates remove the bush. Guard only those writes inside Bush#updateShape.
 */
@Mixin(targets = "com.conquestrefabricated.content.blocks.block.plants.Bush", remap = false)
abstract class ConquestBushMixin {
    @Redirect(
            method = "m_7417_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;m_61124_(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/lang/Comparable;)Ljava/lang/Object;",
                    remap = false
            ),
            require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object tnc$safelySetLayers(BlockState state, Property<?> property, Comparable<?> value) {
        if (!state.hasProperty(property)) {
            return state;
        }
        return state.setValue((Property) property, (Comparable) value);
    }
}
