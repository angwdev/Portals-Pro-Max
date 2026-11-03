package com.sekwah.advancedportals.core.commands.subcommands.desti;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.commands.subcommands.common.EditTaggedSubCommand;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.destination.Destination;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.services.DestinationServices;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /desti edit &lt;name&gt; [add|remove] tag:value ...
 */
public class EditDestiSubCommand extends EditTaggedSubCommand {
    @Inject
    DestinationServices destinationServices;

    @Inject
    TagRegistry tagRegistry;

    @Override
    protected TagTarget findTarget(String name) {
        return destinationServices.getDestination(name);
    }

    @Override
    protected List<String> getTargetNames() {
        return destinationServices.getDestinationNames();
    }

    @Override
    protected boolean save(TagTarget target) {
        return destinationServices.saveDestination((Destination) target);
    }

    @Override
    protected String notFoundKey() {
        return "desti.error.notfound";
    }

    @Override
    protected String usageKey() {
        return "command.destination.edit.usage";
    }

    @Override
    public boolean hasPermission(CommandSenderContainer sender) {
        return Permissions.EDIT_DESTI.hasPermission(sender);
    }

    @Override
    protected List<Tag> getRelatedTags() {
        return tagRegistry.getTags()
            .stream()
            .filter(tag
                    -> Arrays.asList(tag.getTagTypes())
                           .contains(Tag.TagType.DESTINATION))
            .collect(Collectors.toList());
    }

    @Override
    public String getBasicHelpText() {
        return Lang.translate("command.destination.edit.help");
    }

    @Override
    public String getDetailedHelpText() {
        return Lang.translate("command.destination.edit.detailedhelp");
    }
}
