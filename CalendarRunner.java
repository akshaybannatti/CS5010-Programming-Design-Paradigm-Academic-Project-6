import calendar.controller.CalendarController;
import calendar.controller.GuiCalendarController;
import calendar.controller.TextController;
import calendar.model.CalendarModel;
import calendar.model.MultiCalendarModelImpl;
import calendar.view.CliView;
import calendar.view.EventView;
import calendar.view.SwingCalendarView;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Entry point of the Calendar application.
 *
 * <p>Supports three modes, as required by the assignment:</p>
 * <ul>
 *   <li><code>java -jar calendar.jar</code> — launches the GUI.</li>
 *   <li><code>java -jar calendar.jar --mode interactive</code> — launches the
 *       interactive text mode.</li>
 *   <li><code>java -jar calendar.jar --mode headless commands.txt</code> — runs
 *       the program in headless mode using the given script file.</li>
 * </ul>
 */
public final class CalendarRunner {

  private CalendarRunner() {
  }

  /**
   * Launches the calendar application in the appropriate mode based on args.
   *
   * @param args command-line arguments
   */
  public static void main(final String[] args) {
    final CalendarModel model = new MultiCalendarModelImpl();

    try {
      if (args == null || args.length == 0) {
        SwingUtilities.invokeLater(() -> {
          SwingCalendarView view = new SwingCalendarView();
          new GuiCalendarController(model, view);
          view.setVisible(true);
        });
        return;
      }

      if (args.length >= 2 && "--mode".equalsIgnoreCase(args[0])) {
        final String mode = args[1].toLowerCase();

        switch (mode) {
          case "interactive":
            {
            EventView view = new CliView(
                System.out,
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
            TextController controller = new CalendarController(model, view);
            controller.runInteractive();
            return;
            }
          case "headless":
            {
            if (args.length < 3) {
              System.err.println("Headless mode requires a script file path.");
              printUsage();
              return;
            }
            Path scriptPath = Path.of(args[2]);
            List<String> lines = Files.readAllLines(scriptPath, StandardCharsets.UTF_8);
            EventView view = new CliView(
                System.out,
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
            TextController controller = new CalendarController(model, view);
            controller.runHeadless(lines);
            return;
            }
          default:
            System.err.println("Unknown mode: " + mode);
            printUsage();
            return;
        }
      }

      System.err.println("Invalid command-line arguments.");
      printUsage();
    } catch (IOException ioe) {
      System.err.println("Fatal error: " + ioe.getMessage());
    } catch (RuntimeException re) {
      System.err.println("Fatal error: " + re.getMessage());
    }
  }

  private static void printUsage() {
    System.err.println("Usage:");
    System.err.println("  java -jar calendar.jar");
    System.err.println("  java -jar calendar.jar --mode interactive");
    System.err.println("  java -jar calendar.jar --mode headless <scriptFile>");
  }
}
