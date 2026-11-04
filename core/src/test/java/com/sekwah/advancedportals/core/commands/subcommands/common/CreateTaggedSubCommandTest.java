package com.sekwah.advancedportals.core.commands.subcommands.common;

import static org.junit.jupiter.api.Assertions.*;

import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.util.TagReader;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateTaggedSubCommandTest {
    static class FakeTag implements Tag {
        final String name;
        final String[] aliases;

        FakeTag(String name, String... aliases) {
            this.name = name;
            this.aliases = aliases.length == 0 ? null : aliases;
        }

        @Override
        public TagType[] getTagTypes() {
            return new TagType[] {TagType.PORTAL};
        }
        @Override
        public String getName() {
            return name;
        }
        @Override
        public String[] getAliases() {
            return aliases;
        }
        @Override
        public String description() {
            return "";
        }
    }

    static class SplitTag extends FakeTag implements Tag.Split {
        SplitTag(String name, String... aliases) {
            super(name, aliases);
        }
    }

    static class TestCommand extends CreateTaggedSubCommand {
        final List<Tag> tags = Arrays.asList(
            new FakeTag("name"), new SplitTag("destination", "desti"),
            new FakeTag("message"), new SplitTag("triggerblock"));

        @Override
        protected List<Tag> getRelatedTags() {
            return tags;
        }
        @Override
        public void onCommand(CommandSenderContainer sender, String[] args) {}
        @Override
        public boolean hasPermission(CommandSenderContainer sender) {
            return true;
        }
        @Override
        public String getBasicHelpText() {
            return "";
        }
        @Override
        public String getDetailedHelpText() {
            return "";
        }

        List<DataTag> process(String... args) {
            ArrayList<DataTag> parsed = TagReader.getTagsFromArgs(args);
            filterAndProcessTags(parsed);
            return parsed;
        }
    }

    private static DataTag find(List<DataTag> tags, String name) {
        return tags.stream().filter(t -> t.NAME.equals(name)).findFirst().orElse(null);
    }

    @Test
    void messagesWithCommasAreNotSplitByOtherTagsSplitting() {
        // This used to turn into two messages because the destination tag
        // (which comes first in the registry) split every tags values.
        List<DataTag> tags = new TestCommand().process(
            "name:test", "message:\"Welcome,", "traveler\"");

        assertArrayEquals(new String[] {"Welcome, traveler"},
                          find(tags, "message").VALUES);
    }

    @Test
    void splittingTagsAreSplitAndTrimmed() {
        List<DataTag> tags = new TestCommand().process(
            "name:test", "destination:a,", "b");

        assertArrayEquals(new String[] {"a", "b"},
                          find(tags, "destination").VALUES);
    }

    @Test
    void aliasesBecomeTheRealNameAndAreMerged() {
        List<DataTag> tags = new TestCommand().process(
            "name:test", "desti:a", "destination:b");

        assertEquals(1, tags.stream().filter(t -> t.NAME.equals("destination")).count());
        assertArrayEquals(new String[] {"a", "b"},
                          find(tags, "destination").VALUES);
        assertNull(find(tags, "desti"));
    }

    @Test
    void unknownTagsAreDroppedAndNameComesFirst() {
        List<DataTag> tags = new TestCommand().process(
            "message:hi", "bogus:1", "name:test");

        assertNull(find(tags, "bogus"));
        assertEquals("name", tags.get(0).NAME);
    }
}
