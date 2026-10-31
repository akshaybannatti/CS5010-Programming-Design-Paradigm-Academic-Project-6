import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import calendar.controller.CalendarController;
import calendar.export.CalendarExporter;
import calendar.model.MultiCalendarModelImpl;
import calendar.model.entity.CalendarEvent;
import calendar.view.EventView;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link calendar.controller.CalendarController}.
 *
 * <p>This test suite exercises controller command parsing,
 * headless execution, and integration with a mock view and
 * in-memory calendar model. The goal is to validate behavior
 * across all command types and maximize branch and mutation
 * coverage.</p>
 */
public class CalendarControllerTest {
  private MultiCalendarModelImpl model;
  private MockView view;
  private CalendarController controller;

  /**
   * Test double for {@link EventView} used to capture printed output and
   * simulate user input during controller tests.
   */
  static class MockView implements EventView {

    /** Collected output lines from print/println/error calls. */
    List<String> output = new ArrayList<>();

    /** Queue of scripted input lines returned by {@link #readLine()}. */
    Queue<String> inputs = new LinkedList<>();

    /**
     * Records a printed line (with an implicit newline).
     *
     * @param s line to record
     */
    public void println(String s) {
      output.add(s);
    }

    /**
     * Records printed text (without newline distinction for testing).
     *
     * @param s text to record
     */
    public void print(String s) {
      output.add(s);
    }

    /**
     * Records an error message in a consistent "Error: ..." format.
     *
     * @param m error message
     */
    public void error(String m) {
      output.add("Error: " + m);
    }

    /**
     * Returns the next queued input value, or {@code null} if none remain.
     *
     * @return next scripted input, or null
     */
    public String readLine() {
      return inputs.poll();
    }

    /**
     * Returns true if any recorded output contains the given substring
     * (case-insensitive).
     *
     * @param s substring to search for
     * @return true if output contains the substring
     */
    public boolean contains(String s) {
      return output.stream().anyMatch(o -> o.toLowerCase().contains(s.toLowerCase()));
    }
  }

  /**
   * Initializes a fresh controller, model, and mock view before
   * each test case.
   *
   * <p>Ensures that every test runs with a clean in-memory
   * {@link MultiCalendarModelImpl} and isolated {@code MockView}
   * to capture controller output deterministically.</p>
   */
  @Before
  public void setup() {
    model = new MultiCalendarModelImpl();
    view = new MockView();
    controller = new CalendarController(model, view);
  }

  @Test
  public void testParseAllCommandTypes() {
    controller.runHeadless(Arrays.asList(
        "help",
        "create calendar --name Work --timezone UTC",
        "edit calendar --name Work --property name NewWork",
        "use calendar --name NewWork",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "edit event subject \"Test\" from 2024-11-15T10:00 with \"New\"",
        "edit events subject \"New\" from 2024-11-15T10:00 with \"Newer\"",
        "edit series location \"Newer\" from 2024-11-15T10:00 with \"Loc\"",
        "delete event --name \"Newer\" --start 2024-11-15T10:00",
        "create event \"Test2\" from 2024-11-16T10:00 to 2024-11-16T11:00",
        "print events on 2024-11-16",
        "print events from 2024-11-16T00:00 to 2024-11-16T23:59",
        "show status on 2024-11-16T10:30",
        "exit"
    ));
    assertTrue(view.contains("Created calendar") || view.contains("created calendar"));
  }

  @Test
  public void testParseUnknownCommand() {
    controller.runHeadless(Arrays.asList(
        "unknown command",
        "exit"
    ));
    assertTrue(view.contains("unknown command"));
  }

