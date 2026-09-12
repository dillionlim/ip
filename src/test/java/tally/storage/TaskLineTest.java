package tally.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tally.task.Task;

/**
 * Tests the one line a task is written as, read back.
 *
 * <p>Asked of TaskLine directly rather than through Storage, since none of this needs
 * a file: these are questions about the format, and a line that does not describe a
 * task is answered with an empty Optional however it reached us.
 */
public class TaskLineTest {
    @Test
    public void read_aLineOfEachKind_readsThemBackAsThemselves() {
        assertEquals("[T][ ] read book", TaskLine.read("T | 0 | read book").orElseThrow().toString());
        assertEquals("[D][X] return book (by: Oct 15 2019)",
                TaskLine.read("D | 1 | return book | 2019-10-15").orElseThrow().toString());
        assertEquals("[E][ ] project meeting (from: Mon 2pm to: 4pm)",
                TaskLine.read("E | 0 | project meeting | Mon 2pm | 4pm").orElseThrow().toString());
        assertEquals("[W][ ] submit form (window: Sep 08 2026 to Sep 12 2026)",
                TaskLine.read("W | 0 | submit form | 2026-09-08 | 2026-09-12").orElseThrow().toString());
    }

    @Test
    public void read_theDoneFlag_decidesWhetherTheTaskIsDone() {
        assertEquals(false, TaskLine.read("T | 0 | read book").orElseThrow().isDone());
        assertEquals(true, TaskLine.read("T | 1 | read book").orElseThrow().isDone());
        // Anything else in that field is not a flag at all.
        assertTrue(TaskLine.read("T | 2 | read book").isEmpty());
        assertTrue(TaskLine.read("T | yes | read book").isEmpty());
    }

    @Test
    public void read_aTypeLetterNobodyWrites_isNoTask() {
        assertTrue(TaskLine.read("Q | 0 | whatever this is").isEmpty());
        assertTrue(TaskLine.read("| 0 | no letter at all").isEmpty());
        assertTrue(TaskLine.read("not a task line").isEmpty());
        assertTrue(TaskLine.read("").isEmpty());
    }

    @Test
    public void read_theWrongNumberOfFields_isNoTask() {
        assertTrue(TaskLine.read("T | 0 | read book | extra").isEmpty());
        assertTrue(TaskLine.read("D | 0 | return book").isEmpty());
        assertTrue(TaskLine.read("E | 0 | meeting | 2pm").isEmpty());
        assertTrue(TaskLine.read("W | 0 | form | 2026-09-08").isEmpty());
    }

    @Test
    public void read_aBlankFieldWhereOneIsRequired_isNoTask() {
        assertTrue(TaskLine.read("T |   | read book").isEmpty());
        assertTrue(TaskLine.read("T | 0 |   ").isEmpty());
        assertTrue(TaskLine.read("D | 0 |   | 2019-10-15").isEmpty());
    }

    @Test
    public void read_datesThatCannotBeRead_isNoTask() {
        assertTrue(TaskLine.read("D | 0 | return book | last Tuesday").isEmpty());
        assertTrue(TaskLine.read("W | 0 | submit form | last Tuesday | 2026-09-12").isEmpty());
        assertTrue(TaskLine.read("W | 0 | submit form | 2026-09-08 | whenever").isEmpty());
        // Nobody types February 30th, and LocalDate will not read it either.
        assertTrue(TaskLine.read("D | 0 | return book | 2026-02-30").isEmpty());
    }

    @Test
    public void read_endsThatRunBackwards_isNoTask() {
        // The parser refuses both of these, so a file holding one was edited by hand.
        assertTrue(TaskLine.read("W | 0 | submit form | 2026-09-12 | 2026-09-08").isEmpty());
        assertTrue(TaskLine.read("E | 0 | trip | 2026-09-12 | 2026-09-08").isEmpty());
        // Padded the same way, since the ends are tidied before they are read.
        assertTrue(TaskLine.read("E | 0 | trip |  2026-09-12 | 2026-09-08").isEmpty());
    }

    @Test
    public void read_anEventWhoseEndsAreNotDates_isTakenInAnyOrder() {
        Task standup = TaskLine.read("E | 0 | standup | 4pm | Mon 2pm").orElseThrow();
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
            assertEquals(line, TaskLine.read(line).orElseThrow().toSaveFormat(), line);
        }
    }
}
