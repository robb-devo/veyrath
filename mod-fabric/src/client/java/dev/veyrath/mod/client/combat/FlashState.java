package dev.veyrath.mod.client.combat;

/** The combat hit flash carried on a living entity's render state (added by a mixin on LivingEntityRenderState). */
public interface FlashState {
    float veyrath$flash();

    void veyrath$setFlash(float flash);

    /** Weapon id drawn in the hands ("unarmed" or null draws nothing). */
    String veyrath$weapon();

    void veyrath$setWeapon(String weapon);
}
