package tally.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Tests how each kind of task shows itself to the user and writes itself to the file. */
public class TaskTest {
    @Test
    public void isSameAs_theSameThingRecordedTwice_isRecognized() {
        Task done = new Todo("read book");
        done.markAsDone();
        // Marking a task done does not turn it into a different task, so adding it
        // again would still be adding the same thing twice.
        assertTrue(new Todo("read book").isSameAs(done));
        assertTrue(new Deadline("essay", LocalDate.of(2026, 9, 10))
                .isSameAs(new Deadline("essay", LocalDate.of(2026, 9, 10))));
    }

    @Test
    public void isSameAs_tasksDifferingInAnyPart_areNotTheSame() {
        assertFalse(new Todo("read book").isSameAs(new Todo("buy bread")));
        // Same words, different kind of task.
        assertFalse(new Todo("essay").isSameAs(new Deadline("essay", LocalDate.of(2026, 9, 10))));
        assertFalse(new Deadline("essay", LocalDate.of(2026, 9, 10))
                .isSameAs(new Deadline("essay", LocalDate.of(2026, 9, 11))));
        assertFalse(new Event("party", "2pm", "4pm").isSameAs(new Event("party", "2pm", "6pm")));
    }

    @Test
    public void constructor_windowEndingBeforeItStarts_isRefused() {
        // The class documents that the end is never before the start. The parser and the
        // storage reader both enforce it, so one arriving here came from neither.
        assertThrows(AssertionError.class, () ->
                new Window("backwards", LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 8)));
    }

    @Test
    public void constructor_eventWithDatedEndsRunningBackwards_isRefused() {
        // This used to be accepted and quietly read as the same stretch of days, shown
        // to the user back to front. The parser and the storage reader both refuse it
        // now, for the same reason a window that ends before it starts is refused, so
        // one arriving here came from neither.
        assertThrows(AssertionError.class, () -> new Event("trip", "2026-09-10", "2026-09-08"));
    }

    @Test
    public void constructor_eventEndsThatAreNotDates_areLeftAlone() {
        // Nothing can tell whether "4pm" falls before "Mon 2pm", so neither is refused.
        Event event = new Event("standup", "Mon 2pm", "4pm");
        assertTrue(event.hasUnreadableDates());
        assertFalse(event.occupies(LocalDate.of(2026, 9, 9)));
    }

    @Test
    public void occupies_eventWithDatedEnds_coversTheDaysBetweenThem() {
        Event trip = new Event("trip", "2026-09-08", "2026-09-10");
        assertFalse(trip.occupies(LocalDate.of(2026, 9, 7)));
        assertTrue(trip.occupies(LocalDate.of(2026, 9, 8)));
        assertTrue(trip.occupies(LocalDate.of(2026, 9, 10)));
        assertFalse(trip.occupies(LocalDate.of(2026, 9, 11)));
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
        assertTrue(new Event("standup", "Mon 2pm", "3pm").hasUnreadableDates());
        // One end readable and the other not still leaves the pair unusable.
        assertTrue(new Event("standup", "2026-09-08", "3pm").hasUnreadableDates());
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

    @Test
    public void occupies_aTaskNamingNoDays_takesUpNone() {
        // A todo is owed whenever; it does not stand between the user and a free day.
        Task todo = new Todo("read book");
        assertFalse(todo.occupies(LocalDate.of(2026, 9, 9)));
        assertFalse(todo.hasUnreadableDates(), "a todo names no times to fail to read");
    }

    @Test
    public void constructor_datesWrittenWithExtraSpacing_areStillRead() {
        // The data file is edited by hand, so a date can arrive with a space in front
        // of it. Tidying the ends and then reading the untidied text left an event
        // showing two dates and counting as having none, so the free-day search stepped
        // straight over it while the list showed it plainly.
        Event padded = new Event("trip", "  2026-09-08", "2026-09-10 ");
        assertFalse(padded.hasUnreadableDates(), "the dates were shown but not read");
        assertTrue(padded.occupies(LocalDate.of(2026, 9, 9)));
        assertEquals("[E][ ] trip (from: 2026-09-08 to: 2026-09-10)", padded.toString());
    }

    @Test
    public void constructor_paddedEndsRunningBackwards_areStillRefused() {
        // Untidied reading also let this past the check, and saving then wrote it back
        // tidied, so a file that loaded was rejected as damaged on the next start.
        assertThrows(AssertionError.class, () -> new Event("trip", " 2026-09-12", "2026-09-08 "));
    }
}
