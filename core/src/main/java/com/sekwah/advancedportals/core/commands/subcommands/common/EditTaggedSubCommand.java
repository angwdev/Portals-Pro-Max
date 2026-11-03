package com.sekwah.advancedportals.core.commands.subcommands.common;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.connector.containers.EditorForm;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.util.TagEditor;
import com.sekwah.advancedportals.core.util.TagReader;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Base for the portal and destination "edit" commands.
 *
 * <pre>
 * /portal edit &lt;name&gt; tag:value ...          sets (replaces) tags
 * /portal edit &lt;name&gt; add tag:value ...      appends values to a tag
 * /portal edit &lt;name&gt; remove tag[:value] ... removes tags (or single values)
 * </pre>
 */
public abstract class EditTaggedSubCommand extends CreateTaggedSubCommand {
    @Inject
    protected TagEditor tagEditor;

    private static final List<String> MODES =
        Arrays.asList("add", "remove", "set");

    /**
     * @return the portal/destination with that name or null
     */
    protected abstract TagTarget findTarget(String name);

    protected abstract List<String> getTargetNames();

    /**
     * Persists the target. Return false if it couldn't be saved.
     */
    protected abstract boolean save(TagTarget target);

    /**
     * Called after a successful edit, for example to refresh caches.
     */
    protected void edited(TagTarget target) {}

    protected abstract String notFoundKey();

    protected abstract String usageKey();

