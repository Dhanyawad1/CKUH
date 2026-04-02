package com.xanicle.bot.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

/**
 * Utility methods for inspecting and changing the player's inventory.
 */
public final class InventoryUtil {

    private InventoryUtil() {}

    /**
     * Returns the first hotbar slot (0-8) that contains {@code item},
     * or -1 if the item is not in the hotbar.
     */
    public static int findInHotbar(ClientPlayerEntity player, Item item) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the offhand item stack.
     */
    public static ItemStack getOffhand(ClientPlayerEntity player) {
        return player.getInventory().offHand.get(0);
    }

    /**
     * Returns the first inventory slot (0-35) that contains {@code item},
     * checking hotbar first, or -1 if not found anywhere.
     */
    public static int findInInventory(ClientPlayerEntity player, Item item) {
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Switches the hotbar selection to {@code slot} (0-8) and sends the
     * appropriate packet so the server agrees on the selection.
     */
    public static void switchToSlot(MinecraftClient client, int slot) {
        if (slot < 0 || slot > 8) return;
        client.player.getInventory().selectedSlot = slot;
        client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    /**
     * Returns true if the player has at least one of {@code item} anywhere
     * in their hotbar (slots 0-8).
     */
    public static boolean hasInHotbar(ClientPlayerEntity player, Item item) {
        return findInHotbar(player, item) != -1;
    }

    /**
     * Returns the count of {@code item} across all hotbar slots.
     */
    public static int countInHotbar(ClientPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }
}
