package tally.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import tally.TallyException;
import tally.task.Deadline;
import tally.task.Event;
import tally.task.Todo;

/** Tests that Parser makes sense of good commands and refuses the rest. */
public class ParserTest {
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
        assertEquals("book", Parser.parseSearchWord("book"));
        assertEquals("read book", Parser.parseSearchWord("read book"));
    }

    @Test
    public void parseSearchWord_nothingToLookFor_throws() {
        assertThrows(TallyException.class, () -> Parser.parseSearchWord(""));
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
        TallyException thrown = assertThrows(TallyException.class,
                () -> Parser.parseTaskIndex("abc", 3, Command.DELETE));
        assertEquals("delete needs the number of a task. Try: delete 2", thrown.getMessage());
    }
}
