package com.xanicle.bot.combat;

import com.xanicle.bot.util.InventoryUtil;
import com.xanicle.bot.util.RotationUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Respawn-anchor PvP logic.
 *
 * Sequence (mirrors the datapack anchor/* functions):
 *   1. placeAnchor  — place a respawn anchor at a good position near target
 *   2. chargeAnchor — right-click the anchor with glowstone to charge it
 *   3. detonate     — right-click a charged anchor to explode it
 *
 * Anchors only explode in the Overworld (and End).  The logic here checks
 * the dimension before attempting to detonate.
 *
 * Cooldowns are managed by BotController.
 */
public class AnchorLogic {

    /** Ticks between anchor placement and first glowstone charge. */
    public static final int ANCHOR_DELAY    = 2;
    /** Default ticks between placements. */
    public static final int DEFAULT_ANCHOR_CD = 12;

    private BlockPos anchorPos   = null;
    private int      chargeCount = 0;   // 0-4 glowstone charges applied so far
    private int      anchorDelay = 0;

    // ─── State machine ──────────────────────────────────────────────────────

    public enum Phase { IDLE, PLACED, CHARGED, DONE }
    private Phase phase = Phase.IDLE;

    /**
     * Main tick called by BotController when the target is in anchor range
     * (roughly 1.75 – 5.5 blocks, matching the datapack condition).
     */
    public void tick(MinecraftClient client, AbstractClientPlayerEntity target) {
        if (anchorDelay > 0) { anchorDelay--; return; }

        switch (phase) {
            case IDLE    -> tryPlaceAnchor(client, target);
            case PLACED  -> tryChargeAnchor(client);
            case CHARGED -> tryDetonate(client);
            case DONE    -> reset();
        }
    }

    // ─── Phase implementations ──────────────────────────────────────────────

    private void tryPlaceAnchor(MinecraftClient client, AbstractClientPlayerEntity target) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        int anchorSlot = InventoryUtil.findInHotbar(player, Items.RESPAWN_ANCHOR);
        if (anchorSlot == -1) return;

        // Find a ground position adjacent to target (same logic as obsidian placement)
        BlockPos targetFeet = BlockPos.ofFloored(target.getPos());
        BlockPos placeAt    = null;

        for (int x = -1; x <= 1 && placeAt == null; x++) {
            for (int z = -1; z <= 1 && placeAt == null; z++) {
                BlockPos candidate = targetFeet.add(x, 0, z);
                if (!client.world.getBlockState(candidate).isAir()) continue;
                if (client.world.getBlockState(candidate.down()).isAir()) continue;
                Vec3d center = Vec3d.ofCenter(candidate.down());
                if (player.getEyePos().distanceTo(center) > 4.5) continue;
                placeAt = candidate;
            }
        }
        if (placeAt == null) return;

        // Look and place
        Vec3d clickPoint = Vec3d.ofCenter(placeAt.down()).add(0, 0.5, 0);
        float[] rots = RotationUtil.getRotationsTo(player.getEyePos(), clickPoint);
        player.setYaw(rots[0]); player.setPitch(rots[1]);

        InventoryUtil.switchToSlot(client, anchorSlot);
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                new BlockHitResult(clickPoint, Direction.UP, placeAt.down(), false));
        player.swingHand(Hand.MAIN_HAND);

        anchorPos   = placeAt;
        chargeCount = 0;
        phase       = Phase.PLACED;
        anchorDelay = ANCHOR_DELAY;
    }

    private void tryChargeAnchor(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || anchorPos == null) { reset(); return; }

        // Confirm the anchor is still there
        if (!client.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
            reset(); return;
        }

        int glowSlot = InventoryUtil.findInHotbar(player, Items.GLOWSTONE);
        if (glowSlot == -1) {
            // No glowstone — skip charging and detonate with 0 charges
            // (will do nothing; reset instead)
            reset(); return;
        }

        // Right-click the anchor with glowstone
        Vec3d clickPoint = Vec3d.ofCenter(anchorPos).add(0, 0.5, 0);
        float[] rots = RotationUtil.getRotationsTo(player.getEyePos(), clickPoint);
        player.setYaw(rots[0]); player.setPitch(rots[1]);

        InventoryUtil.switchToSlot(client, glowSlot);
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                new BlockHitResult(clickPoint, Direction.UP, anchorPos, false));
        player.swingHand(Hand.MAIN_HAND);

        chargeCount++;

        // Maximum 4 charges; detonate after at least 1
        if (chargeCount >= 1) {
            phase       = Phase.CHARGED;
            anchorDelay = ANCHOR_DELAY;
        }
    }

    private void tryDetonate(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || anchorPos == null) { reset(); return; }

        // Only explodes in Overworld / End (not Nether)
        String dimension = client.world.getRegistryKey().getValue().toString();
        if (dimension.contains("the_nether")) {
            reset(); return;
        }

        // Confirm still charged
        var state = client.world.getBlockState(anchorPos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) { reset(); return; }
        int charges = state.get(RespawnAnchorBlock.CHARGES);
        if (charges <= 0) { reset(); return; }

        // Right-click with empty hand to detonate
        Vec3d clickPoint = Vec3d.ofCenter(anchorPos).add(0, 0.5, 0);
        float[] rots = RotationUtil.getRotationsTo(player.getEyePos(), clickPoint);
        player.setYaw(rots[0]); player.setPitch(rots[1]);

        // Switch to an empty / non-glowstone slot so we don't add more charges
        player.getInventory().selectedSlot = 0;

        client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                new BlockHitResult(clickPoint, Direction.UP, anchorPos, false));
        player.swingHand(Hand.MAIN_HAND);

        phase = Phase.DONE;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    public void reset() {
        anchorPos   = null;
        chargeCount = 0;
        anchorDelay = 0;
        phase       = Phase.IDLE;
    }

    public Phase getPhase() { return phase; }
}
