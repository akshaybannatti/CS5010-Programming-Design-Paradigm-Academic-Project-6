import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import calendar.model.CalendarModel;
import calendar.model.MultiCalendarModelImpl;
import calendar.model.entity.CalendarEvent;
import calendar.model.entity.PrivacyStatus;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * High-coverage tests for {@link MultiCalendarModelImpl}.
 */
public class CalendarModelImplTest {

  private CalendarModel model;
  private static Method csvMethod;

  /**
   * Initializes reflective access to the private static {@code csv(String)} helper method
   * in {@code MultiCalendarModelImpl} before any tests are executed.
   *
   * <p>This setup uses reflection to obtain and make accessible the internal CSV
   * formatting method so it can be invoked from test cases, allowing branch and
   * mutation coverage of otherwise private logic.</p>
   *
   * @throws Exception if reflection fails to locate or access the {@code csv} method
   */
  @BeforeClass
  public static void reflectCsv() throws Exception {
    Class<?> impl = Class.forName("calendar.model.MultiCalendarModelImpl");
    csvMethod = impl.getDeclaredMethod("csv", String.class);
    csvMethod.setAccessible(true);
  }

  private static String callCsv(String s) {
    try {
      return (String) csvMethod.invoke(null, s);
    } catch (Exception e) {
      throw new AssertionError("Reflection invoke failed", e);
    }
  }

  /**
   * Initializes a fresh instance of {@link MultiCalendarModelImpl} before each test.
   *
   * <p>This setup method ensures that every test starts with a clean model state
   * to prevent interference between test cases. It is automatically executed by
   * the JUnit framework prior to each {@code @Test} method.</p>
   */
  @Before
  public void setUp() {
    model = new MultiCalendarModelImpl();
  }

  private static CalendarEvent ev(String name,
                                  String desc,
                                  String loc,
                                  int y, int m, int d, int sh, int sm,
                                  int eh, int em) {
    return new CalendarEvent(
        name,
        desc,
        loc,
        LocalDateTime.of(y, m, d, sh, sm),
        LocalDateTime.of(y, m, d, eh, em),
        Optional.empty(),
        Optional.of(UUID.randomUUID()),
        PrivacyStatus.PUBLIC
    );
  }

  private static CalendarEvent span(String name,
                                    int ys, int ms, int ds, int sh, int sm,
                                    int ye, int me, int de, int eh, int em) {
    return new CalendarEvent(
        name, "", "",
        LocalDateTime.of(ys, ms, ds, sh, sm),
        LocalDateTime.of(ye, me, de, eh, em),
        Optional.empty(),
        Optional.of(UUID.randomUUID()),
        PrivacyStatus.PUBLIC
    );
  }

  /**
   * Functional helper that permits checked exceptions inside lambdas.
   */
  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static void expectThrow(Class<? extends Throwable> type, ThrowingRunnable r) {
    try {
      r.run();
      fail("Expected " + type.getSimpleName());
    } catch (Throwable t) {
      if (!type.isInstance(t)) {
        fail("Expected " + type.getSimpleName() + " but got " + t);
      }
    }
  }

  @Test
  public void createAndUseCalendar_setsCurrentAndZone() {
    model.createCalendar("Work", ZoneId.of("America/New_York"));
    assertEquals("Work", model.currentCalendarName());
    assertEquals(ZoneId.of("America/New_York"), model.currentZone());

    model.createCalendar("School", ZoneId.of("America/Los_Angeles"));
    model.useCalendar("School");
    assertEquals("School", model.currentCalendarName());
    assertEquals(ZoneId.of("America/Los_Angeles"), model.currentZone());
  }

  @Test
  public void createCalendar_duplicateName_throws() {
    model.createCalendar("A", ZoneId.of("UTC"));
    expectThrow(IllegalArgumentException.class,
        () -> model.createCalendar("A", ZoneId.of("UTC")));
  }

  @Test
  public void useCalendar_missing_throws() {
    model.createCalendar("A", ZoneId.of("UTC"));
    expectThrow(IllegalArgumentException.class, () -> model.useCalendar("B"));
  }

  @Test
  public void renameCalendar_happy_and_updatesCurrent() {
    model.createCalendar("Old", ZoneId.of("UTC"));
    model.renameCalendar("Old", "New");
    assertEquals("New", model.currentCalendarName());

    expectThrow(IllegalArgumentException.class, () -> model.useCalendar("Old"));
    model.useCalendar("New");
    assertEquals("New", model.currentCalendarName());
  }

  @Test
  public void renameCalendar_conflict_reverts_and_throws() {
    model.createCalendar("A", ZoneId.of("UTC"));
    model.createCalendar("B", ZoneId.of("UTC"));
    expectThrow(IllegalArgumentException.class, () -> model.renameCalendar("A", "B"));

    model.useCalendar("A");
    assertEquals("A", model.currentCalendarName());
  }

  @Test
  public void changeTimezone_updatesZone() {
    model.createCalendar("A", ZoneId.of("UTC"));
    model.changeTimezone("A", ZoneId.of("America/New_York"));
    assertEquals(ZoneId.of("America/New_York"), model.currentZone());
  }

  @Test
  public void addFindRemoveEvent_basic() {
    model.createCalendar("A", ZoneId.of("UTC"));
    CalendarEvent e = ev("Mtg", "desc", "room", 2025, 1, 10, 11, 0, 12, 0);
    model.addEvent(e);

    Optional<CalendarEvent> found = model.findEvent("Mtg", e.start());
    assertTrue(found.isPresent());

    boolean removed = model.removeEvent("Mtg", e.start());
    assertTrue(removed);
    assertFalse(model.findEvent("Mtg", e.start()).isPresent());
  }

  @Test
  public void addEvent_conflict_sameWindow_throws() {
    model.createCalendar("A", ZoneId.of("UTC"));
    CalendarEvent e1 = ev("One", "", "", 2025, 1, 10, 10, 0, 11, 0);
    CalendarEvent e2 = ev("Two", "", "", 2025, 1, 10, 10, 30, 10, 45);
    model.addEvent(e1);
    expectThrow(IllegalArgumentException.class, () -> model.addEvent(e2));
  }

