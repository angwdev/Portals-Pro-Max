package com.sekwah.advancedportals.core.commands.subcommands.portal;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.commands.subcommands.common.EditTaggedSubCommand;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.services.PortalServices;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /portal edit &lt;name&gt; [add|remove] tag:value ...
 */
public class EditPortalSubCommand extends EditTaggedSubCommand {
    @Inject
    PortalServices portalServices;

    @Inject
    TagRegistry tagRegistry;

    @Override
    protected TagTarget findTarget(String name) {
        return portalServices.getPortal(name);
    }

    @Override
    protected List<String> getTargetNames() {
        return portalServices.getPortalNames();
    }

    @Override
    protected boolean save(TagTarget target) {
        return portalServices.savePortal((AdvancedPortal) target);
    }

    @Override
    protected void edited(TagTarget target) {
        // Rebuild the sorted tag list straight away so the next activation
        // uses the new tags (and any new priority).
        ((AdvancedPortal) target).updatePortalTagList();
    }

    @Override
    protected String notFoundKey() {
        return "command.portal.info.notfound";
    }

    @Override
    protected String usageKey() {
        return "command.portal.edit.usage";
    }

    @Override
    public boolean hasPermission(CommandSenderContainer sender) {
        return Permissions.EDIT_PORTAL.hasPermission(sender);
    }

    @Override
    protected List<Tag> getRelatedTags() {
        return tagRegistry.getTags()
            .stream()
            .filter(tag
                    -> Arrays.asList(tag.getTagTypes())
                           .contains(Tag.TagType.PORTAL))
            .collect(Collectors.toList());
    }

    @Override
    public String getBasicHelpText() {
        return Lang.translate("command.portal.edit.help");
    }

    @Override
    public String getDetailedHelpText() {
        return Lang.translate("command.portal.edit.detailedhelp");
    }
}
