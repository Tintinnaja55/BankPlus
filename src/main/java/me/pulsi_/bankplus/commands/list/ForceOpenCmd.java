package me.pulsi_.bankplus.commands.list;

import me.pulsi_.bankplus.bankSystem.BankUtils;
import me.pulsi_.bankplus.commands.BPCommand;
import me.pulsi_.bankplus.utils.texts.BPArgs;
import me.pulsi_.bankplus.utils.texts.BPMessages;
import me.pulsi_.bankplus.values.Values;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ForceOpenCmd extends BPCommand {

    public ForceOpenCmd(String... aliases) {
        super(aliases);
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public boolean skipUsageWarn() {
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, String[] args) {
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) {
            BPMessages.send(s, "Invalid-Player");
            return false;
        }

        String bankName = Values.CONFIG.getMainGuiName();
        if (args.length > 2) bankName = args[2];
        boolean silent = args.length > 3 && args[3].toLowerCase().contains("true");

        if (!BankUtils.exist(bankName)) {
            BPMessages.send(s, "Invalid-Bank");
            return false;
        }

        if (skipToConfirm(s)) return false;

        BankUtils.getBank(bankName).openBankGui(p, true);
        if (!silent) BPMessages.send(s, "Force-Open", "%player%$" + p.getName(), "%bank%$" + bankName);
        return true;
    }

    @Override
    public List<String> tabCompletion(CommandSender s, String[] args) {
        if (args.length == 3)
            return BPArgs.getBanks(args);

        if (args.length == 4)
            return BPArgs.getArgs(args, "silent=true", "silent=false");
        return null;
    }
}