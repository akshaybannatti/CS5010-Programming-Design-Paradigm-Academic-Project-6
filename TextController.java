package calendar.controller;

import java.util.List;

/**
 * Operations that a text-based (CLI/headless) calendar controller must support.
 */
public interface TextController {

  /**
   * Runs the controller in interactive REPL mode until the user enters {@code exit}
   * or the input stream ends.
   *
   * <p>Behavior:</p>
   * <ul>
   *   <li>Reads one line at a time from the {@link calendar.view.EventView}.</li>
   *   <li>Ignores blank lines and lines beginning with {@code #}.</li>
   *   <li>On {@code exit}, prints a farewell and returns.</li>
   *   <li>For invalid commands, prints an error and continues the loop.</li>
   * </ul>
   */
  void runInteractive();

  /**
   * Executes a finite list of commands in headless mode.
   *
   * <p>Behavior:</p>
   * <ul>
   *   <li>Processes the list in order; trims each line.</li>
   *   <li>Ignores blank lines and lines beginning with {@code #}.</li>
   *   <li>Stops early if a line equals {@code exit} (case-insensitive).</li>
   *   <li>For invalid commands, prints an error and continues with the next line.</li>
   * </ul>
   *
   * @param commands lines from a script, in order; must not be {@code null}
   * @throws NullPointerException if {@code commands} is {@code null}
   */
  void runHeadless(List<String> commands);
}
