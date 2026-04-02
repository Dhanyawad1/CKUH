package com.xanicle.bot.combat;

import com.xanicle.bot.util.InventoryUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Automatically eats a golden apple (gap) when the player's health or
 * saturation drops below configured thresholds.
 *
 * Eating in Minecraft takes 32 ticks (1.6 s). During that window the player
 * holds the use button and the bot suspends other interactions.
 */
public class GapLogic {

    /** Health threshold (half-hearts, so 14 = 7 full hearts). */
    private static final float HP_THRESHOLD  = 14.0f;
    /** Saturation threshold — eat a gap to restore food early. */
    private static final float SAT_THRESHOLD = 6.0f;
    /** Ticks the use-item button is held down while eating. */
    private static final int   EAT_TICKS     = 32;

    private int eatTimer  = 0;
    private int savedSlot = -1;

    /**
     * Called every tick. Returns true while actively eating (so other logic
     * can yield).
     */
    public boolean tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        // ── Currently eating ────────────────────────────────────────────────
        if (eatTimer > 0) {
            client.options.useKey.setPressed(true);
            eatTimer--;
            if (eatTimer <= 0) {
                client.options.useKey.setPressed(false);
                // Restore hotbar slot
                if (savedSlot >= 0) {
                    InventoryUtil.switchToSlot(client, savedSlot);
                    savedSlot = -1;
                }
            }
            return true;
        }

        // ── Decide whether to eat ───────────────────────────────────────────
        boolean lowHp  = player.getHealth() < HP_THRESHOLD;
        boolean lowSat = player.getHungerManager().getSaturationLevel() < SAT_THRESHOLD;

        if (!lowHp && !lowSat) return false;

        int gapSlot = InventoryUtil.findInHotbar(player, Items.GOLDEN_APPLE);
        if (gapSlot == -1) return false; // no gapples available

        // Switch to the gapple slot and start holding use
        savedSlot = player.getInventory().selectedSlot;
        InventoryUtil.switchToSlot(client, gapSlot);
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        client.options.useKey.setPressed(true);
        eatTimer = EAT_TICKS;
        return true;
    }

    public boolean isEating() {
        return eatTimer > 0;
    }

    public void reset() {
        eatTimer = 0;
        if (savedSlot >= 0) {
            savedSlot = -1;
        }
    }
}
