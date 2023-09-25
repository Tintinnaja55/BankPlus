package me.pulsi_.bankplus.commands.list;

import me.pulsi_.bankplus.bankSystem.BankReader;
import me.pulsi_.bankplus.commands.BPCommand;
import me.pulsi_.bankplus.utils.BPArgs;
import me.pulsi_.bankplus.utils.BPMessages;
import me.pulsi_.bankplus.utils.BPUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class CustomWithdrawCmd extends BPCommand {

    public CustomWithdrawCmd(String... aliases) {
        super(aliases);
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean skipUsageWarn() {
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, String args[]) {
        Player p = (Player) s;

        if (args.length == 1) {
            if (getUsage() != null && !getUsage().equals("")) BPMessages.send(s, getUsage(), true);
            return false;
        }

        String bankName = args[1];
        if (!new BankReader(bankName).exist()) {
            BPMessages.send(s, "Invalid-Bank");
            return false;
        }

        if (confirm(s)) return false;
        BPUtils.customWithdraw(p, bankName);
        return true;
    }

    @Override
    public List<String> tabCompletion(CommandSender s, String[] args) {
        if (args.length == 2)
            return BPArgs.getBanks(args);
        return null;
    }
}