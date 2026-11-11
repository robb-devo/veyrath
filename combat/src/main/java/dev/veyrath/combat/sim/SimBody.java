package dev.veyrath.combat.sim;

import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.hit.Box;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.input.InputBuffer;
import dev.veyrath.combat.math.Vec3;

/** A fighter with a body in the headless sim. */
public final class SimBody {
    public final Fighter fighter;
    public final InputBuffer input = new InputBuffer();
    public final Controls controls = new Controls();
    public Vec3 pos;
    public Vec3 vel = Vec3.ZERO;
    public double width = 0.6, height = 1.8;
    public double health, maxHealth;
    public boolean grounded = true;
    /** Mobs and dummies: no inputs, reactions only. */
    public boolean passive;
    /** Training dummy: health refills, never moves on its own. */
    public boolean immortal;
    public Vec3 lockTarget;
    /** Velocity of the locked target as this body's side knows it (blocks per tick), or null. */
    public Vec3 lockVelocity;
    /** AI that fills this body's controls and presses each tick (null: scripted or passive). */
    public Mind mind;
    /** Multiplier on the damage this body's hits deal (a sparring Duelist hits softer). */
    public double damageScale = 1;

    /** A mind that drives a body. */
    public interface Mind {
        /** Called at the start of each tick, before the fighters tick. */
        void think(SimWorld w, SimBody self);
        /** A hit this body dealt or took. */
        default void hit(SimWorld.Hit h, SimBody self) {}
    }

    public SimBody(Fighter f, Vec3 pos, double health) {
        this.fighter = f;
        this.pos = pos;
        this.health = health;
        this.maxHealth = health;
    }

    public Box box() { return Box.ofEntity(pos, width, height); }

    public void press(dev.veyrath.combat.input.Action a) { input.press(a, fighter.clock() + 1); }
}