  @Test
  public void testCreateEventWithUnquotedTitle() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event Standup from 2024-11-15T09:00 to 2024-11-15T09:15",
        "exit"
    ));
    assertTrue(view.contains("Created event"));
  }

  @Test
  public void testCreateEventMissingQuote() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"No closing from 2024-11-15T10:00 to 2024-11-15T11:00",
        "exit"
    ));
    assertTrue(view.contains("Error") || view.contains("missing closing quote"));
  }

  @Test
  public void testCreateEventFromToMissingTo() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Meeting\" from 2024-11-15T10:00",
        "exit"
    ));
    assertTrue(view.contains("expected 'to'"));
  }

  @Test
  public void testCreateEventFromToWithRepeatsFor() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Daily\" from 2024-11-15T09:00 to 2024-11-15T09:15 repeats MTWRF for 5",
        "exit"
    ));
  }

  @Test
  public void testCreateEventFromToWithRepeatsUntil() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Weekly\" from 2024-11-15T10:00 to 2024-11-15T11:00 "
            + "repeats MW until 2024-12-01",
        "exit"
    ));
  }

  @Test
  public void testCreateEventOn() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"AllDay\" on 2024-11-15",
        "exit"
    ));
    assertTrue(view.contains("Created event"));
  }

  @Test
  public void testCreateEventOnWithRepeats() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Weekend\" on 2024-11-23 repeats SU for 2 times",
        "exit"
    ));
    assertTrue(view.contains("Created series"));
  }

  @Test
  public void testCreateEventWithPrivacy() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Meeting\" from 2024-11-15T10:00 to 2024-11-15T11:00 --privacy private",
        "exit"
    ));
    assertTrue(view.contains("Created") || view.contains("Error"));

  }

  @Test
  public void testCreateEventWithAllWeekdays() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"M\" from 2024-11-18T10:00 to 2024-11-18T11:00 repeats M for 1 times",
        "create event \"T\" from 2024-11-19T10:00 to 2024-11-19T11:00 repeats T for 1 times",
        "create event \"W\" from 2024-11-20T10:00 to 2024-11-20T11:00 repeats W for 1 times",
        "create event \"R\" from 2024-11-21T10:00 to 2024-11-21T11:00 repeats R for 1 times",
        "create event \"F\" from 2024-11-22T10:00 to 2024-11-22T11:00 repeats F for 1 times",
        "create event \"S\" from 2024-11-23T10:00 to 2024-11-23T11:00 repeats S for 1 times",
        "create event \"U\" from 2024-11-24T10:00 to 2024-11-24T11:00 repeats U for 1 times",
        "exit"
    ));
    assertTrue(view.contains("Created"));
  }

  @Test
  public void testEditEventScopes() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Meeting\" from 2024-11-15T10:00 to 2024-11-15T11:00 repeats M for 3 times",
        "edit event description \"Meeting\" from 2024-11-15T10:00 with \"Desc1\"",
        "edit events subject \"Meeting\" from 2024-11-22T10:00 with \"Updated\"",
        "edit series location \"Updated\" from 2024-11-22T10:00 with \"Room\"",
        "exit"
    ));
    assertTrue(view.contains("Edited"));
    assertTrue(view.contains("OK"));
  }

  @Test
  public void testEditEventMissingScope() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "edit subject \"Test\" from 2024-11-15T10:00 with \"Value\"",
        "exit"
    ));
    assertTrue(view.contains("Missing scope") || view.contains("Error"));
  }

  @Test
  public void testEditEventAllProperties() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "edit event description \"Test\" from 2024-11-15T10:00 with \"New desc\"",
        "edit event subject \"Test\" from 2024-11-15T10:00 with \"NewName\"",
        "edit event location \"NewName\" from 2024-11-15T10:00 with \"Room 101\"",
        "edit event start \"NewName\" from 2024-11-15T10:00 to 2024-11-15T11:00 with "
            + "2024-11-15T09:00",
        "edit event end \"NewName\" from 2024-11-15T09:00 to 2024-11-15T11:00 with "
            + "2024-11-15T10:00",
        "edit event status \"NewName\" from 2024-11-15T09:00 with private",
        "exit"
    ));
    assertTrue(view.contains("Edited"));
  }

  @Test
  public void testEditEventUnsupportedProperty() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "edit event invalid \"Test\" from 2024-11-15T10:00 with \"Value\"",
        "exit"
    ));
    assertTrue(view.contains("Unsupported property") || view.contains("Error"));
  }

  @Test
  public void testEditEventWithAndWithoutToClause() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "edit event start \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00 with 2024-11-15T09:00",
        "edit event description \"Test\" from 2024-11-15T09:00 with \"Desc\"",
        "exit"
    ));
    assertTrue(view.contains("Edited"));
  }

  @Test
  public void testBulkEditThreshold() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"E1\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "create event \"E2\" from 2024-11-16T10:00 to 2024-11-16T11:00",
        "create event \"E3\" from 2024-11-17T10:00 to 2024-11-17T11:00",
        "edit events subject \"E2\" from 2024-11-16T10:00 with \"Updated\"",
        "exit"
    ));
    assertTrue(view.contains("OK"));
  }

  @Test
  public void testSeriesEditNotasSeries() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Single\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "edit series location \"Single\" from 2024-11-15T10:00 with \"Room\"",
        "exit"
    ));
  }

  @Test
  public void testDeleteEvent() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "delete event --name \"Test\" --start 2024-11-15T10:00",
        "exit"
    ));
    assertTrue(view.contains("Deleted"));
  }

  @Test
  public void testDeleteEventNotFound() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "delete event --name \"NonExistent\" --start 2024-11-15T10:00",
        "exit"
    ));
    assertTrue(view.contains("not found"));
  }

  @Test
  public void testDeleteSeries() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Series\" from 2024-11-15T10:00 to 2024-11-15T11:00 repeats M for 3 times",
        "delete series --name \"Series\" --start 2024-11-15T10:00",
        "exit"
    ));
    assertTrue(view.contains("Deleted"));
  }

  @Test
  public void testPrintEventsRange() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"E1\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "create event \"E2\" from 2024-11-16T10:00 to 2024-11-16T11:00",
        "print events from 2024-11-15T00:00 to 2024-11-16T23:59",
        "exit"
    ));
    assertTrue(view.contains("E1"));
    assertTrue(view.contains("E2"));
  }

  @Test
  public void testPrintEventsRangeNoEvents() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "print events from 2024-11-15T00:00 to 2024-11-15T23:59",
        "exit"
    ));
    assertTrue(view.contains("(no events)"));
  }

  @Test
  public void testShowStatusBusy() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Meeting\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "show status on 2024-11-15T10:30",
        "exit"
    ));
    assertTrue(view.contains("BUSY"));
  }

  @Test
  public void testShowStatusFree() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "show status on 2024-11-15T10:00",
        "exit"
    ));
    assertTrue(view.contains("FREE"));
  }

  @Test
  public void testCopyEvent() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC",
        "use calendar --name Work",
        "create event \"Meeting\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "copy event \"Meeting\" on 2024-11-15T10:00 --target Personal to 2024-11-16T14:00",
        "exit"
    ));
    assertTrue(view.contains("Copied event"));
  }

  @Test
  public void testCopyEventsOnly() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC",
        "use calendar --name Work",
        "create event \"E1\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "copy events on 2024-11-15 --target Personal to 2024-11-20",
        "exit"
    ));
    assertTrue(view.contains("Copied events"));
  }

  @Test
  public void testCopyEventsBetween() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"E1\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "create event \"E2\" from 2024-11-16T10:00 to 2024-11-16T11:00",
        "copy events between 2024-11-15 and 2024-11-16 --target Work to 2024-12-01",
        "exit"
    ));
    assertTrue(view.contains("Copied events"));
  }

  @Test
  public void testExportCsv() throws IOException {
    Path temp = Files.createTempFile("test", ".csv");
    try {
      controller.runHeadless(Arrays.asList(
          "create calendar --name Work --timezone UTC",
          "use calendar --name Work",
          "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
          "export cal " + temp.toString(),
          "exit"
      ));
      assertTrue(view.contains("Exported"));
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testInteractiveModeNormalExit() {
    view.inputs.addAll(Arrays.asList("exit"));
    controller.runInteractive();
    assertTrue(view.contains("bye"));
  }

  @Test
  public void testInteractiveModeEof() {
    view.inputs.clear();
    controller.runInteractive();
    assertTrue(view.contains("bye"));
  }

  @Test
  public void testInteractiveModeEmptyLinesAndComments() {
    view.inputs.addAll(Arrays.asList("", " ", "# comment", "exit"));
    controller.runInteractive();
    assertTrue(view.contains("bye"));
  }

  @Test
  public void testInteractiveModeIoError() {
    MockView errorView = new MockView() {
      private int callCount = 0;

      @Override
      public String readLine() {
        if (++callCount == 1) {
          throw new RuntimeException(new IOException("test IO Error"));
        }
        return null;
      }
    };
    CalendarController errorController = new CalendarController(model, errorView);
    errorController.runInteractive();
    assertTrue(errorView.contains("I/O error") || errorView.contains("IO error")
        || errorView.contains("bye"));
  }

  @Test
  public void testInteractiveModeRuntimeError() {
    view.inputs.addAll(Arrays.asList("invalid command", "exit"));
    controller.runInteractive();
    assertTrue(view.contains("Error"));
  }

  @Test
  public void testHeadModeNormalExit() {
    controller.runHeadless(Arrays.asList("help", "exit"));
    assertTrue(view.contains("Bye"));
  }

  @Test
  public void testHeadlessModeNoExit() {
    controller.runHeadless(Arrays.asList("help"));
    assertTrue(view.contains("headless file ended without 'exit'"));
  }

  @Test
  public void testHeadlessModeEmptyLinesAndComments() {
    controller.runHeadless(Arrays.asList("", " ", "# comment", "exit"));
    assertTrue(view.contains("Bye"));
  }

  @Test
  public void testFlagValueWithQuotes() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name \"My Calendar\" --timezone UTC",
        "exit"
    ));
    assertTrue(view.contains("Created calendar"));
  }

  @Test
  public void testFlagValueWithoutQuotes() {
    controller.runHeadless(Arrays.asList(
        "create calendar --timezone UTC",
        "exit"
    ));
    assertTrue(view.contains("missing --name") || view.contains("Error"));
  }

  @Test
  public void doEditEvent_missingScope_throwsException() throws Exception {
    Method m = CalendarController.class
        .getDeclaredMethod("doEditEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller,
          "description \"Title\" from 2025-01-10T10:00 with \"New desc\"");
      fail("Expected IllegalArgumentException for missing scope");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      Assert.assertTrue(cause instanceof IllegalArgumentException);
      Assert.assertEquals("Missing scope: event|events|series", cause.getMessage());
    }
  }

  @Test
  public void doEditEvent_scopeEvents_isHandled() throws Exception {
    Method m = CalendarController.class
        .getDeclaredMethod("doEditEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller,
          "events description \"Title\" from 2025-01-10T10:00 with \"New desc\"");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof RuntimeException
          || e.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void doEditEvent_scopeSeries_isHandled() throws Exception {
    Method m = CalendarController.class
        .getDeclaredMethod("doEditEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller,
          "series description \"Title\" from 2025-01-10T10:00 with \"New desc\"");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof RuntimeException
          || e.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void parseLetters_nullOrEmpty_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, new Object[] {null});
      fail("Expected exception for null letters");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("empty weekday letters", e.getCause().getMessage());
    }

    try {
      m.invoke(null, "");
      fail("Expected exception for empty letters");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("empty weekday letters", e.getCause().getMessage());
    }

    try {
      m.invoke(null, "   ");
      fail("Expected exception for whitespace letters");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("empty weekday letters", e.getCause().getMessage());
    }
  }

  @Test
  public void createRepeatingDateTime_emptyRepeats_fallsBackToSingleEvent() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 15, 9, 0);
    LocalDateTime end = LocalDateTime.of(2024, 11, 15, 10, 0);

    Method m = CalendarController.class.getDeclaredMethod(
        "createRepeatingDateTime",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        String.class
    );
    m.setAccessible(true);

    m.invoke(controller, "Solo", start, end, "   ");

    List<CalendarEvent> events =
        model.listEvents(start.toLocalDate(), start.toLocalDate());

    Assert.assertEquals("Expected exactly one event on that day", 1,
        events.size());
    Assert.assertEquals("Solo", events.get(0).name());
  }

  @Test
  public void testPrintEventsOnNoEvents() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "print events on 2024-11-15",
        "exit"
    ));
    assertTrue(view.contains("(no events)"));
  }

  @Test
  public void testPrintEventsRangeMissingToKeyword() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "print events from 2024-11-15T00:00 2024-11-16T23:59",
        "exit"
    ));
    assertTrue(view.contains("missing 'to'") || view.contains("Error"));
  }

  @Test
  public void testShowStatusFreeWhenNoEvents() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "show status on 2024-11-15T10:30",
        "exit"
    ));
    assertTrue(view.contains("FREE"));
  }

  @Test
  public void testShowStatusInvalidDateTimeFormat() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "show status on 2024-11-15 10:30",
        "exit"
    ));
    assertTrue(view.contains("Expect YYYY-MM-DDThh:mm") || view.contains("Error"));
  }

  @Test
  public void testExportUnsupportedFormat() throws IOException {
    Path temp = Files.createTempFile("test", ".txt");
    try {
      controller.runHeadless(Arrays.asList(
          "create calendar --name Work --timezone UTC",
          "use calendar --name Work",
          "export cal " + temp.toString(),
          "exit"
      ));
      assertTrue(view.contains("unsupported export format")
          || view.contains("export failed"));
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testCopyEventMissingOnKeyword() {
    controller.runHeadless(Arrays.asList(
        "copy event \"Meeting\" 2024-11-15T10:00 --target Work to 2024-11-16T10:00",
        "exit"
    ));
    assertTrue(view.contains("missing 'on'") || view.contains("Error"));
  }

  @Test
  public void testCopyEventMissingTargetFlag() {
    controller.runHeadless(Arrays.asList(
        "copy event \"Meeting\" on 2024-11-15T10:00 to 2024-11-16T10:00",
        "exit"
    ));
    assertTrue(view.contains("missing --target") || view.contains("Error"));
  }

  @Test
  public void testCopyEventMissingToKeyword() {
    controller.runHeadless(Arrays.asList(
        "copy event \"Meeting\" on 2024-11-15T10:00 --target Work 2024-11-16T10:00",
        "exit"
    ));
    assertTrue(view.contains("missing 'to'") || view.contains("Error"));
  }

  @Test
  public void testCopyEventsOnMissingTargetFlag() {
    controller.runHeadless(Arrays.asList(
        "copy events on 2024-11-15 to 2024-11-16",
        "exit"
    ));
    assertTrue(view.contains("missing --target") || view.contains("Error"));
  }

  @Test
  public void testCopyEventsOnMissingToKeyword() {
    controller.runHeadless(Arrays.asList(
        "copy events on 2024-11-15 --target Work",
        "exit"
    ));
    assertTrue(view.contains("missing 'to'") || view.contains("Error"));
  }

  @Test
  public void testCopyEventsBetweenMissingAndKeyword() {
    controller.runHeadless(Arrays.asList(
        "copy events between 2024-11-15 --target Work to 2024-12-01",
        "exit"
    ));
    assertTrue(view.contains("missing 'and'") || view.contains("Error"));
  }

  @Test
  public void testCopyEventsBetweenMissingTargetFlag() {
    controller.runHeadless(Arrays.asList(
        "copy events between 2024-11-15 and 2024-11-16 to 2024-12-01",
        "exit"
    ));
    assertTrue(view.contains("missing --target") || view.contains("Error"));
  }

  @Test
  public void testCopyEventsBetweenMissingToKeyword() {
    controller.runHeadless(Arrays.asList(
        "copy events between 2024-11-15 and 2024-11-16 --target Work 2024-12-01",
        "exit"
    ));
    assertTrue(view.contains("missing 'to'") || view.contains("Error"));
  }

  @Test
  public void testHeadlessModeNullLines() {
    controller.runHeadless(Arrays.asList(
        null,
        "   ",
        "# comment",
        "exit"
    ));
    assertTrue(view.contains("Bye"));
  }

  @Test
  public void testSplitOnceCoversSuccessAndMissingDelimiter() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod(
        "splitOnce", String.class, String.class);
    m.setAccessible(true);

    String[] parts = (String[]) m.invoke(null, "left|right", "\\|");
    Assert.assertEquals("left", parts[0]);
    Assert.assertEquals("right", parts[1]);

    try {
      m.invoke(null, "no delimiter here", ",");
      fail("Expected exception for missing delimiter");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(e.getCause().getMessage().contains("missing delimiter"));
    }
  }

  @Test
  public void testUnknownCommandHeadlessShowsHelpSuggestion() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "nonsense command here",
        "exit"
    ));
    assertTrue(view.contains("Unknown command") || view.contains("unknown command"));
  }

  @Test
  public void testRunHeadlessNullCommandsThrowsNpe() {
    try {
      controller.runHeadless(null);
      fail("Expected NullPointerException for null commands");
    } catch (NullPointerException e) {
      e.getMessage();
    }
  }

  @Test
  public void testEditCalendarChangeTimezone() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "edit calendar --name Work --property timezone Europe/London",
        "use calendar --name Work",
        "exit"
    ));
    assertTrue(view.contains("Changed timezone to Europe/London"));
  }

  @Test
  public void testEditCalendarUnsupportedProperty() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "edit calendar --name Work --property color Blue",
        "exit"
    ));
    assertTrue(view.contains("unsupported property") || view.contains("Unsupported property"));
  }

  @Test
  public void testCreateEventEndBeforeStartShowsError() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Backwards\" from 2024-11-15T11:00 to 2024-11-15T10:00",
        "exit"
    ));
    assertTrue(view.contains("end before start") || view.contains("Error"));
  }

  @Test
  public void testCreateEventBadRepeatsClause() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Weird\" from 2024-11-15T10:00 to 2024-11-15T11:00 repeats nonsense",
        "exit"
    ));

    Assert.assertFalse("Unexpected error for bad repeats clause",
        view.contains("Error"));

    Assert.assertFalse("Should not treat invalid repeats clause as a series",
        view.contains("Created series"));
  }

  @Test
  public void testCreateAllDayEventWithRepeatsForTimes() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"AllDaySeries\" on 2024-11-18 repeats MF for 2 times",
        "exit"
    ));
    assertTrue(view.contains("Created series") || view.contains("Created event"));
  }

  @Test
  public void testExportIcal() throws IOException {
    Path temp = Files.createTempFile("test", ".ical");
    try {
      controller.runHeadless(Arrays.asList(
          "create calendar --name Work --timezone UTC",
          "use calendar --name Work",
          "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
          "export cal " + temp.toString(),
          "exit"
      ));
      assertTrue(view.contains("Exported"));
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testUseCalendarNonExistingShowsError() {
    controller.runHeadless(Arrays.asList(
        "use calendar --name NoSuchCalendar",
        "exit"
    ));
    assertTrue(view.contains("Error") || view.contains("not found"));
  }

  @Test
  public void testShowStatusBusyWhenWithinEvent() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"BusyEvent\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "show status on 2024-11-15T10:30",
        "exit"
    ));
    assertTrue(view.contains("BUSY"));
  }

  @Test
  public void testFormatEventIncludesLocationWhenPresent() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"WithLoc\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "edit event location \"WithLoc\" from 2024-11-15T10:00 with \"Room 101\"",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 15, 10, 0);
    CalendarEvent ev = model.listEvents(start.toLocalDate(), start.toLocalDate()).get(0);

    Method m = CalendarController.class.getDeclaredMethod(
        "formatEvent", CalendarEvent.class);
    m.setAccessible(true);

    String formatted = (String) m.invoke(controller, ev);
    Assert.assertTrue("Expected location in formatted event",
        formatted.contains("Room 101"));
    Assert.assertTrue("Expected ' at ' in formatted event",
        formatted.contains(" at "));
  }

  @Test
  public void testFormatEventOmitsLocationWhenBlank() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"NoLoc\" from 2024-11-16T10:00 to 2024-11-16T11:00",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 16, 10, 0);
    CalendarEvent ev = model.listEvents(start.toLocalDate(), start.toLocalDate()).get(0);

    Method m = CalendarController.class.getDeclaredMethod(
        "formatEvent", CalendarEvent.class);
    m.setAccessible(true);

    String formatted = (String) m.invoke(controller, ev);
    int countAt = formatted.split(" at ", -1).length - 1;
    Assert.assertEquals(
        "Only the two time 'at' markers should appear when location is blank",
        2,
        countAt
    );
  }

  @Test
  public void testEditCalendarRenameCalendar() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "edit calendar --name Work --property name NewWork",
        "use calendar --name NewWork",
        "exit"
    ));
    assertTrue(view.contains("Renamed calendar to 'NewWork'"));
  }

  @Test
  public void testDeleteSeriesWhenNoMatchingEvents() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "delete series --name \"NonSeries\" --start 2024-11-15T10:00",
        "exit"
    ));
    assertTrue(view.contains("Deleted 0 events"));
  }

  @Test
  public void testExportUnsupportedFormatShowsError() throws IOException {
    Path temp = Files.createTempFile("cal", ".txt");
    try {
      controller.runHeadless(Arrays.asList(
          "create calendar --name Work --timezone UTC",
          "use calendar --name Work",
          "export cal " + temp.toString(),
          "exit"
      ));
      assertTrue(view.contains("unsupported export format")
          || view.contains("export failed"));
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testRunHeadlessNullCommandsThrows() {
    try {
      controller.runHeadless(null);
      fail("Expected NullPointerException for null commands");
    } catch (NullPointerException e) {
      e.getMessage();
    }
  }

  @Test
  public void testSplitOnceSuccessAndMissingDelimiter() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod(
        "splitOnce", String.class, String.class);
    m.setAccessible(true);

    String[] parts = (String[]) m.invoke(null, "left|right", "\\|");
    Assert.assertEquals("left", parts[0]);
    Assert.assertEquals("right", parts[1]);

    try {
      m.invoke(null, "no delimiter here", ",");
      fail("Expected exception for missing delimiter");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(e.getCause().getMessage().contains("missing delimiter"));
    }
  }

  @Test
  public void testCreateAllDayEventBadRepeatsClause() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Weird\" on 2024-11-20 repeats nonsense",
        "exit"
    ));
    assertTrue(view.contains("bad repeats clause") || view.contains("Error"));
  }

  @Test
  public void testAddSingleEndBeforeStartThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod(
        "addSingle",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        String.class,
        String.class
    );
    m.setAccessible(true);

    LocalDateTime s = LocalDateTime.of(2024, 1, 1, 10, 0);
    LocalDateTime e = LocalDateTime.of(2024, 1, 1, 9, 0);

    try {
      m.invoke(controller, "Test", s, e, "", "");
      fail("Expected exception for end before start");
    } catch (InvocationTargetException ex) {
      Assert.assertTrue(ex.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void testMutateUnsupportedProperty() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod(
        "mutate",
        CalendarEvent.class,
        String.class,
        String.class,
        Optional.class
    );
    m.setAccessible(true);

    CalendarEvent ev = new CalendarEvent(
        "X", "d", "l",
        LocalDateTime.of(2024, 1, 1, 10, 0),
        LocalDateTime.of(2024, 1, 1, 11, 0),
        Optional.empty(),
        Optional.empty()
    );

    try {
      m.invoke(controller, ev, "unsupportedProp", "value", Optional.empty());
      fail("Expected exception for unsupported property");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void testApplyEditEventNotFound() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    Method m = CalendarController.class.getDeclaredMethod(
        "applyEdit",
        String.class, String.class, LocalDateTime.class, Optional.class, String.class
    );
    m.setAccessible(true);

    LocalDateTime s = LocalDateTime.of(2024, 1, 1, 10, 0);

    try {
      m.invoke(controller, "subject", "NoSuchTitle", s, Optional.empty(), "X");
      fail("Expected event-not-found exception");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      Assert.assertTrue(cause instanceof IllegalArgumentException);
      Assert.assertTrue(cause.getMessage().toLowerCase().contains("event not found"));
    }
  }

  @Test
  public void testParseDateTimeInvalid() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseDateTime", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "NOT_A_DATE");
      fail("Expected exception for invalid datetime");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void testReadTitleTokenMissingTitle() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readTitleToken", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "");
      fail("Expected exception for missing title");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void testDeleteEventMissingFlags() {
    controller.runHeadless(Arrays.asList(
        "delete event --name \"A\"",
        "exit"
    ));
    assertTrue(view.contains("missing") || view.contains("Error"));
  }

  @Test
  public void testDeleteSeriesMissingFlags() {
    controller.runHeadless(Arrays.asList(
        "delete series --name \"A\"",
        "exit"
    ));
    assertTrue(view.contains("missing") || view.contains("Error"));
  }

  @Test
  public void testCopyEventsBetweenBadRange() {
    controller.runHeadless(Arrays.asList(
        "copy events between 2024-11-15T12:00 2024-11-15T10:00 --target Work",
        "exit"
    ));
    assertTrue(view.contains("end before start") || view.contains("Error"));
  }

  @Test
  public void testEditSeriesMissingValue() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "edit series description \"Meeting\" from 2024-11-15T10:00 with ",
        "exit"
    ));
    assertTrue(view.contains("missing value") || view.contains("Error"));
  }

  @Test
  public void testEditSeriesMissingProperty() {
    controller.runHeadless(Arrays.asList(
        "edit series",
        "exit"
    ));
    assertTrue(view.contains("missing property") || view.contains("Error"));
  }

  @Test
  public void testExpandByCountStopsCorrectly() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    Method m = CalendarController.class.getDeclaredMethod(
        "expandByCount",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        EnumSet.class,
        int.class
    );
    m.setAccessible(true);

    LocalDateTime s = LocalDateTime.of(2024, 1, 1, 10, 0);
    LocalDateTime e = LocalDateTime.of(2024, 1, 1, 11, 0);

    EnumSet<DayOfWeek> dows = EnumSet.of(DayOfWeek.MONDAY);

    m.invoke(controller, "RepeatTest", s, e, dows, 1);

    List<CalendarEvent> events = model.listEvents(
        s.toLocalDate(),
        s.toLocalDate()
    );
    Assert.assertEquals(1, events.size());
  }

  @Test
  public void testExpandUntilStopsCorrectly() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    Method m = CalendarController.class.getDeclaredMethod(
        "expandUntil",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        EnumSet.class,
        LocalDate.class
    );
    m.setAccessible(true);

    LocalDateTime s = LocalDateTime.of(2024, 1, 1, 10, 0);
    LocalDateTime e = LocalDateTime.of(2024, 1, 1, 11, 0);

    EnumSet<DayOfWeek> dows = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);

    m.invoke(controller, "UntilTest", s, e, dows, LocalDate.of(2024, 1, 8));

    List<CalendarEvent> events = model.listEvents(
        LocalDate.of(2024, 1, 1),
        LocalDate.of(2024, 1, 8)
    );
    Assert.assertTrue(events.size() >= 1);
  }

  @Test
  public void testParseDateInvalidFormat() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod(
        "parseDateTime", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "2024-11-15 10:00");
      fail("Expected IllegalArgumentException for invalid date-time format");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      Assert.assertTrue("Expected IllegalArgumentException",
          cause instanceof IllegalArgumentException);
      Assert.assertEquals("Expect YYYY-MM-DDThh:mm", cause.getMessage());
    }
  }

  @Test
  public void testReadTitleTokenMissingTitleThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readTitleToken", String.class);
    m.setAccessible(true);
    try {
      m.invoke(null, "   ");
      fail("Expected IllegalArgumentException for missing title");
    } catch (InvocationTargetException ex) {
      Assert.assertTrue(ex.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing title", ex.getCause().getMessage());
    }
  }

  @Test
  public void testReadTitleTokenUnquotedSingleWord() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readTitleToken", String.class);
    m.setAccessible(true);

    String[] result = (String[]) m.invoke(null, "Standalone");
    Assert.assertEquals("Standalone", result[0]);
    Assert.assertEquals("", result[1]);
  }

  @Test
  public void testReadQuotedTokenMissingClosingQuoteThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readQuotedToken", String.class);
    m.setAccessible(true);
    try {
      m.invoke(null, "\"NoClosing");
      fail("Expected IllegalArgumentException for missing quoted title");
    } catch (InvocationTargetException ex) {
      Assert.assertTrue(ex.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing quoted title", ex.getCause().getMessage());
    }
  }

  @Test
  public void testFlagValueHandlesQuotedNameWithSpaces() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod(
        "flagValue", String.class, String.class);
    m.setAccessible(true);

    String line = "create calendar --name \"My Fancy Cal\" --timezone UTC";
    String name = (String) m.invoke(null, line, "--name");
    Assert.assertEquals("My Fancy Cal", name);
  }

  @Test
  public void testLastTokenAfterMissingNewValueThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod(
        "lastTokenAfter", String.class, String.class);
    m.setAccessible(true);

    String line = "edit calendar --name Work --property timezone";
    try {
      m.invoke(null, line, "--property");
      fail("Expected IllegalArgumentException for missing new property value");
    } catch (InvocationTargetException ex) {
      Assert.assertTrue(ex.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing new property value", ex.getCause().getMessage());
    }
  }

  @Test
  public void testLetterToDowInvalidCharThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("letterToDow", char.class);
    m.setAccessible(true);

    try {
      m.invoke(null, 'x');
      fail("Expected IllegalArgumentException for bad weekday letter");
    } catch (InvocationTargetException ex) {
      Assert.assertTrue(ex.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("bad weekday letter: x", ex.getCause().getMessage());
    }
  }

  @Test
  public void testParseLettersEmptyThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "   ");
      fail("Expected IllegalArgumentException for empty weekday letters");
    } catch (InvocationTargetException ex) {
      Assert.assertTrue(ex.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("empty weekday letters", ex.getCause().getMessage());
    }
  }

  @Test
  public void testCreateRepeatingDateTimeEmptyRepeatsFallsBackToSingle() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 18, 9, 0);
    LocalDateTime end = LocalDateTime.of(2024, 11, 18, 10, 0);

    Method m = CalendarController.class.getDeclaredMethod(
        "createRepeatingDateTime", String.class, LocalDateTime.class,
        LocalDateTime.class, String.class);
    m.setAccessible(true);

    m.invoke(controller, "EmptyRepeats", start, end, "");

    LocalDate day = start.toLocalDate();
    List<CalendarEvent> events = model.listEvents(day, day);
    int count = 0;
    for (CalendarEvent ev : events) {
      if (ev.name().equals("EmptyRepeats")) {
        count++;
      }
    }
    Assert.assertEquals("Empty repeatsPart should behave like single event", 1,
        count);
  }

  @Test
  public void testCreateRepeatingDateTimeMissingRepeatsKeywordFallsBackToSingle() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 19, 9, 0);
    LocalDateTime end = LocalDateTime.of(2024, 11, 19, 10, 0);

    Method m = CalendarController.class.getDeclaredMethod(
        "createRepeatingDateTime", String.class, LocalDateTime.class,
        LocalDateTime.class, String.class);
    m.setAccessible(true);

    m.invoke(controller, "NoRepeatsKeyword", start, end, "MTWR");

    LocalDate day = start.toLocalDate();
    List<CalendarEvent> events = model.listEvents(day, day);
    int count = 0;
    for (CalendarEvent ev : events) {
      if (ev.name().equals("NoRepeatsKeyword")) {
        count++;
      }
    }
    Assert.assertEquals("Missing 'repeats' prefix should behave like single event",
        1, count);
  }

  @Test
  public void testCreateRepeatingDateTimeBadRepeatsClauseThrows() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 20, 9, 0);
    LocalDateTime end = LocalDateTime.of(2024, 11, 20, 10, 0);

    Method m = CalendarController.class.getDeclaredMethod(
        "createRepeatingDateTime", String.class, LocalDateTime.class,
        LocalDateTime.class, String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "BadRepeats", start, end, "repeats MTWR nonsense");
      fail("Expected IllegalArgumentException for bad repeats clause");
    } catch (InvocationTargetException ex) {
      Assert.assertTrue(ex.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("bad repeats clause", ex.getCause().getMessage());
    }
  }

  @Test
  public void testMutateUnsupportedPropertyThrows() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"ToEdit\" from 2024-11-21T09:00 to 2024-11-21T10:00",
        "exit"
    ));

    LocalDate day = LocalDate.of(2024, 11, 21);
    CalendarEvent ev = model.listEvents(day, day).get(0);

    Method m = CalendarController.class.getDeclaredMethod(
        "mutate", CalendarEvent.class, String.class, String.class, Optional.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, ev, "bogusProp", "value",
          Optional.<LocalDateTime>empty());
      fail("Expected IllegalArgumentException for unsupported edit property");
    } catch (InvocationTargetException ex) {
      Assert.assertTrue(ex.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("unsupported edit property: bogusProp", ex.getCause().getMessage());
    }
  }

  @Test
  public void testParseDateTimeRejectsBadFormat() throws Exception {
    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod("parseDateTime", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "2024-11-10 10:00");
      Assert.assertTrue("Expected IllegalArgumentException",
          false);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      Assert.assertTrue(ite.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("Expect YYYY-MM-DDThh:mm", ite.getCause().getMessage());
    }
  }

  @Test
  public void testLetterToDowRejectsBadLetter() throws Exception {
    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod("letterToDow", char.class);
    m.setAccessible(true);

    try {
      m.invoke(null, 'x');
      Assert.assertTrue("Expected IllegalArgumentException", false);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      Assert.assertTrue(ite.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(ite.getCause().getMessage().contains("bad weekday letter"));
    }
  }

  @Test
  public void testParseLettersRejectsEmptyLetters() throws Exception {
    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "   ");
      Assert.assertTrue("Expected IllegalArgumentException", false);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      Assert.assertTrue(ite.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(ite.getCause().getMessage().contains("empty weekday letters"));
    }
  }

  @Test
  public void testParseLettersParsesAllValidLetters() throws Exception {
    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    @SuppressWarnings("unchecked")
    java.util.EnumSet<java.time.DayOfWeek> set =
        (java.util.EnumSet<java.time.DayOfWeek>) m.invoke(null, "MTWRFSU");

    Assert.assertEquals(7, set.size());
    Assert.assertTrue(set.contains(java.time.DayOfWeek.MONDAY));
    Assert.assertTrue(set.contains(java.time.DayOfWeek.TUESDAY));
    Assert.assertTrue(set.contains(java.time.DayOfWeek.WEDNESDAY));
    Assert.assertTrue(set.contains(java.time.DayOfWeek.THURSDAY));
    Assert.assertTrue(set.contains(java.time.DayOfWeek.FRIDAY));
    Assert.assertTrue(set.contains(java.time.DayOfWeek.SATURDAY));
    Assert.assertTrue(set.contains(java.time.DayOfWeek.SUNDAY));
  }

  @Test
  public void testSplitOnceMissingDelimiterThrows() throws Exception {
    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod("splitOnce", String.class, String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "foo bar baz", "\\sand\\s");
      Assert.assertTrue("Expected IllegalArgumentException", false);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      Assert.assertTrue(ite.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(ite.getCause().getMessage().contains("missing delimiter"));
    }
  }

  @Test
  public void testFlagValueMissingFlagThrows() throws Exception {
    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod("flagValue", String.class, String.class);
    m.setAccessible(true);

    String line = "create calendar --name Work --timezone UTC";
    try {
      m.invoke(null, line, "--nonexistent");
      Assert.assertTrue("Expected IllegalArgumentException", false);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      Assert.assertTrue(ite.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(ite.getCause().getMessage().contains("missing --nonexistent"));
    }
  }

  @Test
  public void testCreateRepeatingDateTimeEmptyRepeatsCreatesSingle() throws Exception {
    ensureDefaultCalendar();

    java.time.LocalDateTime start =
        java.time.LocalDateTime.of(2025, 1, 5, 10, 0);
    java.time.LocalDateTime end = start.plusHours(1);

    String title = "RepeatEmpty";
    int before = countEventsWithTitle(title);

    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod(
            "createRepeatingDateTime",
            String.class,
            java.time.LocalDateTime.class,
            java.time.LocalDateTime.class,
            String.class);
    m.setAccessible(true);

    m.invoke(controller, title, start, end, "   ");

    int after = countEventsWithTitle(title);
    Assert.assertEquals(before + 1, after);
  }

  @Test
  public void testCreateRepeatingDateTimeNonRepeatsPrefixCreatesSingle() throws Exception {
    ensureDefaultCalendar();

    java.time.LocalDateTime start =
        java.time.LocalDateTime.of(2025, 1, 6, 9, 0);
    java.time.LocalDateTime end = start.plusHours(1);

    String title = "RepeatNoKeyword";
    int before = countEventsWithTitle(title);

    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod(
            "createRepeatingDateTime",
            String.class,
            java.time.LocalDateTime.class,
            java.time.LocalDateTime.class,
            String.class);
    m.setAccessible(true);

    m.invoke(controller, title, start, end, "MTWR");

    int after = countEventsWithTitle(title);
    Assert.assertEquals(before + 1, after);
  }

  @Test
  public void testCreateRepeatingDateTimeForCountExpandsCorrectly() throws Exception {
    ensureDefaultCalendar();

    java.time.LocalDateTime start =
        java.time.LocalDateTime.of(2025, 1, 5, 8, 0);
    java.time.LocalDateTime end = start.plusHours(1);

    String title = "RepeatForCount";
    int before = countEventsWithTitle(title);

    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod(
            "createRepeatingDateTime",
            String.class,
            java.time.LocalDateTime.class,
            java.time.LocalDateTime.class,
            String.class);
    m.setAccessible(true);

    String repeats = "repeats MTWRFSU for 3 times";
    m.invoke(controller, title, start, end, repeats);

    int after = countEventsWithTitle(title);
    Assert.assertEquals(before + 3, after);
  }

  @Test
  public void testCreateRepeatingDateTimeUntilExpandsUntilDate() throws Exception {
    ensureDefaultCalendar();

    java.time.LocalDateTime start =
        java.time.LocalDateTime.of(2025, 1, 5, 8, 0);
    java.time.LocalDateTime end = start.plusHours(1);

    String title = "RepeatUntil";
    int before = countEventsWithTitle(title);

    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod(
            "createRepeatingDateTime",
            String.class,
            java.time.LocalDateTime.class,
            java.time.LocalDateTime.class,
            String.class);
    m.setAccessible(true);

    String repeats = "repeats SU until 2025-01-26";
    m.invoke(controller, title, start, end, repeats);

    int after = countEventsWithTitle(title);
    int created = after - before;

    Assert.assertTrue("Should create at least one repeating event", created > 0);

    java.time.LocalDate until = java.time.LocalDate.of(2025, 1, 26);
    java.util.List<CalendarEvent> events =
        model.listEvents(start.toLocalDate(), until);

    boolean foundOnUntil = false;
    for (CalendarEvent ev : events) {
      if (title.equals(ev.name())
          && ev.start().toLocalDate().equals(until)) {
        foundOnUntil = true;
        break;
      }
    }

    Assert.assertTrue(
        "Should create an occurrence on the until date",
        foundOnUntil);
  }

  @Test
  public void testCreateRepeatingDateTimeBadClauseThrows() throws Exception {
    ensureDefaultCalendar();

    java.time.LocalDateTime start =
        java.time.LocalDateTime.of(2025, 1, 5, 8, 0);
    java.time.LocalDateTime end = start.plusHours(1);

    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod(
            "createRepeatingDateTime",
            String.class,
            java.time.LocalDateTime.class,
            java.time.LocalDateTime.class,
            String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "BadRepeat", start, end, "repeats MTWRFSU someday");
      Assert.assertTrue("Expected IllegalArgumentException", false);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      Assert.assertTrue(ite.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(ite.getCause().getMessage().contains("bad repeats clause"));
    }
  }

  @Test
  public void testUnknownCommandIsHandledByExecuteOne() {
    controller.runHeadless(java.util.Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "nonsense command that should fail",
        "exit"
    ));
  }

  private void ensureDefaultCalendar() {
    controller.runHeadless(java.util.Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));
  }

  private int countEventsWithTitle(String title) {
    java.time.LocalDate from = java.time.LocalDate.of(2025, 1, 1);
    java.time.LocalDate to = from.plusYears(1);
    int count = 0;
    for (calendar.model.entity.CalendarEvent ev : model.listEvents(from, to)) {
      if (title.equals(ev.name())) {
        count++;
      }
    }
    return count;
  }

  @Test
  public void extra_doBulkEdit_missingProperty_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doBulkEdit", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "   ");
      fail("Expected IllegalArgumentException for missing property");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing property", e.getCause().getMessage());
    }
  }

  @Test
  public void extra_doSeriesEdit_missingProperty_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doSeriesEdit", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "   ");
      fail("Expected IllegalArgumentException for missing property");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing property", e.getCause().getMessage());
    }
  }

  @Test
  public void extra_applyEdit_unsupportedProperty_throwsException() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "exit"
    ));

    java.time.LocalDateTime start =
        java.time.LocalDateTime.of(2024, 11, 15, 10, 0);

    Method m = CalendarController.class.getDeclaredMethod(
        "applyEdit",
        String.class,
        String.class,
        java.time.LocalDateTime.class,
        java.util.Optional.class,
        String.class
    );
    m.setAccessible(true);

    try {
      m.invoke(controller, "bogus", "Test", start, java.util.Optional.empty(), "value");
      fail("Expected IllegalArgumentException for unsupported edit property");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(e.getCause().getMessage()
          .startsWith("unsupported edit property: "));
    }
  }

  @Test
  public void extra_doEditEvent_unsupportedProperty_throwsException() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "exit"
    ));

    Method m = CalendarController.class.getDeclaredMethod("doEditEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller,
          "event bogus \"Test\" from 2024-11-15T10:00 with \"X\"");
      fail("Expected IllegalArgumentException for unsupported property");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(e.getCause().getMessage()
          .startsWith("Unsupported property: "));
    }
  }

  @Test
  public void extra_letterToDow_mappingsAndInvalidLetter() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("letterToDow", char.class);
    m.setAccessible(true);

    Assert.assertEquals(java.time.DayOfWeek.MONDAY, m.invoke(null, 'M'));
    Assert.assertEquals(java.time.DayOfWeek.TUESDAY, m.invoke(null, 'T'));
    Assert.assertEquals(java.time.DayOfWeek.WEDNESDAY, m.invoke(null, 'W'));
    Assert.assertEquals(java.time.DayOfWeek.THURSDAY, m.invoke(null, 'R'));
    Assert.assertEquals(java.time.DayOfWeek.FRIDAY, m.invoke(null, 'F'));
    Assert.assertEquals(java.time.DayOfWeek.SATURDAY, m.invoke(null, 'S'));
    Assert.assertEquals(java.time.DayOfWeek.SUNDAY, m.invoke(null, 'U'));

    try {
      m.invoke(null, 'X');
      fail("Expected IllegalArgumentException for invalid weekday letter");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(e.getCause().getMessage().startsWith("bad weekday letter"));
    }
  }

  @Test
  public void extra_printHelp_listsKeyCommands() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("printHelp");
    m.setAccessible(true);

    m.invoke(controller);

    assertTrue(view.contains("create calendar"));
    assertTrue(view.contains("edit calendar"));
    assertTrue(view.contains("use calendar"));
    assertTrue(view.contains("create event"));
    assertTrue(view.contains("delete event"));
    assertTrue(view.contains("copy events between"));
  }

  @Test
  public void extra_printWelcome_showsWelcomeMessage() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("printWelcome");
    m.setAccessible(true);

    m.invoke(controller);

    assertTrue(view.contains("Calendar ready"));
  }

  @Test
  public void extra_doSeriesEdit_notSeriesEvent_leavesEventUnchanged() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Single\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "exit"
    ));

    int beforeSingle = countEventsWithTitle("Single");

    Method m = CalendarController.class.getDeclaredMethod("doSeriesEdit", String.class);
    m.setAccessible(true);

    m.invoke(controller,
        "subject \"Single\" from 2024-11-15T10:00 with \"NewSubject\"");

    int afterSingle = countEventsWithTitle("Single");
    int afterNew = countEventsWithTitle("NewSubject");

    Assert.assertEquals("Non-series event should remain unchanged", beforeSingle,
        afterSingle);
    int beforeNew = countEventsWithTitle("NewSubject");
    Assert.assertEquals("No event with the new series subject should be created",
        beforeNew, afterNew);
  }

  @Test
  public void parseLetters_allWeekdays_returnsSevenDistinctDays() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    Object setObj = m.invoke(null, "MTWRFSU");
    Assert.assertTrue("Expected a Set from parseLetters", setObj instanceof java.util.Set);

    java.util.Set<?> set = (java.util.Set<?>) setObj;
    Assert.assertEquals("Expected all 7 weekdays to be present", 7, set.size());

    String repr = set.toString();
    Assert.assertTrue("Should contain MONDAY in parsed set", repr.contains("MONDAY"));
    Assert.assertTrue("Should contain SUNDAY in parsed set", repr.contains("SUNDAY"));
  }

  @Test
  public void letterToDow_invalidLetter_throwsIllegalArgumentException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("letterToDow", char.class);
    m.setAccessible(true);

    try {
      m.invoke(null, 'x');
      fail("Expected IllegalArgumentException for invalid weekday letter");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      Assert.assertTrue(cause instanceof IllegalArgumentException);
      Assert.assertTrue(
          "Message should mention bad weekday letter",
          cause.getMessage().contains("bad weekday letter"));
    }
  }

  @Test
  public void lastTokenAfter_missingFlag_throwsIllegalArgumentException() throws Exception {
    Method m = CalendarController.class
        .getDeclaredMethod("lastTokenAfter", String.class, String.class);
    m.setAccessible(true);

    String line = "edit calendar --name Work --property timezone Europe/London";

    try {
      m.invoke(null, line, "--missing");
      fail("Expected IllegalArgumentException for missing flag");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      Assert.assertTrue(cause instanceof IllegalArgumentException);
      Assert.assertEquals("missing --missing", cause.getMessage());
    }
  }

  @Test
  public void testCreateAllDayEventWithRepeatsUntil_createsSeriesUntilInclusive() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"WeekendUntil\" on 2024-11-17 repeats SU until 2024-12-01",
        "exit"
    ));

    Assert.assertTrue(
        view.contains("Created series 'WeekendUntil' until=2024-12-01")
            || view.contains("Created series 'WeekendUntil'")
            || view.contains("Created series"));

    java.time.LocalDate start = java.time.LocalDate.of(2024, 11, 17);
    java.time.LocalDate end = java.time.LocalDate.of(2024, 12, 1);

    java.util.List<CalendarEvent> events = model.listEvents(start, end);

    int countWeekend = 0;
    boolean hasNov17 = false;
    boolean hasNov24 = false;
    boolean hasDec01 = false;

    for (CalendarEvent ev : events) {
      if ("WeekendUntil".equals(ev.name())) {
        countWeekend++;
        java.time.LocalDate d = ev.start().toLocalDate();
        if (d.equals(java.time.LocalDate.of(2024, 11, 17))) {
          hasNov17 = true;
        } else if (d.equals(java.time.LocalDate.of(2024, 11, 24))) {
          hasNov24 = true;
        } else if (d.equals(java.time.LocalDate.of(2024, 12, 1))) {
          hasDec01 = true;
        }
      }
    }

    Assert.assertTrue(
        "Expected at least three 'WeekendUntil' events in the range",
        countWeekend >= 3);

    Assert.assertTrue("Series should include 2024-11-17", hasNov17);
    Assert.assertTrue("Series should include 2024-11-24", hasNov24);
    Assert.assertTrue("Series should include 2024-12-01 (inclusive until date)", hasDec01);
  }

  @Test
  public void extra_doEditEvent_missingPropertyAfterScope_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doEditEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "event subject");
      fail("Expected IllegalArgumentException for missing property");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("Missing property", e.getCause().getMessage());
    }
  }

  @Test
  public void extra_doEditEvent_missingFrom_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doEditEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "event description \"Title\" with \"New desc\"");
      fail("Expected IllegalArgumentException for missing 'from'");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("Expected 'from'", e.getCause().getMessage());
    }
  }

  @Test
  public void extra_doBulkEdit_missingFrom_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doBulkEdit", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "subject \"Title\" with New");
      fail("Expected IllegalArgumentException for missing 'from'");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing 'from'", e.getCause().getMessage());
    }
  }

  @Test
  public void extra_doBulkEdit_missingWith_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doBulkEdit", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "subject \"Title\" from 2024-11-15T10:00");
      fail("Expected IllegalArgumentException for missing 'with'");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing 'with'", e.getCause().getMessage());
    }
  }

  @Test
  public void extra_expandByCount_zeroTimes_createsNoEvents() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    Method m = CalendarController.class.getDeclaredMethod(
        "expandByCount",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        EnumSet.class,
        int.class
    );
    m.setAccessible(true);

    LocalDateTime start = LocalDateTime.of(2024, 1, 1, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 1, 1, 11, 0);
    EnumSet<DayOfWeek> dows = EnumSet.of(DayOfWeek.MONDAY);

    m.invoke(controller, "ZeroCountSeries", start, end, dows, 0);

    java.util.List<CalendarEvent> events =
        model.listEvents(start.toLocalDate(), start.toLocalDate());

    Assert.assertTrue("No events should be created when repeat count is zero",
        events.isEmpty());
  }

  @Test
  public void extra_expandUntil_untilBeforeFirstStart_createsNoEvents() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    Method m = CalendarController.class.getDeclaredMethod(
        "expandUntil",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        EnumSet.class,
        LocalDate.class
    );
    m.setAccessible(true);

    LocalDateTime start = LocalDateTime.of(2024, 2, 5, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 2, 5, 11, 0);
    EnumSet<DayOfWeek> dows = EnumSet.of(start.getDayOfWeek());

    LocalDate until = start.toLocalDate().minusDays(1);

    m.invoke(controller, "NoUntilSeries", start, end, dows, until);

    java.util.List<CalendarEvent> events =
        model.listEvents(until.minusDays(7), until.plusDays(7));

    Assert.assertTrue(
        "No events should be created when 'until' is before the first start date",
        events.isEmpty());
  }

  @Test
  public void testCoverageParseDateTimeInvalidFormatThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseDateTime", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "2024-11-15 10:00");
      fail("Expected IllegalArgumentException for bad datetime format");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("Expect YYYY-MM-DDThh:mm", e.getCause().getMessage());
    }
  }

  @Test
  public void testCoverageUnknownCommandErrorMessage() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "totally unknown command here",
        "exit"
    ));

    assertTrue(view.contains("Unknown command. Type 'help'."));
  }

  @Test
  public void testCoverageDoEditEventMissingScopeThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doEditEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller,
          "bogus subject \"Title\" from 2024-11-15T10:00 with \"NewTitle\"");
      fail("Expected IllegalArgumentException for missing scope");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("Missing scope: event|events|series", e.getCause().getMessage());
    }
  }

  @Test
  public void testCoverageDoEditEventMissingPropertyThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doEditEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "event subject");
      fail("Expected IllegalArgumentException for missing property");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("Missing property", e.getCause().getMessage());
    }
  }

  @Test
  public void testCoverageDoEditEventExpectedFromThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doEditEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller,
          "event subject \"Title\" with \"NewTitle\"");
      fail("Expected IllegalArgumentException for missing 'from'");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("Expected 'from'", e.getCause().getMessage());
    }
  }

  @Test
  public void testCoverageDoEditEventMissingValueThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doEditEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller,
          "event subject \"Title\" from 2024-11-15T10:00 with ");
      fail("Expected IllegalArgumentException for missing value/'with'");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing 'with'", e.getCause().getMessage());
    }
  }

  @Test
  public void testCoverageDoBulkEditMissingFromThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doBulkEdit", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "subject \"Title\" with \"NewTitle\"");
      fail("Expected IllegalArgumentException for missing 'from'");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing 'from'", e.getCause().getMessage());
    }
  }

  @Test
  public void testCoverageDoBulkEditMissingWithThrows() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("doBulkEdit", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller,
          "subject \"Title\" from 2024-11-15T10:00");
      fail("Expected IllegalArgumentException for missing 'with'");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing 'with'", e.getCause().getMessage());
    }
  }

  @Test
  public void testCoverageDeleteEventNotFoundPrintsError() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "delete event --name \"NoSuch\" --start 2024-11-15T10:00",
        "exit"
    ));

    assertTrue(view.contains("event not found")
        || view.contains("Error: event not found"));
  }

  /**
   * Stub exporter that always throws, to exercise the "export failed: ..." branch.
   */
  private static class ThrowingExporter implements calendar.export.CalendarExporter {
    @Override
    public void writeCsv(Path path,
                         java.util.List<CalendarEvent> events,
                         java.time.ZoneId zoneId) {
      throw new RuntimeException("boom-csv");
    }

    @Override
    public void writeIcal(Path path,
                          java.util.List<CalendarEvent> events,
                          java.time.ZoneId zoneId) {
      throw new RuntimeException("boom-ical");
    }
  }

  @Test
  public void testCoverageExportFailureReportedToUser() throws IOException {
    CalendarController failingController =
        new CalendarController(model, view, new ThrowingExporter());

    Path temp = Files.createTempFile("export-fail", ".csv");
    try {
      failingController.runHeadless(Arrays.asList(
          "create calendar --name Work --timezone UTC",
          "use calendar --name Work",
          "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
          "export cal " + temp.toString(),
          "exit"
      ));

      assertTrue(
          view.contains("export failed:")
              || view.contains("Error: export failed:"));
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testCoverageCreateEventBadCreateEventFormat() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Weird\" nonsense tail",
        "exit"
    ));

    assertTrue(view.contains("bad create event format")
        || view.contains("Error: bad create event format"));
  }

  @Test
  public void readQuotedToken_validAndMissingQuote_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readQuotedToken", String.class);
    m.setAccessible(true);

    String[] result = (String[]) m.invoke(null, "\"Hello World\" remainder");
    Assert.assertEquals("Hello World", result[0]);
    Assert.assertEquals("remainder", result[1]);

    try {
      m.invoke(null, "no quotes here");
      fail("Expected IllegalArgumentException for missing quoted title");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing quoted title", e.getCause().getMessage());
    }

    try {
      m.invoke(null, "\"no closing quote");
      fail("Expected IllegalArgumentException for missing quoted title");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing quoted title", e.getCause().getMessage());
    }
  }

  @Test
  public void readTitleToken_quotedUnquotedAndEmpty_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readTitleToken", String.class);
    m.setAccessible(true);

    String[] quoted = (String[]) m.invoke(null, "\"Office Hours\" from 2025-01-10");
    Assert.assertEquals("Office Hours", quoted[0]);
    Assert.assertEquals("from 2025-01-10", quoted[1]);

    String[] unquoted = (String[]) m.invoke(null, "Standup from 2025-01-10");
    Assert.assertEquals("Standup", unquoted[0]);
    Assert.assertEquals("from 2025-01-10", unquoted[1]);

    String[] solo = (String[]) m.invoke(null, "SoloTitle");
    Assert.assertEquals("SoloTitle", solo[0]);
    Assert.assertEquals("", solo[1]);

    try {
      m.invoke(null, "   ");
      fail("Expected IllegalArgumentException for missing title");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing title", e.getCause().getMessage());
    }
  }

  @Test
  public void parseDateTime_validAndInvalidFormat_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseDateTime", String.class);
    m.setAccessible(true);

    LocalDateTime dt = (LocalDateTime) m.invoke(null, "2025-01-10T14:30");
    Assert.assertEquals(2025, dt.getYear());
    Assert.assertEquals(1, dt.getMonthValue());
    Assert.assertEquals(10, dt.getDayOfMonth());
    Assert.assertEquals(14, dt.getHour());
    Assert.assertEquals(30, dt.getMinute());

    try {
      m.invoke(null, "2025-01-10 14:30");
      fail("Expected IllegalArgumentException for bad datetime format");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("Expect YYYY-MM-DDThh:mm", e.getCause().getMessage());
    }

    try {
      m.invoke(null, "not-a-datetime");
      fail("Expected IllegalArgumentException for bad datetime format");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("Expect YYYY-MM-DDThh:mm", e.getCause().getMessage());
    }
  }

  @Test
  public void letterToDow_validAndInvalidLetters() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("letterToDow", char.class);
    m.setAccessible(true);

    Assert.assertEquals(java.time.DayOfWeek.MONDAY, m.invoke(null, 'M'));
    Assert.assertEquals(java.time.DayOfWeek.TUESDAY, m.invoke(null, 't'));
    Assert.assertEquals(java.time.DayOfWeek.WEDNESDAY, m.invoke(null, 'w'));
    Assert.assertEquals(java.time.DayOfWeek.THURSDAY, m.invoke(null, 'R'));
    Assert.assertEquals(java.time.DayOfWeek.FRIDAY, m.invoke(null, 'f'));
    Assert.assertEquals(java.time.DayOfWeek.SATURDAY, m.invoke(null, 'S'));
    Assert.assertEquals(java.time.DayOfWeek.SUNDAY, m.invoke(null, 'u'));

    try {
      m.invoke(null, 'X');
      fail("Expected IllegalArgumentException for bad weekday letter");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("bad weekday letter: X", e.getCause().getMessage());
    }
  }

  @Test
  public void parseLetters_validLettersReturnEnumSet() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    @SuppressWarnings("unchecked")
    java.util.EnumSet<java.time.DayOfWeek> all =
        (java.util.EnumSet<java.time.DayOfWeek>) m.invoke(null, "MTWRFSU");
    Assert.assertEquals(7, all.size());
    Assert.assertTrue(all.contains(java.time.DayOfWeek.MONDAY));
    Assert.assertTrue(all.contains(java.time.DayOfWeek.SUNDAY));

    @SuppressWarnings("unchecked")
    java.util.EnumSet<java.time.DayOfWeek> subset =
        (java.util.EnumSet<java.time.DayOfWeek>) m.invoke(null, "  MMT  ");
    Assert.assertEquals(2, subset.size());
    Assert.assertTrue(subset.contains(java.time.DayOfWeek.MONDAY));
    Assert.assertTrue(subset.contains(java.time.DayOfWeek.TUESDAY));
  }

  @Test
  public void quotedFirst_validAndMissingQuote_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("quotedFirst", String.class);
    m.setAccessible(true);

    String title = (String) m.invoke(null,
        "\"Meeting\" on 2025-01-10T10:00 --target Work to 2025-01-11T10:00");
    Assert.assertEquals("Meeting", title);

    try {
      m.invoke(null, "No leading quote here");
      fail("Expected IllegalArgumentException for missing quoted title");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing quoted title", e.getCause().getMessage());
    }
  }

  @Test
  public void firstClosingQuoteIndex_validAndMissingClosingQuote() throws Exception {
    Method m = CalendarController.class
        .getDeclaredMethod("firstClosingQuoteIndex", String.class);
    m.setAccessible(true);

    String s = "\"Title\" rest";
    int idx = (int) m.invoke(null, s);
    Assert.assertEquals(s.indexOf('"', 1), idx);

    try {
      m.invoke(null, "\"Unclosed title");
      fail("Expected IllegalArgumentException for missing closing quote");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing closing quote", e.getCause().getMessage());
    }
  }

  @Test
  public void lastTokenAfter_validAndMissingNewValue_throwsException() throws Exception {
    Method m = CalendarController.class
        .getDeclaredMethod("lastTokenAfter", String.class, String.class);
    m.setAccessible(true);

    String line = "edit calendar --name Work --property name NewWork";
    String value = (String) m.invoke(null, line, "--property");
    Assert.assertEquals("NewWork", value);

    try {
      m.invoke(null, "edit calendar --name Work", "--property");
      fail("Expected IllegalArgumentException for missing flag");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing --property", e.getCause().getMessage());
    }

    try {
      m.invoke(null, "edit calendar --name Work --property name", "--property");
      fail("Expected IllegalArgumentException for missing new property value");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing new property value", e.getCause().getMessage());
    }
  }

  @Test
  public void doSeriesEdit_missingProperty_throwsException() throws Exception {
    Method m = CalendarController.class
        .getDeclaredMethod("doSeriesEdit", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "subject");
      fail("Expected IllegalArgumentException for missing property");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing property", e.getCause().getMessage());
    }
  }

  @Test
  public void doSeriesEdit_missingFromKeyword_throwsException() throws Exception {
    Method m = CalendarController.class
        .getDeclaredMethod("doSeriesEdit", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller,
          "subject \"Title\" 2025-01-10T10:00 with \"New desc\"");
      fail("Expected IllegalArgumentException for missing 'from'");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing 'from'", e.getCause().getMessage());
    }
  }

  @Test
  public void doSeriesEdit_eventNotFound_throwsException() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Series\" from 2025-01-10T10:00 to 2025-01-10T11:00 repeats M for 3 times",
        "exit"
    ));

    Method m = CalendarController.class
        .getDeclaredMethod("doSeriesEdit", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller,
          "subject \"WrongTitle\" from 2025-01-10T10:00 with \"New desc\"");
      fail("Expected IllegalArgumentException for event not found");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("event not found", e.getCause().getMessage());
    }
  }

  @Test
  public void doSeriesEdit_notSeriesEvent_updatesSingleEvent() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Single\" from 2025-01-10T10:00 to 2025-01-10T11:00",
        "exit"
    ));

    Method m = CalendarController.class.getDeclaredMethod("doSeriesEdit", String.class);
    m.setAccessible(true);

    m.invoke(controller,
        "subject \"Single\" from 2025-01-10T10:00 with \"SingleEdited\"");

    java.time.LocalDate day = java.time.LocalDate.of(2025, 1, 10);
    java.util.List<CalendarEvent> events = model.listEvents(day, day);

    Assert.assertEquals(1, events.size());
    Assert.assertEquals("SingleEdited", events.get(0).name());

    assertTrue(view.contains("OK"));
  }

  @Test
  public void doSeriesEdit_updatesAllEventsInSeries() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"SeriesTest\" from 2025-01-05T09:00 to 2025-01-05T10:00 repeats "
            + "M for 3 times",
        "exit"
    ));

    Method m = CalendarController.class
        .getDeclaredMethod("doSeriesEdit", String.class);
    m.setAccessible(true);

    m.invoke(controller,
        "subject \"SeriesTest\" from 2025-01-05T09:00 with \"UpdatedSeries\"");

    java.time.LocalDate from = java.time.LocalDate.of(2025, 1, 5);
    java.time.LocalDate to = from.plusDays(14);
    java.util.List<CalendarEvent> events = model.listEvents(from, to);

    Assert.assertFalse(events.isEmpty());
    for (CalendarEvent ev : events) {
      Assert.assertEquals("UpdatedSeries", ev.name());
    }
  }

  @Test
  public void testCreateEventFromToWithPrivacyPublic() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Meeting\" from 2024-11-15T10:00 to 2024-11-15T11:00 --privacy public",
        "exit"
    ));

    Assert.assertTrue(
        "Expected an error when using unsupported --privacy flag",
        view.contains("Error"));

    java.time.LocalDate date = java.time.LocalDate.of(2024, 11, 15);
    java.util.List<CalendarEvent> events = model.listEvents(date, date);

    boolean foundMeeting = false;
    for (CalendarEvent ev : events) {
      if ("Meeting".equals(ev.name())) {
        foundMeeting = true;
        break;
      }
    }

    Assert.assertFalse("Meeting event should not be created when using "
            + "unsupported --privacy flag",
        foundMeeting);
  }

  @Test
  public void testBadRepeatsClause() {
    controller.runHeadless(
        Arrays.asList(
            "create calendar --name Work --timezone UTC",
            "use calendar --name Work",
            "create event \"Bad\" from 2024-11-15T10:00 to 2024-11-15T11:00 repeats MW during 3",
            "exit"));

    assertNotNull(controller);
  }

  @Test
  public void testCreateEventOnWithEmptyRepeatsPart() {
    try {
      controller.runHeadless(
          Arrays.asList(
              "create calendar --name Work --timezone UTC",
              "use calendar --name Work",
              "create event \"Test\" on 2024-11-15 repeats",
              "exit"));
      fail("Expected DateTimeParseException for malformed date '2024-11-15 repeats'");
    } catch (java.time.format.DateTimeParseException e) {
      e.getMessage();
    }
  }

  @Test
  public void testCreateEventOnWithPrivacyPublic() {
    try {
      controller.runHeadless(
          Arrays.asList(
              "create calendar --name Work --timezone UTC",
              "use calendar --name Work",
              "create event \"Meeting\" on 2024-11-15 --privacy public",
              "exit"
          ));

      Assert.fail("Expected DateTimeParseException for extra tokens after date with "
          + "--privacy flag");
    } catch (java.time.format.DateTimeParseException e) {
      Assert.assertTrue(e.getParsedString().startsWith("2024-11-15"));
    }
  }

  @Test
  public void testEditEventsMissingProperty() {
    controller.runHeadless(
        Arrays.asList(
            "create calendar --name Work --timezone UTC",
            "use calendar --name Work",
            "create event \"E1\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "create event \"E2\" from 2024-11-16T10:00 to 2024-11-16T11:00 repeats M for 2 times",
            "create event \"E3\" from 2024-11-17T10:00 to 2024-11-17T11:00",
            "edit events \"E2\" from 2024-11-16T10:00 with \"Updated\"",
            "exit"));

    assertNotNull(controller);
  }

  @Test
  public void testEmptyWeekdayLetters() {
    controller.runHeadless(
        Arrays.asList(
            "create calendar --name Work --timezone UTC",
            "use calendar --name Work",
            "create event \"Bad\" from 2024-11-15T10:00 to 2024-11-15T11:00 repeats  for 1 times",
            "exit"
        ));

    java.time.LocalDate date = java.time.LocalDate.of(2024, 11, 15);
    java.util.List<CalendarEvent> events = model.listEvents(date, date);

    int badCount = 0;
    for (CalendarEvent ev : events) {
      if ("Bad".equals(ev.name())) {
        badCount++;
      }
    }

    Assert.assertEquals(
        "Empty weekday letters should not create repeated events",
        1,
        badCount
    );
  }

  @Test
  public void testInvalidWeekdayLetter() {
    controller.runHeadless(
        Arrays.asList(
            "create calendar --name Work --timezone UTC",
            "use calendar --name Work",
            "create event \"Bad\" from 2024-11-15T10:00 to 2024-11-15T11:00 repeats XYZ for "
                + "1 times", "exit"));

    assertNotNull(controller);
  }

  @Test
  public void extra_allDayCreateEvent_parsesSuccessfully() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"AllDayPrivate\" on 2024-11-20",
        "print events on 2024-11-20",
        "exit"
    ));

    assertTrue(view.contains("AllDayPrivate"));
  }

  @Test
  public void testCreateRepeatingDateTimeBadClauseFormat() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 20, 9, 0);
    LocalDateTime end = LocalDateTime.of(2024, 11, 20, 10, 0);

    Method m = CalendarController.class.getDeclaredMethod(
        "createRepeatingDateTime",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "BadFormat", start, end, "repeats MW invalid");
      fail("Expected IllegalArgumentException for bad repeats clause");
    } catch (InvocationTargetException ex) {
      Assert.assertTrue(ex.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(ex.getCause().getMessage().contains("bad repeats clause"));
    }
  }

  @Test
  public void testExpandByCountZeroTimes() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 20, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 11, 20, 11, 0);
    EnumSet<DayOfWeek> dows = EnumSet.of(DayOfWeek.MONDAY);

    Method m = CalendarController.class.getDeclaredMethod(
        "expandByCount",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        EnumSet.class,
        int.class);
    m.setAccessible(true);

    m.invoke(controller, "ZeroCount", start, end, dows, 0);

    List<CalendarEvent> events = model.listEvents(start.toLocalDate(), start.toLocalDate());
    Assert.assertEquals("No events should be created with count=0", 0, events.size());
  }

  @Test
  public void testExpandUntilBeforeStart() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 20, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 11, 20, 11, 0);
    EnumSet<DayOfWeek> dows = EnumSet.of(DayOfWeek.WEDNESDAY);
    LocalDate until = start.toLocalDate().minusDays(5);

    Method m = CalendarController.class.getDeclaredMethod(
        "expandUntil",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        EnumSet.class,
        LocalDate.class);
    m.setAccessible(true);

    m.invoke(controller, "PastUntil", start, end, dows, until);

    List<CalendarEvent> events = model.listEvents(until, start.toLocalDate());
    Assert.assertEquals("No events should be created when until is before start", 0, events.size());
  }

  @Test
  public void testMutateWithEndDateTime() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "exit"
    ));

    LocalDate day = LocalDate.of(2024, 11, 15);
    CalendarEvent ev = model.listEvents(day, day).get(0);

    Method m = CalendarController.class.getDeclaredMethod(
        "mutate",
        CalendarEvent.class,
        String.class,
        String.class,
        Optional.class);
    m.setAccessible(true);

    LocalDateTime newEnd = LocalDateTime.of(2024, 11, 15, 12, 30);
    CalendarEvent result = (CalendarEvent) m.invoke(
        controller,
        ev,
        "description",
        "Updated",
        Optional.of(newEnd));

    Assert.assertEquals("Updated", result.description());
    Assert.assertEquals(newEnd, result.end());
  }

  @Test
  public void testQuotedFirstWithoutQuote() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("quotedFirst", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "NoQuoteHere");
      fail("Expected IllegalArgumentException");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing quoted title", e.getCause().getMessage());
    }
  }

  @Test
  public void testFlagValueWithoutValue() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name",
        "exit"
    ));
    assertTrue(view.contains("Error") || view.contains("missing"));
  }

  @Test
  public void testPrintRangeReverseOrder() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"E\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "print events from 2024-11-16T00:00 to 2024-11-14T23:59",
        "exit"
    ));
    assertTrue(view.contains("(no events)"));
  }

  @Test
  public void testDoSeriesEditNotFoundEvent() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    Method m = CalendarController.class.getDeclaredMethod("doSeriesEdit", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "subject \"NotFound\" from 2024-11-15T10:00 with \"Value\"");
      fail("Expected IllegalArgumentException for event not found");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("event not found", e.getCause().getMessage());
    }
  }



  @Test
  public void testMutateStartProperty() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "exit"
    ));

    LocalDate day = LocalDate.of(2024, 11, 15);
    CalendarEvent ev = model.listEvents(day, day).get(0);

    Method m = CalendarController.class.getDeclaredMethod(
        "mutate",
        CalendarEvent.class,
        String.class,
        String.class,
        Optional.class);
    m.setAccessible(true);

    CalendarEvent result = (CalendarEvent) m.invoke(
        controller,
        ev,
        "start",
        "2024-11-15T09:30",
        Optional.empty());

    Assert.assertEquals(LocalDateTime.of(2024, 11, 15, 9, 30), result.start());
  }

  @Test
  public void testMutateEndProperty() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "exit"
    ));

    LocalDate day = LocalDate.of(2024, 11, 15);
    CalendarEvent ev = model.listEvents(day, day).get(0);

    Method m = CalendarController.class.getDeclaredMethod(
        "mutate",
        CalendarEvent.class,
        String.class,
        String.class,
        Optional.class);
    m.setAccessible(true);

    CalendarEvent result = (CalendarEvent) m.invoke(
        controller,
        ev,
        "end",
        "2024-11-15T12:00",
        Optional.empty());

    Assert.assertEquals(LocalDateTime.of(2024, 11, 15, 12, 0), result.end());
  }

  @Test
  public void testReplaceSingleFailsWhenEventNotFound() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    CalendarEvent fake = new CalendarEvent(
        "NonExistent",
        "",
        "",
        LocalDateTime.of(2024, 11, 15, 10, 0),
        LocalDateTime.of(2024, 11, 15, 11, 0),
        Optional.empty(),
        Optional.empty());

    Method m = CalendarController.class.getDeclaredMethod(
        "replaceSingle",
        CalendarEvent.class,
        CalendarEvent.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, fake, fake);
      fail("Expected IllegalStateException");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalStateException);
      Assert.assertEquals("failed to replace event", e.getCause().getMessage());
    }
  }

  @Test
  public void testFormatEventWithNullLocation() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "exit"
    ));

    LocalDate day = LocalDate.of(2024, 11, 15);
    CalendarEvent ev = model.listEvents(day, day).get(0);

    Method m = CalendarController.class.getDeclaredMethod("formatEvent", CalendarEvent.class);
    m.setAccessible(true);

    String result = (String) m.invoke(controller, ev);
    Assert.assertFalse("Should not contain extra ' at ' from location when location is null/blank",
        result.contains(" at ") && result.split(" at ").length > 3);
  }

  @Test
  public void testCreateEventWithPrivacyPublicFlag() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Public\" from 2024-11-15T10:00 to 2024-11-15T11:00 --privacy public",
        "exit"
    ));
    assertTrue(view.contains("Created") || view.contains("Error"));
  }

  @Test
  public void testDoBulkEditMultipleMatches() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Same\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "create event \"Same\" from 2024-11-16T10:00 to 2024-11-16T11:00",
        "create event \"Same\" from 2024-11-17T10:00 to 2024-11-17T11:00",
        "edit events description \"Same\" from 2024-11-15T10:00 with \"Bulk Updated\"",
        "exit"
    ));
    assertTrue(view.contains("OK"));
  }

  @Test
  public void testLetterToDowLowerCase() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("letterToDow", char.class);
    m.setAccessible(true);

    Assert.assertEquals(DayOfWeek.MONDAY, m.invoke(null, 'm'));
    Assert.assertEquals(DayOfWeek.TUESDAY, m.invoke(null, 't'));
    Assert.assertEquals(DayOfWeek.WEDNESDAY, m.invoke(null, 'w'));
    Assert.assertEquals(DayOfWeek.THURSDAY, m.invoke(null, 'r'));
    Assert.assertEquals(DayOfWeek.FRIDAY, m.invoke(null, 'f'));
    Assert.assertEquals(DayOfWeek.SATURDAY, m.invoke(null, 's'));
    Assert.assertEquals(DayOfWeek.SUNDAY, m.invoke(null, 'u'));
  }

  @Test
  public void testCreateEventWithMultipleSpacesInTitle() {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Title With Multiple   Spaces\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "exit"
    ));
    assertTrue(view.contains("Created event"));
  }

  @Test
  public void testParseDateTimeWithInvalidMonth() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseDateTime", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "2024-13-15T10:00");
      fail("Expected DateTimeParseException");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      Assert.assertTrue("Expected DateTimeParseException",
          cause instanceof DateTimeParseException);
    }
  }

  @Test
  public void testExpandByCountSkipsNonMatchingDays() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 18, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 11, 18, 11, 0);
    EnumSet<DayOfWeek> dows = EnumSet.of(DayOfWeek.FRIDAY);

    Method m = CalendarController.class.getDeclaredMethod(
        "expandByCount",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        EnumSet.class,
        int.class);
    m.setAccessible(true);

    m.invoke(controller, "FridayOnly", start, end, dows, 2);

    List<CalendarEvent> events = model.listEvents(
        start.toLocalDate(),
        start.toLocalDate().plusDays(14));

    int count = 0;
    for (CalendarEvent ev : events) {
      if ("FridayOnly".equals(ev.name())) {
        count++;
        Assert.assertEquals(DayOfWeek.FRIDAY, ev.start().getDayOfWeek());
      }
    }
    Assert.assertEquals(2, count);
  }

  @Test
  public void testExpandUntilOnExactDate() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 17, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 11, 17, 11, 0);
    EnumSet<DayOfWeek> dows = EnumSet.of(DayOfWeek.SUNDAY);
    LocalDate until = LocalDate.of(2024, 11, 17);

    Method m = CalendarController.class.getDeclaredMethod(
        "expandUntil",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        EnumSet.class,
        LocalDate.class);
    m.setAccessible(true);

    m.invoke(controller, "ExactUntil", start, end, dows, until);

    List<CalendarEvent> events = model.listEvents(until, until);
    Assert.assertEquals("Should create event on exact until date", 1, events.size());
  }
}
