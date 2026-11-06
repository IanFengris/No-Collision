package de.vincent.nocollision;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

final class NoCollisionCommand implements TabExecutor {

    private static final String PREFIX = "§8[§bNoCollision§8] ";

    private final NoCollisionPlugin plugin;

    NoCollisionCommand(NoCollisionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(PREFIX + "§aConfiguration reloaded.");
            }
            case "status" -> {
                CollisionManager manager = plugin.getManager();
                sender.sendMessage(PREFIX + "§7Worlds without collision: §e" + String.join(", ", manager.getWorlds()));
                sender.sendMessage(PREFIX + "§7Mode: §e" + (manager.usesTab() ? "TAB (this server)" : "own scoreboard team")
                        + "§7, proxy TAB support: §e" + manager.isProxyTabSupport());
                if (sender instanceof Player player) {
                    sender.sendMessage(PREFIX + "§7Collision in your world (§e" + player.getWorld().getName() + "§7): "
                            + (manager.isCollisionDisabled(player) ? "§cdisabled" : "§aenabled"));
                }
            }
            default -> sender.sendMessage(PREFIX + "§7Usage: §e/" + label + " <reload|status>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return List.of("reload", "status").stream().filter(s -> s.startsWith(typed)).toList();
        }
        return List.of();
    }
}
