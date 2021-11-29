package me.pulsi_.bankplus.guis;

import me.pulsi_.bankplus.BankPlus;
import me.pulsi_.bankplus.managers.EconomyManager;
import me.pulsi_.bankplus.utils.ChatUtils;
import me.pulsi_.bankplus.utils.ListUtils;
import me.pulsi_.bankplus.utils.Methods;
import me.pulsi_.bankplus.utils.SetUtils;
import me.pulsi_.bankplus.values.configs.ConfigValues;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public class GuiBankListener implements Listener {

    private final BankPlus plugin;
    private static BukkitTask runnable;
    public GuiBankListener(BankPlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void guiListener(InventoryClickEvent e) {

        Player p = (Player) e.getWhoClicked();

        if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof GuiBankHolder)) return;
        debug("&aBANKPLUS &8-> &3GUIBANK &8: &fEvent cancelled");
        e.setCancelled(true);

        for (String key : plugin.config().getConfigurationSection("Gui.Items").getKeys(false)) {
            ConfigurationSection items = plugin.config().getConfigurationSection("Gui.Items." + key);

            if (e.getSlot() + 1 != items.getInt("Slot") || items.getString("Action.Action-Type") == null) continue;
            debug("&aBANKPLUS &8-> &3GUIBANK &8: &fThe clicked slot was containing an action, waiting...");

            final String actionType = items.getString("Action.Action-Type").toLowerCase();
            final String actionAmount = items.getString("Action.Amount").toLowerCase();

            long amount;
            switch (actionType) {
                case "withdraw":
                    debug("&aBANKPLUS &8-> &3GUIBANK &8: &fAction Type: Withdraw");
                    switch (actionAmount) {
                        case "custom":
                            debug("&aBANKPLUS &8-> &3GUIBANK &8: &fWithdraw amount: CUSTOM, Processing...");
                            SetUtils.playerWithdrawing.add(p.getUniqueId());
                            if (ConfigValues.isTitleCustomAmountEnabled())
                                Methods.sendTitle("Title-Custom-Amount.Title-Withdraw", p, plugin);
                            messMan.chatWithdraw(p);
                            p.closeInventory();
                            break;

                        case "all":
                            debug("&aBANKPLUS &8-> &3GUIBANK &8: &fWithdraw amount: ALL, Processing...");
                            amount = EconomyManager.getBankBalance(p);
                            Methods.withdraw(p, amount, plugin);
                            break;

                        case "half":
                            debug("&aBANKPLUS &8-> &3GUIBANK &8: &fWithdraw amount: HALF, Processing...");
                            amount = EconomyManager.getBankBalance(p) / 2;
                            Methods.withdraw(p, amount, plugin);
                            break;

                        default:
                            try {
                                amount = Long.parseLong(actionAmount);
                                debug("&aBANKPLUS &8-> &3GUIBANK &8: &fWithdraw amount: " + amount + ", Processing...");
                                Methods.withdraw(p, amount, plugin);
                            } catch (NumberFormatException ex) {
                                debug("&aBANKPLUS &8-> &3GUIBANK &8: &cError! The Withdraw amount was containing an invalid number!");
                                ChatUtils.consoleMessage("&a&lBank&9&lPlus &cInvalid number in the withdraw amount!");
                            }
                            break;
                    }
                    break;

                case "deposit":
                    debug("&aBANKPLUS &8-> &3GUIBANK &8: &fAction Type: Deposit");
                    switch (actionAmount) {
                        case "custom":
                            debug("&aBANKPLUS &8-> &3GUIBANK &8: &fDeposit amount: CUSTOM, Processing...");
                            SetUtils.playerDepositing.add(p.getUniqueId());
                            if (ConfigValues.isTitleCustomAmountEnabled())
                                Methods.sendTitle("Title-Custom-Amount.Title-Deposit", p, plugin);
                            messMan.chatDeposit(p);
                            p.closeInventory();
                            break;

                        case "all":
                            debug("&aBANKPLUS &8-> &3GUIBANK &8: &fDeposit amount: ALL, Processing...");
                            amount = (long) plugin.getEconomy().getBalance(p);
                            Methods.deposit(p, amount, plugin);
                            break;

                        case "half":
                            debug("&aBANKPLUS &8-> &3GUIBANK &8: &fDeposit amount: HALF, Processing...");
                            amount = (long) (plugin.getEconomy().getBalance(p) / 2);
                            Methods.deposit(p, amount, plugin);
                            break;

                        default:
                            try {
                                amount = Long.parseLong(actionAmount);
                                debug("&aBANKPLUS &8-> &3GUIBANK &8: &fDeposit amount: " + amount + ", Processing...");
                                Methods.deposit(p, amount, plugin);
                                break;
                            } catch (NumberFormatException ex) {
                                debug("&aBANKPLUS &8-> &3GUIBANK &8: &cError! The Deposit amount was containing an invalid number!");
                                ChatUtils.consoleMessage("&a&lBank&9&lPlus &cInvalid number in the deposit amount!");
                            }
                            break;
                    }
                    break;
            }
        }
    }

    @EventHandler
    public void updateGui(InventoryOpenEvent e) {
        Player p = (Player) e.getPlayer();
        int delay = ConfigValues.getGuiUpdateDelay();
        if (delay != 0) runnable = Bukkit.getScheduler().runTaskTimer(plugin, () -> updateLore(p),0, delay * 20L);
    }

    @EventHandler
    public void closeGUI(InventoryCloseEvent e) {
        if (runnable != null) runnable.cancel();
    }

    private void debug(String message) {
        if (ListUtils.GUIBANK_DEBUG.get(0).equals("ENABLED")) ChatUtils.consoleMessage(message);
    }


    private void updateLore(Player p) {
        Inventory inventory = p.getOpenInventory().getTopInventory();
        if (!(inventory.getHolder() instanceof GuiBankHolder)) return;

        ConfigurationSection c = plugin.config().getConfigurationSection("Gui.Items");
        for (String items : c.getKeys(false)) {
            ItemStack i = inventory.getItem(c.getConfigurationSection(items).getInt("Slot") - 1);
            if (i != null && i.hasItemMeta())
                i.setItemMeta(ItemUtils.setLore(c.getConfigurationSection(items), i, p));
        }
    }
}