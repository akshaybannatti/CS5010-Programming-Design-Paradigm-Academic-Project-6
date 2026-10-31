import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import calendar.view.CliView;
import calendar.view.EventView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import org.junit.Test;

/**
 * Unit tests for {@link calendar.view.CliView}.
 *
 * <p>Verifies that the console-based implementation of {@link calendar.view.EventView}
 * correctly prints messages, including error output, to the configured stream.</p>
 */
public class CliViewTest {

  /**
   * Ensures that {@link CliView#error(String)} prints the provided message
   * to the output stream exactly as expected.
   *
   * <p>This test captures {@code System.out}-like output via a
   * {@link ByteArrayOutputStream} and verifies that the error text appears
   * in the printed output.</p>
   *
   * @throws IOException if reading or writing to the test stream fails
   */
  @Test
  public void testErrorPrintsMessage() throws IOException {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    String input = "";
    EventView view = new CliView(new PrintStream(outContent), new StringReader(input));

    view.error("Something went wrong!");

    String result = outContent.toString().trim();
    assertTrue("Expected error message to be printed",
        result.contains("Something went wrong!"));
  }

  /**
   * Ensures that {@link CliView#print(String)} writes the given text
   * directly to the output stream without appending a newline.
   *
   * @throws IOException if writing to the test output stream fails
   */
  @Test
  public void testPrintWritesWithoutNewline() throws IOException {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    EventView view = new CliView(new PrintStream(outContent), new StringReader(""));

    view.print("Hello");
    view.print("World");

    String result = outContent.toString("UTF-8");
    assertTrue("Expected concatenated output without newline",
        result.contains("HelloWorld"));
    assertFalse("Expected no newline between prints",
        result.contains(System.lineSeparator()));
  }
}
