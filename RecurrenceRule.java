package calendar.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;

/**
 * Represents a recurrence rule for an event series. A recurrence rule specifies:.
 * <ul>
 *   <li>The set of weekdays on which the event repeats</li>
 *   <li>A uniform start time shared by all occurrences</li>
 *   <li>Whether the series is all-day</li>
 * </ul>
 *
 * <p>These classes do not require interfaces because they represent fixed-value data types or
 * concrete view helpers with no alternative implementations, so introducing interfaces would
 * add unnecessary indirection without improving flexibility or testability.</p>
 *
 * <p>This class is immutable. All collections returned are defensive copies.</p>
 */
public final class RecurrenceRule {
  /** Weekdays included in this recurrence. */
  private final EnumSet<Weekday> days;
  /** The uniform start time for each occurrence in the series. */
  private final LocalTime seriesStartTime;
  /** True if the series represents all-day events. */
  private final boolean allDay;

  /**
   * Creates a recurrence rule with the given set of weekdays, series start time,
   * and all-day flag.
   *
   * <p>The {@code days} set must be non-null and contain at least one weekday.</p>
   *
   * @param days the set of weekdays on which the event recurs
   * @param seriesStartTime the uniform start time for each occurrence
   * @param allDay whether this recurrence represents all-day occurrences
   * @throws IllegalArgumentException if {@code days} is null or empty
   */
  public RecurrenceRule(EnumSet<Weekday> days, LocalTime seriesStartTime, boolean allDay) {
    if (days == null || days.isEmpty()) {
      throw new IllegalArgumentException("Recurrence weekdays must be non-empty.");
    }
    this.days = EnumSet.copyOf(days);
    this.seriesStartTime = seriesStartTime;
    this.allDay = allDay;
  }

  /**
   * Returns the set of weekdays on which this recurrence rule applies.
   *
   * <p>The returned set is a defensive copy and can be modified by callers
   * without affecting the internal representation.</p>
   *
   * @return a copy of the weekdays included in this recurrence
   */
  public EnumSet<Weekday> days() {
    return EnumSet.copyOf(days);
  }

  /**
   * Returns the uniform start time-shared by all occurrences in this series.
   *
   * @return the series start time
   */
  public LocalTime seriesStartTime() {
    return seriesStartTime;
  }

  /**
   * Returns whether this recurrence rule represents all-day events.
   *
   * @return {@code true} if all-day, {@code false} otherwise
   */
  public boolean isAllDay() {
    return allDay;
  }

  /**
   * Determines whether the given {@link LocalDateTime} matches the recurrence rule.
   *
   * <p>A datetime matches if:
   * <ul>
   *   <li>Its weekday is included in this rule’s set of weekdays</li>
   *   <li>Its <em>local time</em> equals the recurrence’s series start time</li>
   * </ul>
   * </p>
   *
   * @param dt the datetime to test
   * @return true if the datetime matches this rule, false otherwise
   */
  public boolean matches(LocalDateTime dt) {
    return days.contains(Weekday.fromJava(dt.getDayOfWeek()))
        && dt.toLocalTime().equals(seriesStartTime);
  }

  /**
   * Determines whether the given {@link LocalDate} matches this recurrence rule
   * based solely on weekday.
   *
   * <p>This is typically used for all-day recurrences or when the time component
   * is not relevant.</p>
   *
   * @param date the date to test
   * @return true if the date’s weekday is included in this rule, false otherwise
   */
  public boolean matches(LocalDate date) {
    return days.contains(Weekday.fromJava(date.getDayOfWeek()));
  }
}
