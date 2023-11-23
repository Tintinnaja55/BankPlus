package me.pulsi_.bankplus.economy;

import me.pulsi_.bankplus.BankPlus;
import me.pulsi_.bankplus.account.BPPlayerManager;
import me.pulsi_.bankplus.bankSystem.BankGuiRegistry;
import me.pulsi_.bankplus.bankSystem.BankReader;
import me.pulsi_.bankplus.events.BPAfterTransactionEvent;
import me.pulsi_.bankplus.events.BPPreTransactionEvent;
import me.pulsi_.bankplus.mySQL.BPSQL;
import me.pulsi_.bankplus.mySQL.SQLPlayerManager;
import me.pulsi_.bankplus.utils.BPFormatter;
import me.pulsi_.bankplus.utils.BPLogger;
import me.pulsi_.bankplus.utils.BPMessages;
import me.pulsi_.bankplus.utils.BPUtils;
import me.pulsi_.bankplus.values.Values;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class BPEconomy {

    private final Map<UUID, HashMap<String, BankBalance>> playerBalances = new HashMap<>();

    private final BankGuiRegistry banksRegistry;

    public BPEconomy() {
        banksRegistry = BankPlus.INSTANCE.getBankGuiRegistry();
    }

    /**
     * Return a list with all player balances.
     *
     * @return A hashmap with the player name as key and the sum of all the player bank balances as value.
     */
    public LinkedHashMap<String, BigDecimal> getAllBankBalances() {
        LinkedHashMap<String, BigDecimal> balances = new LinkedHashMap<>();

        File dataFolder = new File(BankPlus.INSTANCE.getDataFolder(), "playerdata");
        File[] files = dataFolder.listFiles();
        if (files == null) return balances;

        for (File file : files) {
            FileConfiguration config = new YamlConfiguration();

            try {
                config.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                BPLogger.error(e, "An error has occurred while loading a user file (File name: " + file.getName() + "):");
                continue;
            }

            String name = config.getString("name");
            if (name == null) continue;

            ConfigurationSection section = config.getConfigurationSection("banks");
            BigDecimal balance = new BigDecimal(0);

            if (section != null) {
                for (String bankName : section.getKeys(false)) {
                    String bal = config.getString("banks." + bankName + ".money");
                    if (bal != null && !BPUtils.isInvalidNumber(bal))
                        balance = balance.add(new BigDecimal(bal));
                }
            }
            balances.put(name, balance);
        }
        return balances;
    }

    /**
     * Unload the player bank balances from the server.
     */
    public void unloadBankBalance(Player p) {
        unloadBankBalance(p.getUniqueId());
    }

    /**
     * Unload the player bank balances from the server.
     */
    public void unloadBankBalance(UUID playerUUID) {
        playerBalances.remove(playerUUID);
    }

    /**
     * Load the player balance from the connected database.
     * @param p The player.
     */
    public void loadBankBalanceFromDatabase(Player p) {
        if (p == null) return;

        HashMap<String, BankBalance> balances = new HashMap<>();
        // If the player is already loaded, put the loaded map in the "balances" map.
        if (playerBalances.containsKey(p.getUniqueId())) balances = playerBalances.get(p.getUniqueId());

        boolean changes = false;
        SQLPlayerManager pManager = new SQLPlayerManager(p);
        for (String bankName : banksRegistry.getBanks().keySet()) {
            if (balances.containsKey(bankName)) continue;

            balances.put(bankName, new BankBalance(pManager.getMoney(bankName), pManager.getDebt(bankName)));
            changes = true;
        }
        if (changes) playerBalances.put(p.getUniqueId(), balances);
    }

    /**
     * Load the player balance from the playerdata folder.
     * @param p The player.
     * @param config Player's config.
     */
    public void loadBankBalance(Player p, FileConfiguration config) {
        if (p == null) return;

        HashMap<String, BankBalance> balances = new HashMap<>();
        // If the player is already loaded, put the loaded map in the "balances" map.
        if (playerBalances.containsKey(p.getUniqueId())) balances = playerBalances.get(p.getUniqueId());

        boolean changes = false;
        for (String bankName : banksRegistry.getBanks().keySet()) {
            // If the "balances" map already contains the bank values, skip.
            if (balances.containsKey(bankName)) continue;

            String bal = config.getString("banks." + bankName + ".money"), debt = config.getString("banks." + bankName + ".debt");
            BigDecimal balAmount = new BigDecimal(0), debtAmount = new BigDecimal(0);

            if (bal != null) {
                try {
                    balAmount = new BigDecimal(bal);
                } catch (NumberFormatException e) {
                    BPLogger.warn("Could not get \"" + bankName + "\" bank balance for " + p.getName() + " because it contains an invalid number! (Using 0 as default)");
                }
            }
            if (debt != null) {
                try {
                    debtAmount = new BigDecimal(debt);
                } catch (NumberFormatException e) {
                    BPLogger.warn("Could not get \"" + bankName + "\" bank debt for " + p.getName() + " because it contains an invalid number! (Using 0 as default)");
                }
            }
            balances.put(bankName, new BankBalance(balAmount, debtAmount));
            changes = true;
        }
        // If any changes have been made, update the hashmap with the new edits.
        if (changes) playerBalances.put(p.getUniqueId(), balances);
    }

    /**
     * Save all bank balances of the player.
     */
    public void saveBankBalances(Player p, boolean async) {
        if (Values.CONFIG.isSqlEnabled() && BankPlus.INSTANCE.getSql().isConnected()) {
            SQLPlayerManager pManager = new SQLPlayerManager(p);
            for (String bankName : banksRegistry.getBanks().keySet())
                pManager.saveBankBalance(getBankBalance(p, bankName), bankName);
            return;
        }

        BPPlayerManager pManager = new BPPlayerManager(p);
        File file = pManager.getPlayerFile();
        FileConfiguration config = pManager.getPlayerConfig(file);

        for (String bankName : banksRegistry.getBanks().keySet())
            config.set("banks." + bankName + ".money", BPFormatter.formatBigDouble(getBankBalance(p, bankName)));

        pManager.savePlayerFile(config, file, async);
    }

    /**
     * Get the player bank balance of the selected bank.
     * @param uuid The UUID of the player.
     * @param bankName The bank where to get the balance.
     */
    public BigDecimal getBankBalance(UUID uuid, String bankName) {
        return getBankBalance(Bukkit.getOfflinePlayer(uuid), bankName);
    }

    /**
     * Get the player bank balance of the selected bank.
     * @param p The player.
     * @param bankName The bank where to get the balance.
     */
    public BigDecimal getBankBalance(OfflinePlayer p, String bankName) {
        if (!playerBalances.containsKey(p.getUniqueId())) {
            if (Values.CONFIG.isSqlEnabled() && BankPlus.INSTANCE.getSql().isConnected())
                return new SQLPlayerManager(p).getMoney(bankName);

            String bal = new BPPlayerManager(p).getPlayerConfig().getString("banks." + bankName + ".money");
            return new BigDecimal(bal == null ? "0" : bal);
        }
        return playerBalances.get(p.getUniqueId()).get(bankName).getBankBalance();
    }

    /**
     * Get the sum of the player bank balance of all banks.
     * @param p The player.
     */
    public BigDecimal getBankBalance(OfflinePlayer p) {
        BigDecimal amount = new BigDecimal(0);
        for (String bankName : banksRegistry.getBanks().keySet()) amount = amount.add(getBankBalance(p, bankName));
        return amount;
    }

    /**
     * Set the selected amount in the selected bank.
     *
     * @return Number representing the actual amount set.
     */
    public BigDecimal setBankBalance(OfflinePlayer p, BigDecimal amount, String bankName) {
        return setBankBalance(p, amount, bankName, TransactionType.SET);
    }

    /**
     * Set the selected amount in the selected bank.
     *
     * @param ignoreEvents Choose if ignoring or not the bankplus transaction event.
     * @return Number representing the actual amount set.
     */
    public BigDecimal setBankBalance(OfflinePlayer p, BigDecimal amount, String bankName, boolean ignoreEvents) {
        return setBankBalance(p, amount, bankName, ignoreEvents, TransactionType.SET);
    }

    /**
     * Set the selected amount in the selected bank.
     *
     * @param type Override the transaction type with the one you choose.
     * @return Number representing the actual amount set.
     */
    public BigDecimal setBankBalance(OfflinePlayer p, BigDecimal amount, String bankName, TransactionType type) {
        return setBankBalance(p, amount, bankName, false, type);
    }

    private BigDecimal setBankBalance(OfflinePlayer p, BigDecimal amount, String bankName, boolean ignoreEvents, TransactionType type) {
        Economy economy = BankPlus.INSTANCE.getVaultEconomy();
        BigDecimal result = new BigDecimal(0);

        if (!ignoreEvents) {
            BPPreTransactionEvent event = startEvent(p, type, economy.getBalance(p), amount, bankName);
            if (event.isCancelled()) return result;

            amount = event.getTransactionAmount();
        }

        result = result.max(amount.min(new BankReader(bankName).getCapacity(p)));
        set(p, result, bankName);

        if (!ignoreEvents) endEvent(p, type, economy.getBalance(p), amount, bankName);
        return result;
    }

    /**
     * Add the selected amount to the selected bank.
     *
     * @return Number representing the actual amount added.
     */
    public BigDecimal addBankBalance(OfflinePlayer p, BigDecimal amount, String bankName) {
        return addBankBalance(p, amount, bankName, false, TransactionType.ADD, false);
    }

    /**
     * Add the selected amount to the selected bank.
     *
     * @param ignoreEvents Choose if ignoring or not the bankplus transaction event.
     * @return Number representing the actual amount added.
     */
    public BigDecimal addBankBalance(OfflinePlayer p, BigDecimal amount, String bankName, boolean ignoreEvents) {
        return addBankBalance(p, amount, bankName, ignoreEvents, TransactionType.ADD, false);
    }

    /**
     * Add the selected amount to the selected bank.
     *
     * @param type Override the transaction type with the one you choose.
     * @return Number representing the actual amount added.
     */
    public BigDecimal addBankBalance(OfflinePlayer p, BigDecimal amount, String bankName, TransactionType type) {
        return addBankBalance(p, amount, bankName, false, type, false);
    }

    /**
     * Add the selected amount to the selected bank.
     *
     * @param type Override the transaction type with the one you choose.
     * @param addOfflineInterest Choose if updating the offline interest with this transaction.
     * @return Number representing the actual amount added.
     */
    public BigDecimal addBankBalance(OfflinePlayer p, BigDecimal amount, String bankName, TransactionType type, boolean addOfflineInterest) {
        return addBankBalance(p, amount, bankName, false, type, addOfflineInterest);
    }

    private BigDecimal addBankBalance(OfflinePlayer p, BigDecimal amount, String bankName, boolean ignoreEvents, TransactionType type, boolean addOfflineInterest) {
        Economy economy = BankPlus.INSTANCE.getVaultEconomy();
        BigDecimal result = new BigDecimal(0);

        if (!ignoreEvents) {
            BPPreTransactionEvent event = startEvent(p, type, economy.getBalance(p), amount, bankName);
            if (event.isCancelled()) return result;

            amount = event.getTransactionAmount();
        }

        BigDecimal capacity = new BankReader(bankName).getCapacity(p), balance = getBankBalance(p, bankName);
        if (capacity.doubleValue() <= 0D || balance.add(amount).doubleValue() < capacity.doubleValue()) {
            result = amount;
            BigDecimal moneyToAdd = balance.add(result);
            if (addOfflineInterest) set(p, moneyToAdd, moneyToAdd, bankName);
            else set(p, balance.add(result), bankName);
        } else {
            result = capacity.subtract(balance);

            if (addOfflineInterest) set(p, capacity, result, bankName);
            else set(p, capacity, bankName);
        }

        if (!ignoreEvents) endEvent(p, type, economy.getBalance(p), amount, bankName);
        return result;
    }

    /**
     * Remove the selected amount.
     *
     * @return Number representing the actual amount removed.
     */
    public BigDecimal removeBankBalance(OfflinePlayer p, BigDecimal amount, String bankName) {
        return removeBankBalance(p, amount, bankName, TransactionType.REMOVE);
    }

    /**
     * Remove the selected amount.
     *
     * @param ignoreEvents Choose if ignoring or not the bankplus transaction event.
     * @return Number representing the actual amount removed.
     */
    public BigDecimal removeBankBalance(OfflinePlayer p, BigDecimal amount, String bankName, boolean ignoreEvents) {
        return removeBankBalance(p, amount, bankName, ignoreEvents, TransactionType.REMOVE);
    }

    /**
     * Remove the selected amount.
     *
     * @param type Override the transaction type with the one you choose.
     * @return Number representing the actual amount removed.
     */
    public BigDecimal removeBankBalance(OfflinePlayer p, BigDecimal amount, String bankName, TransactionType type) {
        return removeBankBalance(p, amount, bankName, false, type);
    }

    private BigDecimal removeBankBalance(OfflinePlayer p, BigDecimal amount, String bankName, boolean ignoreEvents, TransactionType type) {
        Economy economy = BankPlus.INSTANCE.getVaultEconomy();
        BigDecimal result = new BigDecimal(0);
        if (!ignoreEvents) {
            BPPreTransactionEvent event = startEvent(p, type, economy.getBalance(p), amount, bankName);
            if (event.isCancelled()) return result;

            amount = event.getTransactionAmount();
        }

        BigDecimal balance = getBankBalance(p, bankName);
        if (balance.subtract(amount).doubleValue() < 0D) {
            result = balance;
            set(p, new BigDecimal(0), bankName);
        } else {
            result = amount;
            set(p, balance.subtract(result), bankName);
        }

        if (!ignoreEvents) endEvent(p, type, economy.getBalance(p), amount, bankName);
        return result;
    }

    /**
     * Get the total offline interest earned from the selected player from all banks.
     * @param p The player.
     * @return Total offline interest.
     */
    public BigDecimal getOfflineInterest(OfflinePlayer p) {
        BigDecimal amount = new BigDecimal(0);
        for (String bankName : banksRegistry.getBanks().keySet()) amount = amount.add(getOfflineInterest(p, bankName));
        return amount;
    }

    /**
     * Get the offline interest earned from the selected player in the selected bank.
     * @param p The player.
     * @param bankName The bank name.
     * @return Offline interest.
     */
    public BigDecimal getOfflineInterest(OfflinePlayer p, String bankName) {
        if (Values.CONFIG.isSqlEnabled() && BankPlus.INSTANCE.getSql().isConnected())
            return new SQLPlayerManager(p).getOfflineInterest(bankName);

        String interest = new BPPlayerManager(p).getPlayerConfig().getString("banks." + bankName + ".interest");
        return new BigDecimal(interest == null ? "0" : interest);
    }

    /**
     * Set the player bank debt to the selected amount.
     * @param p The player.
     * @param amount The new debt amount.
     * @param bankName The bank where to set the debt.
     */
    public void setDebt(OfflinePlayer p, BigDecimal amount, String bankName) {
        if (playerBalances.containsKey(p.getUniqueId())) {
            BigDecimal amountFormatted = new BigDecimal(BPFormatter.formatBigDouble(amount));

            HashMap<String, BankBalance> balances = playerBalances.get(p.getUniqueId());
            balances.get(bankName).setBankDebt(amountFormatted.max(BigDecimal.valueOf(0)));
            return;
        }

        if (Values.CONFIG.isSqlEnabled()) {
            BPSQL sql = BankPlus.INSTANCE.getSql();
            if (sql.isConnected()) {
                new SQLPlayerManager(p).setDebt(amount, bankName);
                return;
            }
        }

        BPPlayerManager files = new BPPlayerManager(p);
        File file = files.getPlayerFile();
        FileConfiguration config = files.getPlayerConfig(file);
        config.set("banks." + bankName + ".debt", BPFormatter.formatBigDouble(amount));
        files.savePlayerFile(config, file, true);
    }

    /**
     * Get the player bank debt of the selected bank.
     * @param uuid The player UUID.
     * @param bankName The bank where to get the balance.
     */
    public BigDecimal getDebt(UUID uuid, String bankName) {
        return getDebt(Bukkit.getOfflinePlayer(uuid), bankName);
    }

    /**
     * Get the player bank debt of the selected bank.
     * @param p The player.
     * @param bankName The bank where to get the balance.
     */
    public BigDecimal getDebt(OfflinePlayer p, String bankName) {
        if (!playerBalances.containsKey(p.getUniqueId())) {
            if (Values.CONFIG.isSqlEnabled() && BankPlus.INSTANCE.getSql().isConnected())
                return new SQLPlayerManager(p).getDebt(bankName);

            String bal = new BPPlayerManager(p).getPlayerConfig().getString("banks." + bankName + ".debt");
            return new BigDecimal(bal == null ? "0" : bal);
        }
        return playerBalances.get(p.getUniqueId()).get(bankName).getBankDebt();
    }

    /**
     * Get the sum of the player bank debt of all banks.
     * @param uuid The UUID of the player.
     */
    public BigDecimal getDebts(UUID uuid) {
        return getDebts(Bukkit.getOfflinePlayer(uuid));
    }

    /**
     * Get the sum of the player bank debt of all banks.
     * @param p The player.
     */
    public BigDecimal getDebts(OfflinePlayer p) {
        BigDecimal amount = new BigDecimal(0);
        for (String bankName : banksRegistry.getBanks().keySet()) amount = amount.add(getDebt(p, bankName));
        return amount;
    }

    /**
     * Method internally used to simplify the transactions.
     */
    private void set(OfflinePlayer p, BigDecimal amount, String bankName) {
        set(p, amount, new BigDecimal(0), bankName);
    }

    /**
     * Method internally used to simplify the transactions.
     */
    private void set(OfflinePlayer p, BigDecimal amount, BigDecimal offlineInterest, String bankName) {
        if (playerBalances.containsKey(p.getUniqueId())) {
            BigDecimal amountFormatted = new BigDecimal(BPFormatter.formatBigDouble(amount));

            HashMap<String, BankBalance> balances = playerBalances.get(p.getUniqueId());
            balances.get(bankName).setBankBalance(amountFormatted.max(BigDecimal.valueOf(0)));
            return;
        }

        boolean changeOfflineInterest = offlineInterest.doubleValue() != 0d;
        if (Values.CONFIG.isSqlEnabled()) {
            BPSQL sql = BankPlus.INSTANCE.getSql();
            if (sql.isConnected()) {
                SQLPlayerManager pManager = new SQLPlayerManager(p);
                pManager.setMoney(amount, bankName);
                if (changeOfflineInterest) pManager.setOfflineInterest(offlineInterest, bankName);
                return;
            }
        }

        BPPlayerManager files = new BPPlayerManager(p);
        File file = files.getPlayerFile();
        FileConfiguration config = files.getPlayerConfig();
        config.set("banks." + bankName + ".money", BPFormatter.formatBigDouble(amount));
        if (changeOfflineInterest) config.set("banks." + bankName + ".interest", BPFormatter.formatBigDouble(offlineInterest));
        files.savePlayerFile(config, file, true);
    }

    public void deposit(Player p, BigDecimal amount, String bankName) {
        Economy economy = BankPlus.INSTANCE.getVaultEconomy();
        BPPreTransactionEvent event = startEvent(p, TransactionType.DEPOSIT, economy.getBalance(p), amount, bankName);
        if (event.isCancelled()) return;

        amount = event.getTransactionAmount();

        if (amount.doubleValue() < Values.CONFIG.getDepositMinimumAmount().doubleValue()) {
            BPMessages.send(p, "Minimum-Number", "%min%$" + Values.CONFIG.getDepositMinimumAmount());
            return;
        }

        BigDecimal money = BigDecimal.valueOf(BankPlus.INSTANCE.getVaultEconomy().getBalance(p));
        if (!BPUtils.checkPreRequisites(money, amount, p) || BPUtils.isBankFull(p, bankName)) return;

        if (money.doubleValue() < amount.doubleValue()) amount = money;

        BigDecimal maxDepositAmount = Values.CONFIG.getMaxDepositAmount();
        if (maxDepositAmount.doubleValue() != 0 && amount.doubleValue() >= maxDepositAmount.doubleValue())
            amount = maxDepositAmount;

        BigDecimal taxes = new BigDecimal(0);
        if (Values.CONFIG.getDepositTaxes().doubleValue() > 0 && !p.hasPermission("bankplus.deposit.bypass-taxes"))
            taxes = amount.multiply(Values.CONFIG.getDepositTaxes().divide(BigDecimal.valueOf(100)));

        BigDecimal capacity = new BankReader(bankName).getCapacity(p);
        BigDecimal newBankBalance = getBankBalance(p, bankName).add(amount);

        /*
        Make it possible so when depositing all your money with taxes, the money will have the ability
        to FILL the bank instead of always depositing a bit less and never filling up the bank.
        */
        if (capacity.doubleValue() > 0d && newBankBalance.doubleValue() >= capacity.doubleValue()) {
            BigDecimal moneyToFull = capacity.subtract(getBankBalance(p, bankName));
            amount = moneyToFull.add(taxes);
        }

        EconomyResponse depositResponse = BankPlus.INSTANCE.getVaultEconomy().withdrawPlayer(p, amount.doubleValue());
        if (BPUtils.hasFailed(p, depositResponse)) return;

        addBankBalance(p, amount.subtract(taxes), bankName, true);
        BPMessages.send(p, "Success-Deposit", BPUtils.placeValues(p, amount.subtract(taxes)), BPUtils.placeValues(taxes, "taxes"));
        BPUtils.playSound("DEPOSIT", p);

        endEvent(p, TransactionType.DEPOSIT, economy.getBalance(p), amount, bankName);
    }

    public void withdraw(Player p, BigDecimal amount, String bankName) {
        Economy economy = BankPlus.INSTANCE.getVaultEconomy();
        BPPreTransactionEvent event = startEvent(p, TransactionType.WITHDRAW, economy.getBalance(p), amount, bankName);
        if (event.isCancelled()) return;

        amount = event.getTransactionAmount();

        if (amount.doubleValue() < Values.CONFIG.getWithdrawMinimumAmount().doubleValue()) {
            BPMessages.send(p, "Minimum-Number", "%min%$" + Values.CONFIG.getWithdrawMinimumAmount());
            return;
        }

        BigDecimal bankBal = getBankBalance(p, bankName);
        if (!BPUtils.checkPreRequisites(bankBal, amount, p)) return;

        if (bankBal.doubleValue() < amount.doubleValue()) amount = bankBal;

        BigDecimal maxWithdrawAmount = Values.CONFIG.getMaxWithdrawAmount();
        if (maxWithdrawAmount.doubleValue() > 0 && amount.doubleValue() >= maxWithdrawAmount.doubleValue())
            amount = maxWithdrawAmount;

        BigDecimal taxes = new BigDecimal(0);
        if (Values.CONFIG.getWithdrawTaxes().doubleValue() > 0 && !p.hasPermission("bankplus.withdraw.bypass-taxes"))
            taxes = amount.multiply(Values.CONFIG.getWithdrawTaxes().divide(BigDecimal.valueOf(100)));

        EconomyResponse withdrawResponse = economy.depositPlayer(p, amount.subtract(taxes).doubleValue());
        if (BPUtils.hasFailed(p, withdrawResponse)) return;

        removeBankBalance(p, amount, bankName, true);
        BPMessages.send(p, "Success-Withdraw", BPUtils.placeValues(p, amount.subtract(taxes)), BPUtils.placeValues(taxes, "taxes"));
        BPUtils.playSound("WITHDRAW", p);

        endEvent(p, TransactionType.WITHDRAW, economy.getBalance(p), amount, bankName);
    }

    /**
     * Method used to execute the pay transaction.
     *
     * @param from     The player that will give the money.
     * @param to       The player that will receive your money.
     * @param amount   How much money you want to pay.
     * @param fromBank The bank where the money will be taken.
     * @param toBank   The bank where the money will be added.
     */
    public void pay(Player from, Player to, BigDecimal amount, String fromBank, String toBank) {
        BigDecimal senderBalance = getBankBalance(from, fromBank);

        // Check if the sender has at least more than 0 money
        if (senderBalance.doubleValue() < amount.doubleValue()) {
            BPMessages.send(from, "Insufficient-Money");
            return;
        }

        // Check if the receiver of the payment has the bank full
        if (getBankBalance(to, toBank).doubleValue() >= new BankReader(toBank).getCapacity(to).doubleValue()) {
            BPMessages.send(from, "Bank-Full", "%player%$" + to.getName());
            return;
        }

        BigDecimal added = addBankBalance(to, amount, toBank, TransactionType.PAY), extra = amount.subtract(added);
        BPMessages.send(to, "Payment-Received", BPUtils.placeValues(from, added));

        BigDecimal removed = removeBankBalance(from, amount.subtract(extra), fromBank, TransactionType.PAY);
        BPMessages.send(from, "Payment-Sent", BPUtils.placeValues(to, removed));
    }

    private BPPreTransactionEvent startEvent(OfflinePlayer p, TransactionType type, double vaultBalance, BigDecimal amount, String bankName) {
        BPPreTransactionEvent event = new BPPreTransactionEvent(
                p, type, getBankBalance(p, bankName), vaultBalance, amount, bankName
        );
        BPUtils.callEvent(event);
        return event;
    }

    private void endEvent(OfflinePlayer p, TransactionType type, double vaultBalance, BigDecimal amount, String bankName) {
        BPAfterTransactionEvent event = new BPAfterTransactionEvent(
                p, type, getBankBalance(p, bankName), vaultBalance, amount, bankName
        );
        BPUtils.callEvent(event);
    }

    public static class BankBalance {
        private BigDecimal bankBalance, bankDebt;

        public BankBalance(BigDecimal bankBalance, BigDecimal bankDebt) {
            this.bankBalance = bankBalance;
            this.bankDebt = bankDebt;
        }

        public BigDecimal getBankBalance() {
            return bankBalance;
        }

        public BigDecimal getBankDebt() {
            return bankDebt;
        }

        public void setBankBalance(BigDecimal bankBalance) {
            this.bankBalance = bankBalance;
        }

        public void setBankDebt(BigDecimal bankDebt) {
            this.bankDebt = bankDebt;
        }
    }
}