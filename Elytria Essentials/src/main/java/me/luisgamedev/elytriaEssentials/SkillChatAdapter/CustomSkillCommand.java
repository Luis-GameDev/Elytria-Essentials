package me.luisgamedev.elytriaEssentials.SkillChatAdapter;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import mineverse.Aust1n46.chat.proxy.VentureChatBungee;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.manager.InventoryManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CustomSkillCommand implements CommandExecutor {
    private final ElytriaEssentials plugin;

    public CustomSkillCommand(ElytriaEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage("§cThis command is console only.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /vcadap <player> <message>");
            return true;
        }

        Player caster = org.bukkit.Bukkit.getPlayerExact(args[0]);
        if (caster == null) {
            sender.sendMessage("Player not found.");
            return true;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        mineverse.Aust1n46.chat.channel.ChatChannel channel =
                mineverse.Aust1n46.chat.channel.ChatChannel.getChannel("Skills");

        if (channel == null) {
            sender.sendMessage("Skills channel not found.");
            return true;
        }

        double maxDistance = channel.getDistance();
        String formattedMessage = channel.getPrefix() + " " + message;
        formattedMessage = org.bukkit.ChatColor.translateAlternateColorCodes('&', formattedMessage);

        caster.sendMessage(formattedMessage);

        for (mineverse.Aust1n46.chat.api.MineverseChatPlayer mcp :
                mineverse.Aust1n46.chat.api.MineverseChatAPI.getOnlineMineverseChatPlayers()) {

            if (mcp == null || !mcp.isOnline() || mcp.getPlayer() == null) {
                continue;
            }

            Player target = mcp.getPlayer();

            if (target.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }

            if (!mcp.isListening(channel.getName())) {
                continue;
            }

            if (!target.getWorld().equals(caster.getWorld())) {
                continue;
            }

            if (maxDistance > 0 &&
                    target.getLocation().distanceSquared(caster.getLocation()) > (maxDistance * maxDistance)) {
                continue;
            }

            target.sendMessage(formattedMessage);
        }

        return true;
    }
}
