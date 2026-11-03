package dev.veyrath.combat.hit;

import dev.veyrath.combat.anim.Clip;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Clips by id. The server uses it to place hit shapes; the client to animate. */
public final class ClipLibrary {
    private final Map<String, Clip> clips = new LinkedHashMap<>();

    public ClipLibrary add(Clip c) {
        if (clips.put(c.id, c) != null) throw new IllegalArgumentException("duplicate clip " + c.id);
        return this;
    }

    public Clip get(String id) { return id == null ? null : clips.get(id); }

    public boolean has(String id) { return clips.containsKey(id); }

    public Collection<Clip> all() { return clips.values(); }
}
