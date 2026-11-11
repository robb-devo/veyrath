package dev.veyrath.combat.hit;

import dev.veyrath.combat.anim.Clip;
import dev.veyrath.combat.move.Move;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Clips by id. The server uses it to place hit shapes; the client to animate. It also keeps the
 * hit capsules worked out from its clips per move ({@link HitDetector}), since they depend only on
 * the clip and the tick. Clips are not changed after the library is built: the client and the
 * integrated server threads read the same library.
 */
public final class ClipLibrary {
    private final Map<String, Clip> clips = new LinkedHashMap<>();
    /** Per move (weakly held, so a move set that is dropped takes its capsules with it): window, tick. */
    private final Map<Move, HitDetector.Swept[][]> swept = Collections.synchronizedMap(new WeakHashMap<>());

    public ClipLibrary add(Clip c) {
        if (clips.put(c.id, c) != null) throw new IllegalArgumentException("duplicate clip " + c.id);
        swept.clear();
        return this;
    }

    public Clip get(String id) { return id == null ? null : clips.get(id); }

    public boolean has(String id) { return clips.containsKey(id); }

    public Collection<Clip> all() { return clips.values(); }

    HitDetector.Swept[][] swept(Move m, Function<Move, HitDetector.Swept[][]> make) {
        return swept.computeIfAbsent(m, make);
    }
}
