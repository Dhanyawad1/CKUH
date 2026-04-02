package com.xanicle.bot.combat;

import com.xanicle.bot.util.CombatUtil;
import com.xanicle.bot.util.InventoryUtil;
import com.xanicle.bot.util.RotationUtil;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Places obsidian blocks adjacent to the target so that end crystals
 * can be placed on them.  Mirrors the obbycmd / spawnobby logic from
 * the original datapack.
 *
 * Cooldown is controlled by BotController; this class just tries to
 * place when called.
 */
public class ObsidianLogic {

    /**
     * Attempts to place one obsidian block in the best position near
     * the target.  Returns true if a placement packet was sent.
     */
    public boolean tryPlace(MinecraftClient client, AbstractClientPlayerEntity target) {
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        // Need obsidian in hotbar
        int obsSlot = InventoryUtil.findInHotbar(player, Items.OBSIDIAN);
        if (obsSlot == -1) return false;

        // Find a valid placement position
        List<BlockPos> candidates = CombatUtil.getObsidianCandidates(client, target);
        if (candidates.isEmpty()) return false;

        // The block we want to place obsidian AT is candidates.get(0).
        // We click the top face of the block directly below it.
        BlockPos placeAt = candidates.get(0);
        BlockPos clickBlock = placeAt.down(); // the block we actually click on

        // Verify the click target is solid (shouldn't be air)
        if (client.world.getBlockState(clickBlock).isAir()) return false;
        // Verify the placement spot is still air (world may have changed)
        if (!client.world.getBlockState(placeAt).isAir()) return false;

        // Rotate to face the click block
        Vec3d clickPoint = Vec3d.ofCenter(clickBlock).add(0, 0.5, 0);
        float[] rots = RotationUtil.getRotationsTo(player.getEyePos(), clickPoint);
        player.setYaw(rots[0]);
        player.setPitch(rots[1]);

        // Switch to obsidian
        InventoryUtil.switchToSlot(client, obsSlot);

        // Build the hit result (clicking the top face of clickBlock)
        BlockHitResult hitResult = CombatUtil.topFaceHit(clickBlock);

        // Send the interaction
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        player.swingHand(Hand.MAIN_HAND);

        return true;
    }

    /**
     * Returns true if there is already at least one obsidian block near
     * the target that could hold a crystal — used to skip re-placing.
     */
    public boolean hasObsidianNearTarget(MinecraftClient client,
                                         AbstractClientPlayerEntity target) {
        return !CombatUtil.getCrystalCandidates(client, target).isEmpty();
    }
}
