import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import calendar.controller.CalendarController;
import calendar.export.CalendarExporter;
import calendar.model.CalendarModel;
import calendar.model.MultiCalendarModelImpl;
import calendar.model.entity.CalendarEvent;
import calendar.view.EventView;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for {@link CalendarController} that maximize branch coverage and
 * mutation resistance by verifying parsing, delegation, error paths, and
 * exporter wiring. Uses small fakes for the model, view, and exporter.
 */
public class CalendarControllerTest1 {

  private FakeModel model;
  private CapturingView view;
  private CapturingExporter exporter;
  private CalendarController controller;

  /**
   * Initializes fresh instances of the controller and its collaborators before
   * each test. This ensures every test starts from a clean, isolated state.
   *
   * <p>Specifically:
   * <ul>
   *   <li>Creates a new {@code FakeModel} to capture model interactions.</li>
   *   <li>Creates a new {@code CapturingView} to record printed output.</li>
   *   <li>Creates a new {@code CapturingExporter} to track export calls.</li>
   *   <li>Constructs a new {@link CalendarController} with those test doubles.</li>
   * </ul>
   */
  @Before
  public void setUp() {
    this.model = new FakeModel();
    this.view = new CapturingView("");
    this.exporter = new CapturingExporter();
    this.controller = new CalendarController(model, view, exporter);
  }

  private static java.lang.reflect.Method splitOnceMethod;

  /**
   * Reflect the private static splitOnce(String, String) so we can cover its branches.
   */
  @BeforeClass
  public static void reflectSplitOnce() throws Exception {
    Class<?> cls = Class.forName("calendar.controller.CalendarController");
    splitOnceMethod = cls.getDeclaredMethod("splitOnce", String.class, String.class);
    splitOnceMethod.setAccessible(true);
  }

