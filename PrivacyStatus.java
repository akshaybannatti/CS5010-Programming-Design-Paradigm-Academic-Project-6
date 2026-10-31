package calendar.model.entity;

/**
 * Represents the visibility level of a calendar event.
 * These classes do not require interfaces because they represent fixed-value data types or
 * concrete view helpers with no alternative implementations, so introducing interfaces would
 * add unnecessary indirection without improving flexibility or testability.
 *
 * <p>An event may be either:
 * <ul>
 *   <li>{@link #PUBLIC} — visible to all viewers</li>
 *   <li>{@link #PRIVATE} — restricted visibility</li>
 * </ul>
 *
 * <p>This enum provides a utility method for parsing string representations
 * into a corresponding {@code PrivacyStatus} value.</p>
 */
public enum PrivacyStatus {
  /** Event is publicly visible. */
  PUBLIC,
  /** Event has restricted visibility. */
  PRIVATE;

  /**
   * Parses a string into a {@code PrivacyStatus}.
   *
   * <p>The comparison is case-insensitive. Any value equal to
   * {@code "private"} (ignoring case) returns {@link #PRIVATE}.
   * All other inputs—including {@code null}, empty strings, or
   * unrecognized words—default to {@link #PUBLIC}.</p>
   *
   * @param s the string to parse
   * @return the corresponding {@code PrivacyStatus}, defaulting to {@link #PUBLIC}
   */
  public static PrivacyStatus fromString(String s) {
    return "private".equalsIgnoreCase(s) ? PRIVATE : PUBLIC;
  }
}
