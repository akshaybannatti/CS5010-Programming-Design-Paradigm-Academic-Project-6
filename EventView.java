package calendar.view;

import java.io.IOException;

/**
 * Abstraction for the textual view in the MVC architecture.
 *
 * <p>Implementations display messages and errors to the user and
 * supply line-oriented input to the controller. This interface
 * intentionally hides the underlying I/O mechanism so controllers
 * can work with either interactive (console) or headless (script)
 * execution without being coupled to specific streams.</p>
 *
 * <p><strong>Responsibilities</strong></p>
 * <ul>
 *   <li>Print normal output lines ({@link #println(String)}) and text without
 *       newline ({@link #print(String)}).</li>
 *   <li>Print error lines via {@link #error(String)}.</li>
 *   <li>Provide the next input line via {@link #readLine()}.</li>
 * </ul>
 */
public interface EventView {

  /**
   * Prints a line of text followed by a newline to the output destination.
   *
   * @param s the text to display; if {@code null}, implementations may print the
   *              literal {@code "null"}
   */
  void println(String s);

  /**
   * Prints text to the output destination without appending a newline.
   *
   * @param s the text to display; if {@code null}, implementations may
   *              print the literal {@code "null"}
   */
  void print(String s);

  /**
   * Prints an error message followed by a newline to the error output channel.
   * Implementations may route this to the same destination as {@link #println(String)}.
   *
   * @param message the error message to display; if {@code null}, implementations
   *                    may print the literal {@code "null"}
   */
  void error(String message);

  /**
   * Reads the next complete line of input from the underlying source.
   *
   * @return the next line of input, or {@code null} if the end of stream has been reached
   * @throws IOException if an I/O error occurs while reading from the input source
   */
  String readLine() throws IOException;
}
