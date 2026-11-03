package dev.veyrath.combat.input;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Predicate;

/**
 * Remembers recent presses for a few ticks, so a press slightly before an action becomes
 * possible still counts (input buffering). The newest press that can run wins; running it
 * discards everything pressed before it.
 */
public final class InputBuffer {
    public record Press(Action action, long tick) {}

    public static final int DEFAULT_WINDOW = 6;

    private final ArrayDeque<Press> presses = new ArrayDeque<>();
    private final int window;

    public InputBuffer() { this(DEFAULT_WINDOW); }

    public InputBuffer(int window) { this.window = window; }

    public int window() { return window; }

    public void press(Action a, long tick) {
        presses.addLast(new Press(a, tick));
        while (presses.size() > 16) presses.removeFirst();
    }

    /** Drops presses older than the buffer window. */
    public void expire(long now) {
        while (!presses.isEmpty() && now - presses.peekFirst().tick() > window) presses.removeFirst();
    }

    /** The newest press matching the test, or null. */
    public Press newest(Predicate<Press> test) {
        Iterator<Press> it = presses.descendingIterator();
        while (it.hasNext()) {
            Press p = it.next();
            if (test.test(p)) return p;
        }
        return null;
    }

    /** Removes this press and every older one. */
    public void consume(Press p) {
        while (!presses.isEmpty()) {
            Press first = presses.removeFirst();
            if (first == p) return;
        }
    }

    /** Removes one press without touching others (for releases that are only bookkeeping). */
    public void remove(Press p) { presses.remove(p); }

    public boolean isEmpty() { return presses.isEmpty(); }

    public void clear() { presses.clear(); }

    public int size() { return presses.size(); }
}
