package me.luisgamedev.elytriaEssentials.MMOCore;

import io.lumine.mythic.lib.message.PlayerMessage;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.social.Request;
import net.Indyuce.mmocore.manager.social.RequestManager;
import net.Indyuce.mmocore.party.provided.PartyInvite;
import net.Indyuce.mmocore.player.Message;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class PartyIntegrationManager {

    private static final String DEFAULT_INVITE_MESSAGE = "&6{player} &ehas invited you to their party.";
    private static final String DEFAULT_BUTTON_PREFIX = "";
    private static final String DEFAULT_ACCEPT_TEXT = "&8[&a&lACCEPT&8]";
    private static final String DEFAULT_ACCEPT_HOVER = "&eClick to accept!";
    private static final String DEFAULT_DENY_TEXT = "&8[&c&lDENY&8]";
    private static final String DEFAULT_DENY_HOVER = "&eClick to deny.";
    private static final String DEFAULT_SEPARATOR = "&r   ";

    private final ElytriaEssentials plugin;
    private final Set<UUID> processedInvites = new HashSet<>();
    private Field requestsField;
    private int taskId = -1;

    public PartyIntegrationManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        suppressDefaultInviteMessage();
        startInviteMonitor();
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        processedInvites.clear();
    }

    private void startInviteMonitor() {
        if (taskId != -1) {
            return;
        }
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::checkPendingInvites, 20L, 20L);
    }

    private void checkPendingInvites() {
        RequestManager requestManager = MMOCore.plugin != null ? MMOCore.plugin.requestManager : null;
        if (requestManager == null) {
            return;
        }

        Map<UUID, Request> requests = getRequestMap(requestManager);

        processedInvites.retainAll(requests.keySet());
        for (Request request : requests.values()) {
            if (!(request instanceof PartyInvite partyInvite)) {
                continue;
            }
            UUID requestId = partyInvite.getUniqueId();
            if (!processedInvites.add(requestId)) {
                continue;
            }
            sendInviteMessage(partyInvite);
        }
    }

    private Map<UUID, Request> getRequestMap(RequestManager manager) {
        try {
            if (requestsField == null) {
                requestsField = RequestManager.class.getDeclaredField("requests");
                requestsField.setAccessible(true);
            }
            @SuppressWarnings("unchecked")
            Map<UUID, Request> requests = (Map<UUID, Request>) requestsField.get(manager);
            return requests != null ? requests : Collections.emptyMap();
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to access MMOCore request registry", exception);
            return Collections.emptyMap();
        }
    }

    private void sendInviteMessage(PartyInvite invite) {
        PlayerData targetData = invite.getTarget();
        if (targetData == null || !targetData.isOnline()) {
            return;
        }
        Player target = targetData.getPlayer();
        if (target == null) {
            return;
        }

        String inviterName = invite.getCreator().getMMOPlayerData().getPlayerName();
        String prefix = plugin.getLanguageConfig().getString("prefix", "");
        String messageTemplate = plugin.getLanguageConfig().getString("party.invite.message", DEFAULT_INVITE_MESSAGE);
        String formattedMessage = ChatColor.translateAlternateColorCodes('&', prefix + messageTemplate.replace("{player}", inviterName));
        target.sendMessage(formattedMessage);

        TextComponent line = new TextComponent(ChatColor.translateAlternateColorCodes('&', plugin.getLanguageConfig().getString("party.invite.buttons-prefix", DEFAULT_BUTTON_PREFIX)));

        TextComponent accept = new TextComponent(ChatColor.translateAlternateColorCodes('&', plugin.getLanguageConfig().getString("party.invite.accept-text", DEFAULT_ACCEPT_TEXT)));
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept " + invite.getUniqueId()));
        BaseComponent[] acceptHover = TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', plugin.getLanguageConfig().getString("party.invite.accept-hover", DEFAULT_ACCEPT_HOVER)));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, acceptHover));
        line.addExtra(accept);

        line.addExtra(new TextComponent(ChatColor.translateAlternateColorCodes('&', plugin.getLanguageConfig().getString("party.invite.separator", DEFAULT_SEPARATOR))));

        TextComponent deny = new TextComponent(ChatColor.translateAlternateColorCodes('&', plugin.getLanguageConfig().getString("party.invite.deny-text", DEFAULT_DENY_TEXT)));
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party deny " + invite.getUniqueId()));
        BaseComponent[] denyHover = TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', plugin.getLanguageConfig().getString("party.invite.deny-hover", DEFAULT_DENY_HOVER)));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, denyHover));
        line.addExtra(deny);

        target.spigot().sendMessage(line);
    }

    private void suppressDefaultInviteMessage() {
        try {
            Field wrappedField = Message.class.getDeclaredField("wrapped");
            wrappedField.setAccessible(true);
            wrappedField.set(Message.PARTY_INVITE, PlayerMessage.fromConfig(""));
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to replace MMOCore party invite message", exception);
        }
    }
}
