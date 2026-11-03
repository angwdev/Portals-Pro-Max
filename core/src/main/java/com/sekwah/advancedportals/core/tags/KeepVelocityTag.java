package com.sekwah.advancedportals.core.tags;

import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.warphandler.ActivationData;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * keepvelocity:true makes the player keep their momentum through the warp
 * (launch pads, minecart style tracks, speed runs...). It can be set on the
 * portal or on a destination. Only platforms that support it (Paper) apply it.
 */
public class KeepVelocityTag
    implements Tag.Activation, Tag.AutoComplete, Tag.Creation {
    public static final String TAG_NAME = "keepvelocity";

    private final TagType[] tagTypes =
        new TagType[] {TagType.PORTAL, TagType.DESTINATION};

    @Override
    public TagType[] getTagTypes() {
        return tagTypes;
    }

    @Override
    public String getName() {
        return TAG_NAME;
    }

    @Nullable
    @Override
    public String[] getAliases() {
        return new String[] {"momentum"};
    }

    @Override
    public String description() {
        return Lang.translate("tag.keepvelocity.description");
    }

    @Override
    public boolean preActivated(TagTarget target, PlayerContainer player,
                                ActivationData activeData, String[] argData) {
        // Set this early so it is known by the time the destination teleports
        if (argData != null && argData.length > 0
            && argData[0].equalsIgnoreCase("true")) {
            activeData.setMetadata(TAG_NAME, "true");
        }
        return true;
    }

    @Override
    public void postActivated(TagTarget target, PlayerContainer player,
                              ActivationData activationData, String[] argData) {}

    @Override
    public boolean activated(TagTarget target, PlayerContainer player,
                             ActivationData activationData, String[] argData) {
        return true;
    }

    @Override
    public List<String> autoComplete(String argData) {
        return Arrays.asList("true", "false");
    }

    @Override
    public boolean created(TagTarget target, PlayerContainer player,
                           String[] argData) {
        if (argData != null && argData.length == 1
            && (argData[0].equalsIgnoreCase("true")
                || argData[0].equalsIgnoreCase("false"))) {
            return true;
        }
        if (player != null) {
            player.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("tag.keepvelocity.fail"));
        }
        return false;
    }

    @Override
    public void destroyed(TagTarget target, PlayerContainer player,
                          String[] argData) {}
}