  @Test(expected = IllegalArgumentException.class)
  public void addEvent_edgeTouches_isConflict() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("C", ZoneId.of("America/New_York"));
    model.useCalendar("C");

    LocalDateTime s1 = LocalDateTime.of(2025, 1, 10, 10, 0);
    LocalDateTime e1 = LocalDateTime.of(2025, 1, 10, 11, 0);
    LocalDateTime s2 = e1;
    LocalDateTime e2 = LocalDateTime.of(2025, 1, 10, 12, 0);

    model.addEvent(new calendar.model.entity.CalendarEvent(
        "One", "", "", s1, e1, Optional.empty(), Optional.<UUID>empty()));

    model.addEvent(new calendar.model.entity.CalendarEvent(
        "Two", "", "", s2, e2, Optional.empty(), Optional.<UUID>empty()));
  }

  @Test
  public void listEvents_inclusiveAndSorted_andUnmodifiable() {
    model.createCalendar("A", ZoneId.of("UTC"));
    CalendarEvent e1 = ev("Early", "", "", 2025, 1, 10, 9, 0, 10, 0);
    CalendarEvent e2 = ev("Mid", "", "", 2025, 1, 10, 13, 0, 14, 0);
    CalendarEvent e3 =
        span("Over", 2025, 1, 9, 23, 30, 2025, 1, 10, 0, 30);

    model.addEvent(e2);
    model.addEvent(e1);
    model.addEvent(e3);

    List<CalendarEvent> got =
        model.listEvents(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 10));
    assertEquals(3, got.size());
    assertEquals("Over", got.get(0).name());
    assertEquals("Early", got.get(1).name());
    assertEquals("Mid", got.get(2).name());

    expectThrow(UnsupportedOperationException.class, () -> got.add(e1));
  }

  @Test
  public void copyEvent_respectsTargetLocalStartAndDuration() {
    model.createCalendar("East", ZoneId.of("America/New_York"));
    model.createCalendar("West", ZoneId.of("America/Los_Angeles"));
    model.useCalendar("East");

    CalendarEvent src = ev("TZ Demo", "", "",
        2025, 1, 10, 14, 0,
        15, 0);
    model.addEvent(src);

    LocalDateTime targetLocal = LocalDateTime.of(2025, 1, 10, 10, 0);
    model.copyEvent("TZ Demo", src.start(), "West", targetLocal);

    model.useCalendar("West");
    List<CalendarEvent> west =
        model.listEvents(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 10));
    CalendarEvent copy =
        west.stream().filter(e -> e.name().equals("TZ Demo")).findFirst().orElseThrow(
            () -> new AssertionError("copy not found"));
    assertEquals(LocalDateTime.of(2025, 1, 10, 10, 0), copy.start());
    assertEquals(LocalDateTime.of(2025, 1, 10, 11, 0), copy.end());
  }

  @Test
  public void copyEventsOnDate_convertsWallTimesToTargetZoneAnchor() {
    model.createCalendar("East", ZoneId.of("America/New_York"));
    model.createCalendar("West", ZoneId.of("America/Los_Angeles"));
    model.useCalendar("East");

    CalendarEvent src = ev("DayCopy", "", "", 2025, 1, 10, 14, 0, 15, 0);
    model.addEvent(src);

    model.copyEventsOnDate(LocalDate.of(2025, 1, 10), "West", LocalDate.of(2025, 1, 10));

    model.useCalendar("West");
    List<CalendarEvent> west =
        model.listEvents(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 10));
    CalendarEvent copy =
        west.stream().filter(e -> e.name().equals("DayCopy")).findFirst().orElseThrow(
            () -> new AssertionError("copy not found"));
    assertEquals(LocalDateTime.of(2025, 1, 10, 11, 0), copy.start());
    assertEquals(LocalDateTime.of(2025, 1, 10, 12, 0), copy.end());
  }

  @Test
  public void copyEventsBetween_mapsDayOffsets_andPreservesDurationAndZone() {
    model.createCalendar("East", ZoneId.of("America/New_York"));
    model.createCalendar("West", ZoneId.of("America/Los_Angeles"));
    model.useCalendar("East");

    CalendarEvent d8 = ev("Standup", "", "", 2025, 1, 8, 9, 0, 9,
        15);
    CalendarEvent d10 =
        ev("Review", "", "", 2025, 1, 10, 11, 0, 12, 0);
    model.addEvent(d8);
    model.addEvent(d10);

    model.copyEventsBetween(LocalDate.of(2025, 1, 8), LocalDate.of(2025, 1, 10),
        "West", LocalDate.of(2025, 1, 13));

    model.useCalendar("West");
    List<CalendarEvent> west =
        model.listEvents(LocalDate.of(2025, 1, 13), LocalDate.of(2025, 1, 15));
    CalendarEvent c1 =
        west.stream().filter(e -> e.name().equals("Standup")).findFirst().orElse(null);
    CalendarEvent c2 =
        west.stream().filter(e -> e.name().equals("Review")).findFirst().orElse(null);

    assertNotNull("Standup copy missing", c1);
    assertNotNull("Review copy missing", c2);

    assertEquals(LocalDateTime.of(2025, 1, 13, 6, 0), c1.start());
    assertEquals(LocalDateTime.of(2025, 1, 13, 6, 15), c1.end());

    assertEquals(LocalDateTime.of(2025, 1, 15, 8, 0), c2.start());
    assertEquals(LocalDateTime.of(2025, 1, 15, 9, 0), c2.end());
  }

  @Test
  public void copyEventsBetween_invalidRange_throws() {
    model.createCalendar("A", ZoneId.of("UTC"));
    expectThrow(IllegalArgumentException.class, () ->
        model.copyEventsBetween(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 9),
            "A", LocalDate.of(2025, 1, 10)));
  }

  @Test
  public void exportCurrent_throwsUnsupported() {
    model.createCalendar("A", ZoneId.of("UTC"));
    expectThrow(UnsupportedOperationException.class,
        () -> model.exportCurrent(java.nio.file.Paths.get("x.ics")));
  }

  @Test
  public void operationsWithoutCurrentCalendar_throwIllegalState() {
    expectThrow(IllegalStateException.class, () -> model.addEvent(ev(
        "X", "", "", 2025, 1, 1, 10, 0, 11, 0)));
    expectThrow(IllegalStateException.class, () ->
        model.listEvents(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2)));
    expectThrow(IllegalStateException.class, () ->
        model.findEvent("No", LocalDateTime.of(2025, 1, 1, 0, 0)));
  }

  @Test
  public void changeTimezone_missingCalendar_throws() {
    model.createCalendar("A", ZoneId.of("UTC"));
    expectThrow(IllegalArgumentException.class, () ->
        model.changeTimezone("Missing", ZoneId.of("UTC")));
  }

  @Test
  public void renameCalendar_missingSource_throws() {
    model.createCalendar("A", ZoneId.of("UTC"));
    expectThrow(IllegalArgumentException.class, () -> model.renameCalendar("B", "C"));
  }

  @Test
  public void csv_null_returnsEmpty() {
    org.junit.Assert.assertEquals("", callCsv(null));
  }

  @Test
  public void csv_empty_returnsEmptyNoQuotes() {
    org.junit.Assert.assertEquals("", callCsv(""));
  }

  @Test
  public void csv_plainNoSpecials_unquoted() {
    org.junit.Assert.assertEquals("hello", callCsv("hello"));
    org.junit.Assert.assertEquals("ABC123", callCsv("ABC123"));
  }

  @Test
  public void csv_containsComma_isQuoted() {
    org.junit.Assert.assertEquals("\"a,b\"", callCsv("a,b"));
  }

  @Test
  public void csv_containsQuote_isEscapedAndQuoted() {
    org.junit.Assert.assertEquals("\"He said \"\"hi\"\"\"", callCsv("He said \"hi\""));

    org.junit.Assert.assertEquals("\"\"\"\"\"\"", callCsv("\"\""));
  }

  @Test
  public void csv_containsNewline_isQuoted() {
    org.junit.Assert.assertEquals("\"line1\nline2\"", callCsv("line1\nline2"));
  }

  @Test
  public void csv_containsCarriageReturn_isQuoted() {
    org.junit.Assert.assertEquals("\"line1\rline2\"", callCsv("line1\rline2"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void requireName_null_throwsException() throws Exception {
    Method m = MultiCalendarModelImpl.class.getDeclaredMethod("requireName", String.class);
    m.setAccessible(true);
    try {
      m.invoke(null, (String) null);
    } catch (java.lang.reflect.InvocationTargetException e) {
      throw (IllegalArgumentException) e.getCause();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void requireName_emptyOrWhitespace_throwsException() throws Exception {
    Method m = MultiCalendarModelImpl.class.getDeclaredMethod("requireName", String.class);
    m.setAccessible(true);
    try {
      m.invoke(null, "   ");
    } catch (java.lang.reflect.InvocationTargetException e) {
      throw (IllegalArgumentException) e.getCause();
    }
  }

  @Test
  public void removeEvent_returnsFalseWhenEventNotFound() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("C", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("C");

    java.time.LocalDateTime s1 = java.time.LocalDateTime.of(2025, 1, 10, 10, 0);
    java.time.LocalDateTime e1 = java.time.LocalDateTime.of(2025, 1, 10, 11, 0);

    model.addEvent(new calendar.model.entity.CalendarEvent(
        "One", "", "", s1, e1,
        java.util.Optional.empty(), java.util.Optional.empty()));

    boolean removed = model.removeEvent("DoesNotExist", s1);

    org.junit.Assert.assertFalse("Expected removeEvent to return false when no "
        + "match is found", removed);
  }

  @Test
  public void copyEventsBetween_skipsNonOverlappingEvents() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("A", ZoneId.of("America/New_York"));
    model.createCalendar("B", ZoneId.of("America/Los_Angeles"));
    model.useCalendar("A");

    LocalDateTime start = LocalDateTime.of(2025, 1, 1, 9, 0);
    LocalDateTime end = LocalDateTime.of(2025, 1, 1, 10, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "OldEvent", "desc", "loc", start, end, Optional.empty(), Optional.empty()));

    model.copyEventsBetween(LocalDate.of(2025, 1, 5),
        LocalDate.of(2025, 1, 7), "B", LocalDate.of(2025, 2, 1));

    model.useCalendar("B");
    List<calendar.model.entity.CalendarEvent> events = model.listEvents(
        LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 10));
    assertTrue("Expected no copied events because source didn't overlap window",
        events.isEmpty());
  }

  @Test
  public void copyEventsBetween_copiesWhenOverlaps() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("A", ZoneId.of("America/New_York"));
    model.createCalendar("B", ZoneId.of("America/Los_Angeles"));
    model.useCalendar("A");

    LocalDateTime start = LocalDateTime.of(2025, 1, 6, 9, 0);
    LocalDateTime end = LocalDateTime.of(2025, 1, 6, 10, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "InRange", "desc", "loc", start, end, Optional.empty(), Optional.empty()));

    model.copyEventsBetween(LocalDate.of(2025, 1, 5),
        LocalDate.of(2025, 1, 7), "B", LocalDate.of(2025, 2, 1));

    model.useCalendar("B");
    List<calendar.model.entity.CalendarEvent> events = model.listEvents(
        LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 10));
    assertEquals("Expected exactly one overlapping event copied", 1, events.size());
    assertEquals("InRange", events.get(0).name());
  }

  @Test
  public void listEvents_includesEventsWhollyInsideRange() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("C", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("C");

    java.time.LocalDateTime s = java.time.LocalDateTime.of(2025, 1, 10, 10, 0);
    java.time.LocalDateTime e = java.time.LocalDateTime.of(2025, 1, 10, 11, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "Inside", "d", "l", s, e,
        java.util.Optional.empty(), java.util.Optional.empty()));

    java.util.List<calendar.model.entity.CalendarEvent> got =
        model.listEvents(java.time.LocalDate.of(2025, 1, 10),
            java.time.LocalDate.of(2025, 1, 10));

    org.junit.Assert.assertEquals(1, got.size());
    org.junit.Assert.assertEquals("Inside", got.get(0).name());
  }

  @Test
  public void listEvents_includesEdgeTouchingByDate() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("C", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("C");

    java.time.LocalDateTime s1 = java.time.LocalDateTime.of(2025, 1, 11, 23, 0);
    java.time.LocalDateTime e1 = java.time.LocalDateTime.of(2025, 1, 12, 1, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "TouchesEnd", "d", "l", s1, e1,
        java.util.Optional.empty(), java.util.Optional.empty()));

    java.time.LocalDateTime s2 = java.time.LocalDateTime.of(2025, 1, 9, 23, 0);
    java.time.LocalDateTime e2 = java.time.LocalDateTime.of(2025, 1, 10, 1, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "TouchesStart", "d", "l", s2, e2,
        java.util.Optional.empty(), java.util.Optional.empty()));

    java.util.List<calendar.model.entity.CalendarEvent> got =
        model.listEvents(java.time.LocalDate.of(2025, 1, 10),
            java.time.LocalDate.of(2025, 1, 12));

    java.util.Set<String> names = new java.util.HashSet<>();
    for (calendar.model.entity.CalendarEvent ev : got) {
      names.add(ev.name());
    }
    org.junit.Assert.assertTrue(names.contains("TouchesStart"));
    org.junit.Assert.assertTrue(names.contains("TouchesEnd"));
  }

  @Test
  public void listEvents_excludesNonOverlappingByDate() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("C", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("C");

    java.time.LocalDateTime s = java.time.LocalDateTime.of(2025, 1, 13, 9, 0);
    java.time.LocalDateTime e = java.time.LocalDateTime.of(2025, 1, 13, 10, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "Outside", "d", "l", s, e, java.util.Optional.empty(), java.util.Optional.empty()));

    java.util.List<calendar.model.entity.CalendarEvent> got =
        model.listEvents(java.time.LocalDate.of(2025, 1, 10), java.time.LocalDate.of(2025, 1, 11));

    org.junit.Assert.assertTrue(got.isEmpty());
  }

  @Test
  public void copyEventsOnDate_copiesOnlyMatchingDate_andConvertsTimeZones() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("East", java.time.ZoneId.of("America/New_York"));
    model.createCalendar("West", java.time.ZoneId.of("America/Los_Angeles"));
    model.useCalendar("East");

    java.time.LocalDateTime s1 = java.time.LocalDateTime.of(2025, 1, 10, 14, 0);
    java.time.LocalDateTime e1 = java.time.LocalDateTime.of(2025, 1, 10, 15, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "On10", "d", "l", s1, e1, java.util.Optional.empty(), java.util.Optional.empty()));

    java.time.LocalDateTime s2 = java.time.LocalDateTime.of(2025, 1, 11, 9, 0);
    java.time.LocalDateTime e2 = java.time.LocalDateTime.of(2025, 1, 11, 10, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "On11", "d", "l", s2, e2, java.util.Optional.empty(), java.util.Optional.empty()));

    model.copyEventsOnDate(java.time.LocalDate.of(2025, 1, 10), "West",
        java.time.LocalDate.of(2025, 1, 10));

    model.useCalendar("West");
    java.util.List<calendar.model.entity.CalendarEvent> west =
        model.listEvents(java.time.LocalDate.of(2025, 1, 10),
            java.time.LocalDate.of(2025, 1, 10));

    org.junit.Assert.assertEquals(1, west.size());
    calendar.model.entity.CalendarEvent copied = west.get(0);
    org.junit.Assert.assertEquals("On10", copied.name());
    org.junit.Assert.assertEquals(java.time.LocalDateTime.of(2025, 1, 10, 11, 0), copied.start());
    org.junit.Assert.assertEquals(java.time.LocalDateTime.of(2025, 1, 10, 12, 0), copied.end());

    java.util.List<calendar.model.entity.CalendarEvent> west11 =
        model.listEvents(java.time.LocalDate.of(2025, 1, 11),
            java.time.LocalDate.of(2025, 1, 11));
    org.junit.Assert.assertTrue(west11.isEmpty());
  }

  @Test
  public void copyEventsOnDate_noEventsOnThatDate_nothingCopied() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Src", java.time.ZoneId.of("America/New_York"));
    model.createCalendar("Dst", java.time.ZoneId.of("America/Los_Angeles"));
    model.useCalendar("Src");

    java.time.LocalDateTime s = java.time.LocalDateTime.of(2025, 1, 12, 10, 0);
    java.time.LocalDateTime e = java.time.LocalDateTime.of(2025, 1, 12, 11, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "OnlyOn12", "d", "l", s, e,
        java.util.Optional.empty(), java.util.Optional.empty()));

    model.copyEventsOnDate(java.time.LocalDate.of(2025, 1, 10), "Dst",
        java.time.LocalDate.of(2025, 1, 10));

    model.useCalendar("Dst");
    java.util.List<calendar.model.entity.CalendarEvent> dstAll =
        model.listEvents(java.time.LocalDate.of(2025, 1, 1),
            java.time.LocalDate.of(2025, 1, 31));
    org.junit.Assert.assertTrue(dstAll.isEmpty());
  }

  @Test
  public void renameCalendar_updatesCurrentWhenRenamingActiveCalendar() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("A", java.time.ZoneId.of("America/New_York"));
    model.createCalendar("B", java.time.ZoneId.of("America/Los_Angeles"));
    model.useCalendar("A");

    model.renameCalendar("A", "A2");

    org.junit.Assert.assertEquals("A2", model.currentCalendarName());

    model.useCalendar("A2");

    boolean threw = false;
    try {
      model.useCalendar("A");
    } catch (IllegalArgumentException expected) {
      threw = true;
    }
    org.junit.Assert.assertTrue("Using old calendar name should fail after rename", threw);
  }

  @Test
  public void renameCalendar_doesNotChangeCurrentWhenRenamingDifferentCalendar() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("A", java.time.ZoneId.of("America/New_York"));
    model.createCalendar("B", java.time.ZoneId.of("America/Los_Angeles"));
    model.useCalendar("A");

    model.renameCalendar("B", "B2");

    org.junit.Assert.assertEquals("A", model.currentCalendarName());

    model.useCalendar("B2");
    org.junit.Assert.assertEquals("B2", model.currentCalendarName());
  }

  @Test
  public void removeEvent_matchesNameAndStartTime_removesEvent() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Main", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("Main");

    java.time.LocalDateTime start = java.time.LocalDateTime.of(2025, 1, 10, 9, 0);
    java.time.LocalDateTime end = java.time.LocalDateTime.of(2025, 1, 10, 10, 0);

    calendar.model.entity.CalendarEvent ev = new calendar.model.entity.CalendarEvent(
        "Meeting", "desc", "loc", start, end,
        java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(ev);

    boolean removed = model.removeEvent("Meeting", start);
    org.junit.Assert.assertTrue("Expected event to be removed", removed);

    java.util.List<calendar.model.entity.CalendarEvent> events =
        model.listEvents(java.time.LocalDate.of(2025, 1, 10),
            java.time.LocalDate.of(2025, 1, 10));
    org.junit.Assert.assertTrue(events.isEmpty());
  }

  @Test
  public void removeEvent_nonMatchingNameOrStartTime_returnsFalse() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Main", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("Main");

    java.time.LocalDateTime start = java.time.LocalDateTime.of(2025, 1, 10, 9, 0);
    java.time.LocalDateTime end = java.time.LocalDateTime.of(2025, 1, 10, 10, 0);

    calendar.model.entity.CalendarEvent ev = new calendar.model.entity.CalendarEvent(
        "Meeting", "desc", "loc", start, end,
        java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(ev);

    boolean wrongName = model.removeEvent("Different", start);
    org.junit.Assert.assertFalse("Removing wrong name should return false", wrongName);

    java.time.LocalDateTime wrongStart = java.time.LocalDateTime.of(2025, 1, 10, 11, 0);
    boolean wrongTime = model.removeEvent("Meeting", wrongStart);
    org.junit.Assert.assertFalse("Removing wrong start should return false", wrongTime);
  }

  @Test
  public void copyEventsBetween_nonOverlap_startAfterToDate_notCopied() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Src", java.time.ZoneId.of("America/New_York"));
    model.createCalendar("Dst", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("Src");

    java.time.LocalDate from = java.time.LocalDate.of(2025, 1, 8);
    java.time.LocalDate to = java.time.LocalDate.of(2025, 1, 10);

    java.time.LocalDateTime s = java.time.LocalDateTime.of(2025, 1, 11, 9, 0);
    java.time.LocalDateTime e = java.time.LocalDateTime.of(2025, 1, 11, 10, 0);
    calendar.model.entity.CalendarEvent ev = new calendar.model.entity.CalendarEvent(
        "AfterTo", "d", "l", s, e, java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(ev);

    model.copyEventsBetween(from, to, "Dst", java.time.LocalDate.of(2025, 1, 15));

    model.useCalendar("Dst");
    java.util.List<calendar.model.entity.CalendarEvent> copied =
        model.listEvents(java.time.LocalDate.of(2025, 1, 1),
            java.time.LocalDate.of(2025, 1, 31));
    org.junit.Assert.assertTrue("Event starting after 'toDate' must not be "
        + "copied", copied.isEmpty());
  }

  @Test
  public void copyEventsBetween_nonOverlap_endBeforeFromDate_notCopied() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Src", java.time.ZoneId.of("America/New_York"));
    model.createCalendar("Dst", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("Src");

    java.time.LocalDate from = java.time.LocalDate.of(2025, 1, 8);
    java.time.LocalDate to = java.time.LocalDate.of(2025, 1, 10);

    java.time.LocalDateTime s = java.time.LocalDateTime.of(2025, 1, 7, 8, 0);
    java.time.LocalDateTime e = java.time.LocalDateTime.of(2025, 1, 7, 9, 0);
    calendar.model.entity.CalendarEvent ev = new calendar.model.entity.CalendarEvent(
        "BeforeFrom", "d", "l", s, e, java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(ev);

    model.copyEventsBetween(from, to, "Dst", java.time.LocalDate.of(2025, 1, 15));

    model.useCalendar("Dst");
    java.util.List<calendar.model.entity.CalendarEvent> copied =
        model.listEvents(java.time.LocalDate.of(2025, 1, 1),
            java.time.LocalDate.of(2025, 1, 31));
    org.junit.Assert.assertTrue("Event ending before 'fromDate' "
        + "must not be copied", copied.isEmpty());
  }

  @Test
  public void copyEventsBetween_inclusiveBoundaries_areCopiedAndAligned() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Src", java.time.ZoneId.of("America/New_York"));
    model.createCalendar("Dst", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("Src");

    java.time.LocalDate from = java.time.LocalDate.of(2025, 1, 8);
    java.time.LocalDate to = java.time.LocalDate.of(2025, 1, 10);

    java.time.LocalDateTime astart = java.time.LocalDateTime.of(2025, 1, 8, 9, 0);
    java.time.LocalDateTime aend = java.time.LocalDateTime.of(2025, 1, 8, 10, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "OnFrom", "d", "l", astart, aend, java.util.Optional.empty(), java.util.Optional.empty()));

    java.time.LocalDateTime bstart = java.time.LocalDateTime.of(2025, 1, 10, 14, 0);
    java.time.LocalDateTime bend = java.time.LocalDateTime.of(2025, 1, 10, 15, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "OnTo", "d", "l", bstart, bend, java.util.Optional.empty(), java.util.Optional.empty()));

    java.time.LocalDate targetStart = java.time.LocalDate.of(2025, 1, 20);
    model.copyEventsBetween(from, to, "Dst", targetStart);

    model.useCalendar("Dst");
    java.util.List<calendar.model.entity.CalendarEvent> copied =
        model.listEvents(java.time.LocalDate.of(2025, 1, 20), java.time.LocalDate.of(2025, 1, 22));
    org.junit.Assert.assertEquals(2, copied.size());

    boolean foundOnFrom = false;
    boolean foundOnTo = false;
    for (calendar.model.entity.CalendarEvent ev : copied) {
      if ("OnFrom".equals(ev.name())) {
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2025, 1, 20),
            ev.start().toLocalDate());
        foundOnFrom = true;
      } else if ("OnTo".equals(ev.name())) {
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2025, 1, 22),
            ev.start().toLocalDate());
        foundOnTo = true;
      }
    }
    org.junit.Assert.assertTrue("Expected event starting on 'from' to be "
        + "copied and aligned", foundOnFrom);
    org.junit.Assert.assertTrue("Expected event starting on 'to' to be "
        + "copied and aligned", foundOnTo);
  }

  @Test
  public void findEvent_returnsMatchingEvent_whenNameAndStartMatch() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Main", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("Main");

    java.time.LocalDateTime start = java.time.LocalDateTime.of(2025, 1, 10, 9, 0);
    java.time.LocalDateTime end = java.time.LocalDateTime.of(2025, 1, 10, 10, 0);

    calendar.model.entity.CalendarEvent ev = new calendar.model.entity.CalendarEvent(
        "Meeting", "desc", "loc", start, end,
        java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(ev);

    java.util.Optional<calendar.model.entity.CalendarEvent> result
        = model.findEvent("Meeting", start);

    org.junit.Assert.assertTrue("Expected event to be found", result.isPresent());
    org.junit.Assert.assertEquals("Meeting", result.get().name());
    org.junit.Assert.assertEquals(start, result.get().start());
  }

  @Test
  public void findEvent_returnsEmpty_whenNoNameOrStartMatch() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Main", java.time.ZoneId.of("America/New_York"));
    model.useCalendar("Main");

    java.time.LocalDateTime start = java.time.LocalDateTime.of(2025, 1, 10, 9, 0);
    java.time.LocalDateTime end = java.time.LocalDateTime.of(2025, 1, 10, 10, 0);

    calendar.model.entity.CalendarEvent ev = new calendar.model.entity.CalendarEvent(
        "Meeting", "desc", "loc", start, end,
        java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(ev);

    java.util.Optional<calendar.model.entity.CalendarEvent> wrongName
        = model.findEvent("Different", start);
    org.junit.Assert.assertFalse("Wrong name should not find event", wrongName.isPresent());

    java.time.LocalDateTime wrongStart = java.time.LocalDateTime.of(2025, 1, 10, 11, 0);
    java.util.Optional<calendar.model.entity.CalendarEvent> wrongTime
        = model.findEvent("Meeting", wrongStart);
    org.junit.Assert.assertFalse("Wrong start time should not find event",
        wrongTime.isPresent());
  }

  @Test(expected = IllegalArgumentException.class)
  public void copyEvent_throwsWhenSourceMissing() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    java.time.ZoneId zone = java.time.ZoneId.of("America/New_York");

    model.createCalendar("A", zone);
    model.createCalendar("B", zone);
    model.useCalendar("A");

    java.time.LocalDateTime fakeStart = java.time.LocalDateTime.of(2025, 1, 10, 9, 0);
    model.copyEvent("Nope", fakeStart, "B",
        java.time.LocalDateTime.of(2025, 1, 10, 10, 0));
  }

  @Test(expected = IllegalArgumentException.class)
  public void copyEvent_conflictInTarget_throws() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    java.time.ZoneId zone = java.time.ZoneId.of("America/New_York");

    model.createCalendar("A", zone);
    model.createCalendar("B", zone);

    model.useCalendar("A");
    java.time.LocalDateTime s = java.time.LocalDateTime.of(2025, 1, 10, 10, 0);
    java.time.LocalDateTime e = java.time.LocalDateTime.of(2025, 1, 10, 11, 0);
    calendar.model.entity.CalendarEvent src = new calendar.model.entity.CalendarEvent(
        "Meeting", "desc", "loc", s, e,
        java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(src);

    model.useCalendar("B");
    java.time.LocalDateTime tb1 = java.time.LocalDateTime.of(2025, 1, 10, 10, 30);
    java.time.LocalDateTime tb2 = java.time.LocalDateTime.of(2025, 1, 10, 11, 30);
    calendar.model.entity.CalendarEvent block = new calendar.model.entity.CalendarEvent(
        "Blocker", "d", "l", tb1, tb2,
        java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(block);

    model.useCalendar("A");
    model.copyEvent("Meeting", s, "B", java.time.LocalDateTime.of(2025, 1, 10, 10, 0));
  }

  @Test
  public void copyEvent_insertsAndKeepsDestinationSorted() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    java.time.ZoneId zone = java.time.ZoneId.of("America/New_York");

    model.createCalendar("A", zone);
    model.createCalendar("B", zone);

    model.useCalendar("A");
    java.time.LocalDateTime earlyS = java.time.LocalDateTime.of(2025, 1, 10, 9, 0);
    java.time.LocalDateTime earlyE = java.time.LocalDateTime.of(2025, 1, 10, 10, 0);
    calendar.model.entity.CalendarEvent src = new calendar.model.entity.CalendarEvent(
        "Early", "d", "l", earlyS, earlyE,
        java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(src);

    model.useCalendar("B");
    java.time.LocalDateTime laterS = java.time.LocalDateTime.of(2025, 1, 10, 12, 0);
    java.time.LocalDateTime laterE = java.time.LocalDateTime.of(2025, 1, 10, 13, 0);
    calendar.model.entity.CalendarEvent later = new calendar.model.entity.CalendarEvent(
        "Later", "d", "l", laterS, laterE,
        java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(later);

    model.useCalendar("A");
    model.copyEvent("Early", earlyS, "B", java.time.LocalDateTime.of(2025, 1, 10, 9, 0));

    model.useCalendar("B");
    java.time.LocalDate day = java.time.LocalDate.of(2025, 1, 10);
    java.util.List<calendar.model.entity.CalendarEvent> events = model.listEvents(day, day);

    org.junit.Assert.assertEquals(2, events.size());
    org.junit.Assert.assertEquals("Early", events.get(0).name());
    org.junit.Assert.assertEquals("Later", events.get(1).name());
    org.junit.Assert.assertTrue(events.get(0).start().isBefore(events.get(1).start()));
  }

  @Test
  public void copyEventsOnDate_preservesWallTimeInSameZone() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    java.time.ZoneId zone = java.time.ZoneId.of("America/New_York");

    model.createCalendar("Src", zone);
    model.createCalendar("Dst", zone);

    model.useCalendar("Src");
    java.time.LocalDate srcDay = java.time.LocalDate.of(2025, 2, 10);
    java.time.LocalDateTime s = java.time.LocalDateTime.of(2025, 2, 10, 10, 15);
    java.time.LocalDateTime e = java.time.LocalDateTime.of(2025, 2, 10, 11, 45);
    calendar.model.entity.CalendarEvent ev = new calendar.model.entity.CalendarEvent(
        "Calc", "desc", "loc", s, e,
        java.util.Optional.empty(), java.util.Optional.empty());
    model.addEvent(ev);

    java.time.LocalDate targetDay = java.time.LocalDate.of(2025, 2, 20);
    model.copyEventsOnDate(srcDay, "Dst", targetDay);

    model.useCalendar("Dst");
    java.util.List<calendar.model.entity.CalendarEvent> copied =
        model.listEvents(targetDay, targetDay);

    org.junit.Assert.assertEquals(1, copied.size());
    org.junit.Assert.assertEquals(java.time.LocalDateTime.of(2025, 2,
        20, 10, 15), copied.get(0).start());
    org.junit.Assert.assertEquals(java.time.LocalDateTime.of(2025, 2,
        20, 11, 45), copied.get(0).end());
  }

  @Test(expected = IllegalArgumentException.class)
  public void copyEventsOnDate_conflictInTarget_throws() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    java.time.ZoneId zone = java.time.ZoneId.of("America/New_York");

    model.createCalendar("Src", zone);
    model.createCalendar("Dst", zone);

    model.useCalendar("Src");
    java.time.LocalDate srcDay = java.time.LocalDate.of(2025, 3, 5);
    java.time.LocalDateTime s = java.time.LocalDateTime.of(2025, 3, 5, 10, 0);
    java.time.LocalDateTime e = java.time.LocalDateTime.of(2025, 3, 5, 11, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "OverlapMe", "d", "l", s, e, java.util.Optional.empty(), java.util.Optional.empty()));

    model.useCalendar("Dst");
    java.time.LocalDate targetDay = java.time.LocalDate.of(2025, 3, 12);
    java.time.LocalDateTime tdS = java.time.LocalDateTime.of(2025, 3, 12, 10, 30);
    java.time.LocalDateTime tdE = java.time.LocalDateTime.of(2025, 3, 12, 11, 30);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "Block", "d", "l", tdS, tdE, java.util.Optional.empty(), java.util.Optional.empty()));

    model.useCalendar("Src");
    model.copyEventsOnDate(srcDay, "Dst", targetDay);
  }

  @Test
  public void copyEventsOnDate_keepsDestinationSortedWhenPrepopulated() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    java.time.ZoneId zone = java.time.ZoneId.of("America/New_York");

    model.createCalendar("Src", zone);
    model.createCalendar("Dst", zone);

    model.useCalendar("Dst");
    java.time.LocalDate targetDay = java.time.LocalDate.of(2025, 4, 10);
    java.time.LocalDateTime existingS = java.time.LocalDateTime.of(2025, 4, 10, 12, 0);
    java.time.LocalDateTime existingE = java.time.LocalDateTime.of(2025, 4, 10, 13, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "Existing", "d", "l", existingS, existingE,
        java.util.Optional.empty(), java.util.Optional.empty()));

    model.useCalendar("Src");
    java.time.LocalDate srcDay = java.time.LocalDate.of(2025, 4, 3);
    java.time.LocalDateTime s = java.time.LocalDateTime.of(2025, 4, 3, 8, 0);
    java.time.LocalDateTime e = java.time.LocalDateTime.of(2025, 4, 3, 9, 0);
    model.addEvent(new calendar.model.entity.CalendarEvent(
        "Early", "d", "l", s, e, java.util.Optional.empty(), java.util.Optional.empty()));

    model.copyEventsOnDate(srcDay, "Dst", targetDay);

    model.useCalendar("Dst");
    java.util.List<calendar.model.entity.CalendarEvent> events
        = model.listEvents(targetDay, targetDay);
    org.junit.Assert.assertEquals(2, events.size());
    org.junit.Assert.assertEquals("Early", events.get(0).name());
    org.junit.Assert.assertEquals("Existing", events.get(1).name());
    org.junit.Assert.assertTrue(events.get(0).start().isBefore(events.get(1).start()));
  }

  @Test
  public void testCopyEventsBetweenPreservesTimeOfDay() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Src", ZoneId.of("UTC"));
    model.createCalendar("Dst", ZoneId.of("UTC"));

    model.useCalendar("Src");
    LocalDate srcDate = LocalDate.of(2024, 11, 15);
    LocalDateTime start = srcDate.atTime(10, 30);
    LocalDateTime end = srcDate.atTime(11, 45);

    model.addEvent(new CalendarEvent(
        "Meeting",
        "",
        "",
        start,
        end,
        java.util.Optional.empty(),
        java.util.Optional.empty()
    ));

    LocalDate targetStart = LocalDate.of(2024, 12, 1);
    model.copyEventsBetween(srcDate, srcDate, "Dst", targetStart);

    model.useCalendar("Dst");
    java.util.List<CalendarEvent> copied =
        model.listEvents(targetStart, targetStart);
    Assert.assertEquals(1, copied.size());

    CalendarEvent ev = copied.get(0);
    Assert.assertEquals(LocalTime.of(10, 30), ev.start().toLocalTime());
    Assert.assertEquals(LocalTime.of(11, 45), ev.end().toLocalTime());
  }

  @Test
  public void testCopyEventsBetweenDetectsOverlapInTarget() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Src", ZoneId.of("UTC"));
    model.createCalendar("Dst", ZoneId.of("UTC"));

    LocalDate srcDay = LocalDate.of(2024, 11, 15);
    LocalDateTime srcStart = srcDay.atTime(9, 0);
    LocalDateTime srcEnd = srcDay.atTime(10, 0);

    model.useCalendar("Src");
    model.addEvent(new CalendarEvent(
        "OverlapMe",
        "",
        "",
        srcStart,
        srcEnd,
        java.util.Optional.empty(),
        java.util.Optional.empty()
    ));

    LocalDate targetStartDate = LocalDate.of(2024, 12, 1);
    model.useCalendar("Dst");
    model.addEvent(new CalendarEvent(
        "Existing",
        "",
        "",
        targetStartDate.atTime(9, 0),
        targetStartDate.atTime(10, 0),
        java.util.Optional.empty(),
        java.util.Optional.empty()
    ));

    model.useCalendar("Src");
    try {
      model.copyEventsBetween(srcDay, srcDay, "Dst", targetStartDate);
      Assert.fail("Expected IllegalArgumentException due to overlap in target calendar");
    } catch (IllegalArgumentException ex) {
      String msg = ex.getMessage().toLowerCase();
      Assert.assertTrue(msg.contains("conflict") || msg.contains("overlap"));
    }

    model.useCalendar("Dst");
    java.util.List<CalendarEvent> events =
        model.listEvents(targetStartDate, targetStartDate);
    Assert.assertEquals(1, events.size());
  }

  @Test
  public void testCopyEventsBetweenSortsByStartTime() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    model.createCalendar("Src", ZoneId.of("UTC"));
    model.createCalendar("Dst", ZoneId.of("UTC"));

    LocalDate srcDay = LocalDate.of(2024, 11, 15);

    model.useCalendar("Src");
    model.addEvent(new CalendarEvent(
        "Late",
        "",
        "",
        srcDay.atTime(15, 0),
        srcDay.atTime(16, 0),
        java.util.Optional.empty(),
        java.util.Optional.empty()
    ));
    model.addEvent(new CalendarEvent(
        "Early",
        "",
        "",
        srcDay.atTime(9, 0),
        srcDay.atTime(10, 0),
        java.util.Optional.empty(),
        java.util.Optional.empty()
    ));

    LocalDate targetDay = LocalDate.of(2024, 12, 1);
    model.useCalendar("Dst");
    model.addEvent(new CalendarEvent(
        "Middle",
        "",
        "",
        targetDay.atTime(12, 0),
        targetDay.atTime(13, 0),
        java.util.Optional.empty(),
        java.util.Optional.empty()
    ));

    model.useCalendar("Src");
    model.copyEventsBetween(srcDay, srcDay, "Dst", targetDay);

    model.useCalendar("Dst");
    java.util.List<CalendarEvent> all = model.listEvents(targetDay, targetDay);
    Assert.assertEquals(3, all.size());

    Assert.assertEquals(LocalTime.of(9, 0), all.get(0).start().toLocalTime());
    Assert.assertEquals(LocalTime.of(12, 0), all.get(1).start().toLocalTime());
    Assert.assertEquals(LocalTime.of(15, 0), all.get(2).start().toLocalTime());
  }

  @Test
  public void testChangeTimezoneUpdatesZoneButKeepsLocalTimes() {
    MultiCalendarModelImpl model = new MultiCalendarModelImpl();
    ZoneId ny = ZoneId.of("America/New_York");
    ZoneId utc = ZoneId.of("UTC");

    model.createCalendar("Work", ny);
    model.useCalendar("Work");

    LocalDateTime nyNoon = LocalDateTime.of(2024, 11, 15, 12, 0);
    LocalDateTime nyOne = LocalDateTime.of(2024, 11, 15, 13, 0);

    model.addEvent(new CalendarEvent(
        "Meeting",
        "",
        "",
        nyNoon,
        nyOne,
        java.util.Optional.empty(),
        java.util.Optional.empty()
    ));

    model.changeTimezone("Work", utc);

    Assert.assertEquals(utc, model.currentZone());

    java.util.List<CalendarEvent> events =
        model.listEvents(LocalDate.of(2024, 11, 15),
            LocalDate.of(2024, 11, 15));
    Assert.assertEquals(1, events.size());
    CalendarEvent ev = events.get(0);

    Assert.assertEquals(LocalDateTime.of(2024, 11, 15, 12, 0),
        ev.start());
    Assert.assertEquals(LocalDateTime.of(2024, 11, 15, 13, 0),
        ev.end());
  }

  @Test
  public void testConvertZoneUsesSameInstant() throws Exception {
    final MultiCalendarModelImpl model = new MultiCalendarModelImpl();

    Method helper = null;
    for (Method m : MultiCalendarModelImpl.class.getDeclaredMethods()) {
      Class<?>[] params = m.getParameterTypes();
      if (params.length == 3
          && params[0] == LocalDateTime.class
          && params[1] == ZoneId.class
          && params[2] == ZoneId.class
          && m.getReturnType() == LocalDateTime.class) {
        helper = m;
        break;
      }
    }

    Assert.assertNotNull(
        "Expected a helper with signature (LocalDateTime, ZoneId, ZoneId) returning LocalDateTime",
        helper);
    helper.setAccessible(true);

    ZoneId from = ZoneId.of("America/New_York");
    ZoneId to = ZoneId.of("UTC");
    LocalDateTime nyLocal = LocalDateTime.of(2024, 11, 15, 12, 0);

    LocalDateTime expected =
        nyLocal.atZone(from).withZoneSameInstant(to).toLocalDateTime();

    LocalDateTime actual =
        (LocalDateTime) helper.invoke(model, nyLocal, from, to);

    Assert.assertEquals(
        "Zone conversion must preserve instant when changing zones",
        expected, actual);
  }
}
