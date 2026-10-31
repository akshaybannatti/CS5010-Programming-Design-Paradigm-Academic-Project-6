package calendar.export;

import calendar.model.entity.CalendarEvent;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

/**
 * Defines the contract for exporting calendar events to various formats.
 * Implementations must support both CSV (Google Calendar importable)
 * and iCalendar (ICS) export formats.
 */
public interface CalendarExporter {
  /**
   * Writes all calendar events to a CSV file compatible with Google Calendar.
   *
   * @param path the path of the output CSV file
   * @param events the list of calendar events to export
   * @param zone the timezone of the calendar
   * @throws Exception if writing to the file fails
   */
  void writeCsv(Path path, List<CalendarEvent> events, ZoneId zone) throws Exception;

  /**
   * Writes all calendar events to an iCalendar (.ical/.ics) file.
   *
   * @param path the path of the output iCal file
   * @param events the list of calendar events to export
   * @param zone the timezone of the calendar
   * @throws Exception if writing to the file fails
   */
  void writeIcal(Path path, List<CalendarEvent> events, ZoneId zone) throws Exception;
}
