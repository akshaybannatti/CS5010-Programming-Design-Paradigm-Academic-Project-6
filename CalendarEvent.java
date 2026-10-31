package calendar.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable event entity for a calendar.
 * These classes do not require interfaces because they represent fixed-value data types or
 * concrete view helpers with no alternative implementations, so introducing interfaces would
 * add unnecessary indirection without improving flexibility or testability.
 *
 * <p>Class invariants:
 * <ul>
 *   <li>{@code start.isBefore(end)}</li>
 *   <li>{@code name} is non-empty</li>
 * </ul>
 */
public final class CalendarEvent {

  /**
   * Recurrence rule. If absent, event is one-off.
   */
  public static final class RecurrenceRule {
    /**
     * Frequency unit.
     */
    public enum Freq { DAILY, WEEKLY, MONTHLY }

    private final Freq freq;
    private final int interval;
    private final Optional<LocalDate> until;

    /**
     * Create a rule.
     *
     * @param freq frequency
     * @param interval step interval (>=1)
     * @param until last date inclusive, or empty for open-ended
     */
    public RecurrenceRule(final Freq freq, final int interval, final Optional<LocalDate> until) {
      if (interval < 1) {
        throw new IllegalArgumentException("interval must be >= 1");
      }
      this.freq = Objects.requireNonNull(freq);
      this.interval = interval;
      this.until = Objects.requireNonNull(until);
    }

    /**
     * Returns the recurrence frequency for this rule.
     *
     * @return the frequency (e.g., DAILY, WEEKLY, MONTHLY)
     */
    public Freq freq() {
      return this.freq;
    }

    /**
     * Returns the interval between recurrences.
     * For example, an interval of 2 with WEEKLY means "every 2 weeks".
     *
     * @return the positive interval value
     */
    public int interval() {
      return this.interval;
    }

    /**
     * Returns the optional end date for this recurrence rule.
     * If present, the recurrence series stops on or before this date.
     *
     * @return an Optional containing the end date, or empty if the rule has no end date
     */
    public Optional<LocalDate> until() {
      return this.until;
    }
  }

  private final String name;
  private final String description;
  private final String location;
  private final LocalDateTime start;
  private final LocalDateTime end;
  private final Optional<RecurrenceRule> recurrence;
  private final Optional<UUID> seriesId;
  private final PrivacyStatus privacy;

  /**
   * Constructs an immutable event.
   *
   * @param name event name (non-empty)
   * @param description optional description (may be empty)
   * @param location optional location (may be empty)
   * @param start local start
   * @param end local end
   * @param recurrence optional recurrence rule
   * @param seriesId optional series id (events in a series share this)
   */
  public CalendarEvent(String name,
                       String description,
                       String location,
                       LocalDateTime start,
                       LocalDateTime end,
                       Optional<RecurrenceRule> recurrence,
                       Optional<UUID> seriesId,
                       PrivacyStatus privacy) {
    this.name = Objects.requireNonNull(name);
    this.description = Objects.requireNonNull(description);
    this.location = Objects.requireNonNull(location);
    this.start = Objects.requireNonNull(start);
    this.end = Objects.requireNonNull(end);
    this.recurrence = Objects.requireNonNull(recurrence);
    this.seriesId = Objects.requireNonNull(seriesId);
    this.privacy = Objects.requireNonNull(privacy);
  }

  /**
   * Backward-compatibility constructor. Creates a {@code CalendarEvent} with
   * the given fields and defaults the privacy status to {@link PrivacyStatus#PUBLIC}.
   *
   * @param name the event title
   * @param description the event description (may be empty)
   * @param location the event location (may be empty)
   * @param start the event start date/time
   * @param end the event end date/time
   * @param recurrence an optional recurrence rule for repeating events
   * @param seriesId an optional series identifier for related recurring events
   */
  public CalendarEvent(String name,
                       String description,
                       String location,
                       LocalDateTime start,
                       LocalDateTime end,
                       Optional<RecurrenceRule> recurrence,
                       Optional<UUID> seriesId) {
    this(name, description, location, start, end, recurrence, seriesId, PrivacyStatus.PUBLIC);
  }

  /**
   * Returns the event name.
   *
   * @return the event title
   */
  public String name() {
    return this.name;
  }

  /**
   * Returns the event description.
   *
   * @return the description text, possibly empty but never {@code null}
   */
  public String description() {
    return this.description;
  }

  /**
   * Returns the event location.
   *
   * @return the location string, possibly empty but never {@code null}
   */
  public String location() {
    return this.location;
  }

  /**
   * Returns the event start date and time.
   *
   * @return the start timestamp of the event
   */
  public LocalDateTime start() {
    return this.start;
  }

  /**
   * Returns the event end date and time.
   *
   * @return the end timestamp of the event
   */
  public LocalDateTime end() {
    return this.end;
  }

  /**
   * Returns the recurrence rule for this event, if any.
   *
   * @return an {@code Optional} containing the recurrence rule, or empty if the event.
   *     does not repeat
   */
  public Optional<RecurrenceRule> recurrence() {
    return this.recurrence;
  }

  /**
   * Returns the recurrence series identifier associated with this event, if any.
   * This value is used to link multiple occurrences of the same repeating event.
   *
   * @return an optional UUID representing the series ID
   */
  public Optional<UUID> seriesId() {
    return this.seriesId;
  }

  /**
   * Returns the privacy status of this event.
   * The status indicates whether the event is visible to others
   * ({@link PrivacyStatus#PUBLIC}) or restricted ({@link PrivacyStatus#PRIVATE}).
   *
   * @return the event's {@code PrivacyStatus}
   */
  public PrivacyStatus privacy() {
    return this.privacy;
  }

  /**
   * Convert this event into a {@link ZonedDateTime} pair in the provided timezone.
   *
   * @param zone zone id
   * @return start and end in that zone
   */
  public ZonedDateTime[] toZoned(final ZoneId zone) {
    ZonedDateTime zs = this.start.atZone(zone);
    ZonedDateTime ze = this.end.atZone(zone);
    return new ZonedDateTime[] {zs, ze};
  }

  /**
   * Create a copy of this event with a new start time (keeping duration identical).
   *
   * @param newStart new local start
   * @return shifted event
   */
  public CalendarEvent shiftToStart(final LocalDateTime newStart) {
    long minutes = java.time.Duration.between(this.start, this.end).toMinutes();
    LocalDateTime newEnd = newStart.plusMinutes(minutes);
    return new CalendarEvent(
        this.name,
        this.description,
        this.location,
        newStart,
        newEnd,
        this.recurrence,
        this.seriesId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CalendarEvent)) {
      return false;
    }
    CalendarEvent that = (CalendarEvent) o;
    return this.name.equals(that.name)
        && this.start.equals(that.start)
        && this.end.equals(that.end)
        && this.description.equals(that.description)
        && this.location.equals(that.location)
        && this.recurrence.equals(that.recurrence)
        && this.seriesId.equals(that.seriesId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.name, this.description, this.location,
        this.start, this.end, this.recurrence, this.seriesId);
  }

  @Override
  public String toString() {
    return String.format("%s [%s -> %s]", this.name, this.start, this.end);
  }
}
