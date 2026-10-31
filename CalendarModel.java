package calendar.model;

import calendar.model.entity.CalendarEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Model interface for a multi-calendar scheduling system with per-calendar time zones.
 *
 * <p>Implementations manage multiple named calendars, the current (active) calendar
 * context, and operations for querying, editing, copying, and exporting events.
 * All event times are interpreted in the time zone of the calendar that owns them.
 * </p>
 *
 * <p><strong>Design notes:</strong></p>
 * <ul>
 *   <li>Calendar names must be unique.</li>
 *   <li>All operations that do not take an explicit calendar act on the current calendar
 *       (as set by {@link #useCalendar(String)}).</li>
 *   <li>Date range queries are <em>inclusive</em> of both endpoints.</li>
 *   <li>Copy operations preserve event duration; when mapped to a target calendar,
 *       the specified target start is interpreted in the target calendar’s time zone.</li>
 * </ul>
 */
public interface CalendarModel {

  /**
   * Creates a new calendar with the given unique name and time zone.
   *
   * @param name a unique, non-empty calendar name
   * @param zone the calendar time zone (IANA {@link ZoneId}); must not be {@code null}
   * @throws IllegalArgumentException if {@code name} is empty/blank or already exists
   * @throws NullPointerException if {@code name} or {@code zone} is {@code null}
   */
  void createCalendar(String name, ZoneId zone);

  /**
   * Renames an existing calendar.
   *
   * @param oldName the existing calendar name
   * @param newName the new unique name to assign
   * @throws IllegalArgumentException if {@code oldName} does not exist, or {@code newName}
   *                                  is empty/blank or already in use
   * @throws NullPointerException if {@code oldName} or {@code newName} is {@code null}
   */
  void renameCalendar(String oldName, String newName);

  /**
   * Changes the time zone of the specified calendar. Event local times remain the same
   * in that calendar’s semantics; only the zone association changes.
   *
   * @param name the calendar name
   * @param zone the new zone to associate with the calendar
   * @throws IllegalArgumentException if {@code name} does not exist
   * @throws NullPointerException if {@code name} or {@code zone} is {@code null}
   */
  void changeTimezone(String name, ZoneId zone);

  /**
   * Sets the current calendar context used by subsequent operations that do not
   * take an explicit calendar name.
   *
   * @param name the calendar name to activate
   * @throws IllegalArgumentException if {@code name} does not exist
   * @throws NullPointerException if {@code name} is {@code null}
   */
  void useCalendar(String name);

  /**
   * Adds an event to the current calendar.
   *
   * @param event the event to add; the event’s start/end are interpreted in the current
   *                  calendar’s zone
   * @throws IllegalStateException if no current calendar is set
   * @throws IllegalArgumentException if adding would violate uniqueness/overlap constraints
   * @throws NullPointerException if {@code event} is {@code null}
   */
  void addEvent(CalendarEvent event);

  /**
   * Lists all events whose local dates overlap the given inclusive date range
   * in the current calendar.
   *
   * @param start the inclusive start local date
   * @param end   the inclusive end local date
   * @return an ordered list of matching events (may be empty, never {@code null})
   * @throws IllegalStateException if no current calendar is set
   * @throws NullPointerException if {@code start} or {@code end} is {@code null}
   * @throws IllegalArgumentException if {@code end} is before {@code start}
   */
  List<CalendarEvent> listEvents(LocalDate start, LocalDate end);

  /**
   * Finds an event by its subject and exact local start time in the current calendar.
   *
   * @param name the event subject
   * @param startTime the exact local start time to match
   * @return an {@link Optional} containing the event if found, otherwise empty
   * @throws IllegalStateException if no current calendar is set
   * @throws NullPointerException if {@code name} or {@code startTime} is {@code null}
   */
  Optional<CalendarEvent> findEvent(String name, LocalDateTime startTime);

  /**
   * Removes an event identified by subject and exact local start time from the current calendar.
   *
   * @param name the event subject
   * @param startTime the event’s exact local start time
   * @return {@code true} if an event was removed; {@code false} if no matching event existed
   * @throws IllegalStateException if no current calendar is set
   * @throws NullPointerException if {@code name} or {@code startTime} is {@code null}
   */
  boolean removeEvent(String name, LocalDateTime startTime);

  /**
   * Copies a single event (identified by subject and exact local start time) from the
   *     current calendar
   * to a target calendar. The new event will start at {@code targetStartLocal} interpreted in the
   * target calendar’s time zone and will keep the original event’s duration.
   *
   * @param name the event subject in the current calendar
   * @param sourceStartLocal the event’s exact local start time in the current calendar
   * @param targetCalendar the destination calendar name
   * @param targetStartLocal the destination local start time in the target calendar
   * @throws IllegalStateException if no current calendar is set
   * @throws IllegalArgumentException if the source event is not found, the target calendar
   *                                  does not exist, or the copy would violate constraints
   * @throws NullPointerException if any parameter is {@code null}
   */
  void copyEvent(String name, LocalDateTime sourceStartLocal,
                 String targetCalendar, LocalDateTime targetStartLocal);

  /**
   * Copies all events scheduled on {@code sourceDate} (in the current calendar)
   *     into {@code targetCalendar}
   * mapped to {@code targetDate}. Each copied event preserves its wall-clock start/end times but is
   * reinterpreted in the target calendar’s zone (e.g., 14:00 EST becomes 11:00 PST).
   *
   * @param sourceDate the source local date in the current calendar
   * @param targetCalendar the destination calendar name
   * @param targetDate the destination local date in the target calendar
   * @throws IllegalStateException if no current calendar is set
   * @throws IllegalArgumentException if the target calendar does not exist, or any
   *     copy would violate constraints
   * @throws NullPointerException if any parameter is {@code null}
   */
  void copyEventsOnDate(LocalDate sourceDate, String targetCalendar, LocalDate targetDate);

  /**
   * Copies all events that overlap the inclusive date range [{@code fromDate}, {@code toDate}] in
   * the current calendar into {@code targetCalendar}. The first source day is aligned to
   * {@code targetStart}, and each event keeps its original duration while being mapped day-by-day.
   *
   * @param fromDate the inclusive start date in the current calendar
   * @param toDate the inclusive end date in the current calendar
   * @param targetCalendar the destination calendar name
   * @param targetStart the first mapped local date in the target calendar
   * @throws IllegalStateException if no current calendar is set
   * @throws IllegalArgumentException if {@code toDate} is before {@code fromDate}, the
   *     target calendar does not exist,
   *                                  or any copy would violate constraints
   * @throws NullPointerException if any parameter is {@code null}
   */
  void copyEventsBetween(LocalDate fromDate, LocalDate toDate,
                         String targetCalendar, LocalDate targetStart);

  /**
   * Exports the current calendar to a file. The format is auto-detected by extension:
   * <ul>
   *   <li><code>.csv</code> &mdash; CSV export compatible with Google Calendar CSV import.</li>
   *   <li><code>.ical</code> or <code>.ics</code> &mdash; iCalendar export compatible
   *       with Google Calendar.</li>
   * </ul>
   *
   * @param path the requested output path (must end with <code>.csv</code>,
   *                 <code>.ical</code>, or <code>.ics</code>)
   * @return the absolute {@link Path} where the file was written
   * @throws IllegalStateException if no current calendar is set
   * @throws IllegalArgumentException if the file extension is unsupported
   * @throws IOException if an I/O error occurs while writing
   * @throws NullPointerException if {@code path} is {@code null}
   */
  Path exportCurrent(Path path) throws IOException;

  /**
   * Returns the time zone of the current calendar.
   *
   * @return the current calendar’s {@link ZoneId}
   * @throws IllegalStateException if no current calendar is set
   */
  ZoneId currentZone();

  /**
   * Returns the name of the current calendar.
   *
   * @return the current calendar name
   * @throws IllegalStateException if no current calendar is set
   */
  String currentCalendarName();
}
