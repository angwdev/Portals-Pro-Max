package com.sekwah.advancedportals.core.destination;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.warphandler.ActivationData;
import com.sekwah.advancedportals.core.warphandler.Tag;
import com.sekwah.advancedportals.core.warphandler.TriggerType;
import java.util.*;

/**
 * Possibly look at adding the ability to add some tags to destinations such as
 * permissions. Would make it easier to add permissions to block access to
 * certain areas and such. Could be a different permission system or just it
 * takes the tags on the destination and automatically applies them when a
 * portal wants to warp to there. (Of course it would not work cross server
 * unless the data was communicated and checked first however that could affect
 * performance and would definitely affect speed)
 *
 * @author sekwah41
 */
public class Destination implements TagTarget {
    @Inject
    transient TagRegistry tagRegistry;

    private volatile PlayerLocation loc;

    private final HashMap<String, String[]> args = new HashMap<>();
    private final transient List<DataTag> destiTags = new ArrayList<>();

    private transient volatile boolean isSorted = false;

    public Destination() {
        this.loc = new PlayerLocation();
    }

    public Destination(PlayerLocation loc) {
        this.loc = loc;
    }

    @Override
    public synchronized String[] getArgValues(String argName) {
        return this.args.get(argName);
    }

    @Override
    public synchronized void setArgValues(String argName, String[] argValue) {
        this.isSorted = false;
        this.args.put(argName, argValue);
    }

    @Override
    public synchronized void addArg(String argName, String argValues) {
        this.isSorted = false;
        String[] existing = this.args.get(argName);
        if (existing == null) {
            this.args.put(argName, new String[] {argValues});
            return;
        }
        String[] updated = Arrays.copyOf(existing, existing.length + 1);
        updated[existing.length] = argValues;
        this.args.put(argName, updated);
    }

    public void setArgValues(DataTag portalTag) {
        this.setArgValues(portalTag.NAME, portalTag.VALUES);
    }

    @Override
    public synchronized void removeArg(String arg) {
        this.isSorted = false;
        this.args.remove(arg);
    }

    private void updateDestiTagList() {
        destiTags.clear();
        for (Map.Entry<String, String[]> entry : args.entrySet()) {
            this.destiTags.add(new DataTag(entry.getKey(), entry.getValue()));
        }

        this.destiTags.sort(Comparator.comparingInt(o -> {
            Tag tag = tagRegistry.getTag(o.NAME);
            if (tag instanceof Tag.OrderPriority) {
                Tag.OrderPriority tagPriority = (Tag.OrderPriority) tag;
                return tagPriority.getPriority().ordinal();
            } else {
                return Tag.Priority.NORMAL.ordinal();
            }
        }));
        isSorted = true;
    }

    public boolean activate(PlayerContainer player) {
        ActivationData data = new ActivationData(TriggerType.MANUAL);
        if (this.portalActivate(player, data)) {
            this.postActivate(player, data);
            return true;
        }
        return false;
    }

    public boolean portalActivate(PlayerContainer player, ActivationData data) {
        if (!isSorted) {
            updateDestiTagList();
        }
        DataTag[] destiTags = new DataTag[args.size()];
        int i = 0;
        for (Map.Entry<String, String[]> entry : args.entrySet()) {
            destiTags[i++] = new DataTag(entry.getKey(), entry.getValue());
        }
        for (DataTag destiTag : destiTags) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                destiTag.NAME, Tag.TagType.DESTINATION);
            if (activationHandler != null
                && !activationHandler.preActivated(
                    this, player, data, this.getArgValues(destiTag.NAME))) {
                return false;
            }
        }
        for (DataTag destiTag : destiTags) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                destiTag.NAME, Tag.TagType.DESTINATION);
            if (activationHandler != null) {
                activationHandler.activated(this, player, data,
                                            this.getArgValues(destiTag.NAME));
            }
        }
        return true;
    }

    public void postActivate(PlayerContainer player, ActivationData data) {
        DataTag[] destiTags = new DataTag[args.size()];
        int i = 0;
        for (Map.Entry<String, String[]> entry : args.entrySet()) {
            destiTags[i++] = new DataTag(entry.getKey(), entry.getValue());
        }
        for (DataTag destiTag : destiTags) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                destiTag.NAME, Tag.TagType.DESTINATION);
            if (activationHandler != null) {
                activationHandler.postActivated(
                    this, player, data, this.getArgValues(destiTag.NAME));
            }
        }
    }

    @Override
    public synchronized ArrayList<DataTag> getArgs() {
        ArrayList<DataTag> tagList = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : this.args.entrySet()) {
            tagList.add(new DataTag(entry.getKey(), entry.getValue()));
        }
        return tagList;
    }

    @Override
    public String getName() {
        String[] name = this.getArgValues("name");
        return name == null || name.length == 0 ? null : name[0];
    }

    @Override
    public Tag.TagType targetTagType() {
        return Tag.TagType.DESTINATION;
    }

    public PlayerLocation getLoc() {
        return loc;
    }

    public void setLoc(PlayerLocation loc) {
        this.loc = loc;
    }
}
