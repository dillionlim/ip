package tally;

/**
 * Signals that Tally could not carry out what the user asked for.
 *
 * <p>The message of a TallyException is written to be shown to the user as it is,
 * so it should say what went wrong and how to put it right.
 */
public class TallyException extends Exception {
    /**
     * Version stamp used if an instance is ever written out as bytes.
     *
     * <p>Throwable is Serializable, so every exception inherits that whether the
     * ability is wanted or not. Fixing the value here stops the JVM computing one
     * from the class's structure, where any later edit would silently change it.
     */
    // serialVersionUID issue identified and fixed by AI.
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception carrying an explanation meant for the user to read.
     *
     * @param message what went wrong, and how to correct it.
     */
    public TallyException(String message) {
        super(message);
    }
}
