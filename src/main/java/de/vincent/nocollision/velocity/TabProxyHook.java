package de.vincent.nocollision.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Only loaded when TAB is installed on the proxy. */
final class TabProxyHook {

    private static final int MAX_ATTEMPTS = 20;

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    /** Latest wanted state per player, so delayed retries never apply an outdated value. */
    private final Map<UUID, Boolean> wanted = new ConcurrentHashMap<>();
    private volatile boolean warnedNoTeams;

    TabProxyHook(Object plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        TabAPI.getInstance(); // fail fast if the API is missing
    }

    void apply(UUID uuid, boolean disabled) {
        wanted.put(uuid, disabled);
        tryApply(uuid, 0);
    }

    private void tryApply(UUID uuid, int attempt) {
        Boolean disabled = wanted.get(uuid);
        if (disabled == null) {
            return;
        }
        if (proxy.getPlayer(uuid).isEmpty()) {
            wanted.remove(uuid);
            return;
        }
        NameTagManager nameTags = TabAPI.getInstance().getNameTagManager();
        if (nameTags == null) {
            if (!warnedNoTeams) {
                warnedNoTeams = true;
                logger.warn("TAB's scoreboard-teams feature is disabled on the proxy - cannot change collision through TAB.");
            }
            return;
        }
        TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(uuid);
        if (tabPlayer == null || !tabPlayer.isLoaded()) {
            if (attempt < MAX_ATTEMPTS) {
                proxy.getScheduler().buildTask(plugin, () -> tryApply(uuid, attempt + 1))
                        .delay(250, TimeUnit.MILLISECONDS)
                        .schedule();
            }
            return;
        }
        // false = no collision, null = back to TAB's configured default.
        nameTags.setCollisionRule(tabPlayer, disabled ? Boolean.FALSE : null);
    }
}
