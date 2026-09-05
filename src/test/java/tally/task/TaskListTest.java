package tally.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Tests that a TaskList holds tasks in order and keeps its own copy of them. */
public class TaskListTest {
    private static final LocalDate SEP_9 = LocalDate.of(2026, 9, 9);

    @Test
    public void findFreeRun_emptyTally_offersTheDayAskedFrom() {
        assertEquals(Optional.of(SEP_9), new TaskList().findFreeRun(1, SEP_9));
    }

    @Test
    public void findFreeRun_deadlineAndWindow_skipsEveryDayTheyTakeUp() {
        TaskList tasks = new TaskList();
        tasks.add(new Deadline("report", LocalDate.of(2026, 9, 10)),
                new Window("certificate", LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 14)));
        // Sep 9 free, 10 taken, 11 free, 12-14 taken, so three in a row start on the 15th.
        assertEquals(Optional.of(SEP_9), tasks.findFreeRun(1, SEP_9));
        assertEquals(Optional.of(LocalDate.of(2026, 9, 15)), tasks.findFreeRun(3, SEP_9));
    }

    @Test
    public void findFreeRun_eventWrittenAsDates_takesUpThoseDays() {
        TaskList tasks = new TaskList();
        tasks.add(new Event("trip", "2026-09-09", "2026-09-11"));
        assertEquals(Optional.of(LocalDate.of(2026, 9, 12)), tasks.findFreeRun(1, SEP_9));
    }

    @Test
    public void findFreeRun_eventWrittenAsText_takesUpNothingAndIsReported() {
        TaskList tasks = new TaskList();
        tasks.add(new Event("standup", "Mon 2pm", "3pm"));
        assertEquals(Optional.of(SEP_9), tasks.findFreeRun(1, SEP_9));
        assertTrue(tasks.hasUnreadableDates());
    }

    @Test
    public void findFreeRun_everyDayTakenForAYear_findsNothing() {
        TaskList tasks = new TaskList();
        tasks.add(new Window("busy", SEP_9, SEP_9.plusYears(2)));
        assertEquals(Optional.empty(), tasks.findFreeRun(1, SEP_9));
    }

    @Test
    public void add_thenGet_keepsTheOrderTheyWereAdded() {
        TaskList tasks = new TaskList();
        tasks.add(new Todo("first"), new Todo("second"));
        assertEquals(2, tasks.size());
        assertEquals("[T][ ] first", tasks.get(0).toString());
        assertEquals("[T][ ] second", tasks.get(1).toString());
    }

    @Test
    public void remove_middleTask_shiftsTheRestUp() {
        TaskList tasks = new TaskList();
        Task middle = new Todo("second");
        tasks.add(new Todo("first"), middle, new Todo("third"));
        tasks.remove(middle);
        assertEquals(2, tasks.size());
        assertEquals("[T][ ] first", tasks.get(0).toString());
        assertEquals("[T][ ] third", tasks.get(1).toString());
    }

    @Test
    public void remove_oneOfTwoAlikeTasks_removesTheOneGiven() {
        // The two tasks read the same, so removing by value could take the wrong one.
        TaskList tasks = new TaskList();
        Task first = new Todo("same");
        Task second = new Todo("same");
        first.markAsDone();
        tasks.add(first, second);
        tasks.remove(first);
        assertEquals(1, tasks.size());
        assertEquals("[T][ ] same", tasks.get(0).toString());
    }

    @Test
    public void findPositions_wordInSomeDescriptions_returnsTheirPositions() {
        TaskList tasks = new TaskList();
        tasks.add(new Todo("read book"), new Todo("buy bread"), new Todo("return book"));
        assertEquals(List.of(0, 2), tasks.findPositions("book"));
    }

    @Test
    public void findPositions_positionsAreOnTheWholeTally_notAmongTheMatches() {
        // The second match sits at position 2, not at position 1 of the results.
        TaskList tasks = new TaskList();
        tasks.add(new Todo("buy bread"), new Todo("read book"), new Todo("return book"));
        assertEquals(List.of(1, 2), tasks.findPositions("book"));
    }

    @Test
    public void findPositions_differentCase_stillMatches() {
        TaskList tasks = new TaskList();
        tasks.add(new Todo("Read Book"));
        assertEquals(List.of(0), tasks.findPositions("book"));
        assertEquals(List.of(0), tasks.findPositions("BOOK"));
    }

    @Test
    public void findPositions_wordInNoDescription_returnsNothing() {
        TaskList tasks = new TaskList();
        tasks.add(new Todo("read book"));
        assertTrue(tasks.findPositions("umbrella").isEmpty());
        assertTrue(new TaskList().findPositions("book").isEmpty());
    }

    @Test
    public void findPositions_wordInsideALongerWord_stillMatches() {
        TaskList tasks = new TaskList();
        tasks.add(new Todo("bookshop"));
        assertEquals(List.of(0), tasks.findPositions("book"));
    }

    @Test
    public void add_severalAtOnce_keepsTheOrderGiven() {
        TaskList tasks = new TaskList();
        tasks.add(new Todo("first"), new Todo("second"), new Todo("third"));
        assertEquals(3, tasks.size());
        assertEquals("[T][ ] first", tasks.get(0).toString());
        assertEquals("[T][ ] third", tasks.get(2).toString());
    }

    @Test
    public void add_noTasks_leavesTheTallyAlone() {
        TaskList tasks = new TaskList();
        tasks.add(new Todo("only"));
        tasks.add();
        assertEquals(1, tasks.size());
    }

    @Test
    public void isEmpty_withAndWithoutTasks_reflectsWhatIsHeld() {
        TaskList tasks = new TaskList();
        assertTrue(tasks.isEmpty());
        tasks.add(new Todo("something"));
        assertFalse(tasks.isEmpty());
    }

    @Test
    public void constructor_givenList_doesNotShareItWithTheCaller() {
        List<Task> given = new ArrayList<>();
        given.add(new Todo("first"));
        TaskList tasks = new TaskList(given);
        given.add(new Todo("added behind the tally's back"));
        assertEquals(1, tasks.size());
    }

    @Test
    public void asList_changingTheResult_doesNotChangeTheTally() {
        TaskList tasks = new TaskList();
        tasks.add(new Todo("first"));
        tasks.asList().clear();
        assertEquals(1, tasks.size());
    }
}
