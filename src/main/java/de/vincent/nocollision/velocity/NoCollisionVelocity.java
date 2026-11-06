package de.vincent.nocollision.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

/**
 * Velocity part: receives the collision state from the backends and applies it through TAB on the proxy.
 */
@Plugin(
        id = "nocollision",
        name = "NoCollision",
        version = "1.0.0",
        description = "Bridges NoCollision backend states to TAB on the proxy.",
        authors = {"Vincent"},
        dependencies = {@Dependency(id = "tab", optional = true)}
)
public final class NoCollisionVelocity {

    static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("nocollision", "sync");

    private final ProxyServer proxy;
    private final Logger logger;
    private TabProxyHook tabHook;

    @Inject
    public NoCollisionVelocity(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        if (proxy.getPluginManager().isLoaded("tab")) {
            try {
                tabHook = new TabProxyHook(this, proxy, logger);
                logger.info("TAB found - collision states from the backends will be applied through TAB.");
            } catch (Throwable t) {
                logger.warn("TAB found, but its API could not be used: {}", t.toString());
            }
        } else {
            logger.info("TAB is not installed on the proxy - nothing to do here (backends handle collision themselves).");
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        // Never forward this to the client or the backend.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        // Only trust messages from a backend server, not from players.
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }
        byte[] data = event.getData();
        if (data.length < 1) {
            return;
        }
        if (tabHook == null) {
            logger.warn("Got collision state for {} from {}, but TAB is not usable on the proxy.",
                    connection.getPlayer().getUsername(), connection.getServerInfo().getName());
            return;
        }
        tabHook.apply(connection.getPlayer().getUniqueId(), data[0] == 1);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        // Reset when switching servers; the new backend sends its own state right after joining.
        if (tabHook != null) {
            tabHook.apply(event.getPlayer().getUniqueId(), false);
        }
    }
}
