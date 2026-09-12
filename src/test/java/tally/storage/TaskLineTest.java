package tally.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import tally.task.Task;

/**
 * Tests the one line a task is written as, read back.
 *
 * <p>Asked of TaskLine directly rather than through Storage, since none of this needs
 * a file: these are questions about the format, and a line that does not describe a
 * task is answered with null however it reached us.
 */
public class TaskLineTest {
    @Test
    public void read_aLineOfEachKind_readsThemBackAsThemselves() {
        assertEquals("[T][ ] read book", TaskLine.read("T | 0 | read book").toString());
        assertEquals("[D][X] return book (by: Oct 15 2019)",
                TaskLine.read("D | 1 | return book | 2019-10-15").toString());
        assertEquals("[E][ ] project meeting (from: Mon 2pm to: 4pm)",
                TaskLine.read("E | 0 | project meeting | Mon 2pm | 4pm").toString());
        assertEquals("[W][ ] submit form (window: Sep 08 2026 to Sep 12 2026)",
                TaskLine.read("W | 0 | submit form | 2026-09-08 | 2026-09-12").toString());
    }

    @Test
    public void read_theDoneFlag_decidesWhetherTheTaskIsDone() {
        assertEquals(false, TaskLine.read("T | 0 | read book").isDone());
        assertEquals(true, TaskLine.read("T | 1 | read book").isDone());
        // Anything else in that field is not a flag at all.
        assertNull(TaskLine.read("T | 2 | read book"));
        assertNull(TaskLine.read("T | yes | read book"));
    }

    @Test
    public void read_aTypeLetterNobodyWrites_isNoTask() {
        assertNull(TaskLine.read("Q | 0 | whatever this is"));
        assertNull(TaskLine.read("| 0 | no letter at all"));
        assertNull(TaskLine.read("not a task line"));
        assertNull(TaskLine.read(""));
    }

    @Test
    public void read_theWrongNumberOfFields_isNoTask() {
        assertNull(TaskLine.read("T | 0 | read book | extra"));
        assertNull(TaskLine.read("D | 0 | return book"));
        assertNull(TaskLine.read("E | 0 | meeting | 2pm"));
        assertNull(TaskLine.read("W | 0 | form | 2026-09-08"));
    }

    @Test
    public void read_aBlankFieldWhereOneIsRequired_isNoTask() {
        assertNull(TaskLine.read("T |   | read book"));
        assertNull(TaskLine.read("T | 0 |   "));
        assertNull(TaskLine.read("D | 0 |   | 2019-10-15"));
    }

    @Test
    public void read_datesThatCannotBeRead_isNoTask() {
        assertNull(TaskLine.read("D | 0 | return book | last Tuesday"));
        assertNull(TaskLine.read("W | 0 | submit form | last Tuesday | 2026-09-12"));
        assertNull(TaskLine.read("W | 0 | submit form | 2026-09-08 | whenever"));
        // Nobody types February 30th, and LocalDate will not read it either.
        assertNull(TaskLine.read("D | 0 | return book | 2026-02-30"));
    }

    @Test
    public void read_endsThatRunBackwards_isNoTask() {
        // The parser refuses both of these, so a file holding one was edited by hand.
        assertNull(TaskLine.read("W | 0 | submit form | 2026-09-12 | 2026-09-08"));
        assertNull(TaskLine.read("E | 0 | trip | 2026-09-12 | 2026-09-08"));
        // Padded the same way, since the ends are tidied before they are read.
        assertNull(TaskLine.read("E | 0 | trip |  2026-09-12 | 2026-09-08"));
    }

    @Test
    public void read_anEventWhoseEndsAreNotDates_isTakenInAnyOrder() {
        Task standup = TaskLine.read("E | 0 | standup | 4pm | Mon 2pm");
        assertEquals("[E][ ] standup (from: 4pm to: Mon 2pm)", standup.toString());
    }

    @Test
    public void read_aTaskItRead_writesBackTheSameLine() {
        for (String line : new String[] {
            "T | 1 | read book",
            "D | 0 | return book | 2019-10-15",
            "E | 1 | project meeting | Mon 2pm | 4pm",
            "W | 0 | submit form | 2026-09-08 | 2026-09-12",
        }) {
            assertEquals(line, TaskLine.read(line).toSaveFormat(), line);
        }
    }
}
