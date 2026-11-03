package dev.veyrath.combat.rig;

import dev.veyrath.combat.math.Quat;
import dev.veyrath.combat.math.Vec3;

/**
 * Forward kinematics for the humanoid skeleton, matching Minecraft's entity rendering exactly:
 * model parts use {@code translate(x/16, y/16, z/16)} then {@code rotationZYX}, and
 * {@code LivingEntityRenderer} applies scale, a yaw turn of (180 - bodyRot), {@code scale(-1, -1, 1)}
 * and {@code translate(0, -1.501, 0)}.
 *
 * <p>Character space (used for hit shapes and gameplay) is in blocks with the origin at the feet:
 * +X is the character's right, +Y is up, +Z is forward.
 */
public final class Rig {
    private Rig() {}

    public static final double MODEL_Y_OFFSET = 1.501;

    /** Vanilla part transforms of a solved pose, in model space (pixels), plus each bone's frame. */
    public static final class Solved {
        /** Per bone: origin (pivot) and rotation in model space, after the root transform. */
        final Vec3[] origin = new Vec3[Bone.COUNT];
        final Quat[] rotation = new Quat[Bone.COUNT];
        /** Per bone, the vanilla part values relative to the model root (root itself: its own transform). */
        final Vec3[] partPos = new Vec3[Bone.COUNT];
        final Quat[] partRot = new Quat[Bone.COUNT];

        public Vec3 origin(Bone b) { return origin[b.ordinal()]; }
        public Quat rotation(Bone b) { return rotation[b.ordinal()]; }

        /**
         * Value for the vanilla {@code ModelPart} of this bone: position (pixels) and rotation. For
         * {@link Bone#ROOT} it is the model root part; for the others it is relative to the root, as
         * vanilla's flat part list expects.
         */
        public Vec3 partPos(Bone b) { return partPos[b.ordinal()]; }
        public Quat partRot(Bone b) { return partRot[b.ordinal()]; }

        /** A point given in a bone's local pixels, in model space (pixels). */
        public Vec3 toModel(Bone b, Vec3 local) {
            int i = b.ordinal();
            return origin[i].add(rotation[i].rotate(local));
        }

        /** A point given in a bone's local pixels, in character space (blocks, scale 1). */
        public Vec3 toCharacter(Bone b, Vec3 local) { return modelToCharacter(toModel(b, local)); }
    }

    /** Model-space pixels (after the root transform) to character-space blocks. */
    public static Vec3 modelToCharacter(Vec3 m) {
        return new Vec3(-m.x() / 16.0, MODEL_Y_OFFSET - m.y() / 16.0, -m.z() / 16.0);
    }

    /** Character-space blocks to world offset from the entity position, for a body yaw (degrees) and scale. */
    public static Vec3 characterToWorld(Vec3 c, double bodyYawDeg, double scale) {
        double r = Math.toRadians(bodyYawDeg), cos = Math.cos(r), sin = Math.sin(r);
        return new Vec3((-c.x() * cos - c.z() * sin) * scale, c.y() * scale, (-c.x() * sin + c.z() * cos) * scale);
    }

    /** World offset to character space (inverse of {@link #characterToWorld}). */
    public static Vec3 worldToCharacter(Vec3 w, double bodyYawDeg, double scale) {
        double r = Math.toRadians(bodyYawDeg), cos = Math.cos(r), sin = Math.sin(r);
        double x = w.x() / scale, z = w.z() / scale;
        return new Vec3(-x * cos - z * sin, w.y() / scale, -x * sin + z * cos);
    }

    /** Lowest point of the body's part boxes, in character space (blocks above the feet). */
    public static double lowestPoint(Solved s) {
        double low = Double.MAX_VALUE;
        for (Bone b : Bone.ALL) {
            if (b == Bone.ROOT) continue;
            double[] bx = b.box();
            for (int i = 0; i < 8; i++) {
                Vec3 local = new Vec3((i & 1) == 0 ? bx[0] : bx[3], (i & 2) == 0 ? bx[1] : bx[4], (i & 4) == 0 ? bx[2] : bx[5]);
                low = Math.min(low, s.toCharacter(b, local).y());
            }
        }
        return low;
    }

