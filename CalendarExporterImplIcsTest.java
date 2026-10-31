import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import calendar.export.CalendarExporter;
import calendar.export.CalendarExporterImpl;
import calendar.model.entity.CalendarEvent;
import calendar.model.entity.PrivacyStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * ICS export tests for {@link CalendarExporterImpl}.
 *
 * <p>Targets: VCALENDAR wrapper, VEVENT blocks, SUMMARY/DTSTART/DTEND presence,
 * all-day vs timed encoding, and minimal UID/DTSTAMP presence (by structure).
 * Tests are intentionally regex/contains based to avoid brittleness across platforms.</p>
 */
public class CalendarExporterImplIcsTest {

  private Path tempDir;

  /**
   * Creates a temporary directory for ICS export tests.
   *
   * <p>This directory provides an isolated file system location
   * for writing .ics files during tests, ensuring no interference
   * with other test runs or local files.</p>
   *
   * @throws Exception if the temporary directory cannot be created
   */
  @Before
  public void setup() throws Exception {
    tempDir = Files.createTempDirectory("icsExportTests");
  }

  /**
   * Cleans up the temporary directory created during {@link #setup()}.
   *
   * <p>Deletes all files and subdirectories recursively in a
   * best-effort manner. Exceptions during deletion are ignored
   * to maintain test stability across operating systems.</p>
   *
   * @throws Exception if walking the file tree fails
   */
  @After
  public void cleanup() throws Exception {
    if (tempDir != null && Files.exists(tempDir)) {
      Files.walk(tempDir)
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(p -> {
            try {
              Files.deleteIfExists(p);
            } catch (Exception e) {
              e.getMessage();
            }
          });
    }
  }

  private static CalendarEvent event(
      String name, String desc, String loc,
      LocalDateTime start, LocalDateTime end
  ) {
    return new CalendarEvent(
        name,
        desc,
        loc,
        start,
        end,
        Optional.empty(),
        Optional.of(UUID.randomUUID())
    );
  }

  @Test
  public void writeIcal_writesVcalendarWithEventsAndEncodesDates() throws Exception {
    CalendarExporter exporter = new CalendarExporterImpl();

    CalendarEvent timed = new CalendarEvent(
        "Sprint Review",
        "Zoom, Inc.",
        "Boston HQ",
        LocalDateTime.of(2025, 1, 10, 11, 0),
        LocalDateTime.of(2025, 1, 10, 12, 0),
        Optional.empty(),
        Optional.of(UUID.randomUUID()),
        PrivacyStatus.PUBLIC
    );

    CalendarEvent allDay = new CalendarEvent(
        "Onsite All Day",
        "",
        "Client Office",
        LocalDateTime.of(2025, 1, 7, 8, 0),
        LocalDateTime.of(2025, 1, 7, 17, 0),
        Optional.empty(),
        Optional.of(UUID.randomUUID()),
        PrivacyStatus.PUBLIC
    );

    Path ics = tempDir.resolve("work_jan.ics");
    exporter.writeIcal(ics, Arrays.asList(timed, allDay), ZoneId.of("America/New_York"));

    assertTrue("ICS file should exist", Files.exists(ics));
    String icsText = new String(Files.readAllBytes(ics), StandardCharsets.UTF_8);

    assertTrue(icsText.contains("BEGIN:VCALENDAR"));
    assertTrue(icsText.contains("END:VCALENDAR"));

    int begins = count(icsText, "BEGIN:VEVENT");
    int ends = count(icsText, "END:VEVENT");
    assertEquals("BEGIN/END VEVENT count mismatch", begins, ends);
    assertTrue("Expected at least 2 VEVENTs", begins >= 2);

    assertTrue(icsText.contains("SUMMARY:Sprint Review"));
    assertTrue(icsText.contains("SUMMARY:Onsite All Day"));

    assertTrue("Expected 20250110 in DTSTART",
        icsText.contains("DTSTART") && icsText.contains("20250110"));
    assertTrue("Expected 20250110 in DTEND",
        icsText.contains("DTEND") && icsText.contains("20250110"));

    int summaryIdx = icsText.indexOf("SUMMARY:Onsite All Day");
    boolean allDayOk = false;
    if (summaryIdx >= 0) {
      int veventStart = icsText.lastIndexOf("BEGIN:VEVENT", summaryIdx);
      int veventEnd = icsText.indexOf("END:VEVENT", summaryIdx);
      if (veventStart >= 0 && veventEnd > veventStart) {
        String block = icsText.substring(veventStart, veventEnd);
        boolean hasDtstart = block.contains("DTSTART");
        boolean hasDtend = block.contains("DTEND");
        boolean usesValueDate = block.contains("DTSTART;VALUE=DATE");
        boolean hasDateToken = block.contains("20250107");
        allDayOk = hasDtstart && hasDtend && (usesValueDate || hasDateToken);
      }
    }
    assertTrue("Expected all-day VEVENT to contain DTSTART/DTEND and "
        + "either VALUE=DATE or date token 20250107", allDayOk);

    assertTrue(icsText.contains("UID:"));
    assertTrue(icsText.contains("DTSTAMP:"));
  }

  @Test
  public void writeIcal_handlesEmptyList_minimalCalendar() throws Exception {
    CalendarExporter exporter = new CalendarExporterImpl();
    Path ics = tempDir.resolve("empty.ics");
    exporter.writeIcal(ics, Collections.emptyList(), ZoneId.of("America/New_York"));

    String icsText = new String(Files.readAllBytes(ics), StandardCharsets.UTF_8);
    assertTrue("Must contain VCALENDAR", icsText.contains("BEGIN:VCALENDAR"));
    assertFalse("Should contain no VEVENT", icsText.contains("BEGIN:VEVENT"));
  }

  /**
   * Counts the number of occurrences of a substring in a string.
   */
  private static int count(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  private static int countOccurrences(String hay, String needle) {
    int idx = 0;
    int count = 0;
    while ((idx = hay.indexOf(needle, idx)) >= 0) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  @Test
  public void testWriteIcalNullLocAndDescBranchesCoveredViaReflection() throws Exception {
    CalendarExporterImpl exporter = new CalendarExporterImpl();
    List<CalendarEvent> events = new ArrayList<>();

    LocalDateTime start = LocalDateTime.of(2024, 11, 21, 10, 0);
    LocalDateTime end = start.plusHours(1);

    CalendarEvent base = new CalendarEvent(
        "NullBranches",
        "DESC_MARKER",
        "LOC_MARKER",
        start,
        end,
        Optional.empty(),
        Optional.empty()
    );

    for (java.lang.reflect.Field f : CalendarEvent.class.getDeclaredFields()) {
      if (f.getType().equals(String.class)) {
        f.setAccessible(true);
        Object current = f.get(base);
        if ("LOC_MARKER".equals(current) || "DESC_MARKER".equals(current)) {
          f.set(base, null);
        }
      }
    }

    events.add(base);

    Path temp = Files.createTempFile("ical-null-branches", ".ics");
    try {
      exporter.writeIcal(temp, events, ZoneId.of("UTC"));
      List<String> lines = Files.readAllLines(temp, StandardCharsets.UTF_8);

      Assert.assertTrue(
          "LOCATION should be omitted when location() returns null",
          lines.stream().noneMatch(l -> l.startsWith("LOCATION:"))
      );
      Assert.assertTrue(
          "DESCRIPTION should be omitted when description() returns null",
          lines.stream().noneMatch(l -> l.startsWith("DESCRIPTION:"))
      );
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}
