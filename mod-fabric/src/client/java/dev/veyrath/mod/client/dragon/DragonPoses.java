package dev.veyrath.mod.client.dragon;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.veyrath.mod.VeyrathMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Vaelith's key poses from {@code assets/veyrath/dragon/poses.json}, resolved exactly like
 * tools/models/pose_preview.py does (bases, additive offsets, left-to-right mirroring), into flat
 * arrays: per bone pitch, yaw, roll in radians, plus the root's offset and tilt.
 */
public final class DragonPoses {
    private DragonPoses() {}

    static final Identifier FILE = VeyrathMod.id("dragon/poses.json");

    /** A resolved pose. {@code bones[i*3..i*3+2]}: pitch, yaw, roll (radians). */
    public static final class P {
        final float[] bones;
        final float[] root = new float[3];
        float rootPitch, rootRoll;

        P(int n) { bones = new float[n * 3]; }

        P copy() {
            P c = new P(bones.length / 3);
            System.arraycopy(bones, 0, c.bones, 0, bones.length);
            System.arraycopy(root, 0, c.root, 0, 3);
            c.rootPitch = rootPitch;
            c.rootRoll = rootRoll;
            return c;
        }
    }

    private static Map<String, P> poses;

    static Map<String, P> get(SkinnedMesh m) {
        if (poses == null) poses = load(m);
        return poses;
    }

    public static void invalidate() { poses = null; }

    private static Map<String, P> load(SkinnedMesh m) {
        Map<String, P> out = new HashMap<>();
        try (var in = new InputStreamReader(Minecraft.getInstance().getResourceManager().open(FILE), StandardCharsets.UTF_8)) {
            JsonObject all = JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("poses");
            for (String name : all.keySet()) resolve(all, name, m, out);
        } catch (Exception e) {
            VeyrathMod.LOG.error("Could not read the dragon poses", e);
        }
        out.putIfAbsent("rest", new P(m.boneCount()));
        return out;
    }

    private static P resolve(JsonObject all, String name, SkinnedMesh m, Map<String, P> done) {
        P have = done.get(name);
        if (have != null) return have;
        JsonObject j = all.getAsJsonObject(name);
        int n = m.boneCount();
        P p;
        boolean based = j.has("base");
        boolean additive = !j.has("additive") || j.get("additive").getAsBoolean();
        if (based) p = resolve(all, j.get("base").getAsString(), m, done).copy();
        else p = new P(n);
        if (j.has("bones")) {
            JsonObject b = j.getAsJsonObject("bones");
            Map<String, float[]> given = new HashMap<>();
            for (String bone : b.keySet()) {
                JsonArray a = b.getAsJsonArray(bone);
                given.put(bone, new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()});
            }
            Map<String, float[]> mirrored = new HashMap<>(given);
            for (var e : given.entrySet()) {
                if (!e.getKey().endsWith("_L")) continue;
                String r = e.getKey().substring(0, e.getKey().length() - 2) + "_R";
                float[] v = e.getValue();
                mirrored.putIfAbsent(r, new float[]{v[0], -v[1], -v[2]});
            }
            for (var e : mirrored.entrySet()) {
                int i;
                try {
                    i = m.bone(e.getKey());
                } catch (IllegalArgumentException ex) {
                    VeyrathMod.LOG.warn("Dragon pose {} names unknown bone {}", name, e.getKey());
                    continue;
                }
                for (int k = 0; k < 3; k++) {
                    float rad = (float) Math.toRadians(e.getValue()[k]);
                    p.bones[i * 3 + k] = based && additive ? p.bones[i * 3 + k] + rad : rad;
                }
            }
        }
        if (j.has("root")) {
            JsonArray a = j.getAsJsonArray("root");
            for (int k = 0; k < 3; k++) p.root[k] += a.get(k).getAsFloat();
        }
        if (j.has("rootPitch")) p.rootPitch += (float) Math.toRadians(j.get("rootPitch").getAsFloat());
        if (j.has("rootRoll")) p.rootRoll += (float) Math.toRadians(j.get("rootRoll").getAsFloat());
        done.put(name, p);
        return p;
    }
}
