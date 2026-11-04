package com.sekwah.advancedportals.core.util;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.tags.NameTag;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.util.*;

/**
 * Applies edits to the tags of an already created portal or destination.
 *
 * <p>Every changed tag goes through the same {@link Tag.Creation#created}
 * validation as when the target is created, so editing can never be used to
 * get around the checks (e.g. the permissions needed for command portals).
 *
 * <p>The edit is all or nothing: if any tag is rejected nothing is changed.
 */
@Singleton
public class TagEditor {
    @Inject
    private TagRegistry tagRegistry;

    /**
     * A description of what to change.
     */
    public static final class EditRequest {
        /**
         * Tags where all the values are replaced (or the tag is added).
         */
        public final List<DataTag> set = new ArrayList<>();

        /**
         * Values to append to a (multi value) tag, e.g. another destination.
         */
        public final List<DataTag> add = new ArrayList<>();

        /**
         * Tags to remove. With no values the whole tag is removed, otherwise
         * only the listed values are.
         */
        public final List<DataTag> remove = new ArrayList<>();

        public boolean isEmpty() {
            return set.isEmpty() && add.isEmpty() && remove.isEmpty();
        }
    }

    /**
     * What a successful edit changed, so the caller can undo it if saving the
     * target fails and tell the tags once everything is saved.
     */
    public static final class Snapshot {
        private final Map<String, String[]> before;
        private final Map<String, String[]> after;

        private Snapshot(Map<String, String[]> before,
                         Map<String, String[]> after) {
            this.before = before;
            this.after = after;
        }

        /**
         * @return names of the tags that were added or changed
         */
        public Set<String> getChanged() {
            Set<String> changed = new TreeSet<>();
            for (Map.Entry<String, String[]> entry : after.entrySet()) {
                if (!Arrays.equals(before.get(entry.getKey()),
                                   entry.getValue())) {
                    changed.add(entry.getKey());
                }
            }
            for (String name : before.keySet()) {
                if (!after.containsKey(name)) {
                    changed.add(name);
                }
            }
            return changed;
        }

        /**
         * Puts the target back exactly as it was before the edit.
         */
        public void revert(TagTarget target) {
            for (DataTag tag : target.getArgs()) {
                if (!before.containsKey(tag.NAME)) {
                    target.removeArg(tag.NAME);
                }
            }
            for (Map.Entry<String, String[]> entry : before.entrySet()) {
                target.setArgValues(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Validates and applies the edit to the target in memory. The caller is
     * responsible for saving the target afterwards and calling {@link
     * #finish} (or {@link Snapshot#revert} if saving failed).
     *
     * @return the snapshot or null if the edit was rejected (the sender has
     *     already been told why and the target is unchanged)
     */
    public Snapshot apply(TagTarget target, CommandSenderContainer sender,
                          EditRequest request) {
        PlayerContainer player = sender.getPlayerContainer();
        Tag.TagType targetType = target.targetTagType();

        Map<String, String[]> before = copyOf(target.getArgs());
        Map<String, String[]> after = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : before.entrySet()) {
            after.put(entry.getKey(), entry.getValue().clone());
        }
        Set<String> needsValidation = new LinkedHashSet<>();

        for (DataTag remove : request.remove) {
            if (remove.NAME.equals(NameTag.TAG_NAME)) {
                sender.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translate("command.edit.error.name"));
                return null;
            }
            String[] current = after.get(remove.NAME);
            if (current == null) {
                sender.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translateInsertVariables(
                                       "command.edit.error.notset",
                                       remove.NAME));
                return null;
            }
            if (remove.VALUES.length == 0) {
                after.remove(remove.NAME);
                continue;
            }
            List<String> remaining = new ArrayList<>(Arrays.asList(current));
            boolean removedAny = false;
            for (String value : remove.VALUES) {
                removedAny |= remaining.remove(value);
            }
            if (!removedAny) {
                sender.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translateInsertVariables(
                                       "command.edit.error.novalue",
                                       remove.NAME,
                                       String.join(",", remove.VALUES)));
                return null;
            }
            if (remaining.isEmpty()) {
                after.remove(remove.NAME);
            } else {
                after.put(remove.NAME, remaining.toArray(new String[0]));
                needsValidation.add(remove.NAME);
            }
        }

        for (DataTag set : request.set) {
            if (!checkTagUsable(set.NAME, targetType, sender)) {
                return null;
            }
            after.put(set.NAME, set.VALUES.clone());
            needsValidation.add(set.NAME);
        }

        for (DataTag add : request.add) {
            if (!checkTagUsable(add.NAME, targetType, sender)) {
                return null;
            }
            String[] current = after.get(add.NAME);
            Set<String> merged = new LinkedHashSet<>();
            if (current != null) {
                merged.addAll(Arrays.asList(current));
            }
            merged.addAll(Arrays.asList(add.VALUES));
            after.put(add.NAME, merged.toArray(new String[0]));
            needsValidation.add(add.NAME);
        }

        // Validation is done on the values that will be stored, the handlers
        // are allowed to normalise them in place (e.g. material names).
        for (String name : needsValidation) {
            String[] values = after.get(name);
            if (values == null) {
                continue;
            }
            Tag.Creation creation = tagRegistry.getCreationHandler(name);
            if (creation != null && !creation.created(target, player, values)) {
                if (player == null) {
                    // Handlers tell the player themselves, but have nobody to
                    // tell when run from the console
                    sender.sendMessage(Lang.getNegativePrefix()
                                       + Lang.translateInsertVariables(
                                           "command.edit.error.rejected",
                                           name));
                }
                return null;
            }
        }

        // Nothing rejected it so commit to the target.
        for (String name : before.keySet()) {
            if (!after.containsKey(name)) {
                target.removeArg(name);
            }
        }
        for (Map.Entry<String, String[]> entry : after.entrySet()) {
            if (!Arrays.equals(before.get(entry.getKey()), entry.getValue())) {
                target.setArgValues(entry.getKey(), entry.getValue());
            }
        }
        return new Snapshot(before, after);
    }

    /**
     * Call once the edited target has been saved to let removed tags clean up.
     */
    public void finish(TagTarget target, CommandSenderContainer sender,
                       Snapshot snapshot) {
        PlayerContainer player = sender.getPlayerContainer();
        for (Map.Entry<String, String[]> entry : snapshot.before.entrySet()) {
            if (snapshot.after.containsKey(entry.getKey())) {
                continue;
            }
            Tag.Creation creation =
                tagRegistry.getCreationHandler(entry.getKey());
            if (creation != null) {
                creation.destroyed(target, player, entry.getValue());
            }
        }
    }

    private boolean checkTagUsable(String name, Tag.TagType targetType,
                                   CommandSenderContainer sender) {
        if (name.equals(NameTag.TAG_NAME)) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.edit.error.name"));
            return false;
        }
        Tag tag = tagRegistry.getTag(name);
        if (tag == null || !Arrays.asList(tag.getTagTypes()).contains(targetType)) {
            sender.sendMessage(Lang.getNegativePrefix()
                               + Lang.translateInsertVariables(
                                   "command.edit.error.invalidtag", name));
            return false;
        }
        return true;
    }

    private static Map<String, String[]> copyOf(List<DataTag> tags) {
        Map<String, String[]> map = new LinkedHashMap<>();
        for (DataTag tag : tags) {
            map.put(tag.NAME, tag.VALUES.clone());
        }
        return map;
    }
}
