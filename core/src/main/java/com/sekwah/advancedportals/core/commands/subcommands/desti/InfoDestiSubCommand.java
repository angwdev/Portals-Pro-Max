package com.sekwah.advancedportals.core.commands.subcommands.desti;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.commands.SubCommand;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.destination.Destination;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.services.DestinationServices;
import com.sekwah.advancedportals.core.services.PortalServices;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.util.TagReader;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /desti info &lt;name&gt; shows where a destination is, which portals use it
 * and its tags.
 */
public class InfoDestiSubCommand implements SubCommand {
    @Inject
    DestinationServices destinationServices;

    @Inject
    PortalServices portalServices;

    @Override
    public void onCommand(CommandSenderContainer sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.destination.noname"));
            return;
        }
        Destination destination = destinationServices.getDestination(args[1]);
        if (destination == null) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translateInsertVariables(
                                   "desti.error.notfound", args[1]));
            return;
        }
        PlayerLocation loc = destination.getLoc();
        sender.sendMessage(Lang.getPositivePrefix()
                           + Lang.translateInsertVariables(
                               "command.destination.info.complete",
                               destination.getName()));
        sender.sendMessage(Lang.translateInsertVariables(
            "command.destination.info.location", loc.getWorldName(),
            String.format(Locale.ROOT, "%.3f", loc.getPosX()),
            String.format(Locale.ROOT, "%.3f", loc.getPosY()),
            String.format(Locale.ROOT, "%.3f", loc.getPosZ()),
            String.format(Locale.ROOT, "%.1f", loc.getYaw()),
            String.format(Locale.ROOT, "%.1f", loc.getPitch())));
        List<String> usedBy =
            portalServices.getPortalsUsingDestination(destination.getName());
        sender.sendMessage(Lang.translateInsertVariables(
            "command.destination.info.usedby",
            usedBy.isEmpty() ? "-" : String.join(", ", usedBy)));
        TagReader.printArgs(sender, destination.getArgs());
    }

    @Override
    public boolean hasPermission(CommandSenderContainer sender) {
        return Permissions.INFO_DESTI.hasPermission(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSenderContainer sender,
                                      String[] args) {
        if (args.length == 2) {
            return destinationServices.getDestinationNames();
        }
        return Collections.emptyList();
    }

    @Override
    public String getBasicHelpText() {
        return Lang.translate("command.destination.info.help");
    }

    @Override
    public String getDetailedHelpText() {
        return Lang.translate("command.destination.info.help");
    }
}
