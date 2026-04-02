package com.xanicle.bot;

import com.xanicle.bot.combat.*;
import com.xanicle.bot.util.InventoryUtil;
import com.xanicle.bot.util.RotationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Central coordinator for all bot logic.
 *
 * Every client tick (20/s) the following priority order is evaluated:
 *
 *   1. Safety checks  — stop if target gone, player dead, etc.
 *   2. Totem check    — re-equip immediately if offhand is empty.
 *   3. Gap check      — eat a golden apple if HP or saturation is critical.
 *   4. Crystal break  — attack any crystal that already exists near target.
 *   5. Anchor logic   — run anchor sequence if target is in anchor range.
 *   6. Obsidian place — place obsidian if no platform exists yet.
 *   7. Crystal place  — place a crystal on obsidian.
 *   8. Pearl          — throw a pearl to close distance if target is far.
 *   9. Movement       — strafe, chase, or circle based on distance.
 *
 * Steps 3-9 respect individual cooldown timers so the bot doesn't spam
 * packets faster than the server can process them.
 */
public class BotController {

    // ─── Combat sub-systems ────────────────────────────────────────────────
    private final CrystalLogic  crystalLogic  = new CrystalLogic();
    private final ObsidianLogic obsidianLogic = new ObsidianLogic();
    private final AnchorLogic   anchorLogic   = new AnchorLogic();
    private final PearlLogic    pearlLogic    = new PearlLogic();
    private final TotemLogic    totemLogic    = new TotemLogic();
    private final GapLogic      gapLogic      = new GapLogic();

    // ─── State ────────────────────────────────────────────────────────────
    private BotState state      = BotState.IDLE;
    private String   targetName = null;

    // ─── Cooldown timers (ticks) ──────────────────────────────────────────
    private int crystalTimer = 0;   // ticks until next crystal place
    private int obsidianTimer= 0;   // ticks until next obsidian place
    private int anchorTimer  = 0;   // ticks until next anchor sequence

    // Configurable cooldowns — can be adjusted to match server speed
    public int crystalCd  = CrystalLogic.DEFAULT_CRYSTAL_CD;
    public int obsidianCd = 15;
    public int anchorCd   = AnchorLogic.DEFAULT_ANCHOR_CD;

    // ─── Movement output (read by the mixin each tick) ────────────────────
    private final BotMovement desiredMovement = new BotMovement();

    /** Data class holding the desired input state for the current tick. */
    public static class BotMovement {
        public float   forward   = 0f;
        public float   sideways  = 0f;
        public boolean jumping   = false;
        public boolean sneaking  = false;

