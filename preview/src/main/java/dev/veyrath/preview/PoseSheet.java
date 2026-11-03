package dev.veyrath.preview;

import dev.veyrath.combat.anim.Clip;
import dev.veyrath.combat.content.UnarmedClips;
import dev.veyrath.combat.content.UnarmedMoves;
import dev.veyrath.combat.hit.ClipLibrary;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.HitShape;
import dev.veyrath.combat.move.HitWindow;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.rig.Bone;
import dev.veyrath.combat.rig.Pose;
import dev.veyrath.combat.rig.Rig;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Contact sheets of the combat clips, drawn from the same rig and clips the game uses: one row
 * per move, frames across, the vanilla body parts as shaded boxes and the hit shapes in red on
 * active ticks (with the sweep from the previous tick). Engine render, not a game screenshot;
 * root motion from physics (dashes, jumps) is not shown, only the pose.
 */
public final class PoseSheet {
    private PoseSheet() {}

    static final int CELL_W = 176, CELL_H = 214, LABEL_W = 190, COLS = 8, HEADER = 54;
    static final double PX_PER_BLOCK = 74;
    static final double AZIMUTH = 38, ELEVATION = 14;

    record Group(String file, String title, List<String> rows) {}

    static final List<Group> GROUPS = List.of(
            new Group("poses_ground", "Ground string, branches and heavies",
                    List.of("jab", "cross", "hook", "roundhouse", "heavy", "heavy_charged", "launcher", "spin_heel", "dash_strike", "riposte")),
            new Group("poses_air", "Air combat and air movement",
                    List.of("launch_rise", "air_1", "air_2", "air_3", "meteor_drop", "meteor_land", "air_dash", "air_jump")),
            new Group("poses_defence", "Stance, defence and dodges",
                    List.of("stance_idle", "block_hold", "block_hit", "parry", "dodge_forward", "dodge_back", "dodge_left", "dodge_right")),
            new Group("poses_abilities", "Abilities and the Unbound Fist ultimate",
                    List.of("gale_step", "skybreaker", "quake_palm", "unbound_fist")),
            new Group("poses_reactions", "Hit reactions, knockdown and get-up",
                    List.of("react_flinch", "react_stagger", "react_knockback", "react_launch", "react_spike",
                            "react_guard_break", "react_parried", "knockdown", "getup")));

    public static void render(Path out) throws IOException {
        Files.createDirectories(out);
        MoveSet set = UnarmedMoves.moveSet();
        ClipLibrary clips = UnarmedClips.library();
        for (Group g : GROUPS) {
            BufferedImage img = sheet(g, set, clips);
            Path p = out.resolve(g.file + ".png");
            ImageIO.write(img, "png", p.toFile());
            System.out.println("wrote " + p);
        }
    }

    static BufferedImage sheet(Group g, MoveSet set, ClipLibrary clips) {
        int w = LABEL_W + COLS * CELL_W, h = HEADER + g.rows.size() * CELL_H;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = img.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gr.setColor(new Color(0x1d2026));
        gr.fillRect(0, 0, w, h);
        gr.setColor(new Color(0xe8e4da));
        gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        gr.drawString("VEYRATH combat clips: " + g.title, 14, 26);
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        gr.setColor(new Color(0xa9a59b));
        gr.drawString("Engine render from combat/ (not in game). Red: hit shapes on active ticks, faint red: the sweep since the"
                + " previous tick. Pose only; dash and jump motion is applied by physics.", 14, 44);
        for (int r = 0; r < g.rows.size(); r++) {
            String id = g.rows.get(r);
            Move m = set.get(id);
            Clip clip = clips.get(m != null ? m.clip : id);
            int y0 = HEADER + r * CELL_H;
            gr.setColor(r % 2 == 0 ? new Color(0x23262d) : new Color(0x20232a));
            gr.fillRect(0, y0, w, CELL_H);
            label(gr, id, m, clip, 14, y0);
            if (clip == null) continue;
            double[] times = frameTimes(m, clip);
            for (int c = 0; c < times.length; c++) {
                drawFrame(gr, LABEL_W + c * CELL_W, y0, clip, m, times[c]);
            }
        }
        gr.dispose();
        return img;
    }

