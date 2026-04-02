package com.xanicle.bot.combat;

import com.xanicle.bot.util.InventoryUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Automatically moves a totem of undying from the inventory into the offhand
 * whenever the offhand totem is missing or has been consumed.
 *
 * This runs every tick but only acts when the offhand is empty or not a totem,
 * and only if the bot is currently active.
 */
public class TotemLogic {

    /**
     * Ticks after a totem pop before we try to re-equip (gives the client
     * time to receive the item-update packet from the server).
     */
    private static final int RETOTEM_DELAY = 2;

    private int retotemCooldown = 0;

    /**
     * Called every tick while the bot is active.
     * Moves a totem from the inventory to the offhand if needed.
     */
    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Count down the brief delay after a pop
        if (retotemCooldown > 0) {
            retotemCooldown--;
            return;
        }

        ItemStack offhand = InventoryUtil.getOffhand(player);

        // Offhand already has a totem — nothing to do
        if (offhand.isOf(Items.TOTEM_OF_UNDYING)) return;

        // Find a totem in the hotbar first, then fall back to full inventory
        int totemSlot = InventoryUtil.findInHotbar(player, Items.TOTEM_OF_UNDYING);
        if (totemSlot == -1) {
            totemSlot = InventoryUtil.findInInventory(player, Items.TOTEM_OF_UNDYING);
        }
        if (totemSlot == -1) return; // No totems left

        // Swap that slot with the offhand (slot 40 in the screen handler)
        // We do a "pickup then place" sequence using clickSlot.
        // Step 1: pick up the totem
        client.interactionManager.clickSlot(
                player.playerScreenHandler.syncId,
                totemSlot < 9 ? totemSlot + 36 : totemSlot,  // screen-handler slot index
                0,
                SlotActionType.PICKUP,
                player
        );
        // Step 2: place it into the offhand slot (slot 45 in the player screen handler)
        client.interactionManager.clickSlot(
                player.playerScreenHandler.syncId,
                45,
                0,
                SlotActionType.PICKUP,
                player
        );

        // Brief cooldown so we don't spam-click on the same tick
        retotemCooldown = RETOTEM_DELAY;
    }

    /** Called whenever a totem is consumed (offhand becomes empty). */
    public void onTotemPopped() {
        retotemCooldown = RETOTEM_DELAY;
    }

    public void reset() {
        retotemCooldown = 0;
    }
}
