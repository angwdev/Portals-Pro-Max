package com.sekwah.advancedportals.paper.connector.container;

import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.connector.containers.ServerContainer;
import com.sekwah.advancedportals.core.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PaperCommandSenderContainer implements CommandSenderContainer {
    private final CommandSender sender;

    public PaperCommandSenderContainer(CommandSender commandSender) {
        this.sender = commandSender;
    }

    public void sendMessage(String message) {
        if (message.isBlank()
                || message.equals(Lang.getPositivePrefix())
                || message.equals(Lang.getNegativePrefix())) return;
        sender.sendMessage(message);
    }

    @Override
    public ServerContainer getServer() {
        return new PaperServerContainer(sender.getServer());
    }

    public boolean isOp() {
        return sender.isOp();
    }

    /**
     * @return null if there isnt a player e.g. the console
     */
    public PaperPlayerContainer getPlayerContainer() {
        if (sender instanceof Player) {
            return new PaperPlayerContainer((Player) sender);
        }
        return null;
    }

    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }
}
