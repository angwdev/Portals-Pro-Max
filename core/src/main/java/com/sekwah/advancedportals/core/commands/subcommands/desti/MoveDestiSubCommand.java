package com.sekwah.advancedportals.core.commands.subcommands.desti;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.commands.SubCommand;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.destination.Destination;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.services.DestinationServices;
import com.sekwah.advancedportals.core.util.Lang;

import java.util.Collections;
import java.util.List;

public class MoveDestiSubCommand implements SubCommand {
    @Inject
    DestinationServices destinationServices;

    @Override
    public void onCommand(CommandSenderContainer sender, String[] args) {
        // The sender is never a PlayerContainer itself (it is a command
        // sender), so the old "instanceof" check always failed.
        PlayerContainer player = sender.getPlayerContainer();
        if (player == null) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.playeronly"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + getBasicHelpText());
            return;
        }
        Destination destination = destinationServices.getDestination(args[1]);
        if (destination == null) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translateInsertVariables(
                                   "command.destination.notfound", args[1]));
            return;
        }
        // Takes the exact position and the direction the player is facing so
        // the teleport lands precisely where they stood. The service saves it
        // and sends the confirmation.
        destinationServices.changeDestinationLocation(destination.getName(),
                                                      player.getLoc(), player);
    }

    @Override
    public boolean hasPermission(CommandSenderContainer sender) {
        return Permissions.MOVE_DESTI.hasPermission(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSenderContainer sender, String[] args) {
        if (args.length == 2) {
            return destinationServices.getDestinationNames();
        }
        return Collections.emptyList();
    }

    @Override
    public String getBasicHelpText() {
        return Lang.translate("command.desti.move.help");
    }

    @Override
    public String getDetailedHelpText() {
        return Lang.translate("command.desti.move.detailedhelp");
    }
}