    static void label(Graphics2D gr, String id, Move m, Clip clip, int x, int y0) {
        gr.setColor(new Color(0xe8e4da));
        gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        gr.drawString(id, x, y0 + 28);
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        gr.setColor(new Color(0xa9a59b));
        List<String> lines = new ArrayList<>();
        if (m != null) {
            lines.add(m.kind.name().toLowerCase() + ", " + m.duration + " ticks");
            if (!m.hits.isEmpty()) {
                StringBuilder sb = new StringBuilder("active ");
                for (int i = 0; i < m.hits.size() && i < 3; i++) {
                    HitWindow hw = m.hits.get(i);
                    if (i > 0) sb.append(", ");
                    sb.append(hw.from()).append(hw.to() != hw.from() ? "-" + hw.to() : "");
                }
                if (m.hits.size() > 3) sb.append(" ... (").append(m.hits.size()).append(" windows)");
                lines.add(sb.toString());
                lines.add(String.format("damage %.1f, %s", m.hits.getLast().spec().damage(),
                        m.hits.getLast().spec().reaction().name().toLowerCase()));
            }
            if (m.iframeFrom >= 0 && m.iframeTo >= m.iframeFrom) lines.add("i-frames " + m.iframeFrom + "-" + m.iframeTo);
            if (m.armorFrom >= 0 && m.armorTo >= m.armorFrom) lines.add("armor " + m.armorFrom + "-" + m.armorTo);
        } else if (clip != null) {
            lines.add("layer clip, " + (int) clip.length + " ticks" + (clip.loop ? ", loops" : ""));
        }
        if (clip == null) lines.add("NO CLIP");
        for (int i = 0; i < lines.size(); i++) gr.drawString(lines.get(i), x, y0 + 50 + i * 17);
    }

