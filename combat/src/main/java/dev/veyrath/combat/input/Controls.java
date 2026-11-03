package dev.veyrath.combat.input;

/**
 * Held input state for one tick, plus the movement intent in world space. The client fills it from
 * keys and the camera; an NPC's AI fills it directly.
 */
public final class Controls {
    public boolean blockHeld, heavyHeld, sprintHeld, jumpHeld;
    /** Desired horizontal movement direction in world space (unit length, or zero for none). */
    public double moveX, moveZ;
    /** Camera (aim) yaw in degrees, Minecraft convention. */
    public double aimYaw;

    public boolean hasMove() { return moveX * moveX + moveZ * moveZ > 1e-4; }

    public Controls copy() {
        Controls c = new Controls();
        c.blockHeld = blockHeld; c.heavyHeld = heavyHeld; c.sprintHeld = sprintHeld; c.jumpHeld = jumpHeld;
        c.moveX = moveX; c.moveZ = moveZ; c.aimYaw = aimYaw;
        return c;
    }
}
