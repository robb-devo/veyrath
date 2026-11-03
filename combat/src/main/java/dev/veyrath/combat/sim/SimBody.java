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

    public SimBody(Fighter f, Vec3 pos, double health) {
        this.fighter = f;
        this.pos = pos;
        this.health = health;
        this.maxHealth = health;
    }

    public Box box() { return Box.ofEntity(pos, width, height); }

    public void press(dev.veyrath.combat.input.Action a) { input.press(a, fighter.clock() + 1); }
}
