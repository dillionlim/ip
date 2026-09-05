package tally.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import tally.TallyException;
import tally.task.Deadline;
import tally.task.Event;
import tally.task.Todo;
import tally.task.Window;

/** Tests that Parser makes sense of good commands and refuses the rest. */
public class ParserTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);

    @Test
    public void parse_textHoldingTheFileSeparator_throws() {
        // The data file has no way to escape " | ", so a task holding it would be
        // written out and read back as a different number of parts.
        assertThrows(TallyException.class, () -> Parser.parseTodo("laundry | dry cleaning"));
        // A description merely ending in a bar builds the separator when the file joins
        // the parts, so the whole character has to go, not just the separator as written.
        assertThrows(TallyException.class, () -> Parser.parseDeadline("a | /by 2026-10-01"));
        assertThrows(TallyException.class, () -> Parser.parseTodo("x|y"));
        assertThrows(TallyException.class, () -> Parser.parseDeadline("a | b /by 2026-10-01"));
        assertThrows(TallyException.class, () -> Parser.parseEvent("meet /from Mon | room 3 /to 4pm"));
        assertThrows(TallyException.class, () ->
                Parser.parseWindow("x | y /between 2026-09-08 /and 2026-09-12"));
    }

    @Test
    public void parseDate_yearOutsideTheWrittenForm_throws() {
        // LocalDate.parse reads this, and the date arithmetic done later then overflows.
        assertThrows(TallyException.class, () -> Parser.parseDate("+999999999-12-31"));
        assertThrows(TallyException.class, () -> Parser.parseDate("-2026-09-08"));
        assertThrows(TallyException.class, () -> Parser.parseDate("20260-09-08"));
    }

    @Test
    public void parseFreeQuery_noMarkers_isOneDayFromToday() throws TallyException {
        assertEquals(new FreeQuery(1, TODAY), Parser.parseFreeQuery("", TODAY));
    }

    @Test
    public void parseFreeQuery_bothMarkers_areRead() throws TallyException {
        assertEquals(new FreeQuery(3, LocalDate.of(2026, 9, 20)),
                Parser.parseFreeQuery("/for 3 /from 2026-09-20", TODAY));
    }

    @Test
    public void parseFreeQuery_eitherMarkerAlone_isRead() throws TallyException {
        assertEquals(new FreeQuery(3, TODAY), Parser.parseFreeQuery("/for 3", TODAY));
        assertEquals(new FreeQuery(1, LocalDate.of(2026, 9, 20)),
                Parser.parseFreeQuery("/from 2026-09-20", TODAY));
    }

    @Test
    public void parseFreeQuery_countBelowOne_throws() {
        assertThrows(TallyException.class, () -> Parser.parseFreeQuery("/for 0", TODAY));
        assertThrows(TallyException.class, () -> Parser.parseFreeQuery("/for -2", TODAY));
    }

    @Test
    public void parseFreeQuery_malformedMarkerOrDate_throws() {
        assertThrows(TallyException.class, () -> Parser.parseFreeQuery("3", TODAY));
        assertThrows(TallyException.class, () -> Parser.parseFreeQuery("/for abc", TODAY));
        assertThrows(TallyException.class, () -> Parser.parseFreeQuery("/from nope", TODAY));
        assertThrows(TallyException.class, () -> Parser.parseFreeQuery("/from", TODAY));
    }

    @Test
    public void parseWindow_descriptionAndBothDates_returnsWindow() throws TallyException {
        Window window = Parser.parseWindow("submit form /between 2026-09-08 /and 2026-09-12");
        assertEquals("[W][ ] submit form (window: Sep 08 2026 to Sep 12 2026)",
                window.toString());
    }

    @Test
    public void parseWindow_sameDayAtBothEnds_isAccepted() throws TallyException {
        // A window of one day is a period, not a mistake, so only a later start is refused.
        Window window = Parser.parseWindow("submit form /between 2026-09-08 /and 2026-09-08");
        assertEquals("[W][ ] submit form (window: Sep 08 2026 to Sep 08 2026)",
                window.toString());
    }

    @Test
    public void parseWindow_endBeforeStart_throws() {
        assertThrows(TallyException.class, () ->
                Parser.parseWindow("submit form /between 2026-09-12 /and 2026-09-08"));
    }

    @Test
    public void parseWindow_missingMarkerOrBlankPart_throws() {
        assertThrows(TallyException.class, () -> Parser.parseWindow("submit form"));
        assertThrows(TallyException.class, () ->
                Parser.parseWindow("submit form /between 2026-09-08"));
        assertThrows(TallyException.class, () ->
                Parser.parseWindow(" /between 2026-09-08 /and 2026-09-12"));
    }

    @Test
    public void parseWindow_unreadableDate_throws() {
        assertThrows(TallyException.class, () ->
                Parser.parseWindow("submit form /between soon /and 2026-09-12"));
    }

    @Test
    public void parseCommand_knownWord_returnsCommand() throws TallyException {
        assertEquals(Command.TODO, Parser.parseCommand("todo read book"));
        assertEquals(Command.BYE, Parser.parseCommand("bye"));
    }

    @Test
    public void parseCommand_unknownWord_throws() {
        assertThrows(TallyException.class, () -> Parser.parseCommand("blah"));
        assertThrows(TallyException.class, () -> Parser.parseCommand(""));
    }

    @Test
    public void parseCommand_wrongCase_throws() {
        assertThrows(TallyException.class, () -> Parser.parseCommand("TODO read book"));
    }

    @Test
    public void parseArguments_variousSpacing_returnsTrimmedRest() {
        assertEquals("", Parser.parseArguments("list"));
        assertEquals("read book", Parser.parseArguments("todo read book"));
        assertEquals("read   book", Parser.parseArguments("todo    read   book   "));
    }

    @Test
    public void parseTodo_description_returnsTodo() throws TallyException {
        Todo todo = Parser.parseTodo("read book");
        assertEquals("[T][ ] read book", todo.toString());
    }

    @Test
    public void parseTodo_noDescription_throws() {
        assertThrows(TallyException.class, () -> Parser.parseTodo(""));
    }

    @Test
    public void parseDeadline_descriptionAndDate_returnsDeadline() throws TallyException {
        Deadline deadline = Parser.parseDeadline("return book /by 2019-10-15");
        assertEquals("[D][ ] return book (by: Oct 15 2019)", deadline.toString());
    }

    @Test
    public void parseDeadline_missingOrBlankParts_throws() {
        assertThrows(TallyException.class, () -> Parser.parseDeadline("return book"));
        assertThrows(TallyException.class, () -> Parser.parseDeadline("/by 2019-10-15"));
        assertThrows(TallyException.class, () -> Parser.parseDeadline(""));
    }

    @Test
    public void parseDeadline_unreadableDate_throws() {
        assertThrows(TallyException.class, () -> Parser.parseDeadline("do homework /by no idea"));
    }

    @Test
    public void parseEvent_descriptionAndBothTimes_returnsEvent() throws TallyException {
        Event event = Parser.parseEvent("project meeting /from Mon 2pm /to 4pm");
        assertEquals("[E][ ] project meeting (from: Mon 2pm to: 4pm)", event.toString());
    }

    @Test
    public void parseEvent_toBeforeFrom_throws() {
        // Once recorded the times the wrong way round instead of refusing the command.
        assertThrows(TallyException.class, () -> Parser.parseEvent("meeting /to 4pm /from 2pm"));
    }

    @Test
    public void parseEvent_missingMarkerOrBlankPart_throws() {
        assertThrows(TallyException.class, () -> Parser.parseEvent("meeting /from 2pm"));
        assertThrows(TallyException.class, () -> Parser.parseEvent("meeting /to 4pm"));
        assertThrows(TallyException.class, () -> Parser.parseEvent("/from 2pm /to 4pm"));
    }

    @Test
    public void parseSearchWord_givenAWord_returnsIt() throws TallyException {
        assertEquals("book", Parser.parseSearchText("book"));
        assertEquals("read book", Parser.parseSearchText("read book"));
    }

    @Test
    public void parseSearchWord_nothingToLookFor_throws() {
        assertThrows(TallyException.class, () -> Parser.parseSearchText(""));
    }

    @Test
    public void parseDate_isoDate_returnsThatDate() throws TallyException {
        assertEquals(LocalDate.of(2019, 10, 15), Parser.parseDate("2019-10-15"));
    }

    @Test
    public void parseDate_notAnIsoDate_throws() {
        assertThrows(TallyException.class, () -> Parser.parseDate("15/10/2019"));
        assertThrows(TallyException.class, () -> Parser.parseDate("Sunday"));
        assertThrows(TallyException.class, () -> Parser.parseDate("2019-13-01"));
        assertThrows(TallyException.class, () -> Parser.parseDate(""));
    }

    @Test
    public void parseTaskIndex_numberTheUserTyped_countsFromZero() throws TallyException {
        assertEquals(0, Parser.parseTaskIndex("1", 3, Command.MARK));
        assertEquals(2, Parser.parseTaskIndex("3", 3, Command.MARK));
    }

    @Test
    public void parseTaskIndex_outsideTheList_throws() {
        assertThrows(TallyException.class, () -> Parser.parseTaskIndex("0", 3, Command.MARK));
        assertThrows(TallyException.class, () -> Parser.parseTaskIndex("4", 3, Command.MARK));
        assertThrows(TallyException.class, () -> Parser.parseTaskIndex("-1", 3, Command.MARK));
        assertThrows(TallyException.class, () -> Parser.parseTaskIndex("1", 0, Command.MARK));
    }

    @Test
    public void parseTaskIndex_notANumber_throws() {
        assertThrows(TallyException.class, () -> Parser.parseTaskIndex("abc", 3, Command.MARK));
        assertThrows(TallyException.class, () -> Parser.parseTaskIndex("", 3, Command.MARK));
        assertThrows(TallyException.class, () -> Parser.parseTaskIndex("1 2", 3, Command.MARK));
    }

    @Test
    public void parseTaskIndex_badNumber_errorNamesTheCommand() {
        TallyException thrown = assertThrows(
                TallyException.class, () -> Parser.parseTaskIndex("abc", 3, Command.DELETE));
        assertEquals("delete needs the number of a task. Try: delete 2", thrown.getMessage());
    }
}