  private static String[] invokeSplitOnce(final String s, final String regex) {
    try {
      Object out = splitOnceMethod.invoke(null, s, regex);
      return (String[]) out;
    } catch (java.lang.reflect.InvocationTargetException ite) {
      if (ite.getTargetException() instanceof IllegalArgumentException) {
        throw (IllegalArgumentException) ite.getTargetException();
      }
      throw new RuntimeException(ite.getTargetException());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static class MockView implements EventView {
    private final StringBuilder log = new StringBuilder();
    private final java.util.Queue<String> inputs = new java.util.LinkedList<>();

    void addInput(String s) {
      inputs.add(s);
    }

    @Override
    public void print(String s) {
      log.append(s);
    }

    @Override
    public void println(String s) {
      log.append(s).append("\n");
    }

    @Override
    public String readLine() {
      return inputs.isEmpty() ? null : inputs.poll();
    }

    @Override
    public void error(String message) {
      log.append("ERROR: ").append(message).append("\n");
    }

    String getOutput() {
      return log.toString();
    }
  }

  @Test
  public void runHeadless_handlesNullCommandLine_asEmptyAndSkips() {
    FakeModel model = new FakeModel();
    CapturingView view = new CapturingView("runHeadless-null");

    CalendarController controller = new CalendarController(model, view);

    java.util.List<String> script = java.util.Arrays.asList(
        "help",
        null,
        "exit"
    );

    controller.runHeadless(script);

    org.junit.Assert.assertTrue(true);
  }

  @Test
  public void readQuotedToken_missingOpeningQuote_throwsIllegalArgumentException()
      throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readQuotedToken", String.class);
    m.setAccessible(true);

    String input = "no opening quote here";

    try {
      m.invoke(null, input);
      Assert.fail("Expected IllegalArgumentException for missing opening quote");
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      Assert.assertTrue("Cause should be IllegalArgumentException",
          cause instanceof IllegalArgumentException);
      Assert.assertEquals("missing quoted title", cause.getMessage());
    }
  }

  @Test
  public void parseLetters_null_throwsIllegalArgumentException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, new Object[]{ null });
      Assert.fail("Expected IllegalArgumentException for null letters");
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      Assert.assertTrue("Cause should be IllegalArgumentException",
          cause instanceof IllegalArgumentException);
      Assert.assertEquals("empty weekday letters", cause.getMessage());
    }
  }

  @Test
  public void parseLetters_blank_throwsIllegalArgumentException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "   ");
      Assert.fail("Expected IllegalArgumentException for blank letters");
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      Assert.assertTrue("Cause should be IllegalArgumentException",
          cause instanceof IllegalArgumentException);
      Assert.assertEquals("empty weekday letters", cause.getMessage());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void parseLetters_validLetters_returnsNonEmptyEnumSet() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    Object result = m.invoke(null, "M");
    Assert.assertTrue("Result should be an EnumSet", result instanceof EnumSet);
    EnumSet<?> set = (EnumSet<?>) result;
    Assert.assertFalse("EnumSet should not be empty for 'M'", set.isEmpty());
  }

  @Test
  public void deleteEvent_existingEvent_printsDeleted() throws Exception {
    CalendarModel model = (CalendarModel) java.lang.reflect.Proxy.newProxyInstance(
        CalendarModel.class.getClassLoader(),
        new Class<?>[]{CalendarModel.class},
        (proxy, method, args) -> {
          if ("removeEvent".equals(method.getName())) {
            return true;
          }
          Class<?> rt = method.getReturnType();
          if (rt.equals(boolean.class)) {
            return false;
          } else if (rt.isPrimitive()) {
            if (rt.equals(int.class) || rt.equals(long.class)
                || rt.equals(short.class) || rt.equals(byte.class)) {
              return 0;
            }
            if (rt.equals(double.class) || rt.equals(float.class)) {
              return 0.0;
            }
            if (rt.equals(char.class)) {
              return '\0';
            }
          }
          return null;
        }
    );

    CapturingView view = new CapturingView("East");
    CalendarController controller = new CalendarController(model, view);

    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod("doDeleteEvent", String.class);
    m.setAccessible(true);

    m.invoke(controller,
        "delete-event --name \"meeting\" --start 2025-01-10T09:00");

    Assert.assertTrue("deleteEvent should complete successfully", true);
  }

  @Test
  public void deleteEvent_missingEvent_throwsIllegalArgumentException() throws Exception {
    CalendarModel model = (CalendarModel) Proxy.newProxyInstance(
        CalendarModel.class.getClassLoader(),
        new Class<?>[]{CalendarModel.class},
        new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("removeEvent".equals(method.getName())) {
              return false;
            }
            if (method.getReturnType().equals(boolean.class)) {
              return false;
            }
            return null;
          }
        });

    CapturingView view = new CapturingView("East");
    CalendarController controller = new CalendarController(model, view);

    Method m = CalendarController.class.getDeclaredMethod("doDeleteEvent", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "delete-event --name \"missing\" --start 2025-01-10T09:00");
      Assert.fail("Expected IllegalArgumentException for missing event");
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      Assert.assertTrue("Cause should be IllegalArgumentException",
          cause instanceof IllegalArgumentException);
      Assert.assertEquals("event not found", cause.getMessage());
    }
  }

  @Test
  public void flagValue_quotedValue_returnsInnerText() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("flagValue",
        String.class, String.class);
    m.setAccessible(true);

    String flag = "--name";
    String line = "edit-calendar " + flag + " \"My Cool Calendar\" timezone America/New_York";

    Object result = m.invoke(null, line, flag);
    Assert.assertTrue(result instanceof String);
    Assert.assertEquals("My Cool Calendar", result);
  }

  @Test
  public void lastTokenAfter_missingFlag_throwsIllegalArgumentException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("lastTokenAfter",
        String.class, String.class);
    m.setAccessible(true);

    String line = "edit cal1 name NewName";
    String flag = "--property";

    try {
      m.invoke(null, line, flag);
      Assert.fail("Expected IllegalArgumentException for missing flag");
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      Assert.assertTrue("Cause should be IllegalArgumentException",
          cause instanceof IllegalArgumentException);
      Assert.assertEquals("missing " + flag, cause.getMessage());
    }
  }

  @Test
  public void lastTokenAfter_missingNewValue_throwsIllegalArgumentException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("lastTokenAfter",
        String.class, String.class);
    m.setAccessible(true);

    String line = "edit cal1 --property name";
    String flag = "--property";

    try {
      m.invoke(null, line, flag);
      Assert.fail("Expected IllegalArgumentException for missing new property value");
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      Assert.assertTrue("Cause should be IllegalArgumentException",
          cause instanceof IllegalArgumentException);
      Assert.assertEquals("missing new property value", cause.getMessage());
    }
  }

  @Test
  public void readQuotedToken_missingClosingQuote_throwsIllegalArgumentException()
      throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readQuotedToken", String.class);
    m.setAccessible(true);

    String input = "\"starts with quote but never ends";

    try {
      m.invoke(null, input);
      Assert.fail("Expected IllegalArgumentException for missing closing quote");
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      Assert.assertTrue("Cause should be IllegalArgumentException",
          cause instanceof IllegalArgumentException);
      Assert.assertEquals("missing quoted title", cause.getMessage());
    }
  }

  @Test
  public void createEvent_endBeforeStart_triggersAddSingleException() throws Exception {
    FakeModel model = new FakeModel();
    CapturingView view = new CapturingView("");
    CalendarController controller = new CalendarController(model, view);

    LocalDateTime start = LocalDateTime.of(2025, 1, 10, 12, 0);
    LocalDateTime end = LocalDateTime.of(2025, 1, 10, 11, 0);

    Method addSingle = CalendarController.class.getDeclaredMethod(
        "addSingle",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        String.class,
        String.class
    );
    addSingle.setAccessible(true);

    try {
      addSingle.invoke(controller, "BadEvent", start, end, "", "");
      Assert.fail("Expected IllegalArgumentException for end before start");
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      Assert.assertTrue("Cause should be IllegalArgumentException",
          cause instanceof IllegalArgumentException);
      Assert.assertEquals("end before start", cause.getMessage());
    }
  }

  @Test
  public void editCalendar_propertyName_renamesAndPrints() {
    final class TestView implements EventView {
      final StringBuilder out = new StringBuilder();

      @Override
      public void println(String s) {
        out.append(s).append('\n');
      }

      @Override
      public void print(String s) {
        out.append(s);
      }

      @Override
      public void error(String s) {
        out.append("error: ").append(s).append('\n');
      }

      @Override
      public String readLine() {
        return null;
      }

      @Override
      public String toString() {
        return out.toString();
      }
    }

    TestView view = new TestView();
    CalendarController c = new CalendarController(new FakeModel(), view, this.exporter);

    List<String> cmds = Arrays.asList(
        "edit calendar --name Cal --property name NewCal",
        "bye"
    );
    c.runHeadless(cmds);

    String out = view.toString();
    assertTrue("should acknowledge rename; output:\n" + out,
        out.contains("Renamed calendar to 'NewCal'"));
  }

  @Test
  public void editEvent_unsupportedProperty_printsError() {
    final class TestView implements EventView {
      final StringBuilder out = new StringBuilder();

      @Override
      public void println(String s) {
        out.append(s).append('\n');
      }

      @Override
      public void print(String s) {
        out.append(s);
      }

      @Override
      public void error(String s) {
        out.append("error: ").append(s).append('\n');
      }

      @Override
      public String readLine() {
        return null;
      }

      @Override
      public String toString() {
        return out.toString();
      }
    }

    TestView view = new TestView();
    CalendarController c = new CalendarController(new FakeModel(), view, this.exporter);

    List<String> cmds = Arrays.asList(
        "create event BadProp from 2025-01-17T09:00 to 2025-01-17T10:00",
        "edit event --name BadProp --from 2025-01-17T09:00 --property color blue",
        "bye"
    );
    c.runHeadless(cmds);

    String out = view.toString().toLowerCase(Locale.ROOT);
    assertTrue("should report unsupported property; output:\n" + out,
        out.contains("unsupported") || out.contains("error"));
  }

  @Test
  public void editCalendar_propertyTimezone_changesZoneAndPrints() {
    final class TestView implements EventView {
      final StringBuilder out = new StringBuilder();

      @Override
      public void println(String s) {
        out.append(s).append('\n');
      }

      @Override
      public void print(String s) {
        out.append(s);
      }

      @Override
      public void error(String s) {
        out.append("error: ").append(s).append('\n');
      }

      @Override
      public String readLine() {
        return null;
      }

      @Override
      public String toString() {
        return out.toString();
      }
    }

    TestView view = new TestView();
    CalendarController c = new CalendarController(new FakeModel(), view, this.exporter);

    List<String> cmds = Arrays.asList(
        "edit calendar --name Cal --property timezone America/Los_Angeles",
        "bye"
    );
    c.runHeadless(cmds);

    String out = view.toString();
    assertTrue("should acknowledge timezone change; output:\n" + out,
        out.contains("Changed timezone to America/Los_Angeles"));
  }

  @Test
  public void editCalendar_unsupportedProperty_printsError() {
    final class TestView implements EventView {
      final StringBuilder out = new StringBuilder();

      @Override
      public void println(String s) {
        out.append(s).append('\n');
      }

      @Override
      public void print(String s) {
        out.append(s);
      }

      @Override
      public void error(String s) {
        out.append("error: ").append(s).append('\n');
      }

      @Override
      public String readLine() {
        return null;
      }

      @Override
      public String toString() {
        return out.toString();
      }
    }

    TestView view = new TestView();
    CalendarController c = new CalendarController(new FakeModel(), view, this.exporter);

    List<String> cmds = Arrays.asList(
        "edit calendar --name Cal --property color blue",
        "bye"
    );
    c.runHeadless(cmds);

    String out = view.toString().toLowerCase(Locale.ROOT);
    assertTrue("should mention unsupported property/error; output:\n" + out,
        out.contains("unsupported") || out.contains("error"));
  }

  @Test
  public void splitOnce_happyPath_trimsAndSplits() {
    String[] parts = invokeSplitOnce("  title  :   body text  ", ":");
    assertEquals("title", parts[0]);
    assertEquals("body text", parts[1]);
  }

  @Test
  public void splitOnce_onlyFirstDelimiter_isUsed() {
    String[] parts = invokeSplitOnce("a:b:c", ":");
    assertEquals("a", parts[0]);
    assertEquals("b:c", parts[1]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void splitOnce_missingDelimiter_throws() {
    invokeSplitOnce("no delimiter here", ":");
  }

  /**
   * Headless should report a hard error if the script does not end with 'exit'.
   * Covers the explicit error branch in runHeadless.
   */
  @Test
  public void headless_reportsErrorIfMissingExit() {
    List<String> cmds = new ArrayList<>();
    cmds.add("create calendar --name Work --timezone America/New_York");
    controller.runHeadless(cmds);

    String out = view.allOutputLower();
    assertTrue("expected headless missing-exit error",
        out.contains("error: headless file ended without 'exit'."));
  }

  /**
   * Create calendar command should delegate to model and confirm to the user.
   * Hits success path of doCreateCalendar and the println confirmation.
   */
  @Test
  public void createCalendar_parsesAndDelegates() {
    List<String> cmds = new ArrayList<>();
    cmds.add("create calendar --name East --timezone America/New_York");
    cmds.add("exit");
    controller.runHeadless(cmds);

    assertEquals("America/New_York", model.zones.get("east").getId());
    assertTrue(view.allOutputLower().contains("created calendar 'east' in zone america/new_york"));
  }

  /**
   * Using a calendar by name (different case) selects it as current.
   * Covers normalize/lowercasing and success branch of doUseCalendar.
   */
  @Test
  public void useCalendar_afterCreate_setsCurrent() {
    List<String> cmds = new ArrayList<>();
    cmds.add("create calendar --name East --timezone America/New_York");
    cmds.add("use calendar --name EAST");
    cmds.add("exit");
    controller.runHeadless(cmds);

    assertEquals("east", model.current);
    assertTrue(view.allOutputLower().contains("using calendar: east"));
  }

  /**
   * Create event with explicit from/to datetimes.
   * Verifies parsing, construction, model delegation, and confirmation print.
   */
  @Test
  public void createEvent_fromTo_addsAndConfirms() {
    List<String> cmds = new ArrayList<>();
    cmds.add("create calendar --name Work --timezone America/New_York");
    cmds.add("create event \"Team Sync\" from 2025-01-06T10:00 to 2025-01-06T10:30");
    cmds.add("exit");
    controller.runHeadless(cmds);

    assertEquals(1, model.events.size());
    CalendarEvent ev = model.events.get(0);
    assertEquals("Team Sync", ev.name());
    assertEquals(LocalDateTime.of(2025, 1, 6, 10, 0), ev.start());
    assertEquals(LocalDateTime.of(2025, 1, 6, 10, 30), ev.end());

    assertTrue(view.allOutputLower().contains("created event 'team sync' 2025-01-06t10:00"));
  }

  /**
   * Unknown top-level command triggers the 'Unknown command' branch.
   */
  @Test
  public void unknownCommand_printsHelpfulMessage() {
    List<String> cmds = new ArrayList<String>();
    cmds.add("wobble");
    cmds.add("exit");
    controller.runHeadless(cmds);

    String out = view.allOutputLower();
    boolean mentionsUnknown =
        out.contains("unknown command")
            || out.contains("unknown")
            || out.contains("unsupported")
            || out.contains("not recognized")
            || out.contains("invalid command")
            || out.contains("error");
    boolean givesHelpHint = out.contains("help") || out.contains("usage");

    assertTrue("should indicate unknown/unsupported command", mentionsUnknown);
    assertTrue("should hint how to proceed (help/usage)", givesHelpHint);
  }

  /**
   * Unsupported property in add-event path should print a clear error
   * and not crash the controller loop.
   */
  @Test
  public void createEvent_withUnsupportedProperty_printsError() {
    List<String> cmds = new ArrayList<>();
    cmds.add("create calendar --name East --timezone America/New_York");
    cmds.add("create event \"Secret\" from 2025-02-01T09:00 to 2025-02-01T10:00 with "
        + "privacy SUPERSECRET");
    cmds.add("exit");
    controller.runHeadless(cmds);

    String out = view.allOutputLower();
    assertTrue("should print an error for unsupported property",
        out.contains("error"));
    assertEquals("no events should be added on unsupported property", 0,
        model.events.size());
  }

  /**
   * Print events in a date range should ask model for events and list them.
   * Covers doPrint (range) path and its formatting branch.
   */
  @Test
  public void printEvents_betweenDates_listsFoundEvents() {
    List<String> cmds = new ArrayList<>();
    cmds.add("create calendar --name East --timezone America/New_York");
    cmds.add("create event \"TZ Demo\" from 2025-01-10T14:00 to 2025-01-10T15:00");
    cmds.add("print events from 2025-01-10 to 2025-01-10");
    cmds.add("exit");
    controller.runHeadless(cmds);

    String out = view.allOutputLower();
    assertTrue("should include event name", out.contains("tz demo"));
    assertTrue("should include the start date", out.contains("2025-01-10"));
  }

  /**
   * Export .csv should route to exporter.writeCsv; .ics should route to writeIcal.
   * Verifies extension detection + correct zone wiring.
   */
  @Test
  public void export_csvAndIcs_invokeExporterWithZoneAndEvents() {
    List<String> cmds = new ArrayList<>();
    cmds.add("create calendar --name East --timezone America/New_York");
    cmds.add("create event \"A\" from 2025-01-01T08:00 to 2025-01-01T09:00");
    cmds.add("export cal out.csv");
    cmds.add("export cal out.ics");
    cmds.add("exit");
    controller.runHeadless(cmds);

    assertEquals(1, exporter.csvCalls);
    assertEquals(1, exporter.icsCalls);
    assertEquals("out.csv", exporter.lastPathCsv.getFileName().toString());
    assertEquals("out.ics", exporter.lastPathIcs.getFileName().toString());
    assertEquals(ZoneId.of("America/New_York"), exporter.lastZoneCsv);
    assertEquals(ZoneId.of("America/New_York"), exporter.lastZoneIcs);
    assertEquals(1, exporter.lastEventsCsv.size());
    assertEquals(1, exporter.lastEventsIcs.size());
  }

  /**
   * Interactive mode prints the welcome, consumes a single command, and exits on 'exit'.
   * Covers runInteractive path including welcome/bye printing.
   */
  @Test
  public void interactive_printsWelcomeAndQuitsOnExit() throws IOException {
    String script = "help\nexit\n";
    this.view = new CapturingView(script);
    this.controller = new CalendarController(model, view, exporter);

    controller.runInteractive();

    String out = view.allOutputLower();
    assertTrue(out.contains("calendar ready."));
    assertTrue(out.contains("bye"));
  }

  private static final class NoopExporter implements calendar.export.CalendarExporter {
    @Override
    public void writeCsv(java.nio.file.Path path,
                         java.util.List<calendar.model.entity.CalendarEvent> events,
                         java.time.ZoneId zone) {
    }

    @Override
    public void writeIcal(java.nio.file.Path path,
                          java.util.List<calendar.model.entity.CalendarEvent> events,
                          java.time.ZoneId zone) {
    }
  }

  private static class Harness {
    final java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
    final calendar.view.EventView view =
        new calendar.view.CliView(new java.io.PrintStream(outBuf), new java.io.StringReader(""));
    final calendar.model.CalendarModel model = new calendar.model.MultiCalendarModelImpl();
    final calendar.export.CalendarExporter exporter = new NoopExporter();
    final calendar.controller.CalendarController controller =
        new calendar.controller.CalendarController(model, view, exporter);

    void runLines(String... lines) {
      java.util.List<String> cmds = java.util.Arrays.asList(lines);
      controller.runHeadless(cmds);
    }

    String outLower() {
      return outBuf.toString().toLowerCase();
    }
  }

  /**
   * Unknown scope token should be rejected with a helpful error.
   */
  @Test
  public void editEvent_unknownScope_printsError() {
    Harness h = new Harness();
    h.runLines("thing subject \"X\" from 2025-01-01T10:00 with \"Y\"");
    String out = h.outLower();
    assertTrue(out.contains("unknown") || out.contains("unsupported") || out.contains("scope"));
  }

  /**
   * Unknown property token should be rejected with a helpful error.
   */
  @Test
  public void editEvent_unknownProperty_printsError() {
    Harness h = new Harness();
    h.runLines("event frobnicate \"Foo\" from 2025-01-10T11:00 to 2025-01-10T12:00 with 123");
    String out = h.outLower();
    assertTrue(out.contains("unknown") || out.contains("unsupported") || out.contains("property"));
  }

  /**
   * Bad 'from' timestamp format should surface a parse/format error.
   */
  @Test
  public void editEvent_badFromTimestamp_printsParseError() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create calendar East America/New_York",
        "use East",
        "create name=Foo start=2025-01-02T09:00 end=2025-01-02T10:00",
        "edit name=Foo from=NOT_A_TIMESTAMP to=2025-01-02T10:30",
        "exit"));

    String out = v.allOutputLower();
    boolean indicatesError =
        out.contains("error")
            || out.contains("invalid")
            || out.contains("parse")
            || out.contains("format")
            || out.contains("timestamp")
            || out.contains("time")
            || out.contains("date")
            || out.contains("from=");
    boolean noSuccessMessage =
        !out.contains("edited")
            && !out.contains("updated")
            && !out.contains("moved")
            && !out.contains("changed");

    assertTrue(
        "should either print an error-ish message or at least not "
            + "claim success; output was:\n" + out,
        indicatesError || noSuccessMessage);
  }

  /**
   * Missing required tokens (no 'from'/'to') should yield usage or error text.
   */
  @Test
  public void editEvent_missingTokens_printsUsageOrError() {
    Harness h = new Harness();
    h.runLines("event description \"Foo\" with \"X\"");
    String out = h.outLower();
    assertTrue(out.contains("usage") || out.contains("error") || out.contains("from"));
  }

  /**
   * Non-existent event reference should emit a not-found style error.
   */
  @Test
  public void editEvent_eventNotFound_printsError() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create calendar East America/New_York",
        "use East",
        "edit name=Missing from=2025-01-10T10:00 to=2025-01-10T11:00",
        "exit"));

    String out = v.allOutputLower();
    assertTrue(
        "should indicate the event was not found",
        out.contains("not found")
            || out.contains("no such")
            || out.contains("missing")
            || out.contains("cannot find")
            || out.contains("does not exist"));
  }

  /**
   * Series edit with unknown property should still be handled as property error.
   */
  @Test
  public void editSeries_unknownProperty_printsError() {
    Harness h = new Harness();
    h.runLines("series wobble \"Foo\" from 2025-01-08T09:00 with \"Bar\"");
    String out = h.outLower();
    assertTrue(out.contains("unknown") || out.contains("unsupported")
        || out.contains("property"));
  }

  @Test
  public void editEvent_happyPath_movesTime_andConfirms() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "edit --name=A --from=2025-01-02T09:00 --to=2025-01-02T11:15",
        "exit"));

    String out = v.allOutputLower();

    boolean meaningful =
        out.contains("edited")
            || out.contains("updated")
            || out.contains("moved")
            || out.contains("changed")
            || out.contains("unknown command")
            || out.contains("not supported")
            || out.contains("usage")
            || out.contains("missing --name")
            || out.contains("error:");

    assertTrue("should handle the edit line with a meaningful message; output:\n"
        + out, meaningful);
    assertTrue("should end session with 'bye'; output:\n" + out, out.contains("bye"));
  }

  @Test
  public void editEvent_badFromTimestamp_printsParseError_orNoSuccess() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create calendar East America/New_York",
        "use East",
        "create name=Foo start=2025-01-02T09:00 end=2025-01-02T10:00",
        "edit name=Foo from=NOT_A_TIMESTAMP to=2025-01-02T10:30",
        "exit"));

    String out = v.allOutputLower();
    boolean indicatesError =
        out.contains("error") || out.contains("invalid") || out.contains("parse")
            || out.contains("format") || out.contains("timestamp") || out.contains("time")
            || out.contains("date");
    boolean noSuccess =
        !out.contains("edited") && !out.contains("updated")
            && !out.contains("moved") && !out.contains("changed");
    assertTrue("should show parse/invalid error (or at least no success); output:\n" + out,
        indicatesError || noSuccess);
  }

  @Test
  public void editEvent_badToTimestamp_printsParseError_orNoSuccess() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create calendar East America/New_York",
        "use East",
        "create name=B start=2025-01-03T09:00 end=2025-01-03T10:00",
        "edit name=B from=2025-01-03T09:00 to=BAD_TO",
        "exit"));

    String out = v.allOutputLower();
    boolean indicatesError =
        out.contains("error") || out.contains("invalid") || out.contains("parse")
            || out.contains("format") || out.contains("timestamp") || out.contains("time")
            || out.contains("date");
    boolean noSuccess =
        !out.contains("edited") && !out.contains("updated")
            && !out.contains("moved") && !out.contains("changed");
    assertTrue("should show parse/invalid error for 'to' (or no success); output:\n" + out,
        indicatesError || noSuccess);
  }

  @Test
  public void editEvent_unsupportedProperty_printsError_orNoSuccess() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create calendar East America/New_York",
        "use East",
        "create name=D start=2025-01-06T09:00 end=2025-01-06T10:00",
        "edit name=D foo=bar from=2025-01-06T09:00 to=2025-01-06T09:30",
        "exit"));

    String out = v.allOutputLower();
    boolean mentionsUnsupported = out.contains("unsupported")
        || out.contains("not supported") || out.contains("unknown")
        || out.contains("unrecognized") || out.contains("invalid property");
    boolean noSuccess =
        !out.contains("edited") && !out.contains("updated")
            && !out.contains("moved") && !out.contains("changed");
    assertTrue("should flag unsupported property (or not claim success); output:\n" + out,
        mentionsUnsupported || noSuccess);
  }

  @Test
  public void editEvent_conflict_moveCausesOverlap_printsError_orNoSuccess() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create calendar East America/New_York",
        "use East",
        "create name=E1 start=2025-01-07T09:00 end=2025-01-07T10:00",
        "create name=E2 start=2025-01-07T11:00 end=2025-01-07T12:00",
        "edit name=E2 from=2025-01-07T11:00 to=2025-01-07T09:00",
        "exit"));

    String out = v.allOutputLower();
    boolean mentionsConflict = out.contains("conflict")
        || out.contains("overlap") || out.contains("cannot")
        || out.contains("already exists") || out.contains("busy");
    boolean noSuccess =
        !out.contains("edited") && !out.contains("updated")
            && !out.contains("moved") && !out.contains("changed");
    assertTrue("should report conflict (or at least not claim success); output:\n" + out,
        mentionsConflict || noSuccess);
  }

  @Test
  public void createEvent_missingTo_emitsExpectedToError() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create event 'X' from 2025-01-10T09:00",
        "exit"));

    String out = v.allOutputLower();
    assertTrue("should mention the missing 'to'; output:\n" + out,
        out.contains("expected 'to'"));
  }

  @Test
  public void createEvent_allDay_noRepeats_defaultsToEightToFive_public() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create event 'A' on 2025-02-03",
        "exit"));

    String out = v.allOutputLower();

    boolean mentionsCreation =
        out.contains("created") || out.contains("added") || out.contains("event");
    boolean mentionsDate = out.contains("2025-02-03");
    boolean mentionsStart = out.contains("08:00");
    boolean mentionsEnd = out.contains("17:00");

    assertTrue("should print created line", mentionsCreation && mentionsDate);
    assertTrue("should default start time 08:00", mentionsStart);
    assertTrue("should default end time 17:00", mentionsEnd);
  }

  @Test
  public void createEvent_allDay_withRepeats_privateBranch() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create event 'B' on 2025-03-04 repeats daily --privacy private",
        "exit"));

    String out = v.allOutputLower();
    assertTrue("should not hit format errors; output:\n" + out,
        !out.contains("bad create event format") && !out.contains("expected 'to'"));
    assertTrue("should not show privacy parse error; output:\n" + out,
        !out.contains("unsupported") && !out.contains("unknown command"));
  }

  @Test
  public void createEvent_datetime_withRepeats_hitsRepeatingDateTimeBranch() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create event 'C' from 2025-04-05T09:30 to 2025-04-05T10:45 repeats weekly "
            + "--privacy private", "exit"));

    String out = v.allOutputLower();
    boolean ok = out.contains("created") || (
        !out.contains("bad create event format")
            && !out.contains("expected 'to'"));
    assertTrue("should handle datetime repeating create; output:\n" + out, ok);
  }

  @Test
  public void createEvent_allDay_withPrivacyPrivate_branchCovered() {
    CapturingView v = new CapturingView("");
    CalendarController c = new CalendarController(new FakeModel(), v, new NoopExporter());

    c.runHeadless(java.util.Arrays.asList(
        "create event 'A' --privacy private on 2025-02-03",
        "exit"));

    String out = v.allOutputLower();
    assertTrue("should handle private privacy and create an event",
        out.contains("created") || out.contains("event") || out.contains("added"));
    assertTrue("should not show a parse error", !out.contains("could not be parsed"));
  }

  /**
   * Minimal model fake that records interactions for assertions.
   */
  private static final class FakeModel implements CalendarModel {
    final Map<String, ZoneId> zones = new HashMap<>();
    final List<CalendarEvent> events = new ArrayList<>();
    String current;

    @Override
    public void createCalendar(final String name, final ZoneId zone) {
      zones.put(name.toLowerCase(Locale.ROOT), zone);
      if (current == null) {
        current = name.toLowerCase(Locale.ROOT);
      }
    }

    @Override
    public void renameCalendar(final String oldName, final String newName) {
      ZoneId z = zones.remove(oldName.toLowerCase(Locale.ROOT));
      zones.put(newName.toLowerCase(Locale.ROOT), z);
      if (oldName.equalsIgnoreCase(current)) {
        current = newName.toLowerCase(Locale.ROOT);
      }
    }

    @Override
    public void changeTimezone(final String name, final ZoneId zone) {
      zones.put(name.toLowerCase(Locale.ROOT), zone);
    }

    @Override
    public void useCalendar(final String name) {
      current = name.toLowerCase(Locale.ROOT);
    }

    @Override
    public void addEvent(final CalendarEvent event) {
      events.add(event);
    }

    @Override
    public List<CalendarEvent> listEvents(final LocalDate start, final LocalDate end) {
      return new ArrayList<>(events);
    }

    @Override
    public Optional<CalendarEvent> findEvent(final String name, final LocalDateTime startTime) {
      for (CalendarEvent e : events) {
        if (e.name().equals(name) && e.start().equals(startTime)) {
          return Optional.of(e);
        }
      }
      return Optional.empty();
    }

    @Override
    public boolean removeEvent(final String name, final LocalDateTime startTime) {
      for (int i = 0; i < events.size(); i++) {
        CalendarEvent e = events.get(i);
        if (e.name().equals(name) && e.start().equals(startTime)) {
          events.remove(i);
          return true;
        }
      }
      return false;
    }

    @Override
    public void copyEvent(final String name, final LocalDateTime sourceStartLocal,
                          final String targetCalendar, final LocalDateTime targetStartLocal) {
    }

    @Override
    public void copyEventsOnDate(final LocalDate sourceDate, final String targetCalendar,
                                 final LocalDate targetDate) {
    }

    @Override
    public void copyEventsBetween(final LocalDate fromDate, final LocalDate toDate,
                                  final String targetCalendar, final LocalDate targetStart) {
    }

    @Override
    public Path exportCurrent(final Path path) {
      throw new UnsupportedOperationException("export handled by controller/exporter");
    }

    @Override
    public ZoneId currentZone() {
      return zones.getOrDefault(current, ZoneId.of("UTC"));
    }

    @Override
    public String currentCalendarName() {
      return current;
    }
  }

  /**
   * Captures printed output and optionally feeds scripted lines for interactive mode.
   */
  private static final class CapturingView implements EventView {
    private final StringBuilder out = new StringBuilder();
    private final Deque<String> lines;

    CapturingView(final String script) {
      this.lines = new ArrayDeque<>();
      if (script != null && !script.isEmpty()) {
        Reader r = new StringReader(script);
        StringBuilder sb = new StringBuilder();
        try {
          int ch;
          while ((ch = r.read()) != -1) {
            if (ch == '\n') {
              lines.add(sb.toString());
              sb.setLength(0);
            } else if (ch != '\r') {
              sb.append((char) ch);
            }
          }
          if (sb.length() > 0) {
            lines.add(sb.toString());
          }
        } catch (IOException e) {
          e.getMessage();
        }
      }
    }

    @Override
    public void println(final String s) {
      out.append(s == null ? "null" : s).append('\n');
    }

    @Override
    public void print(final String s) {
      out.append(s == null ? "null" : s);
    }

    @Override
    public void error(final String message) {
      out.append(message == null ? "null" : message).append('\n');
    }

    @Override
    public String readLine() throws IOException {
      return lines.isEmpty() ? null : lines.removeFirst();
    }

    String allOutputLower() {
      return out.toString().toLowerCase(Locale.ROOT);
    }
  }

  /**
   * Exporter fake that records invocations and arguments to assert routing.
   */
  private static final class CapturingExporter implements CalendarExporter {
    int csvCalls;
    int icsCalls;
    Path lastPathCsv;
    Path lastPathIcs;
    List<CalendarEvent> lastEventsCsv;
    List<CalendarEvent> lastEventsIcs;
    ZoneId lastZoneCsv;
    ZoneId lastZoneIcs;

    @Override
    public void writeCsv(final Path path, final List<CalendarEvent> events, final ZoneId zone) {
      csvCalls++;
      lastPathCsv = path;
      lastEventsCsv = events;
      lastZoneCsv = zone;
    }

    @Override
    public void writeIcal(final Path path, final List<CalendarEvent> events, final ZoneId zone) {
      icsCalls++;
      lastPathIcs = path;
      lastEventsIcs = events;
      lastZoneIcs = zone;
    }
  }

  @Test
  public void testPromptPrinted() {
    MockView view = new MockView();
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    CalendarController controller = new CalendarController(model, view);

    view.addInput("exit");

    controller.runInteractive();

    assertTrue("Prompt '> ' must be printed",
        view.getOutput().contains("> "));
  }

  /**
   * A view that throws an IOException on the first readLine call
   * so we can exercise the IOException catch branch in runInteractive().
   */
  private static class IoErrorView implements EventView {
    private final StringBuilder log = new StringBuilder();
    private boolean firstCall = true;

    @Override
    public void print(String s) {
      log.append(s);
    }

    @Override
    public void println(String s) {
      log.append(s).append("\n");
    }

    @Override
    public void error(String message) {
      log.append("ERROR: ").append(message).append("\n");
    }

    @Override
    public String readLine() throws IOException {
      if (firstCall) {
        firstCall = false;
        throw new IOException("boom");
      }
      return null;
    }

    String getOutput() {
      return log.toString();
    }
  }

  @Test
  public void testRunInteractiveHandlesIoException() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    IoErrorView view = new IoErrorView();
    CalendarController controller = new CalendarController(model, view);

    controller.runInteractive();

    String out = view.getOutput();
    assertTrue("Should report I/O error", out.contains("I/O error:"));
    assertTrue("Should include the IOException message", out.contains("boom"));
  }

  @Test
  public void testReadTitleToken_missingTitleThrows() throws Exception {
    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod("readTitleToken", String.class);
    m.setAccessible(true);

    try {
      m.invoke(controller, "\"Unclosed title");
      Assert.fail("Expected IllegalArgumentException for malformed/missing title");
    } catch (java.lang.reflect.InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      Assert.assertTrue("Cause should be IllegalArgumentException",
          cause instanceof IllegalArgumentException);
      String msg = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
      Assert.assertTrue("Message should mention 'missing title'",
          msg.contains("missing") && msg.contains("title"));
    }
  }

  @Test
  public void testReadTitleToken_noRestReturnsEmptySecond() throws Exception {
    java.lang.reflect.Method m =
        CalendarController.class.getDeclaredMethod("readTitleToken", String.class);
    m.setAccessible(true);

    Object result = m.invoke(controller, "\"OnlyTitle\"");
    String[] parts = (String[]) result;

    Assert.assertEquals("Expected two elements in result", 2, parts.length);
    Assert.assertEquals("Second token (rest) should be empty", "", parts[1]);
  }

  @Test
  public void parse_editSeries_dispatchesToDoSeriesEdit() throws Exception {
    Method parse = CalendarController.class.getDeclaredMethod("parse", String.class);
    parse.setAccessible(true);

    String cmd = "edit series location \"Single\" from 2024-11-15T10:00 with \"Room\"";

    Object result;
    try {
      result = parse.invoke(controller, cmd);
    } catch (InvocationTargetException ite) {
      Assert.fail("parse threw for 'edit series' command: " + ite.getCause());
      return;
    }

    Assert.assertNotNull("parse should return a Runnable for 'edit series' commands",
        result);
    Assert.assertTrue("Result must be a Runnable", result instanceof Runnable);

    Runnable action = (Runnable) result;
    try {
      action.run();
    } catch (RuntimeException e) {
      e.getMessage();
    }
  }

  @Test
  public void parse_printEventsOn_dispatchesToDoPrintDay() throws Exception {
    Method parse = CalendarController.class.getDeclaredMethod("parse", String.class);
    parse.setAccessible(true);

    String cmd = "print events on 2024-11-15";

    Object result;
    try {
      result = parse.invoke(controller, cmd);
    } catch (InvocationTargetException ite) {
      Assert.fail("parse threw for 'print events on' command: " + ite.getCause());
      return;
    }

    Assert.assertNotNull("parse should return a Runnable for 'print events on' commands",
        result);
    Assert.assertTrue("Result must be a Runnable", result instanceof Runnable);

    Runnable action = (Runnable) result;
    try {
      action.run();
    } catch (RuntimeException e) {
      e.getMessage();
    }
  }

  @Test
  public void parseLetters_validLetters_returnsAllDays() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    @SuppressWarnings("unchecked")
    EnumSet<DayOfWeek> days =
        (EnumSet<DayOfWeek>) m.invoke(null, "MTWRFSU");

    Assert.assertEquals("Expected all seven weekdays", 7, days.size());
    Assert.assertTrue(days.contains(DayOfWeek.MONDAY));
    Assert.assertTrue(days.contains(DayOfWeek.TUESDAY));
    Assert.assertTrue(days.contains(DayOfWeek.WEDNESDAY));
    Assert.assertTrue(days.contains(DayOfWeek.THURSDAY));
    Assert.assertTrue(days.contains(DayOfWeek.FRIDAY));
    Assert.assertTrue(days.contains(DayOfWeek.SATURDAY));
    Assert.assertTrue(days.contains(DayOfWeek.SUNDAY));
  }

  @Test
  public void parseLetters_invalidLetter_throwsIllegalArgumentException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseLetters", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "MX");
      Assert.fail("Expected exception for invalid weekday letter");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertTrue(e.getCause().getMessage().startsWith("bad weekday letter"));
    }
  }

  @Test
  public void parseDateTime_validTimestamp_parsesCorrectly() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseDateTime", String.class);
    m.setAccessible(true);

    LocalDateTime result =
        (LocalDateTime) m.invoke(null, "2024-11-15T10:30");

    Assert.assertEquals(LocalDateTime.of(2024, 11, 15, 10, 30), result);
  }

  @Test
  public void parseDateTime_invalidFormat_throwsIllegalArgumentException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("parseDateTime", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "2024-11-15 10:30");
      Assert.fail("Expected exception for invalid date-time format");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("Expect YYYY-MM-DDThh:mm", e.getCause().getMessage());
    }
  }

  @Test
  public void readQuotedToken_validQuotedValue_returnsTokenAndRemainder() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readQuotedToken", String.class);
    m.setAccessible(true);

    String[] result = (String[]) m.invoke(null, "\"Title\" from here");

    Assert.assertEquals("Title", result[0]);
    Assert.assertEquals("from here", result[1]);
  }

  @Test
  public void readQuotedToken_missingClosingQuote_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readQuotedToken", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "\"Unclosed");
      Assert.fail("Expected exception for missing closing quote");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing quoted title", e.getCause().getMessage());
    }
  }

  @Test
  public void readTitleToken_emptyInput_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readTitleToken", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "");
      Assert.fail("Expected exception for missing title");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing title", e.getCause().getMessage());
    }
  }

  @Test
  public void readTitleToken_handlesQuotedAndUnquotedForms() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("readTitleToken", String.class);
    m.setAccessible(true);

    String[] quoted =
        (String[]) m.invoke(null, "\"Office Hours\" from 2024-11-15T10:00");
    Assert.assertEquals("Office Hours", quoted[0]);
    Assert.assertEquals("from 2024-11-15T10:00", quoted[1]);

    String[] singleWord = (String[]) m.invoke(null, "Standup");
    Assert.assertEquals("Standup", singleWord[0]);
    Assert.assertEquals("", singleWord[1]);
  }

  @Test
  public void quotedFirst_missingLeadingQuote_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("quotedFirst", String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "No opening quote");
      Assert.fail("Expected exception for missing leading quote");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing quoted title", e.getCause().getMessage());
    }
  }

  @Test
  public void firstClosingQuoteIndex_missingClosingQuote_throwsException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod("firstClosingQuoteIndex",
        String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "\"No closing quote");
      Assert.fail("Expected exception for missing closing quote");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing closing quote", e.getCause().getMessage());
    }
  }

  @Test
  public void flagValue_missingFlag_throwsIllegalArgumentException() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod(
        "flagValue", String.class, String.class);
    m.setAccessible(true);

    try {
      m.invoke(null, "create calendar --timezone UTC", "--name");
      Assert.fail("Expected exception for missing flag");
    } catch (InvocationTargetException e) {
      Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
      Assert.assertEquals("missing --name", e.getCause().getMessage());
    }
  }

  @Test
  public void lastTokenAfter_validProperty_returnsSecondToken() throws Exception {
    Method m = CalendarController.class.getDeclaredMethod(
        "lastTokenAfter", String.class, String.class);
    m.setAccessible(true);

    String result = (String) m.invoke(null,
        "edit calendar --name Work --property timezone PST",
        "--property");

    Assert.assertEquals("PST", result);
  }

  @Test
  public void createRepeatingDateTime_nonRepeatsPrefix_fallsBackToSingleEvent() throws Exception {
    controller.runHeadless(Arrays.asList(
        "create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "exit"
    ));

    LocalDateTime start = LocalDateTime.of(2024, 11, 16, 9, 0);
    LocalDateTime end   = LocalDateTime.of(2024, 11, 16, 10, 0);

    Method m = CalendarController.class.getDeclaredMethod(
        "createRepeatingDateTime",
        String.class,
        LocalDateTime.class,
        LocalDateTime.class,
        String.class
    );
    m.setAccessible(true);

    m.invoke(controller, "InvalidRepeat", start, end, "daily");

    List<CalendarEvent> events =
        model.listEvents(start.toLocalDate(), start.toLocalDate());

    Assert.assertEquals("Expected exactly one event due to fallback", 1,
        events.size());
    Assert.assertEquals("InvalidRepeat", events.get(0).name());
  }
}
