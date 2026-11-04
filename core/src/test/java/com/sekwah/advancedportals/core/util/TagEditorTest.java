package com.sekwah.advancedportals.core.util;

import static org.junit.jupiter.api.Assertions.*;

import com.sekwah.advancedportals.core.TestSupport;
import com.sekwah.advancedportals.core.TestSupport.FakeSender;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TagEditorTest {
    /** A multi value tag that rejects the value "bad" and counts removals */
    static class TestTag implements Tag.Creation, Tag.Split {
        final String name;
        int destroyed = 0;

        TestTag(String name) {
            this.name = name;
        }

        @Override
        public Tag.TagType[] getTagTypes() {
            return new Tag.TagType[] {Tag.TagType.PORTAL};
        }
        @Override
        public String getName() {
            return name;
        }
        @Override
        public String[] getAliases() {
            return null;
        }
        @Override
        public String description() {
            return "";
        }
        @Override
        public boolean created(TagTarget target, PlayerContainer player,
                               String[] argData) {
            for (int i = 0; i < argData.length; i++) {
                if (argData[i].equals("bad")) {
                    return false;
                }
                // Normalises in place like the triggerblock tag
                argData[i] = argData[i].toUpperCase();
            }
            return true;
        }
        @Override
        public void destroyed(TagTarget target, PlayerContainer player,
                              String[] argData) {
            destroyed++;
        }
    }

    TagEditor editor;
    TestTag tag;
    AdvancedPortal portal;
    FakeSender sender;

    @BeforeEach
    void setUp() {
        TagRegistry registry = TestSupport.tagRegistry();
        tag = new TestTag("destination");
        TestSupport.registerActivation(registry, tag);
        TestSupport.registerActivation(registry, new TestTag("name"));
        editor = new TagEditor();
        TestSupport.set(editor, "tagRegistry", registry);

        portal = new AdvancedPortal();
        portal.setArgValues("name", new String[] {"portal"});
        portal.setArgValues("destination", new String[] {"A", "B"});
        sender = new FakeSender();
    }

    private TagEditor.EditRequest request() {
        return new TagEditor.EditRequest();
    }

    @Test
    void setReplacesTheValuesAndRunsTheCreationHandler() {
        TagEditor.EditRequest request = request();
        request.set.add(new DataTag("destination", "c"));

        TagEditor.Snapshot snapshot = editor.apply(portal, sender.container(), request);

        assertNotNull(snapshot);
        // Handler normalised the value before it was stored
        assertArrayEquals(new String[] {"C"}, portal.getArgValues("destination"));
        assertTrue(snapshot.getChanged().contains("destination"));
    }

    @Test
    void addAppendsWithoutDuplicates() {
        TagEditor.EditRequest request = request();
        request.add.add(new DataTag("destination", "B", "C"));

        assertNotNull(editor.apply(portal, sender.container(), request));

        assertArrayEquals(new String[] {"A", "B", "C"},
                          portal.getArgValues("destination"));
    }

    @Test
    void removeASingleValueKeepsTheOthers() {
        TagEditor.EditRequest request = request();
        request.remove.add(new DataTag("destination", "A"));

        assertNotNull(editor.apply(portal, sender.container(), request));

        assertArrayEquals(new String[] {"B"}, portal.getArgValues("destination"));
    }

    @Test
    void removingTheLastValueRemovesTheTag() {
        TagEditor.EditRequest request = request();
        request.remove.add(new DataTag("destination", "A", "B"));

        assertNotNull(editor.apply(portal, sender.container(), request));

        assertNull(portal.getArgValues("destination"));
    }

    @Test
    void removingAWholeTagCallsDestroyedOnFinish() {
        TagEditor.EditRequest request = request();
        request.remove.add(new DataTag("destination"));

        TagEditor.Snapshot snapshot = editor.apply(portal, sender.container(), request);
        assertNotNull(snapshot);
        assertNull(portal.getArgValues("destination"));
        assertEquals(0, tag.destroyed);

        editor.finish(portal, sender.container(), snapshot);
        assertEquals(1, tag.destroyed);
    }

    @Test
    void aRejectedTagChangesNothing() {
        TagEditor.EditRequest request = request();
        request.set.add(new DataTag("destination", "bad"));

        assertNull(editor.apply(portal, sender.container(), request));

        assertArrayEquals(new String[] {"A", "B"}, portal.getArgValues("destination"));
    }

    @Test
    void theNameCannotBeEditedOrRemoved() {
        TagEditor.EditRequest set = request();
        set.set.add(new DataTag("name", "other"));
        assertNull(editor.apply(portal, sender.container(), set));

        TagEditor.EditRequest remove = request();
        remove.remove.add(new DataTag("name"));
        assertNull(editor.apply(portal, sender.container(), remove));

        assertEquals("portal", portal.getName());
        assertFalse(sender.messages.isEmpty());
    }

    @Test
    void unknownTagsAndMissingValuesAreRejected() {
        TagEditor.EditRequest unknown = request();
        unknown.set.add(new DataTag("nothing", "x"));
        assertNull(editor.apply(portal, sender.container(), unknown));

        TagEditor.EditRequest notSet = request();
        notSet.remove.add(new DataTag("cooldown"));
        assertNull(editor.apply(portal, sender.container(), notSet));

        TagEditor.EditRequest noValue = request();
        noValue.remove.add(new DataTag("destination", "Z"));
        assertNull(editor.apply(portal, sender.container(), noValue));
    }

    @Test
    void revertRestoresTheOriginalTags() {
        TagEditor.EditRequest request = request();
        request.set.add(new DataTag("destination", "x"));
        request.remove.add(new DataTag("destination", "A"));
        // remove is applied first, then set replaces the rest
        TagEditor.Snapshot snapshot = editor.apply(portal, sender.container(), request);
        assertNotNull(snapshot);

        snapshot.revert(portal);

        assertArrayEquals(new String[] {"A", "B"}, portal.getArgValues("destination"));
        List<String> names = new ArrayList<>();
        for (DataTag t : portal.getArgs()) names.add(t.NAME);
        assertTrue(names.contains("name"));
    }
}