    public static Solved solve(Pose pose) {
        Solved s = new Solved();
        solve(pose, s);
        return s;
    }

    public static void solve(Pose pose, Solved s) {
        int root = Bone.ROOT.ordinal();
        // Root: rotate about the hips, then offset: p -> C + T + R (p - C).
        Quat rr = pose.rot[root];
        Vec3 c = Bone.ROOT.restPivot;
        Vec3 rootPos = c.add(pose.pos[root]).sub(rr.rotate(c));
        s.partPos[root] = rootPos;
        s.partRot[root] = rr;
        s.origin[root] = rootPos;
        s.rotation[root] = rr;
        // Bones in root-local space first (vanilla's flat parts), then through the root transform.
        Vec3[] localOrigin = new Vec3[Bone.COUNT];
        Quat[] localRot = new Quat[Bone.COUNT];
        for (Bone b : Bone.ALL) {
            if (b == Bone.ROOT) continue;
            int i = b.ordinal();
            Vec3 pivot = b.restPivot.add(pose.pos[i]);
            if (b.parent == Bone.ROOT) {
                localOrigin[i] = pivot;
                localRot[i] = pose.rot[i];
            } else {
                int p = b.parent.ordinal();
                localOrigin[i] = localOrigin[p].add(localRot[p].rotate(pivot));
                localRot[i] = localRot[p].mul(pose.rot[i]);
            }
        }
        for (Bone b : Bone.ALL) {
            if (b == Bone.ROOT) continue;
            int i = b.ordinal();
            // Vanilla's body part pivots at the neck, not the waist: shift so the box lands the same.
            if (b == Bone.BODY) {
                Vec3 neckInBody = new Vec3(0, -12, 0);
                s.partPos[i] = localOrigin[i].add(localRot[i].rotate(neckInBody));
            } else {
                s.partPos[i] = localOrigin[i];
            }
            s.partRot[i] = localRot[i];
            s.origin[i] = rootPos.add(rr.rotate(localOrigin[i]));
            s.rotation[i] = rr.mul(localRot[i]);
        }
    }

    /** Box of a bone in its vanilla part's local space: the body box is stored relative to the neck. */
    public static double[] vanillaBox(Bone b) {
        double[] box = b.box();
        if (b == Bone.BODY) return new double[]{box[0], box[1] + 12, box[2], box[3], box[4] + 12, box[5]};
        return box;
    }

    /**
     * Inverse of {@link #solve}: turns vanilla part values (relative to the model root; body
     * pivoting at the neck) back into a local pose of this skeleton. Used to layer VEYRATH clips
     * over vanilla's own animation (walk cycle, head look) in one hierarchy.
     *
     * @param partPos part positions in pixels, indexed by {@link Bone#ordinal()} (ROOT = the root part)
     * @param partRot part rotations, same indexing
     */
    public static Pose fromVanilla(Vec3[] partPos, Quat[] partRot, Pose out) {
        int root = Bone.ROOT.ordinal(), body = Bone.BODY.ordinal();
        Quat rr = partRot[root];
        Vec3 c = Bone.ROOT.restPivot;
        // partPos(root) = C + T - R C  =>  T = partPos + R C - C
        out.set(Bone.ROOT, rr, partPos[root].add(rr.rotate(c)).sub(c));
        Quat rb = partRot[body];
        Vec3 waist = partPos[body].add(rb.rotate(new Vec3(0, 12, 0)));
        out.set(Bone.BODY, rb, waist.sub(Bone.BODY.restPivot));
        Quat rbInv = rb.conjugate();
        for (Bone b : Bone.ALL) {
            if (b == Bone.ROOT || b == Bone.BODY) continue;
            int i = b.ordinal();
            if (b.parent == Bone.ROOT) {
                out.set(b, partRot[i], partPos[i].sub(b.restPivot));
            } else {
                Quat local = rbInv.mul(partRot[i]);
                Vec3 pivot = rbInv.rotate(partPos[i].sub(waist));
                out.set(b, local, pivot.sub(b.restPivot));
            }
        }
        return out;
    }
}
