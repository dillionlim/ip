package tally.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Tests how each kind of task shows itself to the user and writes itself to the file. */
public class TaskTest {
    @Test
    public void toString_window_showsBothEndsInTheDisplayFormat() {
        Window window = new Window("submit form",
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 12));
        assertEquals("[W][ ] submit form (window: Sep 08 2026 to Sep 12 2026)",
                window.toString());
    }

    @Test
    public void toSaveFormat_window_writesBothDatesInTheFormatItReadsBack() {
        Window window = new Window("submit form",
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 12));
        assertEquals("W | 0 | submit form | 2026-09-08 | 2026-09-12", window.toSaveFormat());
    }

    @Test
    public void toString_todo_tagsTypeAndCheckbox() {
        assertEquals("[T][ ] read book", new Todo("read book").toString());
    }

    @Test
    public void toString_afterMarking_showsTheCross() {
        Todo todo = new Todo("read book");
        todo.markAsDone();
        assertEquals("[T][X] read book", todo.toString());
        todo.markAsNotDone();
        assertEquals("[T][ ] read book", todo.toString());
    }

    @Test
    public void toString_deadline_showsDateInTheDisplayFormat() {
        Deadline deadline = new Deadline("return book", LocalDate.of(2019, 10, 15));
        // Level-8 asks that the date be shown in a different format from the one typed.
        assertEquals("[D][ ] return book (by: Oct 15 2019)", deadline.toString());
    }

    @Test
    public void toString_event_showsBothTimes() {
        Event event = new Event("project meeting", "Mon 2pm", "4pm");
        assertEquals("[E][ ] project meeting (from: Mon 2pm to: 4pm)", event.toString());
    }

    @Test
    public void toSaveFormat_todo_writesTypeAndDoneFlag() {
        Todo todo = new Todo("read book");
        assertEquals("T | 0 | read book", todo.toSaveFormat());
        todo.markAsDone();
        assertEquals("T | 1 | read book", todo.toSaveFormat());
    }

    @Test
    public void toSaveFormat_deadline_writesDateInTheFormatItIsReadBackFrom() {
        Deadline deadline = new Deadline("return book", LocalDate.of(2019, 10, 15));
        // The file must keep the yyyy-mm-dd form, not the displayed one, or the
        // deadline cannot be read back on the next run.
        assertEquals("D | 0 | return book | 2019-10-15", deadline.toSaveFormat());
    }

    @Test
    public void toSaveFormat_event_writesBothTimesSeparately() {
        assertEquals("E | 0 | project meeting | Mon 2pm | 4pm",
                new Event("project meeting", "Mon 2pm", "4pm").toSaveFormat());
    }

    @Test
    public void getStatusIcon_reflectsWhetherDone() {
        Todo todo = new Todo("read book");
        assertEquals(" ", todo.getStatusIcon());
        todo.markAsDone();
        assertEquals("X", todo.getStatusIcon());
    }
}
