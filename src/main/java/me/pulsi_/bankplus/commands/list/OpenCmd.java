package me.pulsi_.bankplus.commands.list;

import me.pulsi_.bankplus.bankSystem.BankUtils;
import me.pulsi_.bankplus.commands.BPCommand;
import me.pulsi_.bankplus.utils.texts.BPArgs;
import me.pulsi_.bankplus.utils.texts.BPMessages;
import me.pulsi_.bankplus.values.Values;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class OpenCmd extends BPCommand {

    public OpenCmd(String... aliases) {
        super(aliases);
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean skipUsageWarn() {
        return true;
    }

    @Override
    public boolean onCommand(CommandSender s, String[] args) {
        Player p = (Player) s;

        String bankName = Values.CONFIG.getMainGuiName();
        if (args.length > 1) bankName = args[1];

        if (!BankUtils.exist(bankName)) {
            BPMessages.send(s, "Invalid-Bank");
            return false;
        }

        if (!skipToConfirm(s)) BankUtils.getBank(bankName).openBankGui(p);
        return true;
    }

    @Override
    public List<String> tabCompletion(CommandSender s, String[] args) {
        Player p = (Player) s;

        if (args.length == 2)
            return BPArgs.getArgs(args, BankUtils.getAvailableBankNames(p));
        return null;
    }
}