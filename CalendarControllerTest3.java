import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import calendar.controller.CalendarController;
import calendar.model.MultiCalendarModelImpl;
import calendar.view.EventView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for CalendarControllerTest3.
 */
public class CalendarControllerTest3 {
  private MultiCalendarModelImpl model;
  private MockView view;
  private CalendarController controller;

  /**
   * Test for Mock view.
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
      String m = "";
      output.add("Error: " + m);
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
    public String readLine() throws IOException {
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

    /**
     * Counts how many recorded output entries contain the given substring.
     * (case-insensitive).
     *
     * @param s the substring to search for
     * @return number of output lines that include the substring
     */
    public int countOccurrences(String s) {
      return (int) output.stream().filter(o -> o.toLowerCase().contains(s.toLowerCase())).count();
    }
  }

  /**
   * creates a setup.
   */
  @Before
  public void setUp() {
    model = new MultiCalendarModelImpl();
    view = new MockView();
    controller = new CalendarController(model, view);
  }

  @Test
  public void testBulkEditBoundaryCondition() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "create event \"Test\" from 2024-11-16T10:00 to 2024-11-16T11:00",
            "edit events subject \"Test\" from 2024-11-15T10:01 with \"Updated\"", "exit"));
    assertTrue(view.contains("OK"));
  }

  @Test
  public void testBulkEditExactThresholdMatch() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "create event \"Test\" from 2024-11-16T10:00 to 2024-11-16T11:00",
            "edit events subject \"Test\" from 2024-11-15T10:00 with \"Updated\"",
            "print events from 2024-11-15T00:00 to 2024-11-16T23:59", "exit"));
    assertTrue(view.contains("Updated"));
  }

  @Test
  public void testBulkEditWithMultipleUpdates() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Same\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "create event \"Same\" from 2024-11-16T10:00 to 2024-11-16T11:00",
            "create event \"Same\" from 2024-11-17T10:00 to 2024-11-17T11:00",
            "edit events location \"Same\" from 2024-11-15T10:00 with \"Room 101\"",
            "print events from 2024-11-15T00:00 to 2024-11-17T23:59", "exit"));
    assertTrue(view.contains("Room 101"));
    assertTrue(view.countOccurrences("Room 101") >= 1);
  }

  @Test
  public void testCopyEventBoundaryIndexOf() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC", "use calendar --name Work",
        "create event \"Meeting\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "copy event \"Meeting\" on 2024-11-15T10:00 --target Personal to 2024-11-16T10:00",
        "use calendar --name Personal", "print events on 2024-11-16", "exit"));
    assertTrue(view.contains("Meeting"));
  }

  @Test
  public void testCopyEventVerifyModelCall() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC", "use calendar --name Work",
        "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "copy event \"Test\" on 2024-11-15T10:00 --target Personal to 2024-11-20T14:00",
        "use calendar --name Personal", "print events on 2024-11-20", "exit"));
    assertTrue(view.contains("Test"));
    assertTrue(view.contains("14:00"));
  }

  @Test
  public void testCopyEventsOnBoundaryConditions() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC", "use calendar --name Work",
        "create event \"E1\" from 2024-11-15T09:00 to 2024-11-15T10:00",
        "create event \"E2\" from 2024-11-15T14:00 to 2024-11-15T15:00",
        "copy events on 2024-11-15 --target Personal to 2024-11-20",
        "use calendar --name Personal", "print events on 2024-11-20", "exit"));
    assertTrue(view.contains("E1") && view.contains("E2"));
  }

  @Test
  public void testCopyEventsOnVerifyModelCall() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC", "use calendar --name Work",
        "create event \"Daily\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "copy events on 2024-11-15 --target Personal to 2024-12-01",
        "use calendar --name Personal", "print events on 2024-12-01", "exit"));
    assertTrue(view.contains("Daily"));
  }

  @Test
  public void testCopyEventsBetweenBoundaryConditions() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"E1\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "create event \"E2\" from 2024-11-16T10:00 to 2024-11-16T11:00",
            "create event \"E3\" from 2024-11-17T10:00 to 2024-11-17T11:00",
            "copy events between 2024-11-15 and 2024-11-17 --target Work to 2024-12-01",
            "print events from 2024-12-01T00:00 to 2024-12-03T23:59", "exit"));
    assertTrue(view.contains("E1") && view.contains("E2") && view.contains("E3"));
  }

  @Test
  public void testCopyEventsBetweenVerifyModelCall() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"A\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "create event \"B\" from 2024-11-16T10:00 to 2024-11-16T11:00",
            "copy events between 2024-11-15 and 2024-11-16 --target Work to 2024-12-10",
            "print events from 2024-12-10T00:00 to 2024-12-11T23:59", "exit"));
    assertTrue(view.contains("A") && view.contains("B"));
  }

  @Test
  public void testCreateEventToIndexBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Meet\" from 2024-11-15T10:00 to 2024-11-15T11:00", "exit"));
    assertTrue(view.contains("Created event"));
  }

  @Test
  public void testCreateEventRepeatsIndexBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Daily\" from 2024-11-15T10:00 to 2024-11-15T11:00 "
                + "repeats M for 2 times",
            "exit"));
    assertTrue(view.contains("Created series") || view.contains("created"));
  }

  @Test
  public void testCreateEventPrivacyMatcher() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Private\" from 2024-11-15T10:00 to 2024-11-15T11:00 --privacy private",
            "exit"));
    assertTrue(view.contains("Created") || view.contains("event"));
  }

  @Test
  public void testCreateEventOnRepeatsIndexBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"AllDay\" on 2024-11-15 repeats M for 3 times", "exit"));
    assertTrue(view.contains("Created series"));
  }

  @Test
  public void testCreateEventOnPrivacyMatcher() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Public\" from 2024-11-15T08:00 to 2024-11-15T17:00 --privacy public",
            "exit"));
    assertTrue(view.contains("Created") || view.contains("event"));
  }

  @Test
  public void testCreateEventOnWithoutRepeats() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"SimpleAllDay\" on 2024-11-15",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("SimpleAllDay"));
  }

  @Test
  public void testDeleteSeriesNameEquality() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Series\" from 2024-11-15T10:00 to 2024-11-15T11:00 "
                + "repeats M for 3 times",
            "create event \"Different\" from 2024-11-22T10:00 to 2024-11-22T11:00",
            "delete series --name \"Series\" --start 2024-11-15T10:00",
            "print events from 2024-11-15T00:00 to 2024-11-29T23:59", "exit"));
    assertTrue(view.contains("Different"));
    assertTrue(!view.contains("starting on 2024-11-15"));
  }

  @Test
  public void testDeleteSeriesCountsCorrectly() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"ToDelete\" from 2024-11-18T10:00 to 2024-11-18T11:00 "
                + "repeats M for 5 times",
            "delete series --name \"ToDelete\" --start 2024-11-18T10:00", "exit"));
    assertTrue(view.contains("Deleted") || view.contains("5"));
  }

  @Test
  public void testEditCalendarRenameVerifyModelCall() {
    controller.runHeadless(Arrays.asList("create calendar --name Old --timezone UTC",
        "edit calendar --name Old --property name New", "use calendar --name New", "exit"));
    assertTrue(view.contains("Using calendar: New"));
  }

  @Test
  public void testEditCalendarTimezoneVerifyModelCall() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "edit calendar --name Work --property timezone America/New_York",
        "use calendar --name Work", "exit"));
    assertTrue(view.contains("America/New_York"));
  }


  @Test
  public void testEditEventToIndexBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event start \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00 "
                + "with 2024-11-15T09:00",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("09:00"));
  }

  @Test
  public void testEditEventWithIndexBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event description \"Test\" from 2024-11-15T10:00 with \"New description\"",
            "exit"));
    assertTrue(view.contains("Edited"));
  }

  @Test
  public void testEditEventValueQuotedStripping() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event subject \"Test\" from 2024-11-15T10:00 with \"Quoted Value\"",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("Quoted Value"));
  }

  @Test
  public void testEditEventEndDtParsing() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event end \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00 "
                + "with 2024-11-15T12:00",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("12:00"));
  }

  @Test
  public void testEditEventCallsApplyEdit() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Original\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event subject \"Original\" from 2024-11-15T10:00 with \"Changed\"",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("Changed"));
  }

  @Test
  public void testPrintDayWithEvents() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Morning\" from 2024-11-15T09:00 to 2024-11-15T10:00",
            "create event \"Afternoon\" from 2024-11-15T14:00 to 2024-11-15T15:00",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("Morning"));
    assertTrue(view.contains("Afternoon"));
  }

  @Test
  public void testPrintRangeToIndexBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"E\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "print events from 2024-11-15T09:00 to 2024-11-15T12:00", "exit"));
    assertTrue(view.contains("E"));
  }

  @Test
  public void testPrintRangeOverlapConditions() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Before\" from 2024-11-15T08:00 to 2024-11-15T09:00",
            "create event \"During\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "create event \"After\" from 2024-11-15T12:00 to 2024-11-15T13:00",
            "print events from 2024-11-15T09:30 to 2024-11-15T11:30", "exit"));
    assertTrue(view.contains("During"));
  }

  @Test
  public void testPrintRangeNoMatchPrintsMessage() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"E\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "print events from 2024-11-16T00:00 to 2024-11-16T23:59", "exit"));
    assertTrue(view.contains("(no events)"));
  }

  @Test
  public void testSeriesEditWithIndexBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Series\" from 2024-11-18T10:00 to 2024-11-18T11:00 "
                + "repeats M for 3 times",
            "edit series location \"Series\" from 2024-11-18T10:00 with \"Conference Room\"",
            "print events from 2024-11-18T00:00 to 2024-12-09T23:59", "exit"));
    assertEquals(1, view.countOccurrences("Conference Room"));
  }

  @Test
  public void testSeriesEditValueQuotedStripping() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Series\" from 2024-11-15T10:00 to 2024-11-15T11:00 "
                + "repeats M for 2 times",
            "edit series subject \"Series\" from 2024-11-15T10:00 with \"New Title\"",
            "print events from 2024-11-15T00:00 to 2024-11-22T23:59", "exit"));
    assertTrue(view.contains("New Title"));
  }

  @Test
  public void testFirstClosingQuoteIndexBoundary() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC", "use calendar --name Work",
        "create event \"A\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "copy event \"A\" on 2024-11-15T10:00 --target Personal to 2024-11-16T10:00", "exit"));
    assertTrue(view.contains("Copied event"));
  }

  @Test
  public void testFlagValueIndexOfBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC",
            "delete event --name \"NonExistent\" --start 2024-11-15T10:00", "exit"));
    assertTrue(view.contains("not found") || view.contains("Error"));
  }

  @Test
  public void testFlagValueQuotedExtraction() {
    controller.runHeadless(
        Arrays.asList("create calendar --name \"My Work Calendar\" --timezone UTC",
            "use calendar --name \"My Work Calendar\"", "exit"));
    assertTrue(view.contains("Using calendar: My Work Calendar"));
  }

  @Test
  public void testLastTokenAfterBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC",
            "edit calendar --name Work --property timezone America/Los_Angeles", "exit"));
    assertTrue(view.contains("Changed timezone"));
  }

  @Test
  public void testMutateStatusProperty() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event status \"Test\" from 2024-11-15T10:00 with private", "exit"));
    assertTrue(view.contains("Edited"));
  }


  @Test
  public void testPrintHelpOutputsAllLines() {
    controller.runHeadless(Arrays.asList("help", "exit"));
    assertTrue(view.contains("Commands:"));
    assertTrue(view.contains("create calendar"));
    assertTrue(view.contains("edit calendar"));
    assertTrue(view.contains("use calendar"));
    assertTrue(view.contains("create event"));
    assertTrue(view.contains("edit event"));
    assertTrue(view.contains("delete event"));
    assertTrue(view.contains("print events"));
    assertTrue(view.contains("show status"));
    assertTrue(view.contains("export cal"));
    assertTrue(view.contains("copy event"));
    assertTrue(view.contains("help"));
    assertTrue(view.contains("exit"));
  }

  @Test
  public void testReadQuotedTokenSecondIndexBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"X\" from 2024-11-15T10:00 to 2024-11-15T11:00", "exit"));
    assertTrue(view.contains("Created event"));
  }

  @Test
  public void testReadTitleTokenSpaceIndexBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event Standup from 2024-11-15T10:00 to 2024-11-15T11:00", "exit"));
    assertTrue(view.contains("Created event"));
  }

  @Test
  public void testRunInteractiveRuntimeError() {
    view.inputs.addAll(Arrays.asList("invalid command here", "exit"));
    controller.runInteractive();
    assertTrue(view.contains("Error"));
  }

  /**
   * Tests for boundary cases.
   */

  @Test
  public void testCreateEventWithExactRepeatsMatch() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00 repeats M for 1 times",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("Test"));
  }

  @Test
  public void testEditEventWithExactFromMatch() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event subject \"Test\" from 2024-11-15T10:00 with NewName", "exit"));
    assertTrue(view.contains("Edited"));
  }

  @Test
  public void testPrintRangeExactBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"E\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "print events from 2024-11-15T10:00 to 2024-11-15T11:00", "exit"));
    assertTrue(view.contains("E"));
  }

  @Test
  public void testCopyEventOnExactIndex() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC", "use calendar --name Work",
        "create event \"M\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "copy event \"M\" on 2024-11-15T10:00 --target Personal to 2024-11-16T10:00", "exit"));
    assertTrue(view.contains("Copied event"));
  }

  @Test
  public void testEditEventsThresholdExactlyAtStartTime() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"A\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "create event \"A\" from 2024-11-16T10:00 to 2024-11-16T11:00",
            "edit events subject \"A\" from 2024-11-16T10:00 with \"Modified\"",
            "print events from 2024-11-15T00:00 to 2024-11-16T23:59", "exit"));
    assertTrue(view.contains("A") && view.contains("Modified"));
  }

  @Test
  public void testDeleteSeriesThresholdExactlyAtStartTime() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Del\" from 2024-11-18T10:00 to 2024-11-18T11:00 repeats M for 3 times",
            "delete series --name \"Del\" --start 2024-11-18T10:00", "exit"));
    assertTrue(view.contains("Deleted 1 events"));
  }

  @Test
  public void testBulkEditWithIndexBoundaryAtThreshold() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit events subject \"Test\" from 2024-11-15T09:59 with \"Updated\"",
            "print events from 2024-11-15T00:00 to 2024-11-15T23:59", "exit"));
    assertTrue(view.contains("OK"));
  }

  @Test
  public void testCopyEventWithExactIndexMatching() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC", "use calendar --name Work",
        "create event \"Event\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "copy event \"Event\" on 2024-11-15T10:00 --target Personal to 2024-11-16T10:00",
        "use calendar --name Personal", "print events on 2024-11-16", "exit"));
    assertTrue(view.contains("Event") && view.contains("2024-11-16"));
  }

  @Test
  public void testCopyEventsBetweenWithExactBoundaries() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"E1\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "copy events between 2024-11-15 and 2024-11-15 --target Work to 2024-12-01",
            "print events from 2024-12-01T00:00 to 2024-12-01T23:59", "exit"));
    assertTrue(view.contains("E1"));
  }

  @Test
  public void testCopyEventsOnWithExactIndexPositions() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC", "use calendar --name Work",
        "create event \"Daily\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "copy events on 2024-11-15 --target Personal to 2024-11-16",
        "use calendar --name Personal", "print events on 2024-11-16", "exit"));
    assertTrue(view.contains("Daily") && view.contains("2024-11-16"));
  }

  @Test
  public void testCreateEventWithToAtExactBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("Test") && view.contains("10:00") && view.contains("11:00"));
  }

  @Test
  public void testCreateEventRepeatsAtExactBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Daily\" from 2024-11-18T10:00 to 2024-11-18T11:00 "
                + "repeats M for 1 times",
            "print events from 2024-11-18T00:00 to 2024-11-18T23:59", "exit"));
    assertTrue(view.contains("Daily"));
  }

  @Test
  public void testCreateEventWithPrivacyFlagMatch() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Priv\" from 2024-11-15T10:00 to 2024-11-15T11:00 --privacy private",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("Created") || view.contains("Priv"));
  }

  @Test
  public void testCreateEventOnWithRepeatsAtBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"AllDay\" on 2024-11-18 repeats M for 1 times",
            "print events on 2024-11-18", "exit"));
    assertTrue(view.contains("AllDay"));
  }

  @Test
  public void testCreateEventOnWithPrivacyFlag() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Pub\" from 2024-11-15T10:00 to 2024-11-15T11:00 --privacy public",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("Created") || view.contains("Pub"));
  }

  @Test
  public void testCreateEventActuallyAddsToModel() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"AddedEvent\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("AddedEvent"));
  }

  @Test
  public void testEditCalendarTimezoneActuallyChanges() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "edit calendar --name Work --property timezone America/Chicago",
        "use calendar --name Work", "exit"));
    assertTrue(view.contains("America/Chicago"));
  }

  @Test
  public void testEditEventWithToClauseAtBoundary() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event start \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00 "
                + "with 2024-11-15T09:30",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("09:30"));
  }

  @Test
  public void testEditEventWithExactWithPosition() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event description \"Test\" from 2024-11-15T10:00 with Description",
            "exit"));
    assertTrue(view.contains("Edited"));
  }

  @Test
  public void testEditEventValueWithQuotes() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event subject \"Test\" from 2024-11-15T10:00 with \"NewSubject\"",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("NewSubject"));
  }

  @Test
  public void testEditEventWithEndDateTime() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event end \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00 "
                + "with 2024-11-15T13:00",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("13:00"));
  }

  @Test
  public void testEditEventWithoutEndDateTime() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event subject \"Test\" from 2024-11-15T10:00 with NewName",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("NewName"));
  }

  @Test
  public void testPrintRangeWithExactToIndex() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"E\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "print events from 2024-11-15T09:00 to 2024-11-15T12:00", "exit"));
    assertTrue(view.contains("E") && view.contains("10:00"));
  }

  @Test
  public void testSeriesEditWithExactWithIndex() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Series\" from 2024-11-18T10:00 to 2024-11-18T11:00 "
                + "repeats M for 2 times",
            "edit series location \"Series\" from 2024-11-18T10:00 with Room",
            "print events from 2024-11-18T00:00 to 2024-11-25T23:59", "exit"));
    assertTrue(view.contains("Room"));
  }

  @Test
  public void testSeriesEditWithQuotedValueStripping() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Series\" from 2024-11-18T10:00 to "
                + "2024-11-18T11:00 repeats M for 2 times",
            "edit series subject \"Series\" from 2024-11-18T10:00 with \"NewSeriesName\"",
            "print events from 2024-11-18T00:00 to 2024-11-25T23:59", "exit"));
    assertTrue(view.contains("NewSeriesName"));
  }

  @Test
  public void testCopyEventQuoteIndexBoundary() {
    controller.runHeadless(Arrays.asList("create calendar --name Work --timezone UTC",
        "create calendar --name Personal --timezone UTC", "use calendar --name Work",
        "create event \"E\" from 2024-11-15T10:00 to 2024-11-15T11:00",
        "copy event \"E\" on 2024-11-15T10:00 --target Personal to 2024-11-16T10:00", "exit"));
    assertTrue(view.contains("Copied"));
  }


  @Test
  public void testFlagValueWithExactIndexMatching() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "delete event --name \"NonExist\" --start 2024-11-15T10:00", "exit"));
    assertTrue(view.contains("not found"));
  }

  @Test
  public void testFlagValueWithQuotedString() {
    controller.runHeadless(
        Arrays.asList("create calendar --name \"Test Calendar\" --timezone UTC",
            "use calendar --name \"Test Calendar\"", "exit"));
    assertTrue(view.contains("Test Calendar"));
  }

  @Test
  public void testLastTokenAfterWithExactIndex() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC",
            "edit calendar --name Work --property timezone Europe/London", "exit"));
    assertTrue(view.contains("Changed timezone"));
  }

  @Test
  public void testMutateReturnsNonNullEvent() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"Test\" from 2024-11-15T10:00 to 2024-11-15T11:00",
            "edit event status \"Test\" from 2024-11-15T10:00 with private",
            "print events on 2024-11-15", "exit"));
    assertTrue(view.contains("Test"));
  }

  @Test
  public void testPrintHelpContainsAllCommands() {
    controller.runHeadless(Arrays.asList("help", "exit"));
    assertTrue(view.contains("create calendar"));
    assertTrue(view.contains("edit calendar"));
    assertTrue(view.contains("use calendar"));
    assertTrue(view.contains("create event"));
    assertTrue(view.contains("edit event"));
    assertTrue(view.contains("edit events"));
    assertTrue(view.contains("delete event"));
    assertTrue(view.contains("delete series"));
    assertTrue(view.contains("print events on"));
    assertTrue(view.contains("print events from"));
    assertTrue(view.contains("show status"));
    assertTrue(view.contains("export cal"));
    assertTrue(view.contains("copy event"));
    assertTrue(view.contains("copy events on"));
    assertTrue(view.contains("copy events between"));
  }

  @Test
  public void testReadQuotedTokenWithExactSecondQuote() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event \"A\" from 2024-11-15T10:00 to 2024-11-15T11:00", "exit"));
    assertTrue(view.contains("A"));
  }

  @Test
  public void testReadTitleTokenWithSpaceIndex() {
    controller.runHeadless(
        Arrays.asList("create calendar --name Work --timezone UTC", "use calendar --name Work",
            "create event Meeting from 2024-11-15T10:00 to 2024-11-15T11:00", "exit"));
    assertTrue(view.contains("Meeting"));
  }

  @Test
  public void testRunInteractiveErrorMessage() {
    view.inputs.addAll(Arrays.asList("bad command", "exit"));
    controller.runInteractive();
    assertTrue(view.contains("Error"));
  }


}
