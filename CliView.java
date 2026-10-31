package calendar.view;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;

/**
 * A command-line implementation of the {@link EventView} interface that supports
 * both interactive and headless execution modes.
 *
 * <p>This class provides input and output handling using a {@link Reader} wrapped in
 * a {@link BufferedReader} for command input, and a {@link PrintStream} for output.
 * By decoupling the I/O layer from the controller, this implementation upholds
 * the MVC principle of separation of concerns.</p>
 *
 * <p><strong>Responsibilities:</strong></p>
 * <ul>
 *   <li>Display normal and error messages to the user.</li>
 *   <li>Accept user input line-by-line via the provided {@link Reader}.</li>
 *   <li>Support both console (interactive) and file-driven (headless) modes.</li>
 * </ul>
 */
public final class CliView implements EventView {

  /** The output stream used for printing messages and errors. */
  private final PrintStream out;
  /** The input stream used for reading user commands or script lines. */
  private final BufferedReader in;

  /**
   * Constructs a {@code CliView} using the specified output and input streams.
   *
   * <p>The controller passes these streams to support both interactive console mode
   * and headless mode (script file input/output).</p>
   *
   * @param out the {@link PrintStream} used to display output and error messages; must
   *                not be {@code null}
   * @param in  the {@link Reader} from which user input or command file lines are read; must
   *                not be {@code null}
   * @throws NullPointerException if either {@code out} or {@code in} is {@code null}
   */
  public CliView(final PrintStream out, final Reader in) {
    this.out = out;
    this.in = new BufferedReader(in);
  }

  /**
   * Prints a line of text followed by a newline to the configured output stream.
   *
   * <p>This method is used for standard informational messages or command feedback.</p>
   *
   * @param s the text to display; if {@code null}, the literal string {@code "null"} is printed
   */
  @Override
  public void println(final String s) {
    this.out.println(s);
  }

  /**
   * Prints text to the configured output stream without adding a newline.
   *
   * <p>This method is typically used for prompts or continuous outputs.</p>
   *
   * @param s the text to display; if {@code null}, the literal string {@code "null"} is printed
   */
  @Override
  public void print(final String s) {
    this.out.print(s);
  }

  /**
   * Prints an error message followed by a newline to the configured output stream.
   *
   * <p>Error messages are routed to the same stream as normal output
   * for simplicity, as per assignment specifications.</p>
   *
   * @param message the error message to display; if {@code null}, the literal
   *                    string {@code "null"} is printed
   */
  @Override
  public void error(final String message) {
    this.out.println(message);
  }

  /**
   * Reads and returns the next line of input from the configured input source.
   *
   * <p>In interactive mode, this reads from {@code System.in}; in headless mode,
   * it reads sequentially from a command file.</p>
   *
   * @return the next line of input, or {@code null} if the end of stream has been reached
   * @throws IOException if an I/O error occurs while reading from the input
   */
  @Override
  public String readLine() throws IOException {
    return this.in.readLine();
  }
}
