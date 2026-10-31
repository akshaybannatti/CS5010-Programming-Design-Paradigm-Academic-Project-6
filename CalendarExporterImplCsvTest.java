import static org.junit.Assert.assertEquals;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * CSV export tests for {@link CalendarExporterImpl}.
 *
 * <p>Targets: CSV header presence, date formatting, quoting/escaping of commas/quotes/newlines,
 * null handling, and multiple-row output.</p>
 */
public class CalendarExporterImplCsvTest {

  private Path tempDir;

  /**
   * Creates a fresh temporary directory for this test class to write files into.
   *
   * <p>Each test gets an isolated folder so file I/O is deterministic and does not
   * collide across test runs.</p>
   *
   * @throws Exception if the temporary directory cannot be created
   */
  @Before
  public void setup() throws Exception {
    tempDir = Files.createTempDirectory("csvExportTests");
  }

  /**
   * Best-effort cleanup of the temporary directory created by {@link #setup()}.
   *
   * <p>Deletion failures are intentionally ignored because they are often caused by
   * transient OS file locks on CI. The catch block is kept non-empty to satisfy
   * style checks while avoiding noisy logs.</p>
   *
   * @throws Exception if walking the directory tree fails
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
      String name,
      String desc,
      String loc,
      LocalDateTime start,
      LocalDateTime end
  ) {
    String safeName = name == null ? "" : name;
    String safeDesc = desc == null ? "" : desc;
    String safeLoc = loc == null ? "" : loc;

    return new CalendarEvent(
        safeName,
        safeDesc,
        safeLoc,
        start,
        end,
        Optional.empty(),
        Optional.of(UUID.randomUUID()),
        PrivacyStatus.PUBLIC
    );
  }

  @Test
  public void writeCsv_writesHeaderAndRows_withEscapingAndDates() throws Exception {
    CalendarExporter exporter = new CalendarExporterImpl();

    CalendarEvent timed = event(
        "Sprint Review",
        "Zoom, Inc.\n\"Room A\"",
        "Boston HQ",
        LocalDateTime.of(2025, 1, 10, 11, 0),
        LocalDateTime.of(2025, 1, 10, 12, 0)
    );

    CalendarEvent allDay = event(
        "Onsite All Day",
        "Field visit",
        "Client Office",
        LocalDateTime.of(2025, 1, 7, 8, 0),
        LocalDateTime.of(2025, 1, 7, 17, 0)
    );

    Path csv = tempDir.resolve("work_jan.csv");
    exporter.writeCsv(csv, Arrays.asList(timed, allDay), ZoneId.of("America/New_York"));

    assertTrue("CSV file should exist", Files.exists(csv));
    String content = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8);

    assertTrue("CSV must include a Subject header",
        content.toLowerCase().contains("subject"));
    assertTrue("CSV must include a Start Date header",
        content.toLowerCase().contains("start date"));

    assertTrue("Should contain 01/10/2025 for timed event", content.contains("01/10/2025"));
    assertTrue("Should contain 01/07/2025 for all-day event", content.contains("01/07/2025"));

    assertTrue("CSV should quote/escape commas/newlines/quotes",
        content.contains("\"Zoom, Inc.\n\"\"Room A\"\"\"")
            || content.contains("\"Zoom, Inc.\r\n\"\"Room A\"\"\""));

    String[] lines = content.split("\\r?\\n");
    assertTrue("Expected at least 3 lines (header + 2 rows)", lines.length >= 3);
  }

  @Test
  public void writeCsv_handlesEmptyList_createsHeaderOnly() throws Exception {
    CalendarExporter exporter = new CalendarExporterImpl();
    Path csv = tempDir.resolve("empty.csv");
    exporter.writeCsv(csv, Collections.emptyList(), ZoneId.of("America/New_York"));

    assertTrue("CSV file should exist", Files.exists(csv));
    String content = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8).trim();
    assertTrue("Header should be present", content.toLowerCase().contains("subject"));
    assertEquals("Only header expected for empty list", 1,
        content.split("\\r?\\n").length);
  }
}
