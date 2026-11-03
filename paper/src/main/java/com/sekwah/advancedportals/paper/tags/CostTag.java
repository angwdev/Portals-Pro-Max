package com.sekwah.advancedportals.paper.tags;

import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.warphandler.ActivationData;
import com.sekwah.advancedportals.core.warphandler.Tag;
import com.sekwah.advancedportals.paper.connector.container.PaperPlayerContainer;
import java.util.List;
import net.milkbowl.vault.economy.Economy;

/**
 * cost:amount charges the player (through Vault) when they use the portal.
 */
public class CostTag implements Tag.Activation, Tag.AutoComplete, Tag.Creation {
    private final Economy economy;

    public CostTag(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean preActivated(TagTarget target, PlayerContainer player,
                                ActivationData activeData, String[] argData) {
        double cost;
        try {
            cost = Double.parseDouble(argData[0]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            player.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("tag.cost.error"));
            return false;
        }

        if (!(player instanceof PaperPlayerContainer paperPlayer)) {
            return false;
        }

        if (this.economy.has(paperPlayer.getPlayer(), cost)) {
            return true;
        }

        player.sendMessage(
            Lang.getNegativePrefix()
            + Lang.translateInsertVariables("tag.cost.fail", cost));
        return false;
    }

    @Override
    public void postActivated(TagTarget target, PlayerContainer player,
                              ActivationData activationData, String[] argData) {
        if (player instanceof PaperPlayerContainer paperPlayer) {
            this.economy.withdrawPlayer(paperPlayer.getPlayer(),
                                        Double.parseDouble(argData[0]));
        }
    }

    @Override
    public boolean activated(TagTarget target, PlayerContainer player,
                             ActivationData activationData, String[] argData) {
        return true;
    }

    @Override
    public TagType[] getTagTypes() {
        return new TagType[] {TagType.PORTAL, TagType.DESTINATION};
    }

    @Override
    public String getName() {
        return "cost";
    }

    @Override
    public String[] getAliases() {
        return new String[0];
    }

    @Override
    public String description() {
        return Lang.translate("tag.cost.description");
    }

    @Override
    public List<String> autoComplete(String argData) {
        return List.of("10", "100", "1000", "10000", "100000");
    }

    @Override
    public boolean created(TagTarget target, PlayerContainer player,
                           String[] argData) {
        double cost;
        try {
            cost = Double.parseDouble(argData[0]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            if (player != null) {
                player.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translate("tag.cost.number"));
            }
            return false;
        }
        if (cost <= 0) {
            if (player != null) {
                player.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translate("tag.cost.negative"));
            }
            return false;
        }
        return true;
    }

    @Override
    public void destroyed(TagTarget target, PlayerContainer player,
                          String[] argData) {}
}
