package me.pulsi_.bankplus.placeholders.list;

import me.pulsi_.bankplus.BankPlus;
import me.pulsi_.bankplus.placeholders.BPPlaceholder;
import me.pulsi_.bankplus.utils.BPUtils;
import me.pulsi_.bankplus.values.Values;
import org.bukkit.entity.Player;

public class InterestCooldownMillisPlaceholder extends BPPlaceholder {

    @Override
    public String getIdentifier() {
        return "interest_cooldown_millis";
    }

    @Override
    public String getPlaceholder(Player p, String target, String identifier) {
        return Values.CONFIG.isInterestEnabled() ? String.valueOf(BankPlus.INSTANCE().getInterest().getInterestCooldownMillis()) : "Interest disabled.";
    }
}