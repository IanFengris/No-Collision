package de.vincent.nocollision;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Decides for each player whether collision is disabled (by world) and applies it:
 * through TAB on this server if present, otherwise with an own scoreboard team,
 * and additionally sends the state to TAB on the Velocity proxy.
 */
public final class CollisionManager {

    public static final String TEAM_NAME = "nocollision";

    private final NoCollisionPlugin plugin;
    private final TabBackendHook tabHook;

    private final Set<String> worlds = new HashSet<>();
    private boolean proxyTabSupport;
    private boolean debug;

    /** Scoreboards our team was created on, so we can clean up everywhere. */
    private final Set<Scoreboard> touchedScoreboards = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<UUID, Boolean> lastProxyState = new HashMap<>();
    private final Map<UUID, Boolean> lastTabState = new HashMap<>();
    private final Set<UUID> warnedForeignTeam = new HashSet<>();
    private final Set<UUID> warnedNoChannel = new HashSet<>();
    private boolean tabFallback;

    public CollisionManager(NoCollisionPlugin plugin, TabBackendHook tabHook) {
        this.plugin = plugin;
        this.tabHook = tabHook;
    }

    public void loadConfig() {
        worlds.clear();
        List<String> list = plugin.getConfig().getStringList("worlds");
        for (String world : list) {
            worlds.add(world.toLowerCase(Locale.ROOT));
            if (Bukkit.getWorld(world) == null) {
                plugin.getLogger().warning("World '" + world + "' from config.yml is not loaded (yet). Check the name.");
            }
        }
        proxyTabSupport = plugin.getConfig().getBoolean("proxy-tab-support", true);
        debug = plugin.getConfig().getBoolean("debug", false);
        lastTabState.clear();
        warnedForeignTeam.clear();
    }

    public boolean isCollisionDisabled(Player player) {
        return worlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    public Set<String> getWorlds() {
        return Collections.unmodifiableSet(worlds);
    }

    public boolean usesTab() {
        return tabHook != null && !tabFallback;
    }

    public boolean isProxyTabSupport() {
        return proxyTabSupport;
    }

    /**
     * @param force re-send even if nothing changed (join, reload)
     */
    public void apply(Player player, boolean force) {
        boolean disabled = isCollisionDisabled(player);
        UUID uuid = player.getUniqueId();

        boolean handledByTab = false;
        if (usesTab()) {
            boolean changed = force || !Boolean.valueOf(disabled).equals(lastTabState.get(uuid));
            switch (tabHook.apply(player, disabled, changed)) {
                case APPLIED -> {
                    lastTabState.put(uuid, disabled);
                    handledByTab = true;
                }
                case NOT_LOADED -> handledByTab = true; // retried by the join tasks / resync task
                case UNAVAILABLE -> {
                    tabFallback = true;
                    plugin.getLogger().warning("TAB's scoreboard-teams feature is disabled - using own team instead.");
                }
            }
        }
        if (!handledByTab) {
            applyTeam(player, disabled);
        }

        if (proxyTabSupport) {
            sendToProxy(player, disabled, force);
        }
    }

    private void applyTeam(Player player, boolean disabled) {
        if (!disabled) {
            removeFromOurTeams(player.getName());
            return;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Scoreboard own = player.getScoreboard();
        addToTeam(player, own);
        if (own != main) {
            // Other players usually look at the main scoreboard.
            addToTeam(player, main);
        }
    }

    private void addToTeam(Player player, Scoreboard scoreboard) {
        String entry = player.getName();
        Team current = scoreboard.getEntryTeam(entry);
        if (current != null && !current.getName().equals(TEAM_NAME)) {
            if (warnedForeignTeam.add(player.getUniqueId())) {
                plugin.getLogger().warning(player.getName() + " is already in team '" + current.getName()
                        + "' of another plugin - not moving them (would break their prefix/nametag). "
                        + "Disable collision in that plugin instead.");
            }
            return;
        }

        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
        }
        if (team.getOption(Team.Option.COLLISION_RULE) != Team.OptionStatus.NEVER) {
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        touchedScoreboards.add(scoreboard);
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
            debug("Added " + entry + " to the no-collision team.");
        }
    }

    private void removeFromOurTeams(String entry) {
        for (Scoreboard scoreboard : touchedScoreboards) {
            Team team = scoreboard.getTeam(TEAM_NAME);
            if (team != null && team.hasEntry(entry)) {
                team.removeEntry(entry);
                debug("Removed " + entry + " from the no-collision team.");
            }
        }
    }

    private void sendToProxy(Player player, boolean disabled, boolean force) {
        UUID uuid = player.getUniqueId();
        if (!force && Boolean.valueOf(disabled).equals(lastProxyState.get(uuid))) {
            return;
        }
        if (!player.getListeningPluginChannels().contains(NoCollisionPlugin.CHANNEL)) {
            if (warnedNoChannel.add(uuid)) {
                plugin.getLogger().warning("Cannot send collision state of " + player.getName() + " to the proxy: channel "
                        + NoCollisionPlugin.CHANNEL + " is not registered (yet). Is NoCollision installed on the proxy? "
                        + "Registered channels: " + player.getListeningPluginChannels());
            }
            return;
        }
        player.sendPluginMessage(plugin, NoCollisionPlugin.CHANNEL, new byte[]{(byte) (disabled ? 1 : 0)});
        lastProxyState.put(uuid, disabled);
        debug("Sent collision " + (disabled ? "OFF" : "ON") + " for " + player.getName() + " to the proxy.");
    }

    public void forget(Player player) {
        UUID uuid = player.getUniqueId();
        lastProxyState.remove(uuid);
        lastTabState.remove(uuid);
        warnedForeignTeam.remove(uuid);
        warnedNoChannel.remove(uuid);
        removeFromOurTeams(player.getName());
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (usesTab()) {
                    tabHook.reset(player);
                }
                if (proxyTabSupport && player.getListeningPluginChannels().contains(NoCollisionPlugin.CHANNEL)) {
                    player.sendPluginMessage(plugin, NoCollisionPlugin.CHANNEL, new byte[]{0});
                }
            } catch (Throwable ignored) {
                // Server is shutting down.
            }
        }
        for (Scoreboard scoreboard : touchedScoreboards) {
            Team team = scoreboard.getTeam(TEAM_NAME);
            if (team != null) {
                try {
                    team.unregister();
                } catch (IllegalStateException ignored) {
                    // Already unregistered.
                }
            }
        }
        touchedScoreboards.clear();
    }

    private void debug(String message) {
        if (debug) {
            plugin.getLogger().info("[debug] " + message);
        }
    }
}
