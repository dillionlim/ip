package tally.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests that each command knows whether carrying it out can change the tally. */
public class CommandTest {
    @Test
    public void changesTally_commandsThatOnlyRead_isFalse() {
        assertFalse(Command.LIST.changesTally());
        assertFalse(Command.FIND.changesTally());
        assertFalse(Command.FREE.changesTally());
        assertFalse(Command.BYE.changesTally());
    }

    @Test
    public void changesTally_commandsThatAddRemoveOrAlter_isTrue() {
        assertTrue(Command.TODO.changesTally());
        assertTrue(Command.DEADLINE.changesTally());
        assertTrue(Command.EVENT.changesTally());
        assertTrue(Command.WINDOW.changesTally());
        assertTrue(Command.MARK.changesTally());
        assertTrue(Command.UNMARK.changesTally());
        assertTrue(Command.DELETE.changesTally());
    }
}
