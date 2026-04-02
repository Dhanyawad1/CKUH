package com.xanicle.bot.util;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Combat-specific helper methods: candidate block finding,
 * crystal damage estimation, and BlockHitResult construction.
 */
public final class CombatUtil {

    /** Maximum reach for block interaction (blocks). */
    public static final double REACH = 4.5;

    /** Maximum reach for entity interaction (attacking crystals). */
    public static final double ATTACK_REACH = 4.5;

    /** End crystal explosion damage falloff radius. */
    private static final double EXPLOSION_RADIUS = 6.0;

    private CombatUtil() {}

    // ─────────────────────────────────────────────────────────────
    //  Block-placement helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds a {@link BlockHitResult} that represents right-clicking on the
     * top face of {@code target} — used to place a block on top of it or to
     * place an end crystal on obsidian.
     */
    public static BlockHitResult topFaceHit(BlockPos target) {
        Vec3d hitVec = Vec3d.ofCenter(target).add(0, 0.5, 0);
        return new BlockHitResult(hitVec, Direction.UP, target, false);
    }

    /**
     * Returns candidate positions where obsidian could be placed near the enemy.
     * A valid position is one that:
     *  - is at the enemy's foot level or one below
     *  - has a solid block below it (something to place on)
     *  - has air at position and above (so the enemy can't already be standing there)
     *  - is within REACH of the bot player
     */
    public static List<BlockPos> getObsidianCandidates(MinecraftClient client,
                                                        AbstractClientPlayerEntity target) {
        World world  = client.world;
        Vec3d botPos = client.player.getPos();
        BlockPos targetFeet = BlockPos.ofFloored(target.getPos());

        List<BlockPos> candidates = new ArrayList<>();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 0; y++) {
                    BlockPos pos = targetFeet.add(x, y, z);

                    // The block below must be solid (something to place on)
                    BlockState below = world.getBlockState(pos.down());
                    if (!below.isSolidBlock(world, pos.down())) continue;

                    // Placement position and above must be air
                    if (!world.getBlockState(pos).isAir()) continue;
                    if (!world.getBlockState(pos.up()).isAir()) continue;

                    // Must be within reach
                    Vec3d blockCenter = Vec3d.ofCenter(pos.down()); // we click the block below
                    if (botPos.distanceTo(blockCenter) > REACH) continue;

                    candidates.add(pos);
                }
            }
        }

        // Sort by distance to target — place closest to enemy first for max damage
        Vec3d targetPos = target.getPos();
        candidates.sort((a, b) -> Double.compare(
                Vec3d.ofCenter(a).distanceTo(targetPos),
                Vec3d.ofCenter(b).distanceTo(targetPos)
        ));

        return candidates;
    }

    /**
     * Returns candidate obsidian blocks near the enemy on which a crystal
     * can be placed (block above must be air, no crystal already there).
     */
    public static List<BlockPos> getCrystalCandidates(MinecraftClient client,
                                                       AbstractClientPlayerEntity target) {
        World world  = client.world;
        Vec3d botPos = client.player.getEyePos();
        BlockPos targetFeet = BlockPos.ofFloored(target.getPos());

        List<BlockPos> candidates = new ArrayList<>();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos obsPos = targetFeet.add(x, y, z);

                    // Must be obsidian or bedrock
                    BlockState bs = world.getBlockState(obsPos);
                    if (!bs.isOf(net.minecraft.block.Blocks.OBSIDIAN) &&
                        !bs.isOf(net.minecraft.block.Blocks.BEDROCK)) continue;

                    // Block on top must be air
                    if (!world.getBlockState(obsPos.up()).isAir()) continue;

                    // Two blocks above must also be air (crystal height)
                    if (!world.getBlockState(obsPos.up(2)).isAir()) continue;

                    // Within reach (we click the top face of the obsidian)
                    Vec3d clickPoint = Vec3d.ofCenter(obsPos).add(0, 0.5, 0);
                    if (botPos.distanceTo(clickPoint) > REACH) continue;

                    candidates.add(obsPos);
                }
            }
        }

        // Sort by estimated damage to target (highest first)
        candidates.sort((a, b) -> Double.compare(
                estimateCrystalDamage(b, target),   // descending
                estimateCrystalDamage(a, target)
        ));

        return candidates;
    }

    /**
     * Returns all {@link EndCrystalEntity}s within attack range of the bot
     * that are close to the target, sorted nearest-to-target first.
     */
    public static List<EndCrystalEntity> getCrystalsNearTarget(MinecraftClient client,
                                                                AbstractClientPlayerEntity target) {
        Vec3d botEye    = client.player.getEyePos();
        Vec3d targetPos = target.getPos();

        List<EndCrystalEntity> crystals = new ArrayList<>();
        for (var entity : client.world.getEntitiesByClass(
                EndCrystalEntity.class,
                client.player.getBoundingBox().expand(ATTACK_REACH + 2),
                e -> true)) {

            // Bot must be able to reach the crystal
            if (botEye.distanceTo(entity.getPos()) > ATTACK_REACH) continue;

            // Crystal must be reasonably close to target
            if (entity.getPos().distanceTo(targetPos) > 7.0) continue;

            crystals.add(entity);
        }

        // Sort by distance to target — closest = most dangerous = break first
        crystals.sort((a, b) -> Double.compare(
                a.getPos().distanceTo(targetPos),
                b.getPos().distanceTo(targetPos)
        ));
        return crystals;
    }

    // ─────────────────────────────────────────────────────────────
    //  Damage estimation
    // ─────────────────────────────────────────────────────────────

    /**
     * Rough estimate of how much damage a crystal placed on top of
     * {@code obsidianPos} would deal to {@code target}.
     * Used only for sorting — not for precise game-play decisions.
     */
    public static double estimateCrystalDamage(BlockPos obsidianPos,
                                                AbstractClientPlayerEntity target) {
        // Crystal spawns one block above the obsidian
        Vec3d crystalPos = Vec3d.ofCenter(obsidianPos.up());
        double dist = crystalPos.distanceTo(target.getPos());
        if (dist > EXPLOSION_RADIUS) return 0;

        // Simple inverse-square approximation (no armour / blast protection)
        return (1.0 - dist / EXPLOSION_RADIUS) * 100.0;
    }

    /**
     * Rough estimate of how much self-damage a crystal at the given position
     * would deal to the bot player.
     */
    public static double estimateSelfDamage(MinecraftClient client, BlockPos obsidianPos) {
        Vec3d crystalPos = Vec3d.ofCenter(obsidianPos.up());
        double dist = crystalPos.distanceTo(client.player.getPos());
        if (dist > EXPLOSION_RADIUS) return 0;
        return (1.0 - dist / EXPLOSION_RADIUS) * 100.0;
    }
}
