package dev.veyrath.combat.move;

/**
 * What a hit does. Damage is in Minecraft health points (a heart is 2). Times are ticks.
 *
 * @param push     horizontal speed given to the target along the attack direction (blocks/tick)
 * @param lift     vertical speed given to the target (blocks/tick); launchers use about 0.6-0.9
 * @param hitstop  freeze of attacker and target on impact (ticks)
 * @param shake    camera shake trauma added on impact (0..1)
 * @param guard    stamina a blocking target loses
 * @param gauge    Unbound gauge the attacker gains
 * @param radial   push away from the attacker instead of along its facing (spins and sweeps)
 */
public record HitSpec(double damage, double poise, int hitstun, int blockstun, double push, double lift,
                      Reaction reaction, int hitstop, double shake, double guard, double gauge,
                      boolean unblockable, boolean unparryable, boolean radial, String vfx, String sfx) {

    public static Builder of(double damage) { return new Builder(damage); }

    public static final class Builder {
        private final double damage;
        private double poise = 10, push = 0.15, lift = 0, shake = 0.15, guard = 10, gauge = 2;
        private int hitstun = 8, blockstun = 6, hitstop = 2;
        private Reaction reaction = Reaction.FLINCH;
        private boolean unblockable, unparryable, radial;
        private String vfx = "impact_light", sfx = "hit_light";

        Builder(double damage) { this.damage = damage; }

        public Builder poise(double v) { poise = v; return this; }
        public Builder stun(int hit, int block) { hitstun = hit; blockstun = block; return this; }
        public Builder push(double v) { push = v; return this; }
        public Builder lift(double v) { lift = v; return this; }
        public Builder reaction(Reaction r) { reaction = r; return this; }
        public Builder hitstop(int v) { hitstop = v; return this; }
        public Builder shake(double v) { shake = v; return this; }
        public Builder guard(double v) { guard = v; return this; }
        public Builder gauge(double v) { gauge = v; return this; }
        public Builder unblockable() { unblockable = true; return this; }
        public Builder unparryable() { unparryable = true; return this; }
        public Builder radial() { radial = true; return this; }
        public Builder fx(String vfx, String sfx) { this.vfx = vfx; this.sfx = sfx; return this; }

        public HitSpec build() {
            return new HitSpec(damage, poise, hitstun, blockstun, push, lift, reaction, hitstop, shake, guard, gauge,
                    unblockable, unparryable, radial, vfx, sfx);
        }
    }
}
