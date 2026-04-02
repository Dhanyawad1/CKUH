package com.xanicle.bot.combat;

import com.xanicle.bot.util.InventoryUtil;
import com.xanicle.bot.util.RotationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Throws an ender pearl toward the target when the target is far away.
 * Aims slightly upward to arc the pearl accurately over medium distances.
 */
public class PearlLogic {

    /** Ticks of cooldown after a pearl throw (pearls have a 20-tick server cooldown). */
    public static final int PEARL_COOLDOWN = 25;

    private int cooldown = 0;

    /**
     * Called every tick. Returns true if a pearl was thrown this tick.
     * The BotController is responsible for only calling this when appropriate.
     */
    public boolean tick(MinecraftClient client, AbstractClientPlayerEntity target) {
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        int pearlSlot = InventoryUtil.findInHotbar(player, Items.ENDER_PEARL);
        if (pearlSlot == -1) return false;

        // ── Aim: look slightly above the target's head to arc the pearl ────
        Vec3d eyePos    = player.getEyePos();
        Vec3d targetPos = target.getEyePos().add(0, 0.5, 0); // lead slightly upward

        float[] rots = RotationUtil.getRotationsTo(eyePos, targetPos);
        // Pull pitch up a little so the arc clears obstacles
        float adjustedPitch = rots[1] - 15f;

        player.setYaw(rots[0]);
        player.setPitch(Math.max(-90f, adjustedPitch));

        // ── Throw ───────────────────────────────────────────────────────────
        InventoryUtil.switchToSlot(client, pearlSlot);
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);

        cooldown = PEARL_COOLDOWN;
        return true;
    }

    public void reset() {
        cooldown = 0;
    }

    public int getCooldown() {
        return cooldown;
    }
}
