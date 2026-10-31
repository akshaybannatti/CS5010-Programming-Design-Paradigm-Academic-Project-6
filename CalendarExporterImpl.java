package calendar.export;

import calendar.model.entity.CalendarEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Implementation of {@link CalendarExporter} that exports calendar data
 * to Google Calendar–compatible CSV files and standard iCalendar (.ics) files.
 */
public class CalendarExporterImpl implements CalendarExporter {
  private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
  private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("hh:mm a");

  @Override
  public void writeCsv(Path path, List<CalendarEvent> events, ZoneId zone) throws IOException {
    try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
      bw.write(
          "Subject,Start Date,Start Time,End Date,End Time,All Day Event,"
              + "Description,Location,Private\n");
      for (CalendarEvent e : events) {
        ZonedDateTime zonedStart = e.start().atZone(zone);
        ZonedDateTime zonedEnd = e.end().atZone(zone);
        boolean allDay = isAllDay(e);

        String startDate = CSV_DATE.format(zonedStart);
        String endDate = CSV_DATE.format(zonedEnd);
        String startTime = allDay ? "" : CSV_TIME.format(zonedStart);
        String endTime = allDay ? "" : CSV_TIME.format(zonedEnd);

        bw.write(csv(e.name()));
        bw.write(",");
        bw.write(startDate);
        bw.write(",");
        bw.write(startTime);
        bw.write(",");
        bw.write(endDate);
        bw.write(",");
        bw.write(endTime);
        bw.write(",");
        bw.write(allDay ? "True" : "False");
        bw.write(",");
        bw.write(csv(e.description()));
        bw.write(",");
        bw.write(csv(e.location()));
        bw.write(",");
        bw.write("False\n");
      }
    }
  }

  @Override
  public void writeIcal(Path path, List<CalendarEvent> events, ZoneId zone) throws IOException {
    final String crlf = "\r\n";
    try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
      bw.write("BEGIN:VCALENDAR" + crlf);
      bw.write("VERSION:2.0" + crlf);
      bw.write("PRODID:-//CS5010//Calendar//EN" + crlf);
      bw.write("CALSCALE:GREGORIAN" + crlf);
      bw.write("METHOD:PUBLISH" + crlf);

      for (CalendarEvent e : events) {
        bw.write("BEGIN:VEVENT" + crlf);
        bw.write("UID:" + uidFor(e) + crlf);
        bw.write("DTSTAMP:" + nowStamp() + crlf);

        bw.write("DTSTART:" + utcStamp(e.start(), zone) + crlf);
        bw.write("DTEND:" + utcStamp(e.end(), zone) + crlf);

        bw.write("SUMMARY:" + sanitizeIcal(e.name()) + crlf);
        if (e.location() != null && !e.location().isBlank()) {
          bw.write("LOCATION:" + sanitizeIcal(e.location()) + crlf);
        }
        if (e.description() != null && !e.description().isBlank()) {
          bw.write("DESCRIPTION:" + sanitizeIcal(e.description()) + crlf);
        }
        bw.write("END:VEVENT" + crlf);
      }

      bw.write("END:VCALENDAR" + crlf);
    }
  }

  private static String csv(String s) {
    if (s == null) {
      return "";
    }
    String esc = s.replace("\"", "\"\"");
    boolean q = esc.contains(",") || esc.contains("\"") || esc.contains("\n") || esc.contains("\r");
    return q ? "\"" + esc + "\"" : esc;
  }

  private static String nowStamp() {
    var now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
    return String.format("%04d%02d%02dT%02d%02d%02dZ",
        now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
        now.getHour(), now.getMinute(), now.getSecond());
  }

  private static String utcStamp(java.time.LocalDateTime ldt, ZoneId zone) {
    var z = ldt.atZone(zone).withZoneSameInstant(java.time.ZoneOffset.UTC);
    return String.format("%04d%02d%02dT%02d%02d%02dZ",
        z.getYear(), z.getMonthValue(), z.getDayOfMonth(),
        z.getHour(), z.getMinute(), z.getSecond());
  }

  private static String sanitizeIcal(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\")
        .replace(",", "\\,")
        .replace(";", "\\;")
        .replace("\n", "\\n");
  }

  /**
   * Generates a unique, deterministic UID for each calendar event.
   */
  private static String uidFor(CalendarEvent e) {
    return Math.abs((e.name() + "|" + e.start().toString()).hashCode()) + "@cs5010";
  }

  private static boolean isAllDay(CalendarEvent e) {
    return e.start().toLocalDate().equals(e.end().toLocalDate())
        && e.start().toLocalTime().equals(LocalTime.of(8, 0))
        && e.end().toLocalTime().equals(LocalTime.of(17, 0));
  }
}
