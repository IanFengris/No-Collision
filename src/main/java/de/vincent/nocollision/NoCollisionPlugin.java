package de.vincent.nocollision;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class NoCollisionPlugin extends JavaPlugin implements Listener {

    /** Plugin message channel to the Velocity part of this plugin. */
    public static final String CHANNEL = "nocollision:sync";

    private CollisionManager manager;
    private BukkitTask resyncTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);

        TabBackendHook tabHook = null;
        if (Bukkit.getPluginManager().isPluginEnabled("TAB")) {
            try {
                tabHook = new TabBackendHook();
                getLogger().info("TAB found on this server - collision is applied through TAB.");
            } catch (Throwable t) {
                getLogger().warning("TAB found, but its API could not be used (" + t + "). Using own team instead.");
            }
        }

        manager = new CollisionManager(this, tabHook);
        manager.loadConfig();
        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand command = getCommand("nocollision");
        if (command != null) {
            NoCollisionCommand executor = new NoCollisionCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        startResyncTask();
        applyAll(true);
    }

    @Override
    public void onDisable() {
        if (resyncTask != null) {
            resyncTask.cancel();
        }
        if (manager != null) {
            manager.shutdown();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public void reload() {
        reloadConfig();
        manager.loadConfig();
        startResyncTask();
        applyAll(true);
    }

    private void applyAll(boolean force) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            manager.apply(player, force);
        }
    }

    private void startResyncTask() {
        if (resyncTask != null) {
            resyncTask.cancel();
            resyncTask = null;
        }
        long seconds = getConfig().getLong("resync-interval-seconds", 10);
        if (seconds > 0) {
            long ticks = seconds * 20L;
            resyncTask = Bukkit.getScheduler().runTaskTimer(this, () -> applyAll(false), ticks, ticks);
        }
    }

    public CollisionManager getManager() {
        return manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Several delayed attempts: other plugins may set a scoreboard on join, TAB needs a moment
        // to load the player, and the proxy channel gets registered shortly after joining.
        for (long delay : new long[]{1L, 20L, 100L}) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    manager.apply(player, true);
                }
            }, delay);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.apply(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.forget(event.getPlayer());
    }
}
