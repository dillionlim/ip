package tally;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

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
        for (String command : new String[] {"list", "todo read book", "mark 1", "find book",
            "delete 1", "blah", "mark 99"}) {
            assertFalse(tally.getResponse(command).isBlank(), "empty reply for: " + command);
        }
    }

    @Test
    public void isExiting_onlyAfterGoodbye() {
        Tally tally = newTally();
        tally.getResponse("todo read book");
        assertFalse(tally.isExiting());
        assertTrue(tally.getResponse("bye").contains("Bye."));
        assertTrue(tally.isExiting());
    }

    @Test
    public void getResponse_changes_areSavedForTheNextRun() {
        Path file = folder.resolve("tally.txt");
        new Tally(file, false).getResponse("todo read book");
        assertEquals("1.[T][ ] read book",
                new Tally(file, false).getResponse("list").lines().skip(1).findFirst().orElse(""));
    }
}
