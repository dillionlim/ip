package tally.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Tests how each kind of task shows itself to the user and writes itself to the file. */
public class TaskTest {
    @Test
    public void occupies_eventEndsWrittenBackwards_stillNameTheSameDays() {
        Event backwards = new Event("trip", "2026-09-10", "2026-09-08");
        Event forwards = new Event("trip", "2026-09-08", "2026-09-10");
        for (int day = 7; day <= 11; day++) {
            LocalDate each = LocalDate.of(2026, 9, day);
            assertEquals(forwards.occupies(each), backwards.occupies(each), each.toString());
        }
        assertTrue(backwards.occupies(LocalDate.of(2026, 9, 9)));
        assertFalse(backwards.occupies(LocalDate.of(2026, 9, 11)));
    }

    @Test
    public void occupies_endsThousandsOfYearsApart_costsNothingToAsk() {
        // Both ends are ordinary dates, so the date form does not refuse them: listing
        // the 3.65 million days between them is what used to exhaust the heap. Asking
        // about one day has to stay cheap, which only holds if nothing is built.
        Event doom = new Event("doom", "0001-01-01", "9999-12-31");
        assertFalse(doom.hasUnreadableDates());

        // Asking a thousand times is the point: each answer has to cost nothing. Building
        // the three and a half million days between these ends even once takes a third of
        // a second, so a thousand answers could not be given inside this bound.
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            for (int time = 0; time < 1000; time++) {
                assertTrue(doom.occupies(LocalDate.of(2026, 9, 8)));
                assertFalse(doom.occupies(LocalDate.of(10000, 1, 1)));
            }
        });
    }

    @Test
    public void occupies_endsOutsideTheWrittenDateForm_takeUpNothing() {
        Event odd = new Event("odd", "+999999999-12-30", "-999999999-01-01");
        assertFalse(odd.occupies(LocalDate.of(2026, 9, 8)));
        assertTrue(odd.hasUnreadableDates());
    }

    @Test
    public void hasUnreadableDates_datesOrTextEnds_trueOnlyForText() {
        assertFalse(new Event("trip", "2026-09-08", "2026-09-10").hasUnreadableDates());
        assertFalse(new Event("trip", "2026-09-10", "2026-09-08").hasUnreadableDates());
        assertTrue(new Event("standup", "Mon 2pm", "3pm").hasUnreadableDates());
    }

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
    public void getStatusIcon_doneAndNotDone_showsCrossOrSpace() {
        Todo todo = new Todo("read book");
        assertEquals(" ", todo.getStatusIcon());
        todo.markAsDone();
        assertEquals("X", todo.getStatusIcon());
    }
}
