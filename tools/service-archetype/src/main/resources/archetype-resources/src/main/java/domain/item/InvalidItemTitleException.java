package ${package}.domain.item;

/** The title is blank or too long. */
public class InvalidItemTitleException extends RuntimeException {

    public InvalidItemTitleException(int maxLength) {
        super("Item title must have 1 to " + maxLength + " characters.");
    }
}
