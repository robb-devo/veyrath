package dev.veyrath.combat.rig;

import dev.veyrath.combat.math.Vec3;

/**
 * The humanoid skeleton. It maps one to one onto the parts of Minecraft's {@code HumanoidModel}
 * (head, body, arms, legs, plus the model root), but with a real hierarchy: the head and arms hang
 * from the torso, and the torso pivots at the waist, so bending the torso carries the upper body.
 *
 * <p>Coordinates are Minecraft model space: pixels (1/16 block), Y points down, the face looks
 * toward -Z and the character's right side is -X. {@link #restPivot} is relative to the parent
 * bone's pivot; children of {@link #ROOT} are relative to the model origin (neck height).
 */
public enum Bone {
    /** Whole-model transform, rotating about the hips. */
    ROOT(null, new Vec3(0, 12, 0)),
    /** Torso, pivoting at the waist. */
    BODY(ROOT, new Vec3(0, 12, 0)),
    HEAD(BODY, new Vec3(0, -12, 0)),
    RIGHT_ARM(BODY, new Vec3(-5, -10, 0)),
    LEFT_ARM(BODY, new Vec3(5, -10, 0)),
    RIGHT_LEG(ROOT, new Vec3(-1.9, 12, 0)),
    LEFT_LEG(ROOT, new Vec3(1.9, 12, 0));

    public static final Bone[] ALL = values();
    public static final int COUNT = ALL.length;

    public final Bone parent;
    public final Vec3 restPivot;

    Bone(Bone parent, Vec3 restPivot) {
        this.parent = parent;
        this.restPivot = restPivot;
    }

    /** The bone on the other side (left for right), or the bone itself on the centre line. */
    public Bone mirror() {
        return switch (this) {
            case RIGHT_ARM -> LEFT_ARM;
            case LEFT_ARM -> RIGHT_ARM;
            case RIGHT_LEG -> LEFT_LEG;
            case LEFT_LEG -> RIGHT_LEG;
            default -> this;
        };
    }

    /** Box of the vanilla part in this bone's local space (pixels): {minX, minY, minZ, maxX, maxY, maxZ}. */
    public double[] box() {
        return switch (this) {
            case ROOT -> new double[]{0, 0, 0, 0, 0, 0};
            case BODY -> new double[]{-4, -12, -2, 4, 0, 2};
            case HEAD -> new double[]{-4, -8, -4, 4, 0, 4};
            case RIGHT_ARM -> new double[]{-3, -2, -2, 1, 10, 2};
            case LEFT_ARM -> new double[]{-1, -2, -2, 3, 10, 2};
            case RIGHT_LEG, LEFT_LEG -> new double[]{-2, 0, -2, 2, 12, 2};
        };
    }

    /** Points that strikes come from, in bone-local pixels. */
    public static final Vec3 RIGHT_FIST = new Vec3(-1, 10, 0);
    public static final Vec3 LEFT_FIST = new Vec3(1, 10, 0);
    public static final Vec3 RIGHT_ELBOW = new Vec3(-1, 4, 0);
    public static final Vec3 LEFT_ELBOW = new Vec3(1, 4, 0);
    public static final Vec3 FOOT = new Vec3(0, 12, 0);
    public static final Vec3 KNEE = new Vec3(0, 6, 0);
}
