package dev.veyrath.mod.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.gen.VeyrathChunkGenerator;
import net.minecraft.server.level.ServerLevel;

/**
 * Distant Horizons integration: replaces DH's chunk-based LOD generation with LODs built
 * directly from the VEYRATH engine (see {@link VeyrathLodGenerator}).
 *
 * <p>Written against the DH API 7.2.0 sources (DH 3.3.4, tag {@code 3.3.4} of
 * gitlab.com/distant-horizons-team/distant-horizons-core). Only this package references DH
 * classes; {@code VeyrathMod} loads it reflectively and only when DH is installed, and the build
 * can leave the package out entirely with {@code -Pveyrath.dh=false}.
 */
public final class DhIntegration {
    private DhIntegration() {}

    public static void init() {
        VeyrathMod.LOG.info("Distant Horizons {} (API {}.{}) detected; registering VEYRATH LOD generator hook",
                DhApi.getModVersion(), DhApi.getApiMajorVersion(), DhApi.getApiMinorVersion());
        DhApiEventRegister.on(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent() {
            @Override
            public void onLevelLoad(DhApiEventParam<EventParam> event) {
                IDhApiLevelWrapper level = event.value.levelWrapper;
                Object mc = level.getWrappedMcObject();
                // World generation only happens for server-side levels (also in singleplayer).
                if (!(mc instanceof ServerLevel serverLevel)) {
                    VeyrathMod.LOG.debug("DH level {} is not a server level ({}); no generator override", level.getDimensionName(),
                            mc == null ? "null" : mc.getClass().getName());
                    return;
                }
                if (!(serverLevel.getChunkSource().getGenerator() instanceof VeyrathChunkGenerator)) return;
                try {
                    DhApiResult<Void> result = DhApi.worldGenOverrides.registerWorldGeneratorOverride(level, new VeyrathLodGenerator(level));
                    if (result.success) {
                        VeyrathMod.LOG.info("VEYRATH LOD generator registered for {}", level.getDimensionName());
                    } else {
                        VeyrathMod.LOG.warn("Distant Horizons refused the VEYRATH LOD generator for {}: {}", level.getDimensionName(), result.message);
                    }
                } catch (Throwable t) {
                    VeyrathMod.LOG.error("Could not register the VEYRATH LOD generator; DH falls back to generating chunks", t);
                }
            }
        });
    }
}
