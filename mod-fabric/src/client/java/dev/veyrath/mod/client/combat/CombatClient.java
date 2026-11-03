package dev.veyrath.mod.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.combat.CombatNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/** Client wiring of combat: the stance key, ticks, server messages and the HUD. */
public final class CombatClient {
    private CombatClient() {}

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(VeyrathMod.id("combat"));
    public static KeyMapping STANCE_KEY;

    public static void init() {
        STANCE_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.veyrath.stance", InputConstants.KEY_Z, CATEGORY));

        ClientTickEvents.START_CLIENT_TICK.register(LocalCombat::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            LocalCombat.endTick(mc);
            if (mc.level != null && !mc.isPaused()) CombatAnimations.tick(mc.level);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            LocalCombat.reset();
            CombatAnimations.clear();
        });

        ClientPlayNetworking.registerGlobalReceiver(CombatNet.HitS2C.TYPE, (msg, ctx) -> onHit(ctx.client(), msg));
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.AnimS2C.TYPE, (msg, ctx) -> onAnim(ctx.client(), msg));
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.StanceS2C.TYPE, (msg, ctx) -> {
            Animator a = CombatAnimations.remote(msg.entity());
            a.setBase(msg.on() ? "stance_idle" : null, a.clock, 4);
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.StateS2C.TYPE, (msg, ctx) -> LocalCombat.state(msg));
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.RejectS2C.TYPE, (msg, ctx) -> LocalCombat.rejected(msg.seq()));

        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VeyrathMod.id("combat"), CombatHud::extract);
        // The crosshair means nothing with a free camera: the character attacks where it faces.
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
            if (!LocalCombat.active()) vanilla.extractRenderState(g, delta);
        });
    }

    private static void onHit(Minecraft mc, CombatNet.HitS2C h) {
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null) return;
        Outcome o = h.outcome();
        boolean byMe = h.attacker() == p.getId(), onMe = h.target() == p.getId();
        Vec3 at = new Vec3(h.px(), h.py(), h.pz());
        boolean predicted = byMe && LocalCombat.consumePrediction(LocalCombat.key(h.move(), h.window(), h.target()));
        // A prediction already showed the plain impact; a different outcome (block, parry) still shows.
        if (!predicted || o.result() != Outcome.Result.HIT) {
            var attacker = level.getEntity(h.attacker());
            CombatEffects.impact(level, o.vfx(), at, attacker == null ? h.defenderYaw() + 180 : attacker.getYRot());
            if (byMe) CombatEffects.sound(level, o.sfx(), at);
        }
        if (byMe || onMe) ActionCamera.addTrauma(predicted ? 0 : o.shake());

        if (onMe) {
            if (LocalCombat.active()) LocalCombat.struck(o);
            else if (o.velocity() != null) p.setDeltaMovement(o.velocity().x(), o.velocity().y(), o.velocity().z());
        } else {
            Animator a = CombatAnimations.remote(h.target());
            a.freeze(o.hitstop());
            a.faceOverride = true;
            a.yaw = h.defenderYaw();
        }
        if (byMe) {
            LocalCombat.attackResolved(o);
        } else {
            Animator a = CombatAnimations.find(h.attacker());
            if (a != null && o.result() != Outcome.Result.PARRIED) a.freeze(o.hitstop());
        }
    }

    private static void onAnim(Minecraft mc, CombatNet.AnimS2C msg) {
        if (mc.player != null && msg.entity() == mc.player.getId() && LocalCombat.active()) return; // predicted locally
        Animator a = CombatAnimations.remote(msg.entity());
        a.play(msg.clip(), a.clock, msg.fade());
        if (msg.clip().isEmpty()) a.faceOverride = false;
    }
}
