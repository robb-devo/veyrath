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
        CombatSettings.load();
        dev.veyrath.mod.client.vfx.Vfx.init();
        dev.veyrath.mod.client.vfx.AmbientVfx.init();
        // The drawn weapon on every humanoid renderer (players, the Duelist).
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>) registerWeaponLayer(renderer, helper);
        });
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
            DamageNumbers.clear();
            ScreenFx.clear();
            HitFlash.clear();
            dev.veyrath.mod.client.vfx.Vfx.clear();
            ClientWeapons.clear();
            ActionCamera.reset();
        });

        ClientPlayNetworking.registerGlobalReceiver(CombatNet.HitS2C.TYPE, (msg, ctx) -> onHit(ctx.client(), msg));
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.AnimS2C.TYPE, (msg, ctx) -> onAnim(ctx.client(), msg));
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.StanceS2C.TYPE, (msg, ctx) -> {
            Animator a = CombatAnimations.remote(msg.entity());
            a.setBase(msg.on() ? "stance_idle" : null, a.clock, 4);
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.WeaponS2C.TYPE, (msg, ctx) -> {
            var mc = ctx.client();
            if (mc.player != null && msg.entity() == mc.player.getId()) LocalCombat.weaponFromServer(msg.weapon());
            else {
                ClientWeapons.set(msg.entity(), msg.weapon());
                var w = dev.veyrath.combat.content.Weapons.byId(msg.weapon());
                if (w != null) {
                    Animator a = CombatAnimations.remote(msg.entity());
                    a.setBase(w.stanceClip(), a.clock, 4);
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.VfxS2C.TYPE, (msg, ctx) -> {
            if (ctx.client().level != null) CombatEffects.play(ctx.client().level.getRandom(), msg.id(), new Vec3(msg.x(), msg.y(), msg.z()), msg.yaw());
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.StateS2C.TYPE, (msg, ctx) -> LocalCombat.state(msg));
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.RejectS2C.TYPE, (msg, ctx) -> LocalCombat.rejected(msg.seq()));
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.BaseS2C.TYPE, (msg, ctx) -> {
            Animator a = CombatAnimations.remote(msg.entity());
            a.setBase(msg.clip().isEmpty() ? null : msg.clip(), a.clock, 3);
        });

        // Our HUD (client.hud.VeyrathHud) replaces vanilla's; flashes and the slow-motion vignette
        // sit under everything else, damage numbers included.
        dev.veyrath.mod.client.hud.VeyrathHud.init();
        HudElementRegistry.addFirst(VeyrathMod.id("damage_numbers"), DamageNumbers::extract);
        HudElementRegistry.addFirst(VeyrathMod.id("screen_fx"), ScreenFx::extract);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerWeaponLayer(net.minecraft.client.renderer.entity.LivingEntityRenderer renderer,
                                            net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.RegistrationHelper helper) {
        helper.register(new WeaponLayer(renderer));
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
        // Predicted hits showed their accents already; other outcomes (parry, guard break, dodge) show now.
        if ((byMe || onMe) && (!predicted || o.result() != Outcome.Result.HIT)) ScreenFx.onOutcome(o, o.vfx(), byMe);
        if (o.result() == Outcome.Result.HIT || o.result() == Outcome.Result.ARMORED || o.result() == Outcome.Result.GUARD_BREAK) HitFlash.mark(h.target(), o.hitstop());
        if (byMe || onMe) DamageNumbers.add(h.target(), at, o, byMe);
        if (byMe || onMe) dev.veyrath.mod.client.audio.VeyrathMusic.combatHit(byMe ? h.target() : h.attacker(), byMe);

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
