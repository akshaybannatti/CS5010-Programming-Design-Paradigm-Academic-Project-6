package calendar.model.entity;

import java.time.DayOfWeek;

/**
 * Enumeration representing days of the week using the common single-letter.
 * abbreviations:
 * <ul>
 *   <li>M – Monday</li>
 *   <li>T – Tuesday</li>
 *   <li>W – Wednesday</li>
 *   <li>R – Thursday</li>
 *   <li>F – Friday</li>
 *   <li>S – Saturday</li>
 *   <li>U – Sunday</li>
 * </ul>
 *
 * <p>These classes do not require interfaces because they represent fixed-value data types or
 * concrete view helpers with no alternative implementations, so introducing interfaces would
 * add unnecessary indirection without improving flexibility or testability.</p>
 *
 * <p>This custom representation is used for compact recurrence patterns
 * and aligns with standard academic calendar notation.</p>
 */
public enum Weekday {
  M, T, W, R, F, S, U;

  /**
   * Converts a {@link java.time.DayOfWeek} value from the Java time API
   * into its corresponding {@code Weekday} single-letter abbreviation.
   *
   * <p>If the supplied {@code DayOfWeek} is {@code null}, this method
   * throws an {@link IllegalArgumentException}.</p>
   *
   * @param dow the {@code DayOfWeek} constant to convert
   * @return the corresponding {@code Weekday} letter representation
   * @throws IllegalArgumentException if {@code dow} is {@code null}
   */
  @SuppressWarnings("java:S1301")
  public static Weekday fromJava(java.time.DayOfWeek dow) {
    if (dow == null) {
      throw new IllegalArgumentException("Unexpected DayOfWeek: null");
    }

    if (dow == DayOfWeek.MONDAY) {
      return M;
    } else if (dow == DayOfWeek.TUESDAY) {
      return T;
    } else if (dow == DayOfWeek.WEDNESDAY) {
      return W;
    } else if (dow == DayOfWeek.THURSDAY) {
      return R;
    } else if (dow == DayOfWeek.FRIDAY) {
      return F;
    } else if (dow == DayOfWeek.SATURDAY) {
      return S;
    } else {
      return U;
    }
  }
}
