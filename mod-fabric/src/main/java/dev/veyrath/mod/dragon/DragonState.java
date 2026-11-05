package dev.veyrath.mod.dragon;

/** Vaelith's movement states; the client's animator picks its clip from this (synced). */
public enum DragonState {
    IDLE, WALK, RUN, CROUCH, ROAR, TAKEOFF, FLAP, GLIDE, DIVE, HOVER, LAND, REST;

    public boolean flying() {
        return this == TAKEOFF || this == FLAP || this == GLIDE || this == DIVE || this == HOVER || this == LAND;
    }

    public static DragonState of(int i) {
        DragonState[] v = values();
        return i >= 0 && i < v.length ? v[i] : IDLE;
    }
}
