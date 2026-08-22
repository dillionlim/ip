package tally.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Tests that a TaskList holds tasks in order and keeps its own copy of them. */
public class TaskListTest {
    @Test
    public void add_thenGet_keepsTheOrderTheyWereAdded() {
        TaskList tasks = new TaskList();
        tasks.add(new Todo("first"));
        tasks.add(new Todo("second"));
        assertEquals(2, tasks.size());
        assertEquals("[T][ ] first", tasks.get(0).toString());
        assertEquals("[T][ ] second", tasks.get(1).toString());
    }

    @Test
    public void remove_middleTask_shiftsTheRestUp() {
        TaskList tasks = new TaskList();
        tasks.add(new Todo("first"));
        Task middle = new Todo("second");
        tasks.add(middle);
        tasks.add(new Todo("third"));
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
        tasks.add(first);
        tasks.add(second);
        tasks.remove(first);
        assertEquals(1, tasks.size());
        assertEquals("[T][ ] same", tasks.get(0).toString());
    }

    @Test
    public void isEmpty_reflectsWhetherAnythingIsHeld() {
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
