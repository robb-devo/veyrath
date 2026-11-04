package dev.veyrath.mod.client.mixin;

import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No vanilla music, ever: VEYRATH's own score (client.audio.VeyrathMusic) owns the music channel. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void veyrath$noVanillaMusic(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void veyrath$noVanillaStart(Music music, CallbackInfo ci) {
        ci.cancel();
    }
}
