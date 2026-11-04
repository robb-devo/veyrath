package dev.veyrath.combat;

import dev.veyrath.combat.content.Weapons;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.move.Move;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponTest {
    @Test
    void everyMoveOfEveryWeaponHasItsClip() {
        for (Weapons.Weapon w : Weapons.ALL) {
            assertTrue(Weapons.CLIPS.has(w.stanceClip()), w.id() + " stance clip");
            for (Move m : w.moves().all()) {
                if (m.kind == Move.Kind.REACTION || m.kind == Move.Kind.GETUP) continue;
                assertTrue(Weapons.CLIPS.has(m.clip), w.id() + ": no clip for " + m.id + " (" + m.clip + ")");
            }
        }
    }

    @Test
    void equipOnlyBetweenActions() {
        Fighter f = new Fighter(1, Weapons.UNARMED.moves(), Rules.player());
        Controls c = new Controls();
        assertTrue(f.request(Action.LIGHT, "jab", c));
        assertFalse(f.equip(Weapons.GREATSWORD.moves()), "no swap in the middle of a jab");
        for (int i = 0; i < 40 && f.phase() != Fighter.Phase.IDLE; i++) f.tickFollower(true, false);
        assertTrue(f.equip(Weapons.GREATSWORD.moves()));
        assertTrue(f.request(Action.LIGHT, "gs_slash_1", c), "the greatsword's light is its first cut");
        assertEquals("gs_slash_1", f.move().id);
    }

    @Test
    void greatswordReachIsMeasuredFromItsSwing() {
        for (String id : new String[]{"gs_slash_1", "gs_slash_2", "gs_slash_3", "gs_heavy"}) {
            double r = dev.veyrath.combat.ai.Reach.of(Weapons.GREATSWORD.moves().require(id));
            assertTrue(r > 1.2 && r < 5.5, id + " reach " + r);
        }
    }
}
