package com.sekwah.advancedportals.core.commands.subcommands.portal;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.commands.SubCommand;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.services.PortalServices;
import com.sekwah.advancedportals.core.util.Lang;
import java.util.Collections;
import java.util.List;

/**
 * /portal rename &lt;old&gt; &lt;new&gt;
 */
public class RenamePortalSubCommand implements SubCommand {
    @Inject
    PortalServices portalServices;

    @Override
    public void onCommand(CommandSenderContainer sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.portal.rename.usage"));
            return;
        }
        String oldName = portalServices.getPortalKey(args[1]);
        if (portalServices.renamePortal(args[1], args[2],
                                        sender.getPlayerContainer())) {
            sender.sendMessage(Lang.getPositivePrefix()
                               + Lang.translateInsertVariables(
                                   "command.portal.rename.success", oldName,
                                   args[2]));
        } else {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.portal.rename.error"));
        }
    }

    @Override
    public boolean hasPermission(CommandSenderContainer sender) {
        return Permissions.EDIT_PORTAL.hasPermission(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSenderContainer sender,
                                      String[] args) {
        if (args.length == 2) {
            return portalServices.getPortalNames();
        }
        return Collections.emptyList();
    }

    @Override
    public String getBasicHelpText() {
        return Lang.translate("command.portal.rename.help");
    }

    @Override
    public String getDetailedHelpText() {
        return Lang.translate("command.portal.rename.detailedhelp");
    }
}
