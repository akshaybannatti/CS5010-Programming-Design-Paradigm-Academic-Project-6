package calendar.model;

import calendar.model.entity.CalendarEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory implementation of {@link CalendarModel} with deterministic ordering.
 *
 * <p><strong>Representation:</strong></p>
 * <ul>
 *   <li>Calendars are kept in a {@code LinkedHashMap<String, CalendarData>} to preserve
 *       insertion order.</li>
 *   <li>Each calendar has a {@link java.time.ZoneId} and a {@code List<CalendarEvent>}
 *       sorted by start time.</li>
 *   <li>Events are compared and validated in the owning calendar’s local time semantics.</li>
 * </ul>
 *
 * <p><strong>Invariants:</strong></p>
 * <ul>
 *   <li>Calendar names are unique, non-null, and non-blank.</li>
 *   <li>Within a calendar, overlapping time ranges are disallowed (see {@link
 *       #ensureNoOverlap(CalendarData, calendar.model.entity.CalendarEvent)}).</li>
 *   <li>{@code current} points to an existing calendar when set.</li>
 * </ul>
 *
 * <p><strong>Time zones &amp; copying:</strong> Copy operations preserve duration and
 *     map wall-clock
 * times between source and target calendar zones according to the assignment rules.</p>
 *
 * <p><strong>Export:</strong> File export is intentionally delegated to the
 *     controller/exporter to preserve MVC separation;
 * this implementation throws {@link UnsupportedOperationException} in {@link
 *     #exportCurrent(java.nio.file.Path)}.</p>
 */
public final class MultiCalendarModelImpl implements CalendarModel {

  /**
   * Map of calendar name -> data. Used LinkedHashMap for deterministic order.
   */
  private final Map<String, CalendarData> calendars = new LinkedHashMap<>();
  private String current;

  /**
   * Holds calendar-specific state.
   */
  private static final class CalendarData {
    private ZoneId zone;
    private final List<CalendarEvent> events = new ArrayList<>();

    CalendarData(final ZoneId zone) {
      this.zone = zone;
    }
  }

  @Override
  public void createCalendar(final String name, final ZoneId zone) {
    String key = requireName(name);
    if (this.calendars.containsKey(key)) {
      throw new IllegalArgumentException("calendar exists: " + key);
    }
    this.calendars.put(key, new CalendarData(zone));
    if (this.current == null) {
      this.current = key;
    }
  }

  @Override
  public void renameCalendar(final String oldName, final String newName) {
    String oldKey = requireName(oldName);
    String newKey = requireName(newName);
    CalendarData data = this.calendars.remove(oldKey);
    if (data == null) {
      throw new IllegalArgumentException("no such calendar: " + oldKey);
    }
    if (this.calendars.containsKey(newKey)) {
      this.calendars.put(oldKey, data);
      throw new IllegalArgumentException("target name exists: " + newKey);
    }
    this.calendars.put(newKey, data);
    if (Objects.equals(this.current, oldKey)) {
      this.current = newKey;
    }
  }

  @Override
  public void changeTimezone(final String name, final ZoneId zone) {
    CalendarData data = getData(requireName(name));
    data.zone = Objects.requireNonNull(zone);
  }

  @Override
  public void useCalendar(final String name) {
    String key = requireName(name);
    if (!this.calendars.containsKey(key)) {
      throw new IllegalArgumentException("no such calendar: " + key);
    }
    this.current = key;
  }

  @Override
  public void addEvent(final CalendarEvent event) {
    CalendarData data = currentData();
    ensureNoOverlap(data, event);
    data.events.add(event);
    data.events.sort(Comparator.comparing(CalendarEvent::start));
  }

  @Override
  public List<CalendarEvent> listEvents(final LocalDate start, final LocalDate end) {
    CalendarData data = currentData();
    List<CalendarEvent> out = new ArrayList<>();
    for (CalendarEvent ev : data.events) {
      LocalDate s = ev.start().toLocalDate();
      LocalDate e = ev.end().toLocalDate();
      boolean overlaps = !s.isAfter(end) && !e.isBefore(start);
      if (overlaps) {
        out.add(ev);
      }
    }
    return Collections.unmodifiableList(out);
  }

  @Override
  public Optional<CalendarEvent> findEvent(final String name, final LocalDateTime startTime) {
    CalendarData data = currentData();
    for (CalendarEvent ev : data.events) {
      if (ev.name().equals(name) && ev.start().equals(startTime)) {
        return Optional.of(ev);
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean removeEvent(final String name, final LocalDateTime startTime) {
    CalendarData data = currentData();
    for (int i = 0; i < data.events.size(); i++) {
      CalendarEvent ev = data.events.get(i);
      if (ev.name().equals(name) && ev.start().equals(startTime)) {
        data.events.remove(i);
        return true;
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * <p><strong>Implementation note:</strong> duration is computed in source zone and applied at the
   * target start in the target zone.</p>
   */
  @Override
  public void copyEvent(final String name, final LocalDateTime sourceStartLocal,
                        final String targetCalendar, final LocalDateTime targetStartLocal) {

    CalendarData src = currentData();
    CalendarData dst = getData(requireName(targetCalendar));

    CalendarEvent srcEvent = findEvent(name, sourceStartLocal)
        .orElseThrow(() -> new IllegalArgumentException("source event not found"));

    ZoneId srcZone = src.zone;
    ZoneId dstZone = dst.zone;

    java.time.Instant srcStartI = srcEvent.start().atZone(srcZone).toInstant();
    java.time.Instant srcEndI = srcEvent.end().atZone(srcZone).toInstant();
    long durMinutes = java.time.Duration.between(srcStartI, srcEndI).toMinutes();

    java.time.Instant newStartI = targetStartLocal.atZone(dstZone).toInstant();
    java.time.Instant newEndI = newStartI.plusSeconds(durMinutes * 60L);

    LocalDateTime newStart =
        java.time.ZonedDateTime.ofInstant(newStartI, dstZone).toLocalDateTime();
    LocalDateTime newEnd = java.time.ZonedDateTime.ofInstant(newEndI, dstZone).toLocalDateTime();

    CalendarEvent copy = new CalendarEvent(
        srcEvent.name(),
        srcEvent.description(),
        srcEvent.location(),
        newStart,
        newEnd,
        srcEvent.recurrence(),
        srcEvent.seriesId());

    ensureNoOverlap(dst, copy);
    dst.events.add(copy);
    dst.events.sort(Comparator.comparing(CalendarEvent::start));
  }

  /**
   * {@inheritDoc}
   *
   * <p><strong>Implementation note:</strong> wall-clock start minutes are preserved; the
   * resulting local times are interpreted in the target calendar’s zone.</p>
   */
  @Override
  public void copyEventsOnDate(final LocalDate sourceDate, final String targetCalendar,
                               final LocalDate targetDate) {
    CalendarData src = currentData();
    CalendarData dst = getData(requireName(targetCalendar));

    ZoneId srcZone = src.zone;
    ZoneId dstZone = dst.zone;

    for (CalendarEvent ev : src.events) {
      if (ev.start().toLocalDate().equals(sourceDate)) {
        ZonedDateTime srcStartZ = ev.start().atZone(srcZone);
        ZonedDateTime srcEndZ = ev.end().atZone(srcZone);

        int startMinutes = srcStartZ.getHour() * 60 + srcStartZ.getMinute();
        long durMinutes =
            java.time.Duration.between(srcStartZ.toInstant(), srcEndZ.toInstant()).toMinutes();

        java.time.Instant anchorInstant = targetDate.atStartOfDay().atZone(srcZone).toInstant();
        java.time.Instant newStartInstant = anchorInstant.plusSeconds(startMinutes * 60L);
        java.time.Instant newEndInstant = newStartInstant.plusSeconds(durMinutes * 60L);

        LocalDateTime newStart =
            java.time.ZonedDateTime.ofInstant(newStartInstant, dstZone).toLocalDateTime();
        LocalDateTime newEnd =
            java.time.ZonedDateTime.ofInstant(newEndInstant, dstZone).toLocalDateTime();

        CalendarEvent copy = new CalendarEvent(
            ev.name(),
            ev.description(),
            ev.location(),
            newStart,
            newEnd,
            ev.recurrence(),
            ev.seriesId());

        ensureNoOverlap(dst, copy);
        dst.events.add(copy);
      }
    }
    dst.events.sort(Comparator.comparing(CalendarEvent::start));
  }

  /**
   * {@inheritDoc}
   *
   * <p><strong>Implementation note:</strong> computes an offset from the range anchor to each
   * source event’s day and maps it to the target start day; duration is preserved.</p>
   */
  @Override
  public void copyEventsBetween(final LocalDate fromDate, final LocalDate toDate,
                                final String targetCalendar, final LocalDate targetStart) {
    if (toDate.isBefore(fromDate)) {
      throw new IllegalArgumentException("end before start");
    }

    CalendarData src = currentData();
    CalendarData dst = getData(requireName(targetCalendar));
    ZoneId srcZone = src.zone;
    ZoneId dstZone = dst.zone;

    LocalDate anchor = fromDate;
    long daysSpan = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate);

    java.util.List<CalendarEvent> snapshot = new java.util.ArrayList<>(src.events);

    for (CalendarEvent ev : snapshot) {
      LocalDate s = ev.start().toLocalDate();
      LocalDate e = ev.end().toLocalDate();
      boolean overlaps = !s.isAfter(toDate) && !e.isBefore(fromDate);
      if (!overlaps) {
        continue;
      }

      long offset = java.time.temporal.ChronoUnit.DAYS.between(anchor, s);
      LocalDate mappedDay = targetStart.plusDays(offset);

      ZonedDateTime srcStartZ = ev.start().atZone(srcZone);
      ZonedDateTime srcEndZ = ev.end().atZone(srcZone);
      int startMinutes = srcStartZ.getHour() * 60 + srcStartZ.getMinute();
      long durMinutes =
          java.time.Duration.between(srcStartZ.toInstant(), srcEndZ.toInstant()).toMinutes();

      java.time.Instant anchorInstant = mappedDay.atStartOfDay().atZone(srcZone).toInstant();
      java.time.Instant newStartInstant
          = anchorInstant.plusSeconds(startMinutes * 60L);
      java.time.Instant newEndInstant = newStartInstant.plusSeconds(durMinutes * 60L);

      LocalDateTime newStart =
          java.time.ZonedDateTime.ofInstant(newStartInstant, dstZone).toLocalDateTime();
      LocalDateTime newEnd =
          java.time.ZonedDateTime.ofInstant(newEndInstant, dstZone).toLocalDateTime();

      CalendarEvent copy = new CalendarEvent(
          ev.name(),
          ev.description(),
          ev.location(),
          newStart,
          newEnd,
          ev.recurrence(),
          ev.seriesId());

      ensureNoOverlap(dst, copy);
      dst.events.add(copy);
    }

    dst.events.sort(Comparator.comparing(CalendarEvent::start));
  }

  /**
   * {@inheritDoc}
   *
   * <p><strong>Implementation note:</strong> this model delegates export to the controller/exporter
   * layer and therefore throws {@link UnsupportedOperationException}.</p>
   *
   * @throws UnsupportedOperationException always in this implementation
   */
  @Override
  public java.nio.file.Path exportCurrent(final java.nio.file.Path path) {
    throw new UnsupportedOperationException("Export is handled by the controller/exporter.");
  }

  @Override
  public ZoneId currentZone() {
    return currentData().zone;
  }

  @Override
  public String currentCalendarName() {
    return this.current;
  }

  private CalendarData currentData() {
    if (this.current == null) {
      throw new IllegalStateException("no calendar in use");
    }
    return getData(this.current);
  }

  private CalendarData getData(final String key) {
    CalendarData data = this.calendars.get(key);
    if (data == null) {
      throw new IllegalArgumentException("no such calendar: " + key);
    }
    return data;
  }

  private static String requireName(final String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("name is null");
    }
    String s = raw.trim();
    if (s.isEmpty()) {
      throw new IllegalArgumentException("name is empty");
    }
    return s;
  }

  /**
   * Converts a LocalDateTime that is interpreted in `from` zone to the equivalent
   * wall-clock time in `to` zone for the same instant.
   */
  private static LocalDateTime convertInstant(LocalDateTime dt, ZoneId from, ZoneId to) {
    return dt.atZone(from).withZoneSameInstant(to).toLocalDateTime();
  }

  /**
   * Ensures the candidate does not overlap any existing event in the same calendar.
   *
   * <p>Policy: Adjacent events (where one ends exactly when another starts)
   * are considered overlapping and are rejected.</p>
   *
   * @param data the calendar data containing existing events
   * @param candidate the prospective event to validate
   * @throws IllegalArgumentException if an overlap is detected
   */
  private static void ensureNoOverlap(final CalendarData data, final CalendarEvent candidate) {
    for (CalendarEvent ev : data.events) {
      boolean overlap = !candidate.end().isBefore(ev.start())
          && !candidate.start().isAfter(ev.end());
      if (overlap) {
        throw new IllegalArgumentException("conflict with: " + ev);
      }
    }
  }

  private static String csv(final String s) {
    if (s == null) {
      return "";
    }
    boolean needsQuotes =
        s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
    String escaped = s.replace("\"", "\"\"");
    return needsQuotes ? "\"" + escaped + "\"" : escaped;
  }
}
