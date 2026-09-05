package tally;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the replies Tally hands back for a front end to display. */
public class TallyTest {
    @TempDir
    private Path folder;

    private Tally newTally() {
        return new Tally(folder.resolve("tally.txt"), false);
    }

    @Test
    public void getGreeting_freshTally_greetsWithoutComplaining() {
        String greeting = newTally().getGreeting();
        assertTrue(greeting.contains("Hello! I'm Tally."));
        assertTrue(greeting.contains("What can I do for you?"));
    }

    @Test
    public void getResponse_addThenList_reportsBoth() {
        Tally tally = newTally();
        assertTrue(tally.getResponse("todo read book").contains("[T][ ] read book"));
        assertTrue(tally.getResponse("list").contains("1.[T][ ] read book"));
    }

    @Test
    public void getResponse_badCommand_returnsTheComplaintRatherThanThrowing() {
        assertTrue(newTally().getResponse("blah").startsWith("I don't know that one."));
    }

    @Test
    public void getResponse_reply_carriesNoHorizontalRules() {
        // The rules separate messages in a terminal; a chat window separates them itself.
        assertFalse(newTally().getResponse("todo read book").contains("____"));
    }

    @Test
    public void getResponse_everyReply_isNeverEmpty() {
        Tally tally = newTally();
        List<String> commands = List.of("list", "todo read book", "mark 1", "find book",
                "delete 1", "blah", "mark 99");
        for (String command : commands) {
            assertFalse(tally.getResponse(command).isBlank(), "empty reply for: " + command);
        }
    }

    @Test
    public void isExiting_beforeAndAfterGoodbye_flipsOnlyOnGoodbye() {
        Tally tally = newTally();
        tally.getResponse("todo read book");
        assertFalse(tally.isExiting());
        assertTrue(tally.getResponse("bye").contains("Bye."));
        assertTrue(tally.isExiting());
    }

    @Test
    public void getResponse_freeFromTheLastWritableDate_namesOnlyTheDaysItSearched() {
        // Only one day can be written down at all from there, so a reply naming a year
        // would be describing days the search never looked at and could not offer.
        String reply = newTally().getResponse("free /for 2 /from 9999-12-31");
        assertTrue(reply.contains("from Dec 31 9999 to Dec 31 9999"), reply);
    }

    @Test
    public void getResponse_saveFails_doesNotAnnounceTheChangeFirst() throws IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | read book\n");
        assumeTrue(Files.getFileStore(file).supportsFileAttributeView(PosixFileAttributeView.class),
                "this file system does not carry POSIX permissions");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--------"));
        assumeTrue(!Files.isWritable(file), "these tests are running as a user nothing stops");

        Tally tally = new Tally(file, false);
        String reply = tally.getResponse("todo write essay");

        // Saying "Got it" and then taking it back leaves the user unsure which happened.
        assertFalse(reply.contains("Got it."), "a failed save was announced as a success: " + reply);
        assertTrue(reply.contains("could not save"), reply);
        assertFalse(tally.getResponse("list").contains("write essay"),
                "the tally kept a change that never reached the file");
    }

    @Test
    public void getResponse_changes_areSavedForTheNextRun() {
        Path file = folder.resolve("tally.txt");
        new Tally(file, false).getResponse("todo read book");
        assertEquals("1.[T][ ] read book",
                new Tally(file, false).getResponse("list").lines().skip(1).findFirst().orElse(""));
    }
}
