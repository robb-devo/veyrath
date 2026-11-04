package dev.veyrath.mod.client.dragon;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A skinned triangle mesh in VEYRATH's {@code .vmesh} format (written by
 * tools/models/build_dragon.py): a bone tree with rest positions and orientations, vertices with
 * up to four bone weights, triangles. {@link #skin} deforms it on the CPU for a {@link Pose}.
 * Model space: blocks, +Y up, +Z forward.
 */
public final class SkinnedMesh {
    public final String[] names;
    public final int[] parent;
    final Matrix4f[] restWorld, restLocal, invRestWorld;
    public final Vector3f[] restHead;
    final float[] pos, nrm, uv, weights;
    final byte[] bones;
    public final int[] tris;
    public final int vertices;
    private final Map<String, Integer> index = new HashMap<>();

    /** Skinned output of the last {@link #skin} call. */
    public final float[] outPos, outNrm;
    /** Bone world matrices of the last {@link #skin} call (model space). */
    public final Matrix4f[] world;
    private final Matrix4f[] skinMats;

    private SkinnedMesh(int boneCount, int vertexCount, int triCount) {
        names = new String[boneCount];
        parent = new int[boneCount];
        restWorld = new Matrix4f[boneCount];
        restLocal = new Matrix4f[boneCount];
        invRestWorld = new Matrix4f[boneCount];
        restHead = new Vector3f[boneCount];
        world = new Matrix4f[boneCount];
        skinMats = new Matrix4f[boneCount];
        for (int i = 0; i < boneCount; i++) {
            world[i] = new Matrix4f();
            skinMats[i] = new Matrix4f();
        }
        vertices = vertexCount;
        pos = new float[vertexCount * 3];
        nrm = new float[vertexCount * 3];
        uv = new float[vertexCount * 2];
        weights = new float[vertexCount * 4];
        bones = new byte[vertexCount * 4];
        tris = new int[triCount * 3];
        outPos = new float[vertexCount * 3];
        outNrm = new float[vertexCount * 3];
    }

    public int bone(String name) {
        Integer i = index.get(name);
        if (i == null) throw new IllegalArgumentException("no bone " + name);
        return i;
    }

    public int boneCount() { return names.length; }

    public float u(int v) { return uv[v * 2]; }

    public float v(int v) { return uv[v * 2 + 1]; }

    public static SkinnedMesh read(InputStream in) throws IOException {
        byte[] all = in.readAllBytes();
        ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        b.get(magic);
        if (!"VMSH".equals(new String(magic, StandardCharsets.US_ASCII))) throw new IOException("not a vmesh");
        int version = b.getInt();
        if (version != 1) throw new IOException("vmesh version " + version);
        int nb = b.getInt();
        String[] names = new String[nb];
        int[] parents = new int[nb];
        Vector3f[] heads = new Vector3f[nb];
        Quaternionf[] rots = new Quaternionf[nb];
        for (int i = 0; i < nb; i++) {
            int len = Short.toUnsignedInt(b.getShort());
            byte[] nm = new byte[len];
            b.get(nm);
            names[i] = new String(nm, StandardCharsets.UTF_8);
            parents[i] = b.getInt();
            heads[i] = new Vector3f(b.getFloat(), b.getFloat(), b.getFloat());
            rots[i] = new Quaternionf(b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat()).normalize();
        }
        int nv = b.getInt();
        int vStart = b.position();
        int stride = 4 * 8 + 4 + 4 * 4;
        b.position(vStart + nv * stride);
        int nt = b.getInt();
        SkinnedMesh m = new SkinnedMesh(nb, nv, nt);
        b.position(vStart);
        for (int i = 0; i < nv; i++) {
            for (int k = 0; k < 3; k++) m.pos[i * 3 + k] = b.getFloat();
            for (int k = 0; k < 3; k++) m.nrm[i * 3 + k] = b.getFloat();
            m.uv[i * 2] = b.getFloat();
            m.uv[i * 2 + 1] = b.getFloat();
            for (int k = 0; k < 4; k++) m.bones[i * 4 + k] = b.get();
            for (int k = 0; k < 4; k++) m.weights[i * 4 + k] = b.getFloat();
        }
        b.getInt();
        for (int i = 0; i < nt * 3; i++) m.tris[i] = b.getInt();
        for (int i = 0; i < nb; i++) {
            m.names[i] = names[i];
            m.parent[i] = parents[i];
            m.restHead[i] = heads[i];
            m.index.put(names[i], i);
            m.restWorld[i] = new Matrix4f().translation(heads[i]).rotate(rots[i]);
            m.invRestWorld[i] = new Matrix4f(m.restWorld[i]).invert();
        }
        for (int i = 0; i < nb; i++) {
            int p = parents[i];
            m.restLocal[i] = p < 0 ? new Matrix4f(m.restWorld[i]) : new Matrix4f(m.invRestWorld[p]).mul(m.restWorld[i]);
        }
        return m;
    }

    public static SkinnedMesh read(DataInputStream in) throws IOException { return read((InputStream) in); }

    /** Solves the pose and deforms every vertex into {@link #outPos} and {@link #outNrm}. */
    public void skin(Pose pose) {
        int nb = names.length;
        Matrix4f tmp = new Matrix4f();
        for (int i = 0; i < nb; i++) {
            Matrix4f w = world[i];
            int p = parent[i];
            if (p < 0) w.identity().translate(pose.rootOffset).rotate(pose.rootRotation).mul(restLocal[i]);
            else w.set(world[p]).mul(restLocal[i]);
            if (pose.offset[i] != null) w.translate(pose.offset[i]);
            w.rotate(pose.local[i]);
            skinMats[i].set(w).mul(invRestWorld[i]);
        }
        Vector3f p = new Vector3f(), n = new Vector3f(), acc = new Vector3f(), accN = new Vector3f(), t = new Vector3f();
        for (int v = 0; v < vertices; v++) {
            p.set(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]);
            n.set(nrm[v * 3], nrm[v * 3 + 1], nrm[v * 3 + 2]);
            acc.zero();
            accN.zero();
            for (int k = 0; k < 4; k++) {
                float wgt = weights[v * 4 + k];
                if (wgt <= 0) continue;
                Matrix4f s = skinMats[Byte.toUnsignedInt(bones[v * 4 + k])];
                s.transformPosition(p, t);
                acc.fma(wgt, t);
                s.transformDirection(n, t);
                accN.fma(wgt, t);
            }
            outPos[v * 3] = acc.x;
            outPos[v * 3 + 1] = acc.y;
            outPos[v * 3 + 2] = acc.z;
            float len = accN.length();
            if (len < 1e-6f) len = 1;
            outNrm[v * 3] = accN.x / len;
            outNrm[v * 3 + 1] = accN.y / len;
            outNrm[v * 3 + 2] = accN.z / len;
        }
        tmp.identity();
    }

    /** World (model-space) position of a bone's head after the last {@link #skin}. */
    public Vector3f boneHead(int b, Vector3f out) {
        return world[b].getTranslation(out);
    }

    /** A pose: per bone a rotation in its own rest frame (and an optional offset), plus the root's placement. */
    public static final class Pose {
        public final Quaternionf[] local;
        public final Vector3f[] offset;
        public final Vector3f rootOffset = new Vector3f();
        public final Quaternionf rootRotation = new Quaternionf();

        public Pose(int bones) {
            local = new Quaternionf[bones];
            offset = new Vector3f[bones];
            for (int i = 0; i < bones; i++) local[i] = new Quaternionf();
        }

        public void reset() {
            for (Quaternionf q : local) q.identity();
            java.util.Arrays.fill(offset, null);
            rootOffset.zero();
            rootRotation.identity();
        }
    }
}
