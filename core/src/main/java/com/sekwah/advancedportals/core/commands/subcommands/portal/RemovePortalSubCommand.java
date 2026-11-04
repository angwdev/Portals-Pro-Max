package com.sekwah.advancedportals.core.commands.subcommands.portal;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.commands.SubCommand;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.services.PortalServices;
import com.sekwah.advancedportals.core.util.Lang;
import java.util.List;

public class RemovePortalSubCommand implements SubCommand {
    @Inject
    PortalServices portalServices;

    @Override
    public void onCommand(CommandSenderContainer sender, String[] args) {
        if (args.length > 1) {
            // removePortal already tells the player if the portal is unknown
            if (portalServices.removePortal(args[1],
                                            sender.getPlayerContainer())) {
                sender.sendMessage(
                    Lang.getPositivePrefix()
                    + Lang.translate("command.portal.remove.complete"));
            } else if (portalServices.getPortal(args[1]) != null) {
                sender.sendMessage(
                    Lang.getNegativePrefix()
                    + Lang.translate("command.portal.remove.error"));
            } else if (sender.getPlayerContainer() == null) {
                // removePortal can only message players
                sender.sendMessage(
                    Lang.getNegativePrefix()
                    + Lang.translateInsertVariables(
                        "command.portal.info.notfound", args[1]));
            }
        } else {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.portal.remove.usage"));
        }
    }

    @Override
    public boolean hasPermission(CommandSenderContainer sender) {
        return Permissions.REMOVE_PORTAL.hasPermission(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSenderContainer sender,
                                      String[] args) {
        return portalServices.getPortalNames();
    }

    @Override
    public String getBasicHelpText() {
        return Lang.translate("command.portal.remove.help");
    }

    @Override
    public String getDetailedHelpText() {
        return Lang.translate("command.portal.remove.detailedhelp");
    }
}
