package com.sekwah.advancedportals.core.commands.subcommands.portal;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.commands.SubCommand;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.services.PlayerDataServices;
import com.sekwah.advancedportals.core.util.Lang;
import java.util.Collections;
import java.util.List;

/**
 * /portal pos1 [x y z] and /portal pos2 [x y z] set the corners of the
 * selection without needing the selector axe. Clicking blocks with an item is
 * awkward on touch screens and gamepads (Bedrock players joining through
 * Geyser), so this works from the keyboard/chat instead.
 */
public class PositionPortalSubCommand implements SubCommand {
    private final boolean first;

    @Inject
    PlayerDataServices playerDataServices;

    public PositionPortalSubCommand(boolean first) {
        this.first = first;
    }

    @Override
    public void onCommand(CommandSenderContainer sender, String[] args) {
        PlayerContainer player = sender.getPlayerContainer();
        if (player == null) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.playeronly"));
            return;
        }
        BlockLocation location;
        if (args.length >= 4) {
            try {
                location = new BlockLocation(
                    player.getWorldName(), parseCoordinate(args[1], player.getBlockLoc().getPosX()),
                    parseCoordinate(args[2], player.getBlockLoc().getPosY()),
                    parseCoordinate(args[3], player.getBlockLoc().getPosZ()));
            } catch (NumberFormatException e) {
                sender.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translate("command.portal.pos.usage"));
                return;
            }
        } else if (args.length == 1) {
            // The block the player is standing in
            location = player.getBlockLoc();
        } else {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.portal.pos.usage"));
            return;
        }
        // Sends the same confirmation as clicking with the selector
        playerDataServices.playerSelectorActivate(player, location, first);
    }

    /**
     * Accepts numbers and ~ / ~offset relative to the players position.
     */
    private static int parseCoordinate(String text, int current) {
        if (text.startsWith("~")) {
            return current
                + (text.length() == 1 ? 0 : Integer.parseInt(text.substring(1)));
        }
        return Integer.parseInt(text);
    }

    @Override
    public boolean hasPermission(CommandSenderContainer sender) {
        return Permissions.SELECTOR.hasPermission(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSenderContainer sender,
                                      String[] args) {
        if (args.length >= 2 && args.length <= 4) {
            return Collections.singletonList("~");
        }
        return Collections.emptyList();
    }

    @Override
    public String getBasicHelpText() {
        return Lang.translate("command.portal.pos.help");
    }

    @Override
    public String getDetailedHelpText() {
        return Lang.translate("command.portal.pos.detailedhelp");
    }
}
