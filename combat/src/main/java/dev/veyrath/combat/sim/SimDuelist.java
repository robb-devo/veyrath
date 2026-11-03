package dev.veyrath.combat.sim;

import dev.veyrath.combat.ai.DuelistBrain;

/** Runs a {@link DuelistBrain} on a sim body against one opponent. */
public final class SimDuelist implements SimBody.Mind {
    public final DuelistBrain brain;
    private final SimBody foe;

    public SimDuelist(DuelistBrain brain, SimBody foe) {
        this.brain = brain;
        this.foe = foe;
    }

    @Override
    public void think(SimWorld w, SimBody self) {
        brain.think(self.fighter, self.pos, foe.fighter, foe.pos, self.controls, self.input);
        self.lockTarget = brain.aim();
    }

    @Override
    public void hit(SimWorld.Hit h, SimBody self) {
        if (h.attacker() == self) brain.connected(h.outcome());
        if (h.defender() == self) brain.struck(h.outcome());
    }
}
