package com.tnc.tnc.dungeon.mirror;

import java.util.UUID;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Store originals with the player's inventory in the same player save, including across respawns. */
@Mod.EventBusSubscriber(modid="tnc")
public final class MirrorKit {
    private static final String ESCROW="EchoMirrorOriginalKit";
    public static final String LOAN="tncMirrorLoan";
    private MirrorKit() {}

    private static CompoundTag saved(ServerPlayer player) {
        var root=player.getPersistentData();
        if(!root.contains(Player.PERSISTED_NBT_TAG,Tag.TAG_COMPOUND))
            root.put(Player.PERSISTED_NBT_TAG,new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    static void equip(ServerPlayer player, UUID run) {
        var data=saved(player);
        if(data.contains(ESCROW)) {
            var old=data.getCompound(ESCROW);
            if(old.hasUUID("Run") && old.getUUID("Run").equals(run)) return;
            restore(player);
        }
        player.closeContainer();
        var inv=player.getInventory();
        var original=new CompoundTag();
        original.putUUID("Run",run);
        original.put("Items",inv.save(new ListTag()));
        original.putInt("Selected",inv.selected);
        data.put(ESCROW,original);
        original.put("Accessories",MirrorAccessories.suspend(player));
        player.stopUsingItem();
        inv.clearContent();
        Item[] bar={Items.IRON_SWORD,Items.IRON_AXE,Items.FISHING_ROD,Items.WATER_BUCKET,
                Items.IRON_PICKAXE,Items.BOW,Items.GOLDEN_APPLE,Items.COOKED_BEEF,Items.ENDER_PEARL};
        for(int slot=0;slot<bar.length;slot++) inv.setItem(slot,loan(bar[slot], slot==6?2:slot==7?4:1,run));
        inv.setItem(9,loan(Items.ARROW,12,run));
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND}) {
            Item item=switch(slot) {
                case HEAD -> Items.IRON_HELMET; case CHEST -> Items.IRON_CHESTPLATE;
                case LEGS -> Items.IRON_LEGGINGS; case FEET -> Items.IRON_BOOTS; default -> Items.SHIELD;
            };
            player.setItemSlot(slot,loan(item,1,run));
        }
        inv.selected=0;
        syncInventory(player);
    }

    private static ItemStack loan(Item item,int count,UUID run){
        var stack=new ItemStack(item,count);
        stack.getOrCreateTag().putUUID(LOAN,run);
        return stack;
    }

    static void restore(ServerPlayer player) {
        var data=saved(player);
        if(!data.contains(ESCROW,Tag.TAG_COMPOUND))return;
        var original=data.getCompound(ESCROW);
        player.stopUsingItem();
        player.closeContainer();
        // Another system can deliver real quest mail while the duel is in progress.
        // Keep those items even when the original inventory was completely full.
        var extras=new ArrayList<ItemStack>();
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);
            if(!stack.isEmpty() && !borrowed(stack))extras.add(stack.copy());
        }
        player.getInventory().load(original.getList("Items",Tag.TAG_COMPOUND));
        player.getInventory().selected=Math.max(0,Math.min(8,original.getInt("Selected")));
        MirrorAccessories.restore(player,original.getList("Accessories",Tag.TAG_COMPOUND),extras);
        data.remove(ESCROW);
        for (var extra : extras) {
            player.getInventory().add(extra);
            if (extra.isEmpty()) continue;
            // A partial stack can fit in the original inventory. Keep any remainder in personal mail.
            var dungeon = com.tnc.tnc.dungeon.DungeonData.get(player.server);
            var profile = dungeon.profile(player.getUUID());
            var mail = profile.getList("LeylineMail", Tag.TAG_COMPOUND);
            mail.add(extra.save(new CompoundTag()));
            profile.put("LeylineMail", mail);
            dungeon.setDirty();
        }
        syncInventory(player);
    }

    private static void syncInventory(ServerPlayer player) {
        var inventory=player.getInventory();
        inventory.setChanged();
        player.inventoryMenu.sendAllDataToRemote();
        player.connection.send(new ClientboundSetCarriedItemPacket(inventory.selected));
    }

    private static boolean wearing(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(ESCROW);
    }
    private static boolean borrowed(ItemStack stack){
        return stack.hasTag() && stack.getTag().hasUUID(LOAN);
    }
    @SubscribeEvent public static void drops(LivingDropsEvent e){
        if(e.getEntity() instanceof Player)e.getDrops().removeIf(drop->borrowed(drop.getItem()));
    }

    @SubscribeEvent public static void reconcile(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount%10!=0)return;
        if(MirrorFight.silent(p)) equip(p,MirrorFight.activeRun(p));
        else if(wearing(p)) restore(p);
    }

    @SubscribeEvent public static void containers(PlayerContainerEvent.Open e){
        if(e.getEntity() instanceof ServerPlayer p && wearing(p)) p.closeContainer();
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pickup(EntityItemPickupEvent e) {
        if(wearing(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent public static void toss(ItemTossEvent e){
        if(e.getPlayer() instanceof ServerPlayer p && wearing(p)){
            e.setCanceled(true);
            p.getInventory().add(e.getEntity().getItem());
        }
    }
}
