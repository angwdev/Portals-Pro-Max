package com.sekwah.advancedportals.core.commands.subcommands.portal;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.commands.SubCommand;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.services.PortalServices;
import com.sekwah.advancedportals.core.util.Lang;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /portal highlight &lt;name&gt; [seconds]
 */
public class HighlightPortalSubCommand implements SubCommand {
    public static final int DEFAULT_SECONDS = 10;
    public static final int MAX_SECONDS = 120;

    @Inject
    PortalServices portalServices;

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
                               + Lang.translate("command.portal.highlight.usage"));
            return;
        }
        AdvancedPortal portal = portalServices.getPortal(args[1]);
        if (portal == null) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translateInsertVariables(
                                   "command.portal.info.notfound", args[1]));
            return;
        }
        int seconds = DEFAULT_SECONDS;
        if (args.length > 2) {
            try {
                seconds = Math.max(1, Math.min(MAX_SECONDS, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translate("command.portal.highlight.usage"));
                return;
            }
        }
        if (!portal.getWorldName().equals(player.getWorldName())) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.portal.highlight.toofar"));
            return;
        }
        if (player.highlightRegion(portal.getMinLoc(), portal.getMaxLoc(),
                                   seconds * 20)) {
            sender.sendMessage(Lang.getPositivePrefix()
                               + Lang.translateInsertVariables(
                                   "command.portal.highlight.done",
                                   portal.getName(), seconds));
        } else {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.portal.highlight.unsupported"));
        }
    }

    @Override
    public boolean hasPermission(CommandSenderContainer sender) {
        return Permissions.SHOW_PORTAL.hasPermission(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSenderContainer sender,
                                      String[] args) {
        if (args.length == 2) {
            return portalServices.getPortalNames();
        }
        if (args.length == 3) {
            return Arrays.asList("5", "10", "30", "60");
        }
        return Collections.emptyList();
    }

    @Override
    public String getBasicHelpText() {
        return Lang.translate("command.portal.highlight.help");
    }

    @Override
    public String getDetailedHelpText() {
        return Lang.translate("command.portal.highlight.detailedhelp");
    }
}
