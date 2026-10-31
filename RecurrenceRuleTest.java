import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import calendar.model.entity.RecurrenceRule;
import calendar.model.entity.Weekday;
import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link RecurrenceRule}.
 */
public class RecurrenceRuleTest {

  /**
   * Helper that constructs a RecurrenceRule using reflection so it works
   * regardless of the constructor's visibility (public / package-private).
   */
  @SuppressWarnings("unchecked")
  private RecurrenceRule newRule(EnumSet<Weekday> days,
                                 LocalTime seriesStartTime,
                                 boolean allDay) throws Exception {
    for (Constructor<?> ctor : RecurrenceRule.class.getDeclaredConstructors()) {
      Class<?>[] params = ctor.getParameterTypes();
      if (params.length == 3
          && EnumSet.class.isAssignableFrom(params[0])
          && LocalTime.class.equals(params[1])
          && (params[2] == boolean.class || params[2] == Boolean.class)) {
        ctor.setAccessible(true);
        return (RecurrenceRule) ctor.newInstance(days, seriesStartTime, allDay);
      }
    }
    throw new IllegalStateException("Expected RecurrenceRule(EnumSet<Weekday>, LocalTime, "
        + "boolean) constructor");
  }

  @Test
  public void matchesDateTimeTrueWhenDayAndTimeMatch() throws Exception {
    EnumSet<Weekday> days = EnumSet.of(Weekday.M, Weekday.W);
    LocalTime time = LocalTime.of(9, 0);
    RecurrenceRule rule = newRule(days, time, false);

    LocalDateTime matching = LocalDateTime.of(2025, 1, 6, 9, 0);
    assertTrue("Expected match for same weekday and time", rule.matches(matching));
  }

  @Test
  public void matchesDateTimeFalseWhenTimeDiffers() throws Exception {
    EnumSet<Weekday> days = EnumSet.of(Weekday.M, Weekday.W);
    LocalTime time = LocalTime.of(9, 0);
    RecurrenceRule rule = newRule(days, time, false);

    LocalDateTime differentTime = LocalDateTime.of(2025, 1, 6, 10, 0);
    assertFalse("Different time should not match", rule.matches(differentTime));
  }

  @Test
  public void matchesDateTimeFalseWhenWeekdayNotIncluded() throws Exception {
    EnumSet<Weekday> days = EnumSet.of(Weekday.M, Weekday.W);
    LocalTime time = LocalTime.of(9, 0);
    RecurrenceRule rule = newRule(days, time, false);

    LocalDateTime differentDay = LocalDateTime.of(2025, 1, 7, 9, 0);
    assertFalse("Different weekday should not match", rule.matches(differentDay));
  }

  @Test
  public void matchesDateTrueWhenWeekdayIncluded() throws Exception {
    EnumSet<Weekday> days = EnumSet.of(Weekday.T, Weekday.R);
    LocalTime time = LocalTime.of(12, 30);
    RecurrenceRule rule = newRule(days, time, false);

    LocalDate includedDay = LocalDate.of(2025, 1, 7);
    assertTrue("Included weekday should match", rule.matches(includedDay));
  }

  @Test
  public void matchesDateFalseWhenWeekdayNotIncluded() throws Exception {
    EnumSet<Weekday> days = EnumSet.of(Weekday.T, Weekday.R);
    LocalTime time = LocalTime.of(12, 30);
    RecurrenceRule rule = newRule(days, time, false);

    LocalDate notIncluded = LocalDate.of(2025, 1, 5);
    assertFalse("Non-included weekday should not match", rule.matches(notIncluded));
  }

  @Test
  public void isAllDayReflectsFlag() throws Exception {
    EnumSet<Weekday> days = EnumSet.of(Weekday.M);
    LocalTime time = LocalTime.MIDNIGHT;

    RecurrenceRule allDayRule = newRule(days, time, true);
    RecurrenceRule nonAllDayRule = newRule(days, time, false);

    assertTrue("allDayRule should report allDay", allDayRule.isAllDay());
    assertFalse("nonAllDayRule should not report allDay", nonAllDayRule.isAllDay());
  }

  @Test
  public void constructor_nullDays_throwsException() {
    try {
      new RecurrenceRule(null, java.time.LocalTime.NOON, false);
      Assert.fail("Expected IllegalArgumentException for null days");
    } catch (IllegalArgumentException e) {
      Assert.assertEquals(
          "Recurrence weekdays must be non-empty.",
          e.getMessage()
      );
    }
  }

  @Test
  public void constructor_emptyDays_throwsException() {
    java.util.EnumSet<Weekday> empty =
        java.util.EnumSet.noneOf(Weekday.class);

    try {
      new RecurrenceRule(empty, java.time.LocalTime.NOON, false);
      Assert.fail("Expected IllegalArgumentException for empty days");
    } catch (IllegalArgumentException e) {
      Assert.assertEquals(
          "Recurrence weekdays must be non-empty.",
          e.getMessage()
      );
    }
  }

  @Test
  public void days_returnsDefensiveCopyWithSameContent() {
    EnumSet<Weekday> baseDays = EnumSet.allOf(Weekday.class);
    LocalTime startTime = LocalTime.of(9, 0);

    RecurrenceRule rule = new RecurrenceRule(baseDays, startTime, true);

    EnumSet<Weekday> returned = rule.days();
    Assert.assertEquals(baseDays, returned);

    Weekday someDay = Weekday.values()[0];
    returned.remove(someDay);

    EnumSet<Weekday> afterMutation = rule.days();
    Assert.assertEquals("Internal days set must not be affected by external mutation",
        baseDays, afterMutation);
  }

  @Test
  public void startTime_returnsConfiguredSeriesStartTime() {
    EnumSet<Weekday> baseDays = EnumSet.allOf(Weekday.class);
    LocalTime startTime = LocalTime.of(14, 30);

    RecurrenceRule rule = new RecurrenceRule(baseDays, startTime, false);

    Assert.assertEquals(startTime, rule.seriesStartTime());
  }
}
