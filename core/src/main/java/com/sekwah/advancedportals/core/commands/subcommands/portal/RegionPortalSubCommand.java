package com.sekwah.advancedportals.core.commands.subcommands.portal;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.commands.SubCommand;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.serializeddata.PlayerData;
import com.sekwah.advancedportals.core.services.PlayerDataServices;
import com.sekwah.advancedportals.core.services.PortalServices;
import com.sekwah.advancedportals.core.util.Lang;
import java.util.Collections;
import java.util.List;

/**
 * /portal region &lt;name&gt; loads the region of an existing portal into your
 * selection (pos1/pos2) so you can look at it with /portal show, adjust the
 * corners with the selector and then apply it again with /portal move.
 */
public class RegionPortalSubCommand implements SubCommand {
    @Inject
    PortalServices portalServices;

    @Inject
    PlayerDataServices playerDataServices;

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
                               + Lang.translate("command.portal.region.usage"));
            return;
        }
        AdvancedPortal portal = portalServices.getPortal(args[1]);
        if (portal == null) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translateInsertVariables(
                                   "command.portal.info.notfound", args[1]));
            return;
        }
        PlayerData playerData = playerDataServices.getPlayerData(player);
        playerData.setPos1(portal.getMinLoc());
        playerData.setPos2(portal.getMaxLoc());
        playerData.setSelectedPortal(portal.getName());
        sender.sendMessage(Lang.getPositivePrefix()
                           + Lang.translateInsertVariables(
                               "command.portal.region.loaded",
                               portal.getName()));
        // Shows the region being edited where supported (Paper)
        player.highlightRegion(portal.getMinLoc(), portal.getMaxLoc(), 200);
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
        return Lang.translate("command.portal.region.help");
    }

    @Override
    public String getDetailedHelpText() {
        return Lang.translate("command.portal.region.detailedhelp");
    }
}
