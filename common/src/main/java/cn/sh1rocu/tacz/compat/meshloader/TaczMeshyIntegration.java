package cn.sh1rocu.tacz.compat.meshloader;

import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;
import com.tacz.guns.platform.IdentifiableReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.server.packs.resources.PreparableReloadListener.SharedState;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * TacZ Mesh Loader 整合入口。
 *
 * <p>Loaders reach it through {@code ClientSetupEvent}: {@link #onClientSetup()} from
 * {@code registerItemModelTypes()} (before the first client resource load) and
 * {@link #parseCacheReloadListener()} from {@code registerClientReloadListeners}.
 * The screen extract window that {@code ScreenRenderTracker} needs is bracketed by a mixin,
 * so nothing here is registered per loader.</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public final class TaczMeshyIntegration {

    private static final Identifier PARSE_CACHE_LISTENER_ID = Identifier.fromNamespaceAndPath("tacz", "poly_mesh_parse_cache");

    private TaczMeshyIntegration() {
    }

    /**
     * Registers gun {@code model_type: "mesh"}. Gun displays pick their model class by type while
     * they load, so this must run before the client first loads its resources.
     */
    public static void onClientSetup() {
        TaczPolyMeshGunModel.register();
    }

    /**
     * Drops the geo JSON parse cache once a client resource reload has applied. Register it after
     * TACZ's client asset listeners, matching the order the fork used on Fabric.
     */
    public static IdentifiableReloadListener parseCacheReloadListener() {
        return new IdentifiableReloadListener() {
            @Override
            public Identifier getReloadListenerId() {
                return PARSE_CACHE_LISTENER_ID;
            }

            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor,
                                                  PreparationBarrier barrier, Executor gameExecutor) {
                return barrier.wait(null).thenRunAsync(PolyMeshSupport::invalidateParseCache, gameExecutor);
            }
        };
    }
}
