package com.sekwah.advancedportals.core.commands.subcommands.desti;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.commands.SubCommand;
import com.sekwah.advancedportals.core.commands.subcommands.portal.HighlightPortalSubCommand;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.destination.Destination;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.services.DestinationServices;
import com.sekwah.advancedportals.core.util.Lang;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /desti highlight &lt;name&gt; [seconds]
 */
public class HighlightDestiSubCommand implements SubCommand {
    @Inject
    DestinationServices destinationServices;

    @Override
    public void onCommand(CommandSenderContainer sender, String[] args) {
        PlayerContainer player = sender.getPlayerContainer();
        if (player == null) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.playeronly"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.destination.highlight.usage"));
            return;
        }
        Destination destination = destinationServices.getDestination(args[1]);
        if (destination == null) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translateInsertVariables(
                                   "desti.error.notfound", args[1]));
            return;
        }
        int seconds = HighlightPortalSubCommand.DEFAULT_SECONDS;
        if (args.length > 2) {
            try {
                seconds = Math.max(
                    1, Math.min(HighlightPortalSubCommand.MAX_SECONDS,
                                Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translate("command.destination.highlight.usage"));
                return;
            }
        }
        if (!destination.getLoc().getWorldName().equals(player.getWorldName())) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.destination.highlight.toofar"));
            return;
        }
        if (player.highlightPoint(destination.getLoc(), seconds * 20)) {
            sender.sendMessage(Lang.getPositivePrefix()
                               + Lang.translateInsertVariables(
                                   "command.destination.highlight.done",
                                   destination.getName(), seconds));
        } else {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.destination.highlight.unsupported"));
        }
    }

    @Override
    public boolean hasPermission(CommandSenderContainer sender) {
        return Permissions.SHOW_DESTI.hasPermission(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSenderContainer sender,
                                      String[] args) {
        if (args.length == 2) {
            return destinationServices.getDestinationNames();
        }
        if (args.length == 3) {
            return Arrays.asList("5", "10", "30", "60");
        }
        return Collections.emptyList();
    }

    @Override
    public String getBasicHelpText() {
        return Lang.translate("command.destination.highlight.help");
    }

    @Override
    public String getDetailedHelpText() {
        return Lang.translate("command.destination.highlight.detailedhelp");
    }
}
