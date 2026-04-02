package com.xanicle.bot.util;

import net.minecraft.util.math.Vec3d;

/**
 * Helper methods for converting 3-D positions into Minecraft yaw / pitch angles
 * and for smoothly interpolating between rotations.
 *
 * Minecraft yaw convention:
 *   0   = south (+Z)
 *   90  = west  (-X)
 *   180 = north (-Z)
 *  -90  = east  (+X)
 *
 * Minecraft pitch convention:
 *  -90 = straight up
 *    0 = horizontal
 *   90 = straight down
 */
public final class RotationUtil {

    private RotationUtil() {}

    /**
     * Returns [yaw, pitch] in degrees so that a player at {@code from}
     * looking at {@code to} appears to look directly at the target.
     */
    public static float[] getRotationsTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontalDist)));

        return new float[]{ yaw, pitch };
    }

    /**
     * Smoothly interpolates the current yaw toward the target yaw,
     * moving at most {@code maxStep} degrees per tick.
     * Handles the 180/-180 wrap correctly.
     */
    public static float smoothYaw(float current, float target, float maxStep) {
        float delta = wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) return target;
        return current + Math.signum(delta) * maxStep;
    }

    /**
     * Wraps an angle into the [-180, 180) range.
     */
    public static float wrapDegrees(float angle) {
        angle = angle % 360f;
        if (angle >= 180f)  angle -= 360f;
        if (angle < -180f)  angle += 360f;
        return angle;
    }

    /**
     * Returns the absolute angular difference between two yaw values (0-180).
     */
    public static float yawDifference(float a, float b) {
        return Math.abs(wrapDegrees(a - b));
    }
}