    /** Eight frame times: the start, the run-up, every window's first and last tick, recovery and the end. */
    static double[] frameTimes(Move m, Clip clip) {
        // Reactions last as long as their stun; their clips show the reaction itself.
        double len = m == null ? clip.length : (clip.loop ? m.duration : Math.min(m.duration, clip.length));
        TreeSet<Double> must = new TreeSet<>();
        must.add(0.0);
        if (m != null && !m.hits.isEmpty()) {
            int fa = m.hits.getFirst().from(), la = m.hits.getLast().to();
            must.add(Math.max(0, fa - 2.0));
            must.add((double) fa);
            if (m.hits.size() <= 2) for (HitWindow w : m.hits) { must.add((double) w.from()); must.add((double) w.to()); }
            else { must.add((double) m.hits.get(m.hits.size() / 2).from()); must.add((double) la); }
            must.add(Math.min(len, la + 2.0));
        }
        must.add(len);
        // Fill the gaps with evenly spaced frames until there are COLS.
        while (must.size() < COLS) {
            double bestGap = -1, at = 0, prev = -1;
            for (double t : must) {
                if (prev >= 0 && t - prev > bestGap) { bestGap = t - prev; at = (prev + t) / 2; }
                prev = t;
            }
            double t = Math.round(at);
            if (bestGap < 2 || must.contains(t)) break;
            must.add(t);
        }
        while (must.size() > COLS) must.remove(must.higher(must.first())); // keep the start, thin the earliest
        return must.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // ---------------------------------------------------------------------------------- drawing

    record Face(Polygon poly, double depth, Color color) {}

    static final double AZ = Math.toRadians(AZIMUTH), EL = Math.toRadians(ELEVATION);
    /** Direction from the character to the camera, in character space (+X right, +Y up, +Z forward). */
    static final Vec3 TO_CAM = new Vec3(Math.sin(AZ) * Math.cos(EL), Math.sin(EL), Math.cos(AZ) * Math.cos(EL));
    static final Vec3 VIEW = TO_CAM.scale(-1);
    /** Character space is left-handed (right x up = forward), so screen right is up x view. */
    static final Vec3 SCREEN_RIGHT = cross(new Vec3(0, 1, 0), VIEW).normalize();
    static final Vec3 SCREEN_UP = cross(VIEW, SCREEN_RIGHT).normalize();
    static final Vec3 LIGHT = new Vec3(0.45, 0.8, 0.55).normalize();

    static void drawFrame(Graphics2D gr, int x0, int y0, Clip clip, Move m, double t) {
        Pose pose = clip.sample(t, new Pose());
        Rig.Solved s = Rig.solve(pose);
        java.awt.Shape oldClip = gr.getClip();
        gr.clipRect(x0, y0, CELL_W, CELL_H);
        double cx = x0 + CELL_W * 0.42, cy = y0 + CELL_H - 40; // strikes reach to the right
        int tick = (int) Math.floor(t + 1e-9);
        boolean active = m != null && m.hits.stream().anyMatch(w -> w.activeAt(tick)) && Math.abs(t - tick) < 1e-9;

        // Ground: shadow and a facing arrow.
        gr.setColor(new Color(0x2c3038));
        double[] g0 = project(new Vec3(0, 0, 0), cx, cy);
        gr.fill(new Ellipse2D.Double(g0[0] - 34, g0[1] - 9, 68, 18));
        gr.setColor(new Color(0x59606d));
        gr.setStroke(new BasicStroke(2f));
        double[] a0 = project(new Vec3(0, 0, 0.35), cx, cy), a1 = project(new Vec3(0, 0, 0.85), cx, cy);
        gr.draw(new Line2D.Double(a0[0], a0[1], a1[0], a1[1]));

        List<Face> faces = new ArrayList<>();
        for (Bone b : Bone.values()) {
            if (b == Bone.ROOT) continue;
            addBox(faces, s, b, partColor(b), cx, cy);
        }
        faces.sort((p, q) -> Double.compare(q.depth, p.depth));
        gr.setStroke(new BasicStroke(1f));
        for (Face f : faces) {
            gr.setColor(f.color);
            gr.fillPolygon(f.poly);
            gr.setColor(new Color(0, 0, 0, 90));
            gr.drawPolygon(f.poly);
        }

        if (active) drawHits(gr, m, clip, tick, cx, cy);

        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        gr.setColor(active ? new Color(0xff6b5b) : new Color(0xa9a59b));
        String phase = m == null ? "" : phase(m, t);
        gr.drawString(String.format(t == Math.floor(t) ? "t=%.0f" : "t=%.1f", t) + (phase.isEmpty() ? "" : "  " + phase), x0 + 8, y0 + CELL_H - 10);
        for (Clip.Event e : clip.events) {
            if (Math.abs(e.time() - t) < 0.26) {
                gr.setColor(new Color(0xe0b85a));
                gr.drawString(e.name(), x0 + 8, y0 + 18);
            }
        }
        gr.setClip(oldClip);
    }

    static String phase(Move m, double t) {
        if (m.hits.isEmpty()) {
            if (m.invulnerableAt((int) t)) return "i-frames";
            return t >= m.freeFrom ? "free" : "";
        }
        int tick = (int) Math.floor(t + 1e-9);
        for (HitWindow w : m.hits) if (w.activeAt(tick) && Math.abs(t - tick) < 1e-9) return "ACTIVE";
        if (t < m.firstActive()) return "startup";
        return t >= m.freeFrom ? "free" : "recovery";
    }

    static void drawHits(Graphics2D gr, Move m, Clip clip, int tick, double cx, double cy) {
        Pose pose = new Pose();
        for (HitWindow w : m.hits) {
            if (!w.activeAt(tick)) continue;
            for (HitShape sh : w.shapes()) {
                for (int k = 1; k <= 4; k++) {
                    boolean last = k == 4;
                    Vec3 a, b;
                    if (sh.bone() == null) {
                        if (!last) continue;
                        a = sh.a();
                        b = sh.b();
                    } else {
                        clip.sample(tick - 1 + k / 4.0, pose);
                        Rig.Solved s = Rig.solve(pose);
                        a = s.toCharacter(sh.bone(), sh.a());
                        b = s.toCharacter(sh.bone(), sh.b());
                    }
                    double[] pa = project(a, cx, cy), pb = project(b, cx, cy);
                    float width = (float) (2 * sh.radius() * PX_PER_BLOCK);
                    // The capsule's outline as a shape: a round-capped stroke of the core segment.
                    java.awt.Shape cap = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                            .createStrokedShape(new Line2D.Double(pa[0], pa[1], pb[0] + 1e-3, pb[1]));
                    boolean area = sh.bone() == null;
                    gr.setColor(last ? new Color(255, 70, 50, area ? 26 : 52) : new Color(255, 70, 50, 20));
                    gr.fill(cap);
                    if (last) {
                        gr.setStroke(new BasicStroke(1.6f));
                        gr.setColor(new Color(255, 96, 76, 220));
                        gr.draw(cap);
                        gr.setStroke(new BasicStroke(1f));
                        gr.draw(new Line2D.Double(pa[0], pa[1], pb[0], pb[1]));
                    }
                }
            }
        }
    }

    static Color partColor(Bone b) {
        return switch (b) {
            case HEAD -> new Color(0xc99a73);
            case BODY -> new Color(0x3f6f8f);
            case RIGHT_ARM -> new Color(0xb4835f);
            case LEFT_ARM -> new Color(0xa47a5a);
            case RIGHT_LEG -> new Color(0x3b3f52);
            case LEFT_LEG -> new Color(0x34384a);
            default -> Color.GRAY;
        };
    }

    static void addBox(List<Face> faces, Rig.Solved s, Bone b, Color base, double cx, double cy) {
        double[] bx = b.box();
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            Vec3 local = new Vec3((i & 1) == 0 ? bx[0] : bx[3], (i & 2) == 0 ? bx[1] : bx[4], (i & 4) == 0 ? bx[2] : bx[5]);
            c[i] = s.toCharacter(b, local);
        }
        Vec3 center = Vec3.ZERO;
        for (Vec3 v : c) center = center.add(v);
        center = center.scale(1 / 8.0);
        int[][] quads = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
        for (int[] q : quads) {
            Vec3 fc = c[q[0]].add(c[q[1]]).add(c[q[2]]).add(c[q[3]]).scale(0.25);
            Vec3 n = fc.sub(center);
            if (n.lengthSq() < 1e-12) continue;
            n = n.normalize();
            if (dot(n, VIEW) >= 0) continue; // faces away from the camera
            double shade = 0.45 + 0.55 * Math.max(0, dot(n, LIGHT));
            Polygon poly = new Polygon();
            for (int k : q) {
                double[] p = project(c[k], cx, cy);
                poly.addPoint((int) Math.round(p[0]), (int) Math.round(p[1]));
            }
            faces.add(new Face(poly, dot(fc, VIEW), scale(base, shade)));
        }
    }

    static double[] project(Vec3 p, double cx, double cy) {
        Vec3 q = p.sub(new Vec3(0, 0, 0.15));
        return new double[]{cx + dot(q, SCREEN_RIGHT) * PX_PER_BLOCK, cy - dot(q, SCREEN_UP) * PX_PER_BLOCK};
    }

    static Color scale(Color c, double f) {
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f));
    }

    static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    static double dot(Vec3 a, Vec3 b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }

    static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
    }
}
