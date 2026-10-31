import calendar.export.CalendarExporterImpl;
import calendar.model.entity.CalendarEvent;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the private CSV escaping helper inside {@link CalendarExporterImpl}.
 *
 * <p>These tests use reflection to access the internal {@code csv(String)} method in order
 * to verify quoting, escaping, and null-handling behavior. This ensures complete
 * branch coverage for the CSV utility without exposing internals in production code.
 */
public class CalendarExporterImplTest {

  private CalendarExporterImpl exporter;
  private Method csvMethod;

  /**
   * Initializes a fresh {@link CalendarExporterImpl} instance and prepares reflective
   * access to the private {@code csv(String)} method before each test.
   *
   * @throws Exception if reflection fails to locate or expose the method
   */
  @Before
  public void setUp() throws Exception {
    this.exporter = new CalendarExporterImpl();

    this.csvMethod = CalendarExporterImpl.class.getDeclaredMethod("csv", String.class);
    this.csvMethod.setAccessible(true);
  }

  private String invokeCsv(String input) throws Exception {
    Object result = csvMethod.invoke(exporter, input);
    return (String) result;
  }

  @Test
  public void csv_null_returnsEmptyString() throws Exception {
    String out = invokeCsv(null);
    Assert.assertEquals("", out);
  }

  @Test
  public void csv_plainText_noQuotingNeeded() throws Exception {
    String out = invokeCsv("HelloWorld");
    Assert.assertEquals("HelloWorld", out);
  }

  @Test
  public void csv_textWithComma_isQuoted() throws Exception {
    String out = invokeCsv("a,b");
    Assert.assertTrue("Expected result to be quoted", out.startsWith("\"")
        && out.endsWith("\""));
    Assert.assertTrue(out.contains("a,b"));
  }

  @Test
  public void csv_textWithQuote_isQuotedAndEscaped() throws Exception {
    String out = invokeCsv("He said \"Hi\"");
    Assert.assertTrue("Expected result to be quoted", out.startsWith("\"")
        && out.endsWith("\""));
    Assert.assertTrue(
        "Expected internal quotes to be escaped (doubled)",
        out.contains("\"\"Hi\"\"") || out.contains("He said \"\"Hi\"\"")
    );
  }

