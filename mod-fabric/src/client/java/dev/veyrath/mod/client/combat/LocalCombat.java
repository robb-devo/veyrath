package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.fighter.Controller;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.FighterEvents;
import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.hit.Box;
import dev.veyrath.combat.hit.HitDetector;
import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.input.InputBuffer;
import dev.veyrath.combat.math.Angles;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.HitSpec;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Reaction;
import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.combat.CombatContent;
import dev.veyrath.mod.combat.CombatNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The local player's side of combat stance: reads the keys, runs the predicted fighter, sends each
 * accepted input to the server, moves the body from the fighter's intent and predicts its own
 * hits for an immediate hit-stop and impact. The server's copy decides damage and reactions.
 *
 * <p>Tick order: {@link #startTick} runs before the player's own tick (so the velocity it sets is
 * the movement of this tick, as in the headless sim), {@link #endTick} after it, where hits are
 * checked against the moved body.
 */
public final class LocalCombat {
    private LocalCombat() {}

    static final double AIR_ACCEL = 0.025;
    /**
     * Least downward speed of a body on the ground that is not jumping: vanilla's resting value. At
     * zero speed vanilla finds no collision below and reports the body airborne, so the ticks during
     * and right after every hit-stop (which zeroes the speed) ran as air ticks on the client while
     * the server copy stayed grounded (seen in the launcher trace).
     */
    static final double GROUND_PRESS = 0.0784;
    static final Identifier GRAVITY_ID = VeyrathMod.id("combat_gravity");
    static final Identifier STEP_ID = VeyrathMod.id("stance_step");
    static final double LOCK_RANGE = 24, LOCK_KEEP = 32;

    private static boolean stance;
    private static LocalPlayer owner;
    private static Fighter fighter;
    private static final InputBuffer INPUT = new InputBuffer();
    private static final Controls CONTROLS = new Controls();
    private static final Animator ANIM = new Animator();
    private static CameraType previousCamera;
    private static int seq;
    private static byte lastHeld = -1;
    private static float yaw, prevYaw;
    private static int lockId = -1;
    private static long ticks;
    /** Strikes this client predicted, by "move|window|target", so the server's echo adds no second impact. */
    private static final Map<String, Long> PREDICTED = new HashMap<>();
    private static int rejectedCount;

    public static boolean active() { return stance && fighter != null; }
    public static Fighter fighter() { return fighter; }
    public static Animator animator() { return ANIM; }
    public static int rejectedCount() { return rejectedCount; }
    public static long ticks() { return ticks; }

    public static float renderYaw(float partialTick) { return Mth.rotLerp(partialTick, prevYaw, yaw); }

    public static Entity lockTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (lockId < 0 || mc.level == null) return null;
        return mc.level.getEntity(lockId);
    }

    private static final FighterEvents EVENTS = new FighterEvents() {
        @Override
        public void accepted(Fighter f, Action a, Move m) {
            byte held = CombatNet.held(CONTROLS.blockHeld, CONTROLS.heavyHeld, CONTROLS.sprintHeld, CONTROLS.jumpHeld);
            send(new CombatNet.ActionC2S(++seq, (byte) a.ordinal(), m == null ? "" : m.id, (float) CONTROLS.moveX,
                    (float) CONTROLS.moveZ, (float) CONTROLS.aimYaw, (float) f.yaw, held));
        }

        @Override
        public void chargeReleased(Fighter f, Move m, double level) {
            send(new CombatNet.ReleaseC2S(++seq, m.id, (float) level));
        }

        @Override
        public void moveStarted(Fighter f, Move m) {
            ANIM.play(m.clip, f.clock(), m.blendIn);
            boolean armed = dev.veyrath.combat.content.Weapons.of(f.set) != dev.veyrath.combat.content.Weapons.UNARMED;
            if (armed && !m.hits.isEmpty() && owner != null && owner.level() != null) {
                var r = owner.level().getRandom();
                owner.level().playLocalSound(owner.getX(), owner.getY() + 1, owner.getZ(), dev.veyrath.mod.combat.CombatSounds.event("blade_swing"),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.92f + r.nextFloat() * 0.16f, false);
            }
        }

        @Override
        public void moveEnded(Fighter f, Move m) {
            ANIM.play(null, f.clock(), 2.5);
        }
    };

    // ------------------------------------------------------------------ stance on / off

    static void toggle(Minecraft mc) {
        if (stance) exit(mc, true);
        else enter(mc);
    }

    static boolean canFight(LocalPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isInWater() && !p.getAbilities().flying
                && !p.isFallFlying() && !p.isSleeping();
    }

    static void enter(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !canFight(p)) return;
        if (!ClientPlayNetworking.canSend(CombatNet.StanceC2S.TYPE)) {
            p.sendOverlayMessage(Component.literal("This server does not run VEYRATH combat."));
            return;
        }
        stance = true;
        owner = p;
        fighter = new Fighter(p.getId(), CombatContent.PLAYER, Rules.player()).events(EVENTS);
        fighter.yaw = p.getYRot();
        yaw = prevYaw = p.getYRot();
        INPUT.clear();
        PREDICTED.clear();
        lastHeld = -1;
        lockId = -1;
        ANIM.reset();
        ANIM.clock = fighter.clock();
        ANIM.setBase("stance_idle", ANIM.clock, 4);
        previousCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        ActionCamera.enter(p);
        p.setSprinting(false);
        setModifier(p, Attributes.STEP_HEIGHT, STEP_ID, 0.45, AttributeModifier.Operation.ADD_VALUE);
        send(new CombatNet.StanceC2S(true));
        if (lastWeapon != dev.veyrath.combat.content.Weapons.UNARMED && fighter.equip(lastWeapon.moves())) {
            ANIM.setBase(lastWeapon.stanceClip(), ANIM.clock, 4);
            send(new CombatNet.WeaponC2S(lastWeapon.id()));
        }
        p.sendOverlayMessage(Component.translatable("veyrath.combat.stance_on"));
    }

    static void exit(Minecraft mc, boolean tellServer) {
        if (!stance) return;
        stance = false;
        LocalPlayer p = owner;
        if (p != null) {
            removeModifier(p, Attributes.GRAVITY, GRAVITY_ID);
            removeModifier(p, Attributes.STEP_HEIGHT, STEP_ID);
            if (!p.isRemoved()) {
                p.setYRot(ActionCamera.yaw());
                p.setXRot(ActionCamera.pitch());
                p.sendOverlayMessage(Component.translatable("veyrath.combat.stance_off"));
            }
        }
        if (previousCamera != null) mc.options.setCameraType(previousCamera);
        fighter = null;
        owner = null;
        lockId = -1;
        ANIM.reset();
        if (tellServer && mc.getConnection() != null) send(new CombatNet.StanceC2S(false));
    }

    /** Disconnect or world change: forget everything without messages. */
    public static void reset() {
        stance = false;
        fighter = null;
        owner = null;
        lockId = -1;
        ANIM.reset();
        PREDICTED.clear();
    }

    // ------------------------------------------------------------------ ticks

    public static void startTick(Minecraft mc) {
        ticks++;
        while (CombatClient.STANCE_KEY.consumeClick()) toggle(mc);
        if (!stance) return;
        LocalPlayer p = mc.player;
        if (p == null || p != owner || mc.level == null || !canFight(p)) {
            exit(mc, p != null && mc.getConnection() != null);
            return;
        }
        if (mc.isPaused()) return;
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        readInputs(mc, p);
        fighter.yaw = p.getYRot();
        prevYaw = yaw;
        fighter.tick(CONTROLS, INPUT, p.onGround());
        applyIntent(mc, p);
        ANIM.clock = fighter.clock();
        ANIM.hold = fighter.inHitstop();
        ANIM.setBase(fighter.phase() == Fighter.Phase.BLOCK ? "block_hold" : stanceClip(), fighter.clock(), 2);
    }

    public static void endTick(Minecraft mc) {
        if (!active()) return;
        LocalPlayer p = mc.player;
        if (p == null || p != owner || mc.isPaused()) return;
        // Vanilla's head turn may have nudged the body during the tick.
        p.setYBodyRot(yaw);
        p.setYHeadRot(yaw);
        predictHits(mc, p);
        ANIM.hold = fighter.inHitstop();
        if (ticks % 40 == 0) PREDICTED.values().removeIf(t -> ticks - t > 40);
        Entity lock = lockTarget();
        if (lockId >= 0 && (lock == null || !lock.isAlive() || lock.distanceTo(p) > LOCK_KEEP)) lockId = -1;
    }

    private static void readInputs(Minecraft mc, LocalPlayer p) {
        Options o = mc.options;
        long now = fighter.clock() + 1; // as the sim stamps presses: evaluated by the next advance
        while (o.keyAttack.consumeClick()) INPUT.press(Action.LIGHT, now);
        while (o.keyUse.consumeClick()) INPUT.press(Action.HEAVY, now);
        while (o.keyShift.consumeClick()) INPUT.press(Action.BLOCK, now);
        while (o.keySprint.consumeClick()) INPUT.press(Action.DODGE, now);
        while (o.keyJump.consumeClick()) INPUT.press(Action.JUMP, now);
        Action[] slots = {Action.ABILITY_1, Action.ABILITY_2, Action.ABILITY_3, Action.ULTIMATE};
        for (int i = 0; i < slots.length; i++) {
            while (o.keyHotbarSlots[i].consumeClick()) INPUT.press(slots[i], now);
        }
        while (o.keyPickItem.consumeClick()) cycleLock(mc, p);
        // Keys 5 onward draw the weapons in order (5 = fists, 6 = Oathcleaver, ...).
        for (int i = 0; i < dev.veyrath.combat.content.Weapons.ALL.size() && 4 + i < o.keyHotbarSlots.length; i++) {
            while (o.keyHotbarSlots[4 + i].consumeClick()) draw(dev.veyrath.combat.content.Weapons.ALL.get(i));
        }

        CONTROLS.blockHeld = o.keyShift.isDown();
        CONTROLS.heavyHeld = o.keyUse.isDown();
        CONTROLS.sprintHeld = o.keySprint.isDown();
        CONTROLS.jumpHeld = o.keyJump.isDown();
        // Movement keys are camera relative.
        float camYaw = ActionCamera.yaw();
        double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        Vec3 mv = Angles.forward(camYaw).scale(f).add(Angles.forward(camYaw - 90).scale(s));
        if (mv.lengthSq() > 1e-6) mv = mv.normalize();
        CONTROLS.moveX = mv.x();
        CONTROLS.moveZ = mv.z();
        CONTROLS.aimYaw = camYaw;

        byte held = CombatNet.held(CONTROLS.blockHeld, CONTROLS.heavyHeld, false, false);
        if (held != lastHeld) {
            lastHeld = held;
            send(new CombatNet.HeldC2S(held));
        }
    }

    /** Facing and velocity from the fighter, like the headless sim's body step. */
    /** Weapon remembered across stance changes, drawn again on the next stance. */
    private static dev.veyrath.combat.content.Weapons.Weapon lastWeapon = dev.veyrath.combat.content.Weapons.UNARMED;

    /** Draws a weapon now if the fighter is between actions (predicted; the server confirms). */
    static void draw(dev.veyrath.combat.content.Weapons.Weapon w) {
        if (!active() || w == null) return;
        if (fighter.set == w.moves() || !fighter.equip(w.moves())) return;
        lastWeapon = w;
        INPUT.clear();
        ANIM.setBase(w.stanceClip(), fighter.clock(), 4);
        send(new CombatNet.WeaponC2S(w.id()));
        if (owner != null) owner.sendOverlayMessage(Component.literal(w.name()));
    }

    /** The server's word on the local fighter's weapon (confirmation or correction). */
    static void weaponFromServer(String id) {
        dev.veyrath.combat.content.Weapons.Weapon w = dev.veyrath.combat.content.Weapons.byId(id);
        if (!active() || w == null || fighter.set == w.moves()) return;
        if (fighter.equip(w.moves())) lastWeapon = w;
    }

    static String stanceClip() {
        return fighter == null ? "stance_idle" : dev.veyrath.combat.content.Weapons.of(fighter.set).stanceClip();
    }

    private static void applyIntent(Minecraft mc, LocalPlayer p) {
        Vec3 feet = feet(p);
        Entity lock = lockTarget();
        Vec3 lockFeet = lock == null ? null : feet(lock);
        Vec3 soft = softTarget(mc, p, feet);
        Controller.Intent in = Controller.compute(fighter, CONTROLS, feet, lockFeet, soft);
        fighter.yaw = in.yaw();
        yaw = (float) in.yaw();
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.setYBodyRot(yaw);
        p.setXRot(0);

        net.minecraft.world.phys.Vec3 dm = p.getDeltaMovement();
        double vx = dm.x, vy = dm.y, vz = dm.z;
        Vec3 hit = fighter.takeVelocity();
        if (hit != null) {
            vx = hit.x();
            vy = hit.y();
            vz = hit.z();
        }
        boolean grounded = p.onGround();
        if (in.forcesHorizontal()) {
            vx = in.vx();
            vz = in.vz();
        } else if (CONTROLS.hasMove() && in.inputScale() > 0) {
            double speed = Controller.WALK * in.inputScale();
            if (grounded) {
                vx = CONTROLS.moveX * speed;
                vz = CONTROLS.moveZ * speed;
            } else {
                // Light air control, never faster than walking or the momentum it already has.
                double cap = Math.max(speed, Math.hypot(vx, vz));
                vx += CONTROLS.moveX * AIR_ACCEL;
                vz += CONTROLS.moveZ * AIR_ACCEL;
                double h = Math.hypot(vx, vz);
                if (h > cap) {
                    vx *= cap / h;
                    vz *= cap / h;
                }
            }
        }
        if (fighter.takeJumpRequest() && grounded) vy = p.getAttributeValue(Attributes.JUMP_STRENGTH);
        double bounce = fighter.bounceVelocity();
        if (!Double.isNaN(bounce)) vy = bounce;
        if (in.forcesVertical()) vy = in.vy();
        if (grounded && vy <= 0 && vy > -GROUND_PRESS) vy = -GROUND_PRESS;
        p.setDeltaMovement(vx, vy, vz);

        double g = in.gravity();
        if (Math.abs(g - 1) < 1e-9) removeModifier(p, Attributes.GRAVITY, GRAVITY_ID);
        else setModifier(p, Attributes.GRAVITY, GRAVITY_ID, g - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    // ------------------------------------------------------------------ hits (prediction)

    private static void predictHits(Minecraft mc, LocalPlayer p) {
        if (fighter.activeWindows().length == 0) return;
        bladeTrail(p);
        List<LivingEntity> near = mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8), e -> canTarget(p, e));
        if (near.isEmpty()) return;
        List<HitDetector.Target> targets = new ArrayList<>(near.size());
        for (LivingEntity e : near) {
            AABB bb = e.getBoundingBox();
            targets.add(new HitDetector.Target(e.getId(), new Box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ), null));
        }
        Move m = fighter.move();
        for (HitDetector.Strike s : HitDetector.detect(fighter, CombatContent.CLIPS, feet(p), fighter.yaw, p.getScale(), targets)) {
            Entity struck = mc.level.getEntity(s.target().id());
            if (!(struck instanceof LivingEntity le) || !dev.veyrath.mod.combat.LineOfSight.clear(mc.level, p, le)) continue;
            HitSpec spec = fighter.window(s.window()).spec();
            fighter.connect(Outcome.predictedHit(spec), spec);
            PREDICTED.put(key(m.id, s.window(), s.target().id()), ticks);
            CombatEffects.impact(mc.level, spec.vfx(), s.point(), fighter.yaw);
            CombatEffects.sound(mc.level, spec.sfx(), s.point());
            ActionCamera.addTrauma(spec.shake());
            ScreenFx.impact(spec.vfx());
            HitFlash.mark(s.target().id(), spec.hitstop());
        }
    }

    /** Light along every bone-attached capsule of the live windows (weapon swings; fists too, faintly). */
    private static void bladeTrail(LocalPlayer p) {
        var weapon = dev.veyrath.combat.content.Weapons.of(fighter.set);
        if (weapon == dev.veyrath.combat.content.Weapons.UNARMED) return;
        Move m = fighter.move();
        if (weapon == dev.veyrath.combat.content.Weapons.GRIMOIRE) {
            // Spells show once, as their window opens.
            for (int wi : fighter.activeWindows()) {
                var w = m.hits.get(wi);
                if (fighter.moveTick() != w.from()) continue;
                for (HitDetector.Capsule c : HitDetector.sweep(m, w, fighter.moveTick(), CombatContent.CLIPS, feet(p), fighter.yaw, p.getScale())) {
                    CombatEffects.spell(c.a(), c.b(), c.radius());
                }
            }
            return;
        }
        for (int wi : fighter.activeWindows()) {
            var w = m.hits.get(wi);
            for (HitDetector.Capsule c : HitDetector.sweep(m, w, fighter.moveTick(), CombatContent.CLIPS, feet(p), fighter.yaw, p.getScale())) {
                if (c.radius() <= 0.6) CombatEffects.bladeTrail(c.a(), c.b(), 0xB0E8F2FF); // blades, not area shapes
            }
        }
    }

    static boolean canTarget(LivingEntity self, LivingEntity e) {
        return e != self && e.isAlive() && !e.isSpectator() && e.isAttackable() && !(e instanceof ArmorStand)
                && !self.isAlliedTo(e) && !e.isAlliedTo(self);
    }

    static String key(String move, int window, int target) { return move + "|" + window + "|" + target; }

    /** True once if this strike was predicted here (its impact already shown). */
    static boolean consumePrediction(String key) { return PREDICTED.remove(key) != null; }

    // ------------------------------------------------------------------ server messages

    /** The server resolved a strike on the local player. */
    static void struck(Outcome o) {
        if (!active()) return;
        fighter.applyOutcome(o);
        if (o.velocity() != null) fighter.queueVelocity(o.velocity());
    }

    /** The server resolved a strike by the local player (only a parry changes the attacker). */
    static void attackResolved(Outcome o) {
        if (!active()) return;
        if (o.result() == Outcome.Result.PARRIED) {
            fighter.react(Reaction.PARRIED, 26, o.hitstop());
            if (o.attackerVelocity() != null) fighter.queueVelocity(o.attackerVelocity());
        }
    }

    static void state(CombatNet.StateS2C s) {
        if (!active()) return;
        if (s.refill()) fighter.refill();
        fighter.syncResources(s.stamina(), s.aether(), s.gauge());
    }

    static void rejected(int rejectedSeq) {
        rejectedCount++;
        VeyrathMod.LOG.info("combat: server rejected input {} (latest {})", rejectedSeq, seq);
        if (active()) fighter.cancelMove();
    }

    // ------------------------------------------------------------------ targets

    private static Vec3 softTarget(Minecraft mc, LocalPlayer p, Vec3 feet) {
        double best = Controller.SOFT_RANGE + 1;
        Vec3 found = null;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(best), x -> canTarget(p, x))) {
            Vec3 f = feet(e);
            double d = f.sub(feet).horizontalLength();
            if (d < best) {
                best = d;
                found = f;
            }
        }
        return found;
    }

    /** Middle mouse: lock the enemy nearest the camera's aim, then the next one, then release. */
    private static void cycleLock(Minecraft mc, LocalPlayer p) {
        List<LivingEntity> cands = mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(LOCK_RANGE), e -> canTarget(p, e));
        float camYaw = ActionCamera.yaw();
        LivingEntity best = null;
        double bestAngle = 75;
        Vec3 feet = feet(p);
        // Rank by angle from the camera's aim; skip the current target so presses cycle.
        for (Iterator<LivingEntity> it = cands.iterator(); it.hasNext(); ) {
            LivingEntity e = it.next();
            if (e.getId() == lockId) continue;
            Vec3 f = feet(e);
            if (f.sub(feet).horizontalLength() > LOCK_RANGE) continue;
            double a = Math.abs(Angles.wrapDegrees(Controller.yawTo(feet, f) - camYaw));
            if (a < bestAngle) {
                bestAngle = a;
                best = e;
            }
        }
        lockId = best == null ? -1 : best.getId();
        if (best != null) p.sendOverlayMessage(Component.translatable("veyrath.combat.locked", best.getDisplayName()));
    }

    // ------------------------------------------------------------------ helpers

    static Vec3 feet(Entity e) {
        net.minecraft.world.phys.Vec3 v = e.position();
        return new Vec3(v.x, v.y, v.z);
    }

    static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) ClientPlayNetworking.send(payload);
    }

    private static void setModifier(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                    Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
    }

    private static void removeModifier(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, Identifier id) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null && inst.getModifier(id) != null) inst.removeModifier(id);
    }
}
