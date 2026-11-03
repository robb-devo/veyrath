package dev.veyrath.combat.move;

import dev.veyrath.combat.input.Action;

/**
 * A follow-up: pressing {@code input} during move ticks [from, until] starts move {@code to}.
 * With {@code onHit}, only after this move connected (a hit, block or armoured hit).
 */
public record Branch(Action input, String to, int from, int until, boolean onHit) {}