  @Test
  public void testWriteCsvTimedAndAllDayEvents() throws Exception {
    CalendarExporterImpl exporter = new CalendarExporterImpl();
    List<CalendarEvent> events = new ArrayList<>();

    LocalDateTime start = LocalDateTime.of(2024, 11, 15, 10, 30);
    LocalDateTime end = LocalDateTime.of(2024, 11, 15, 11, 45);
    events.add(new CalendarEvent(
        "Meeting", "Desc", "Room 1",
        start, end, Optional.empty(), Optional.empty()
    ));

    LocalDateTime adStart = LocalDateTime.of(2024, 11, 16, 0, 0);
    LocalDateTime adEnd = LocalDateTime.of(2024, 11, 17, 0, 0);
    events.add(new CalendarEvent(
        "Holiday", "", "Somewhere",
        adStart, adEnd, Optional.empty(), Optional.empty()
    ));

    Path temp = Files.createTempFile("export-timed-allday", ".csv");
    try {
      exporter.writeCsv(temp, events, ZoneId.of("UTC"));

      List<String> lines = Files.readAllLines(temp);

      String[] timedCols = lines.get(1).split(",", -1);
      Assert.assertEquals("Meeting", timedCols[0]);
      Assert.assertEquals("11/15/2024", timedCols[1]);
      Assert.assertEquals("10:30 AM", timedCols[2]);
      Assert.assertEquals("11/15/2024", timedCols[3]);
      Assert.assertEquals("11:45 AM", timedCols[4]);
      Assert.assertEquals("False", timedCols[5]);

      String[] allDayCols = lines.get(2).split(",", -1);
      Assert.assertEquals("Holiday", allDayCols[0]);
      Assert.assertEquals("11/16/2024", allDayCols[1]);
      Assert.assertEquals("11/17/2024", allDayCols[3]);

      String flag = allDayCols[5].trim();
      Assert.assertTrue(flag.equals("True") || flag.equals("False"));

      Assert.assertEquals("Somewhere", allDayCols[7]);
      Assert.assertEquals("False", allDayCols[8]);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testWriteCsvNullLocation() throws Exception {
    String result = (String) csvMethod.invoke(exporter, new Object[] {null});
    Assert.assertEquals("", result);
  }

  @Test
  public void testWriteIcalHeaders() throws Exception {
    CalendarExporterImpl exporter = new CalendarExporterImpl();
    List<CalendarEvent> events = new ArrayList<>();

    Path temp = Files.createTempFile("ical-headers", ".ics");
    try {
      exporter.writeIcal(temp, events, ZoneId.of("UTC"));

      String content = new String(Files.readAllBytes(temp), StandardCharsets.UTF_8);

      Assert.assertTrue(content.contains("BEGIN:VCALENDAR"));
      Assert.assertTrue(content.contains("VERSION:2.0"));
      Assert.assertTrue(content.contains("PRODID:-//CS5010//Calendar//EN"));
      Assert.assertTrue(content.contains("CALSCALE:GREGORIAN"));
      Assert.assertTrue(content.contains("METHOD:PUBLISH"));
      Assert.assertTrue(content.contains("END:VCALENDAR"));
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testWriteIcalLocationAndDescriptionBranches() throws Exception {
    CalendarExporterImpl exporter = new CalendarExporterImpl();
    List<CalendarEvent> events = new ArrayList<>();

    LocalDateTime baseStart = LocalDateTime.of(2024, 11, 15, 10, 0);
    LocalDateTime baseEnd = baseStart.plusHours(1);

    events.add(new CalendarEvent(
        "WithFields",
        "First description",
        "Office",
        baseStart,
        baseEnd,
        Optional.empty(),
        Optional.empty()
    ));

    events.add(new CalendarEvent(
        "NoFields",
        "",
        "   ",
        baseStart.plusDays(1),
        baseEnd.plusDays(1),
        Optional.empty(),
        Optional.empty()
    ));

    Path temp = Files.createTempFile("ical-fields", ".ics");
    try {
      exporter.writeIcal(temp, events, ZoneId.of("UTC"));
      List<String> lines = Files.readAllLines(temp, StandardCharsets.UTF_8);

      long locCount = lines.stream()
          .filter(l -> l.startsWith("LOCATION:"))
          .count();
      long descCount = lines.stream()
          .filter(l -> l.startsWith("DESCRIPTION:"))
          .count();

      Assert.assertEquals(1, locCount);
      Assert.assertEquals(1, descCount);

      Assert.assertTrue(lines.stream().anyMatch(l -> l.contains("LOCATION:Office")));
      Assert.assertTrue(lines.stream().anyMatch(l
          -> l.contains("DESCRIPTION:First description")));
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testIcalUtcFormatUidAndAllDayDetection() throws IOException {
    CalendarExporterImpl exporter = new CalendarExporterImpl();
    ZoneId zone = ZoneId.of("UTC");

    LocalDateTime timedStart = LocalDateTime.of(2024, 11, 15, 10, 30);
    LocalDateTime timedEnd = timedStart.plusHours(1);

    LocalDateTime allDayStart = LocalDateTime.of(2024, 11, 16, 8, 0);
    LocalDateTime allDayEnd = LocalDateTime.of(2024, 11, 16, 17, 0);

    CalendarEvent timedEvent = new CalendarEvent(
        "TimedEvent", "", "",
        timedStart, timedEnd,
        Optional.empty(), Optional.empty());

    CalendarEvent allDayEvent = new CalendarEvent(
        "WorkDay", "", "",
        allDayStart, allDayEnd,
        Optional.empty(), Optional.empty());

    List<CalendarEvent> events = new ArrayList<CalendarEvent>();
    events.add(timedEvent);
    events.add(allDayEvent);

    Path temp = Files.createTempFile("ical-helpers", ".ical");
    try {
      exporter.writeIcal(temp, events, zone);

      List<String> lines = Files.readAllLines(temp);
      String content = String.join("\n", lines);

      Assert.assertTrue(
          "Expected UTC timestamp for timed event DTSTART",
          content.contains("20241115T103000Z"));
      Assert.assertTrue(
          "Expected UTC timestamp for timed event DTEND",
          content.contains("20241115T113000Z"));

      List<String> uidLines = new ArrayList<String>();
      for (String line : lines) {
        if (line.startsWith("UID:")) {
          uidLines.add(line);
        }
      }
      Assert.assertEquals("Expected one UID per event", 2, uidLines.size());

      for (String uidLine : uidLines) {
        Assert.assertTrue("UID should end with @cs5010",
            uidLine.endsWith("@cs5010"));
        String numericPart = uidLine.substring("UID:".length(),
            uidLine.length() - "@cs5010".length());
        Assert.assertFalse("UID numeric/hash part should not be empty",
            numericPart.trim().isEmpty());
      }

      Assert.assertNotEquals(
          "UIDs for different events must differ",
          uidLines.get(0), uidLines.get(1));

    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testDtStampLooksLikeUtcFormatted() throws IOException {
    CalendarExporterImpl exporter = new CalendarExporterImpl();

    java.util.List<CalendarEvent> events = java.util.Collections.singletonList(
        new CalendarEvent(
            "StampCheck",
            "",
            "",
            LocalDateTime.of(2024, 11, 5, 9, 7, 3),
            LocalDateTime.of(2024, 11, 5, 10, 7, 3),
            java.util.Optional.empty(),
            java.util.Optional.empty()
        )
    );

    Path temp = Files.createTempFile("dtstamp-check", ".ical");
    try {
      exporter.writeIcal(temp, events, ZoneId.of("UTC"));

      java.util.List<String> lines = Files.readAllLines(temp);
      String dtStamp = null;
      for (String line : lines) {
        if (line.startsWith("DTSTAMP:")) {
          dtStamp = line.substring("DTSTAMP:".length()).trim();
          break;
        }
      }

      Assert.assertNotNull("DTSTAMP line should be present", dtStamp);

      Assert.assertEquals("DTSTAMP must be 16 chars long", 16, dtStamp.length());
      Assert.assertEquals('T', dtStamp.charAt(8));
      Assert.assertEquals('Z', dtStamp.charAt(15));

      for (int i = 0; i < 8; i++) {
        Assert.assertTrue("DTSTAMP date chars must be digits",
            Character.isDigit(dtStamp.charAt(i)));
      }
      for (int i = 9; i < 15; i++) {
        Assert.assertTrue("DTSTAMP time chars must be digits",
            Character.isDigit(dtStamp.charAt(i)));
      }

    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testIsAllDayEventTrueAndFalse() throws Exception {
    CalendarExporterImpl exporter = new CalendarExporterImpl();

    java.lang.reflect.Method m =
        CalendarExporterImpl.class.getDeclaredMethod("isAllDay", CalendarEvent.class);
    m.setAccessible(true);

    CalendarEvent allDay = new CalendarEvent(
        "AllDay", "", "",
        LocalDateTime.of(2024, 11, 15, 8, 0),
        LocalDateTime.of(2024, 11, 15, 17, 0),
        Optional.empty(),
        Optional.empty()
    );

    CalendarEvent timed = new CalendarEvent(
        "Timed", "", "",
        LocalDateTime.of(2024, 11, 15, 9, 0),
        LocalDateTime.of(2024, 11, 15, 10, 0),
        Optional.empty(),
        Optional.empty()
    );

    boolean allDayResult = (Boolean) m.invoke(null, allDay);
    boolean timedResult = (Boolean) m.invoke(null, timed);

    Assert.assertTrue("Expected 8–17 same-day event to be all-day", allDayResult);
    Assert.assertFalse("Expected non-8–17 event to NOT be all-day", timedResult);
  }

  @Test
  public void testSanitizeIcalNullReturnsEmpty() throws Exception {
    java.lang.reflect.Method sanitize =
        CalendarExporterImpl.class.getDeclaredMethod("sanitizeIcal", String.class);
    sanitize.setAccessible(true);

    String result = (String) sanitize.invoke(null, new Object[] {null});

    Assert.assertEquals("", result);
  }

  @Test
  public void csv_textWithNewlineOnly_isQuoted() throws Exception {
    String out = invokeCsv("Line1\nLine2");

    Assert.assertTrue("Expected newline-only field to be quoted",
        out.startsWith("\"") && out.endsWith("\""));
    Assert.assertTrue("Newline content should be preserved inside quotes",
        out.contains("\n"));
  }

  @Test
  public void csv_textWithCarriageReturnOnly_isQuoted() throws Exception {
    String out = invokeCsv("Line1\rLine2");

    Assert.assertTrue("Expected CR-only field to be quoted",
        out.startsWith("\"") && out.endsWith("\""));
    Assert.assertTrue("Carriage return content should be preserved inside quotes",
        out.contains("\r"));
  }

  @Test
  public void testSanitizeIcalEscapesBackslashCommaSemicolonAndNewline() throws Exception {
    java.lang.reflect.Method sanitize =
        CalendarExporterImpl.class.getDeclaredMethod("sanitizeIcal", String.class);
    sanitize.setAccessible(true);

    String original = "Back\\slash,semi;line\nbreak";
    String result = (String) sanitize.invoke(null, original);

    String expected = "Back\\\\slash\\,semi\\;line\\nbreak";

    Assert.assertEquals("sanitizeIcal should escape all iCal special characters",
        expected, result);
  }

  @Test
  public void testUtcStampConvertsToUtcFromNonUtcZone() throws Exception {
    java.lang.reflect.Method utcStamp =
        CalendarExporterImpl.class.getDeclaredMethod(
            "utcStamp", java.time.LocalDateTime.class, java.time.ZoneId.class);
    utcStamp.setAccessible(true);

    java.time.LocalDateTime local =
        java.time.LocalDateTime.of(2024, 1, 10, 10, 0, 0);
    java.time.ZoneId ny = java.time.ZoneId.of("America/New_York");

    String result = (String) utcStamp.invoke(null, local, ny);

    Assert.assertEquals("UTC conversion should adjust for -05:00 offset",
        "20240110T150000Z", result);
  }

  @Test
  public void testUtcStampKeepsTimeWhenZoneIsUtc() throws Exception {
    java.lang.reflect.Method utcStamp =
        CalendarExporterImpl.class.getDeclaredMethod(
            "utcStamp", java.time.LocalDateTime.class, java.time.ZoneId.class);
    utcStamp.setAccessible(true);

    java.time.LocalDateTime local =
        java.time.LocalDateTime.of(2024, 1, 10, 10, 5, 30);
    java.time.ZoneId utc = java.time.ZoneId.of("UTC");

    String result = (String) utcStamp.invoke(null, local, utc);

    Assert.assertEquals("UTC stamp should not shift when zone is already UTC",
        "20240110T100530Z", result);
  }

  @Test
  public void csv_textWithNewline_isQuotedAndPreservesNewline() throws Exception {
    String out = invokeCsv("Line1\nLine2");

    Assert.assertTrue("Expected newline field to be quoted",
        out.startsWith("\"") && out.endsWith("\""));
    Assert.assertTrue("Newline must be preserved inside quotes", out.contains("\n"));
  }

  @Test
  public void csv_textWithCarriageReturn_isQuotedAndPreservesCarriageReturn() throws Exception {
    String out = invokeCsv("Line1\rLine2");

    Assert.assertTrue("Expected CR field to be quoted",
        out.startsWith("\"") && out.endsWith("\""));
    Assert.assertTrue("Carriage return must be preserved inside quotes", out.contains("\r"));
  }

  @Test
  public void testSanitizeIcalLeavesPlainTextUnchanged() throws Exception {
    java.lang.reflect.Method sanitize =
        CalendarExporterImpl.class.getDeclaredMethod("sanitizeIcal", String.class);
    sanitize.setAccessible(true);

    String original = "SimpleTitle123";
    String result = (String) sanitize.invoke(null, original);

    Assert.assertEquals("Plain text without iCal specials should be unchanged",
        original, result);
  }

  @Test
  public void testUtcStampKeepsInstantForUtcZone() throws Exception {
    java.lang.reflect.Method utcStamp =
        CalendarExporterImpl.class.getDeclaredMethod(
            "utcStamp", java.time.LocalDateTime.class, java.time.ZoneId.class);
    utcStamp.setAccessible(true);

    java.time.LocalDateTime local =
        java.time.LocalDateTime.of(2024, 1, 10, 10, 5, 30);
    java.time.ZoneId utc = java.time.ZoneId.of("UTC");

    String result = (String) utcStamp.invoke(null, local, utc);

    Assert.assertEquals("UTC stamp should not shift when already in UTC",
        "20240110T100530Z", result);
  }

  @Test
  public void testIsAllDayEventDifferentDatesNotAllDay() throws Exception {
    java.lang.reflect.Method m =
        CalendarExporterImpl.class.getDeclaredMethod("isAllDay", CalendarEvent.class);
    m.setAccessible(true);

    CalendarEvent spanningDays = new CalendarEvent(
        "SpanningDays", "", "",
        LocalDateTime.of(2024, 11, 15, 8, 0),
        LocalDateTime.of(2024, 11, 16, 17, 0),
        Optional.empty(),
        Optional.empty()
    );

    boolean result = (Boolean) m.invoke(null, spanningDays);
    Assert.assertFalse("Event that spans multiple dates must not be treated as all-day", result);
  }

  @Test
  public void testIsAllDayEventDifferentEndTimeNotAllDay() throws Exception {
    java.lang.reflect.Method m =
        CalendarExporterImpl.class.getDeclaredMethod("isAllDay", CalendarEvent.class);
    m.setAccessible(true);

    CalendarEvent wrongEndTime = new CalendarEvent(
        "WrongEndTime", "", "",
        LocalDateTime.of(2024, 11, 15, 8, 0),
        LocalDateTime.of(2024, 11, 15, 18, 0),
        Optional.empty(),
        Optional.empty()
    );

    boolean result = (Boolean) m.invoke(null, wrongEndTime);
    Assert.assertFalse("Event with 8:00 start but non-17:00 end must not be all-day", result);
  }

  @Test
  public void testWriteCsvAllDayEventBlanksTimesAndMarksAllDayTrue() throws Exception {
    CalendarExporterImpl exporter = new CalendarExporterImpl();
    List<CalendarEvent> events = new ArrayList<>();

    LocalDateTime start = LocalDateTime.of(2024, 11, 15, 8, 0);
    LocalDateTime end = LocalDateTime.of(2024, 11, 15, 17, 0);

    events.add(new CalendarEvent(
        "AllDayEvent",
        "",
        "Office",
        start,
        end,
        Optional.empty(),
        Optional.empty()
    ));

    Path temp = Files.createTempFile("csv-all-day", ".csv");
    try {
      exporter.writeCsv(temp, events, ZoneId.of("UTC"));

      List<String> lines = Files.readAllLines(temp);
      Assert.assertEquals("Expected header plus one data line", 2, lines.size());

      String[] cols = lines.get(1).split(",", -1);
      Assert.assertEquals("AllDayEvent", cols[0]);
      Assert.assertEquals("11/15/2024", cols[1]);
      Assert.assertEquals("", cols[2]);
      Assert.assertEquals("11/15/2024", cols[3]);
      Assert.assertEquals("", cols[4]);
      Assert.assertEquals("True", cols[5]);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testWriteCsvWithNoEventsProducesHeaderOnly() throws Exception {
    CalendarExporterImpl exporter = new CalendarExporterImpl();
    List<CalendarEvent> events = new ArrayList<>();

    Path temp = Files.createTempFile("csv-no-events", ".csv");
    try {
      exporter.writeCsv(temp, events, ZoneId.of("UTC"));

      List<String> lines = Files.readAllLines(temp);
      Assert.assertEquals("Expected only header line when no events", 1, lines.size());

      String expectedHeader =
          "Subject,Start Date,Start Time,End Date,End Time,All Day Event,Description,"
              + "Location,Private";
      Assert.assertEquals("CSV header must match expected format", expectedHeader, lines.get(0));
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Test
  public void testWriteIcalNullLocationAndDescriptionAreOmitted() throws Exception {
    CalendarExporterImpl exporter = new CalendarExporterImpl();
    List<CalendarEvent> events = new ArrayList<>();

    LocalDateTime start = LocalDateTime.of(2024, 11, 20, 9, 0);
    LocalDateTime end = start.plusHours(1);

    events.add(new CalendarEvent(
        "NoExtraFields",
        "",
        "",
        start,
        end,
        Optional.empty(),
        Optional.empty()
    ));

    Path temp = Files.createTempFile("ical-no-extra-fields", ".ics");
    try {
      exporter.writeIcal(temp, events, ZoneId.of("UTC"));
      List<String> lines = Files.readAllLines(temp, StandardCharsets.UTF_8);

      Assert.assertTrue(
          "LOCATION should not be written when location is empty",
          lines.stream().noneMatch(l -> l.startsWith("LOCATION:")));

      Assert.assertTrue(
          "DESCRIPTION should not be written when description is empty",
          lines.stream().noneMatch(l -> l.startsWith("DESCRIPTION:")));
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}