package dev.veyrath.mod.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Strikes do not pass through terrain: a target counts only if a straight line from the
 * attacker's chest reaches its centre, its head or its legs without crossing a block's collision
 * shape. Checked before hit detection on the server (which decides) and in the client's
 * prediction (so no impact is shown for a blow the server will not count).
 */
public final class LineOfSight {
    private LineOfSight() {}

    public static boolean clear(Level level, LivingEntity attacker, LivingEntity target) {
        Vec3 from = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
        AABB bb = target.getBoundingBox();
        Vec3 c = bb.getCenter();
        double h = bb.getYsize();
        return open(level, attacker, from, c)
                || open(level, attacker, from, new Vec3(c.x, bb.minY + h * 0.85, c.z))
                || open(level, attacker, from, new Vec3(c.x, bb.minY + h * 0.2, c.z));
    }

    private static boolean open(Level level, Entity attacker, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker)).getType()
                == HitResult.Type.MISS;
    }
}