    @Override
    public void onCommand(CommandSenderContainer sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate(usageKey()));
            return;
        }

        TagTarget target = this.findTarget(args[1]);
        if (target == null) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translateInsertVariables(notFoundKey(),
                                                               args[1]));
            return;
        }

        if (args.length < 3) {
            // No tags given: open the click through editor if there is one
            this.openEditor(sender, target);
            return;
        }

        String mode = args[2].toLowerCase();
        boolean explicitMode = !mode.contains(":") && MODES.contains(mode);
        int tagStart = explicitMode ? 3 : 2;
        String[] tagArgs = Arrays.copyOfRange(args, tagStart, args.length);
        if (tagArgs.length == 0) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate(usageKey()));
            return;
        }

        TagEditor.EditRequest request = new TagEditor.EditRequest();
        if (explicitMode && mode.equals("remove")) {
            this.parseRemovals(tagArgs, request);
        } else {
            ArrayList<DataTag> tags = TagReader.getTagsFromArgs(tagArgs);
            if (tags.isEmpty()) {
                sender.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translate(usageKey()));
                return;
            }

            // Tell the user about anything that is not a tag for this target
            // instead of silently ignoring it.
            for (DataTag tag : tags) {
                if (this.findRelatedTag(tag.NAME) == null) {
                    sender.sendMessage(Lang.getNegativePrefix()
                                       + Lang.translateInsertVariables(
                                           "command.edit.error.invalidtag",
                                           tag.NAME));
                    return;
                }
            }
            this.filterAndProcessTags(tags);

            if (explicitMode && mode.equals("add")) {
                request.add.addAll(tags);
            } else {
                request.set.addAll(tags);
            }
        }

        this.applyEdit(sender, target, request);
    }

    /**
     * Applies, saves and reports an edit. Shared by the command and the
     * click through editor.
     */
    protected void applyEdit(CommandSenderContainer sender, TagTarget target,
                             TagEditor.EditRequest request) {
        TagEditor.Snapshot snapshot =
            this.tagEditor.apply(target, sender, request);
        if (snapshot == null) {
            return;
        }
        if (snapshot.getChanged().isEmpty()) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.edit.nochange"));
            return;
        }

        if (!this.save(target)) {
            snapshot.revert(target);
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.edit.error.save"));
            return;
        }
        this.tagEditor.finish(target, sender, snapshot);
        this.edited(target);

        sender.sendMessage(Lang.getPositivePrefix()
                           + Lang.translateInsertVariables(
                               "command.edit.complete", target.getName()));
        sender.sendMessage(Lang.translate("command.create.tags"));
        TagReader.printArgs(sender, target.getArgs());
    }

    /**
     * Shows a text field per tag. Only the fields that were changed are
     * applied, and an emptied field removes the tag.
     */
    private void openEditor(CommandSenderContainer sender, TagTarget target) {
        PlayerContainer player = sender.getPlayerContainer();
        if (player == null) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate(usageKey()));
            return;
        }

        final String targetName = target.getName();
        final Map<String, Tag> tagsByName = new LinkedHashMap<>();
        final Map<String, String> initialValues = new HashMap<>();

        List<Tag> tags = new ArrayList<>(this.getRelatedTags());
        tags.sort(Comparator.comparing(Tag::getName));

        EditorForm form = new EditorForm(
            Lang.translateInsertVariables("command.edit.gui.title", targetName),
            Lang.translate("command.edit.gui.hint"),
            Lang.translate("command.edit.gui.save"),
            Lang.translate("command.edit.gui.cancel"),
            (submitter, values) -> {
                TagTarget current = this.findTarget(targetName);
                if (current == null) {
                    submitter.sendMessage(Lang.getNegativePrefix()
                                          + Lang.translate("command.edit.gui.gone"));
                    return;
                }
                TagEditor.EditRequest request = new TagEditor.EditRequest();
                for (Map.Entry<String, Tag> entry : tagsByName.entrySet()) {
                    String name = entry.getKey();
                    String text = values.getOrDefault(name, "").trim();
                    if (text.equals(initialValues.get(name).trim())) {
                        continue;
                    }
                    if (text.isEmpty()) {
                        request.remove.add(new DataTag(name));
                        continue;
                    }
                    Tag tag = entry.getValue();
                    String splitString = tag instanceof Tag.Split
                        ? ((Tag.Split) tag).splitString()
                        : null;
                    List<String> parts = new ArrayList<>();
                    if (splitString != null) {
                        for (String part :
                             text.split(Pattern.quote(splitString))) {
                            if (!part.trim().isEmpty()) {
                                parts.add(part.trim());
                            }
                        }
                    } else {
                        parts.add(text);
                    }
                    request.set.add(
                        new DataTag(name, parts.toArray(new String[0])));
                }
                if (request.isEmpty()) {
                    submitter.sendMessage(Lang.getNegativePrefix()
                                          + Lang.translate("command.edit.nochange"));
                    return;
                }
                // Removing something that was never there would be rejected
                request.remove.removeIf(
                    removal -> current.getArgValues(removal.NAME) == null);
                this.applyEdit(sender, current, request);
            });

        for (Tag tag : tags) {
            if (tag.getName().equals("name")) {
                continue;
            }
            String[] current = target.getArgValues(tag.getName());
            String splitString = tag instanceof Tag.Split
                ? ((Tag.Split) tag).splitString()
                : null;
            String initial = current == null
                ? ""
                : String.join(splitString == null ? "," : splitString, current);
            tagsByName.put(tag.getName(), tag);
            initialValues.put(tag.getName(), initial);
            form.fields.add(
                new EditorForm.Field(tag.getName(), tag.getName(), initial));
        }

        if (!player.showEditor(form)) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.edit.gui.unsupported"));
            sender.sendMessage(Lang.translate(usageKey()));
        }
    }

    /**
     * Plain words are whole tags to remove, tag:value removes just the
     * listed value(s).
     */
    private void parseRemovals(String[] tagArgs, TagEditor.EditRequest request) {
        List<String> valueArgs = new ArrayList<>();
        for (String arg : tagArgs) {
            if (arg.contains(":")) {
                valueArgs.add(arg);
            } else if (!arg.isEmpty()) {
                Tag tag = this.findRelatedTag(arg);
                request.remove.add(new DataTag(tag == null ? arg : tag.getName()));
            }
        }
        if (!valueArgs.isEmpty()) {
            ArrayList<DataTag> tags =
                TagReader.getTagsFromArgs(valueArgs.toArray(new String[0]));
            for (DataTag tag : tags) {
                Tag related = this.findRelatedTag(tag.NAME);
                String name = related == null ? tag.NAME : related.getName();
                String splitString = related instanceof Tag.Split
                    ? ((Tag.Split) related).splitString()
                    : null;
                List<String> values = new ArrayList<>();
                for (String value : tag.VALUES) {
                    if (splitString != null) {
                        for (String part :
                             value.split(Pattern.quote(splitString))) {
                            values.add(part.trim());
                        }
                    } else {
                        values.add(value);
                    }
                }
                request.remove.add(
                    new DataTag(name, values.toArray(new String[0])));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSenderContainer sender,
                                      String[] args) {
        if (args.length == 2) {
            return this.getTargetNames();
        }
        if (args.length == 3) {
            List<String> suggestions = new ArrayList<>(MODES);
            List<String> tagSuggestions = super.onTabComplete(sender, args);
            if (tagSuggestions != null) {
                suggestions.addAll(tagSuggestions);
            }
            return suggestions;
        }
        if (args.length > 3 && args[2].equalsIgnoreCase("remove")) {
            TagTarget target = this.findTarget(args[1]);
            if (target == null) {
                return Collections.emptyList();
            }
            List<String> names = new ArrayList<>();
            for (DataTag tag : target.getArgs()) {
                if (!tag.NAME.equals("name")) {
                    names.add(tag.NAME);
                }
            }
            return names;
        }
        return super.onTabComplete(sender, args);
    }
}
