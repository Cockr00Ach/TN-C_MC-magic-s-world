const $CustomPortalBuilder = Java.loadClass("net.kyrptonaught.customportalapi.api.CustomPortalBuilder");

StartupEvents.postInit(event => {
  $CustomPortalBuilder.beginPortal()
    ["frameBlock(net.minecraft.world.level.block.Block)"](Block.getBlock("kubejs:primordial_block"))
    .destDimID("alex_caves_dimensions:primordial_caves")
    .lightWithItem(Item.getItem("kubejs:primordial_teleport_crystal"))
    .tintColor(120, 80, 20)
    .registerPortal();

  $CustomPortalBuilder.beginPortal()
    ["frameBlock(net.minecraft.world.level.block.Block)"](Block.getBlock("kubejs:abyssal_block"))
    .destDimID("alex_caves_dimensions:abyssal_chasm")
    .lightWithItem(Item.getItem("kubejs:abyssal_teleport_crystal"))
    .tintColor(10, 10, 50)
    .registerPortal();

  $CustomPortalBuilder.beginPortal()
    ["frameBlock(net.minecraft.world.level.block.Block)"](Block.getBlock("kubejs:forlorn_block"))
    .destDimID("alex_caves_dimensions:forlorn_hollows")
    .lightWithItem(Item.getItem("kubejs:forlorn_teleport_crystal"))
    .tintColor(60, 60, 60)
    .registerPortal();

  $CustomPortalBuilder.beginPortal()
    ["frameBlock(net.minecraft.world.level.block.Block)"](Block.getBlock("kubejs:candy_block"))
    .destDimID("alex_caves_dimensions:candy_cavity")
    .lightWithItem(Item.getItem("kubejs:candy_teleport_crystal"))
    .tintColor(255, 105, 180)
    .registerPortal();

  $CustomPortalBuilder.beginPortal()
    ["frameBlock(net.minecraft.world.level.block.Block)"](Block.getBlock("kubejs:toxic_block"))
    .destDimID("alex_caves_dimensions:toxic_caves")
    .lightWithItem(Item.getItem("kubejs:toxic_teleport_crystal"))
    .tintColor(120, 255, 100)
    .registerPortal();

  $CustomPortalBuilder.beginPortal()
    ["frameBlock(net.minecraft.world.level.block.Block)"](Block.getBlock("kubejs:magnetic_block"))
    .destDimID("alex_caves_dimensions:magnetic_caves")
    .lightWithItem(Item.getItem("kubejs:magnetic_teleport_crystal"))
    .tintColor(80, 80, 255)
    .registerPortal();
});