        void reset() {
            forward = sideways = 0f;
            jumping = sneaking = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────

    public void start(String playerName) {
        this.targetName = playerName;
        this.state      = BotState.FIGHTING;
        resetTimers();
        XanicleBotMod.LOGGER.info("[XanicleBot] Started — targeting {}", playerName);
    }

    public void stop() {
        state = BotState.IDLE;
        targetName = null;
        desiredMovement.reset();
        gapLogic.reset();
        pearlLogic.reset();
        anchorLogic.reset();
        totemLogic.reset();
        XanicleBotMod.LOGGER.info("[XanicleBot] Stopped.");
    }

    public boolean isActive()        { return state != BotState.IDLE; }
    public String  getTargetName()   { return targetName; }
    public BotMovement getDesiredMovement() { return desiredMovement; }

    // ─────────────────────────────────────────────────────────────────────
    //  Main tick — called every client tick by XanicleBotMod
    // ─────────────────────────────────────────────────────────────────────

    public void tick(MinecraftClient client) {
        desiredMovement.reset();
        if (state == BotState.IDLE) return;

        ClientPlayerEntity me = client.player;
        if (me == null) { stop(); return; }

        // ── 1. Find the target ────────────────────────────────────────────
        AbstractClientPlayerEntity target = findTarget(client);
        if (target == null) {
            // Target left the world — stop silently
            me.sendMessage(Text.literal(
                "§7[XanicleBot] §eTarget §6" + targetName + " §eleft. Stopping."), false);
            stop();
            return;
        }

        // ── 2. Totem check — runs every tick, highest priority ────────────
        totemLogic.tick(client);

        // ── 3. Gap (golden apple) — suspends other logic while eating ─────
        if (gapLogic.tick(client)) {
            // While eating, stay still and face the target
            faceTarget(me, target);
            return;
        }

        // Decrement timers
        if (crystalTimer  > 0) crystalTimer--;
        if (obsidianTimer > 0) obsidianTimer--;
        if (anchorTimer   > 0) anchorTimer--;

        double dist = me.getPos().distanceTo(target.getPos());

        // ── 4. Crystal break — always try, no cooldown cost ───────────────
        crystalLogic.breakNearest(client, target);

        // ── 5. Anchor PvP — only when target is in medium range ──────────
        if (dist >= 1.75 && dist <= 5.5 && anchorTimer == 0) {
            if (InventoryUtil.hasInHotbar(me, Items.RESPAWN_ANCHOR) &&
                InventoryUtil.hasInHotbar(me, Items.GLOWSTONE)) {
                anchorLogic.tick(client, target);
                anchorTimer = anchorCd;
            }
        } else if (dist < 1.75 || dist > 5.5) {
            // Out of anchor range — reset the anchor state machine
            anchorLogic.reset();
        }

        // ── 6. Obsidian placement ─────────────────────────────────────────
        if (obsidianTimer == 0 && dist <= 5.0) {
            if (!obsidianLogic.hasObsidianNearTarget(client, target)) {
                if (obsidianLogic.tryPlace(client, target)) {
                    obsidianTimer = obsidianCd;
                }
            }
        }

        // ── 7. Crystal placement ──────────────────────────────────────────
        if (crystalTimer == 0 && dist <= 5.0) {
            if (crystalLogic.tryPlace(client, target)) {
                crystalTimer = crystalCd;
            }
        }

        // ── 8. Pearl to close distance ────────────────────────────────────
        if (dist > 6.0 && pearlLogic.getCooldown() == 0) {
            if (InventoryUtil.hasInHotbar(me, Items.ENDER_PEARL)) {
                pearlLogic.tick(client, target);
            }
        }

        // ── 9. Movement ───────────────────────────────────────────────────
        computeMovement(me, target, dist);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Movement computation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Determines the desired movement vector for this tick based on distance
     * to the target.  Mirrors the botlogic.mcfunction movement section:
     *
     *   dist <= 3  → strafe / circle (stay in melee range for dtap)
     *   dist 3-6   → move toward target
     *   dist > 6   → sprint toward target (pearl was already thrown above)
     */
    private void computeMovement(ClientPlayerEntity me,
                                 AbstractClientPlayerEntity target,
                                 double dist) {
        Vec3d myPos     = me.getPos();
        Vec3d targetPos = target.getPos();

        // Always face the target
        faceTarget(me, target);

        if (dist <= 3.0) {
            // Close range: strafe sideways to keep pressure and avoid getting sandwiched
            desiredMovement.sideways = 1.0f;  // strafe right
            desiredMovement.forward  = 0.3f;  // slight forward lean
        } else if (dist <= 6.0) {
            // Medium range: move straight toward target
            desiredMovement.forward = 1.0f;
        } else {
            // Far: sprint toward target (pearl was thrown, we need to close quickly)
            desiredMovement.forward = 1.0f;
            me.setSprinting(true);
        }
    }

    /** Smoothly snaps yaw / pitch to face the target's eye position. */
    private void faceTarget(ClientPlayerEntity me, AbstractClientPlayerEntity target) {
        Vec3d eyeFrom = me.getEyePos();
        Vec3d eyeTo   = target.getEyePos();

        float[] rots = RotationUtil.getRotationsTo(eyeFrom, eyeTo);
        // Smooth yaw up to 30 degrees/tick; snap pitch instantly
        float newYaw = RotationUtil.smoothYaw(me.getYaw(), rots[0], 30f);
        me.setYaw(newYaw);
        me.setPitch(rots[1]);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────

    private AbstractClientPlayerEntity findTarget(MinecraftClient client) {
        if (client.world == null || targetName == null) return null;
        for (var player : client.world.getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(targetName)
                    && player != client.player) {
                return player;
            }
        }
        return null;
    }

    private void resetTimers() {
        crystalTimer  = 0;
        obsidianTimer = 0;
        anchorTimer   = 0;
    }
}
