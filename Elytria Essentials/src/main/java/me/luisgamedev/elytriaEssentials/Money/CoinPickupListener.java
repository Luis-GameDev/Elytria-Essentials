package me.luisgamedev.elytriaEssentials.Money;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.party.AbstractParty;
import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class CoinPickupListener implements Listener {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#0.##");

    static {
        DECIMAL_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }

    private final Economy economy;
    private final boolean mmocoreEnabled;

    public CoinPickupListener(ElytriaEssentials plugin) {
        this.economy = plugin.getEconomy();
        this.mmocoreEnabled = Bukkit.getPluginManager().isPluginEnabled("MMOCore");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        if (economy == null) {
            return;
        }

        ItemStack itemStack = event.getItem().getItemStack();
        if (itemStack.getType() != Material.GOLD_NUGGET && itemStack.getType() != Material.GOLD_INGOT) {
            return;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) {
            return;
        }

        String displayName = ChatColor.stripColor(meta.getDisplayName());
        if (displayName == null || !displayName.trim().equalsIgnoreCase("coin")) {
            return;
        }

        double moneyPerItem = extractMoneyValue(meta.getLore());
        if (moneyPerItem <= 0) {
            return;
        }

        int amount = itemStack.getAmount();
        double totalValue = moneyPerItem * amount;
        double spread = ThreadLocalRandom.current().nextDouble(0.9, 1.1);
        totalValue *= spread;
        if (totalValue <= 0) {
            return;
        }

        event.setCancelled(true);
        event.getItem().remove();

        distributeCoins(player, totalValue);
    }

    private double extractMoneyValue(List<String> lore) {
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line);
            if (stripped == null) {
                continue;
            }
            stripped = stripped.trim();
            if (!stripped.toLowerCase(Locale.ROOT).startsWith("money=")) {
                continue;
            }

            String valuePart = stripped.substring("money=".length()).trim();
            valuePart = valuePart.replace(',', '.');
            if (valuePart.isEmpty()) {
                continue;
            }

            try {
                return Double.parseDouble(valuePart);
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return 0D;
    }

    private void distributeCoins(Player picker, double totalValue) {
        List<Player> recipients = resolveRecipients(picker);
        if (recipients.isEmpty()) {
            recipients = Collections.singletonList(picker);
        }

        BigDecimal total = BigDecimal.valueOf(totalValue);
        int recipientCount = recipients.size();
        BigDecimal count = BigDecimal.valueOf(recipientCount);
        BigDecimal baseShare = total.divide(count, 2, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(baseShare.multiply(count));
        int extraPennies = remainder.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();

        BigDecimal penny = BigDecimal.valueOf(0.01);
        for (int i = 0; i < recipientCount; i++) {
            Player recipient = recipients.get(i);
            BigDecimal payout = baseShare;
            if (extraPennies > 0) {
                payout = payout.add(penny);
                extraPennies--;
            }

            double amount = payout.doubleValue();
            if (amount <= 0) {
                continue;
            }

            economy.depositPlayer(recipient, amount);
            sendPickupMessage(recipient, amount);
        }
    }

    private List<Player> resolveRecipients(Player picker) {
        if (!mmocoreEnabled) {
            return new ArrayList<>();
        }

        PlayerData playerData = PlayerData.get(picker);
        if (playerData == null) {
            return new ArrayList<>();
        }

        AbstractParty party = playerData.getParty();
        if (party == null || party.countMembers() <= 1) {
            return new ArrayList<>();
        }

        List<Player> players = new ArrayList<>();
        for (PlayerData memberData : party.getOnlineMembers()) {
            Player member = memberData.getPlayer();
            if (member != null && member.isOnline()) {
                players.add(member);
            }
        }

        if (!players.contains(picker)) {
            players.add(picker);
        }

        return players;
    }

    private void sendPickupMessage(Player player, double amount) {
        String formatted = DECIMAL_FORMAT.format(amount);
        player.sendActionBar(Component.text("+ " + formatted, NamedTextColor.GOLD));
    }
}
