package com.xanicle.bot.combat;

import com.xanicle.bot.util.CombatUtil;
import com.xanicle.bot.util.InventoryUtil;
import com.xanicle.bot.util.RotationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Handles placing end crystals on obsidian and immediately attacking them
 * (the "pop" that deals explosion damage to nearby players).
 *
 * Mirrors the crystal/spawncrystal, crystal/break, and ledge/tick
 * behaviour from the original datapack.
 */
public class CrystalLogic {

    // ─── Cooldowns (managed by BotController) ──────────────────────────────

    /** Default ticks between crystal placements. */
    public static final int DEFAULT_CRYSTAL_CD = 4;

    // ─── Place ──────────────────────────────────────────────────────────────

    /**
     * Attempts to place one end crystal on the best obsidian block near the
     * target.  Returns true if a placement packet was sent.
     */
    public boolean tryPlace(MinecraftClient client, AbstractClientPlayerEntity target) {
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        int crystalSlot = InventoryUtil.findInHotbar(player, Items.END_CRYSTAL);
        if (crystalSlot == -1) return false;

        List<BlockPos> candidates = CombatUtil.getCrystalCandidates(client, target);
        if (candidates.isEmpty()) return false;

        // Reject positions where self-damage would be suicidal (> 70 raw units)
        BlockPos bestObs = null;
        for (BlockPos obs : candidates) {
            if (CombatUtil.estimateSelfDamage(client, obs) < 70.0) {
                bestObs = obs;
                break;
            }
        }
        if (bestObs == null) return false;

        // Look at the top face of the obsidian block
        Vec3d clickPoint = Vec3d.ofCenter(bestObs).add(0, 0.5, 0);
        float[] rots = RotationUtil.getRotationsTo(player.getEyePos(), clickPoint);
        player.setYaw(rots[0]);
        player.setPitch(rots[1]);

        // Switch to crystals
        InventoryUtil.switchToSlot(client, crystalSlot);

        // Place the crystal (right-click the top face of the obsidian)
        BlockHitResult hitResult = CombatUtil.topFaceHit(bestObs);
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        player.swingHand(Hand.MAIN_HAND);

        return true;
    }

    // ─── Break ──────────────────────────────────────────────────────────────

    /**
     * Attacks (breaks) the end crystal nearest to the target, if one exists
     * within the bot's attack reach.  Returns true if an attack packet was sent.
     *
     * This is called every tick regardless of crystal cooldown — breaking is
     * always free and should happen as fast as possible.
     */
    public boolean breakNearest(MinecraftClient client, AbstractClientPlayerEntity target) {
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        List<EndCrystalEntity> crystals = CombatUtil.getCrystalsNearTarget(client, target);
        if (crystals.isEmpty()) return false;

        EndCrystalEntity crystal = crystals.get(0);

        // Look at the crystal before attacking (server validates look direction)
        float[] rots = RotationUtil.getRotationsTo(player.getEyePos(), crystal.getPos());
        player.setYaw(rots[0]);
        player.setPitch(rots[1]);

        // Switch to axe/main weapon (slot 0 by convention, same as the datapack)
        // If slot 0 is empty that's fine — we still attack bare-handed
        player.getInventory().selectedSlot = 0;

        client.interactionManager.attackEntity(player, crystal);
        player.swingHand(Hand.MAIN_HAND);

        return true;
    }

    /**
     * Breaks ALL reachable crystals near the target in one tick.
     * Used during dtap where maximum aggression is needed.
     */
    public int breakAll(MinecraftClient client, AbstractClientPlayerEntity target) {
        ClientPlayerEntity player = client.player;
        if (player == null) return 0;

        List<EndCrystalEntity> crystals = CombatUtil.getCrystalsNearTarget(client, target);
        int broken = 0;
        for (EndCrystalEntity crystal : crystals) {
            float[] rots = RotationUtil.getRotationsTo(player.getEyePos(), crystal.getPos());
            player.setYaw(rots[0]);
            player.setPitch(rots[1]);
            client.interactionManager.attackEntity(player, crystal);
            player.swingHand(Hand.MAIN_HAND);
            broken++;
        }
        return broken;
    }
}
