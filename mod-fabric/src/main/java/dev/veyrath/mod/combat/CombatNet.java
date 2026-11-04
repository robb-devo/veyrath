package dev.veyrath.mod.combat;

import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.Reaction;
import dev.veyrath.mod.VeyrathMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Combat messages. The client runs its own fighter and tells the server which inputs it accepted;
 * the server's copy checks each one, detects hits, applies damage and tells everyone nearby what
 * happened. Strings are move and clip ids; enums travel as ordinals.
 */
public final class CombatNet {
    private CombatNet() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(VeyrathMod.id(path));
    }

    public static final int HELD_BLOCK = 1, HELD_HEAVY = 2, HELD_SPRINT = 4, HELD_JUMP = 8;

    // ------------------------------------------------------------------ client to server

    /**
     * An input press the client's fighter accepted. {@code move} is the move it started, or empty
     * for a ground jump or a block press. {@code yaw} is the fighter's facing when it decided.
     */
    public record ActionC2S(int seq, byte action, String move, float moveX, float moveZ, float aimYaw, float yaw, byte held)
            implements CustomPacketPayload {
        public static final Type<ActionC2S> TYPE = payloadType("action");
        public static final StreamCodec<FriendlyByteBuf, ActionC2S> CODEC = CustomPacketPayload.codec(ActionC2S::write, ActionC2S::new);

        private ActionC2S(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readByte(), b.readUtf(64), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readByte());
        }

        private void write(FriendlyByteBuf b) {
            b.writeVarInt(seq);
            b.writeByte(action);
            b.writeUtf(move, 64);
            b.writeFloat(moveX);
            b.writeFloat(moveZ);
            b.writeFloat(aimYaw);
            b.writeFloat(yaw);
            b.writeByte(held);
        }

        @Override public Type<ActionC2S> type() { return TYPE; }
    }

    /** A held heavy was released (or fully charged) into {@code move} at this charge level. */
    public record ReleaseC2S(int seq, String move, float level) implements CustomPacketPayload {
        public static final Type<ReleaseC2S> TYPE = payloadType("release");
        public static final StreamCodec<FriendlyByteBuf, ReleaseC2S> CODEC = CustomPacketPayload.codec(ReleaseC2S::write, ReleaseC2S::new);

        private ReleaseC2S(FriendlyByteBuf b) { this(b.readVarInt(), b.readUtf(64), b.readFloat()); }

        private void write(FriendlyByteBuf b) {
            b.writeVarInt(seq);
            b.writeUtf(move, 64);
            b.writeFloat(level);
        }

        @Override public Type<ReleaseC2S> type() { return TYPE; }
    }

    /** Held block and heavy, sent when they change. */
    public record HeldC2S(byte held) implements CustomPacketPayload {
        public static final Type<HeldC2S> TYPE = payloadType("held");
        public static final StreamCodec<FriendlyByteBuf, HeldC2S> CODEC = CustomPacketPayload.codec(HeldC2S::write, HeldC2S::new);

        private HeldC2S(FriendlyByteBuf b) { this(b.readByte()); }

        private void write(FriendlyByteBuf b) { b.writeByte(held); }

        @Override public Type<HeldC2S> type() { return TYPE; }
    }

    /** Combat stance on or off. */
    public record StanceC2S(boolean on) implements CustomPacketPayload {
        public static final Type<StanceC2S> TYPE = payloadType("stance");
        public static final StreamCodec<FriendlyByteBuf, StanceC2S> CODEC = CustomPacketPayload.codec(StanceC2S::write, StanceC2S::new);

        private StanceC2S(FriendlyByteBuf b) { this(b.readBoolean()); }

        private void write(FriendlyByteBuf b) { b.writeBoolean(on); }

        @Override public Type<StanceC2S> type() { return TYPE; }
    }

    // ------------------------------------------------------------------ server to client

    /**
     * One resolved strike. Sent to the attacker, the defender and everyone tracking either of them.
     * {@code velocity} is set only where the receiving client moves the defender itself (players);
     * mobs are moved by the server. {@code defenderYaw} is the facing the defender reacts with.
     */
    public record HitS2C(int attacker, int target, Outcome outcome, float px, float py, float pz, String move, int window,
                         float defenderYaw) implements CustomPacketPayload {
        public static final Type<HitS2C> TYPE = payloadType("hit");
        public static final StreamCodec<FriendlyByteBuf, HitS2C> CODEC = CustomPacketPayload.codec(HitS2C::write, HitS2C::read);

        private static HitS2C read(FriendlyByteBuf b) {
            int attacker = b.readVarInt(), target = b.readVarInt();
            Outcome.Result result = Outcome.Result.values()[b.readByte()];
            int r = b.readByte();
            Reaction reaction = r < 0 ? null : Reaction.values()[r];
            double damage = b.readFloat();
            int hitstun = b.readVarInt(), hitstop = b.readVarInt();
            Vec3 velocity = readVec(b), attackerVelocity = readVec(b);
            int flags = b.readByte();
            double shake = b.readFloat();
            String vfx = emptyToNull(b.readUtf(64)), sfx = emptyToNull(b.readUtf(64));
            Outcome o = new Outcome(result, damage, reaction, hitstun, hitstop, velocity, attackerVelocity,
                    (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, shake, vfx, sfx);
            return new HitS2C(attacker, target, o, b.readFloat(), b.readFloat(), b.readFloat(), b.readUtf(64), b.readVarInt(), b.readFloat());
        }

        private void write(FriendlyByteBuf b) {
            b.writeVarInt(attacker);
            b.writeVarInt(target);
            b.writeByte(outcome.result().ordinal());
            b.writeByte(outcome.reaction() == null ? -1 : outcome.reaction().ordinal());
            b.writeFloat((float) outcome.damage());
            b.writeVarInt(outcome.hitstun());
            b.writeVarInt(outcome.hitstop());
            writeVec(b, outcome.velocity());
            writeVec(b, outcome.attackerVelocity());
            b.writeByte((outcome.counter() ? 1 : 0) | (outcome.perfect() ? 2 : 0) | (outcome.poiseBreak() ? 4 : 0));
            b.writeFloat((float) outcome.shake());
            b.writeUtf(nullToEmpty(outcome.vfx()), 64);
            b.writeUtf(nullToEmpty(outcome.sfx()), 64);
            b.writeFloat(px);
            b.writeFloat(py);
            b.writeFloat(pz);
            b.writeUtf(move, 64);
            b.writeVarInt(window);
            b.writeFloat(defenderYaw);
        }

        @Override public Type<HitS2C> type() { return TYPE; }
    }

    /** A fighter (mob, other player) started showing a clip; an empty clip fades back to its base pose. */
    public record AnimS2C(int entity, String clip, float fade) implements CustomPacketPayload {
        public static final Type<AnimS2C> TYPE = payloadType("anim");
        public static final StreamCodec<FriendlyByteBuf, AnimS2C> CODEC = CustomPacketPayload.codec(AnimS2C::write, AnimS2C::new);

        private AnimS2C(FriendlyByteBuf b) { this(b.readVarInt(), b.readUtf(64), b.readFloat()); }

        private void write(FriendlyByteBuf b) {
            b.writeVarInt(entity);
            b.writeUtf(clip, 64);
            b.writeFloat(fade);
        }

        @Override public Type<AnimS2C> type() { return TYPE; }
    }

    /** A VEYRATH effect by id at a point (Duelist tells and other non-hit effects); drawn by the client's own engine. */
    public record VfxS2C(String id, float x, float y, float z, float yaw) implements CustomPacketPayload {
        public static final Type<VfxS2C> TYPE = payloadType("vfx");
        public static final StreamCodec<FriendlyByteBuf, VfxS2C> CODEC = CustomPacketPayload.codec(VfxS2C::write, VfxS2C::new);

        private VfxS2C(FriendlyByteBuf b) { this(b.readUtf(64), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()); }

        private void write(FriendlyByteBuf b) {
            b.writeUtf(id, 64);
            b.writeFloat(x);
            b.writeFloat(y);
            b.writeFloat(z);
            b.writeFloat(yaw);
        }

        @Override public Type<VfxS2C> type() { return TYPE; }
    }

    /** Another player's stance changed (shown with the guard pose). */
    public record StanceS2C(int entity, boolean on) implements CustomPacketPayload {
        public static final Type<StanceS2C> TYPE = payloadType("stance_of");
        public static final StreamCodec<FriendlyByteBuf, StanceS2C> CODEC = CustomPacketPayload.codec(StanceS2C::write, StanceS2C::new);

        private StanceS2C(FriendlyByteBuf b) { this(b.readVarInt(), b.readBoolean()); }

        private void write(FriendlyByteBuf b) {
            b.writeVarInt(entity);
            b.writeBoolean(on);
        }

        @Override public Type<StanceS2C> type() { return TYPE; }
    }

    /** The server's resources for the client's fighter; {@code refill} also clears cooldowns (debug). */
    public record StateS2C(float stamina, float aether, float gauge, boolean refill) implements CustomPacketPayload {
        public static final Type<StateS2C> TYPE = payloadType("state");
        public static final StreamCodec<FriendlyByteBuf, StateS2C> CODEC = CustomPacketPayload.codec(StateS2C::write, StateS2C::new);

        private StateS2C(FriendlyByteBuf b) { this(b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean()); }

        private void write(FriendlyByteBuf b) {
            b.writeFloat(stamina);
            b.writeFloat(aether);
            b.writeFloat(gauge);
            b.writeBoolean(refill);
        }

        @Override public Type<StateS2C> type() { return TYPE; }
    }

    /** The server refused an input; the client drops the move it predicted. */
    public record RejectS2C(int seq) implements CustomPacketPayload {
        public static final Type<RejectS2C> TYPE = payloadType("reject");
        public static final StreamCodec<FriendlyByteBuf, RejectS2C> CODEC = CustomPacketPayload.codec(RejectS2C::write, RejectS2C::new);

        private RejectS2C(FriendlyByteBuf b) { this(b.readVarInt()); }

        private void write(FriendlyByteBuf b) { b.writeVarInt(seq); }

        @Override public Type<RejectS2C> type() { return TYPE; }
    }

    // ------------------------------------------------------------------ registration and helpers

    /** Base pose of a fighter that is not a player in stance (the Duelist): "stance_idle", "block_hold" or "" for none. */
    public record BaseS2C(int entity, String clip) implements CustomPacketPayload {
        public static final Type<BaseS2C> TYPE = payloadType("base_pose");
        public static final StreamCodec<FriendlyByteBuf, BaseS2C> CODEC = CustomPacketPayload.codec(BaseS2C::write, BaseS2C::new);

        private BaseS2C(FriendlyByteBuf b) { this(b.readVarInt(), b.readUtf(64)); }

        private void write(FriendlyByteBuf b) {
            b.writeVarInt(entity);
            b.writeUtf(clip, 64);
        }

        @Override public Type<BaseS2C> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ActionC2S.TYPE, ActionC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ReleaseC2S.TYPE, ReleaseC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HeldC2S.TYPE, HeldC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StanceC2S.TYPE, StanceC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HitS2C.TYPE, HitS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AnimS2C.TYPE, AnimS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VfxS2C.TYPE, VfxS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StanceS2C.TYPE, StanceS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StateS2C.TYPE, StateS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RejectS2C.TYPE, RejectS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BaseS2C.TYPE, BaseS2C.CODEC);
    }

    private static void writeVec(FriendlyByteBuf b, Vec3 v) {
        b.writeBoolean(v != null);
        if (v == null) return;
        b.writeFloat((float) v.x());
        b.writeFloat((float) v.y());
        b.writeFloat((float) v.z());
    }

    private static Vec3 readVec(FriendlyByteBuf b) {
        if (!b.readBoolean()) return null;
        return new Vec3(b.readFloat(), b.readFloat(), b.readFloat());
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String emptyToNull(String s) { return s == null || s.isEmpty() ? null : s; }

    public static byte held(boolean block, boolean heavy, boolean sprint, boolean jump) {
        return (byte) ((block ? HELD_BLOCK : 0) | (heavy ? HELD_HEAVY : 0) | (sprint ? HELD_SPRINT : 0) | (jump ? HELD_JUMP : 0));
    }
}
