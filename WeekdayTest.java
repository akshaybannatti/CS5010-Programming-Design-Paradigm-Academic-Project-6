import static org.junit.Assert.assertEquals;

import calendar.model.entity.Weekday;
import java.lang.reflect.Constructor;
import java.time.DayOfWeek;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link Weekday}.
 */
public class WeekdayTest {

  @Test
  public void fromJavaMapsAllDaysCorrectly() {
    assertEquals(Weekday.M, Weekday.fromJava(DayOfWeek.MONDAY));
    assertEquals(Weekday.T, Weekday.fromJava(DayOfWeek.TUESDAY));
    assertEquals(Weekday.W, Weekday.fromJava(DayOfWeek.WEDNESDAY));
    assertEquals(Weekday.R, Weekday.fromJava(DayOfWeek.THURSDAY));
    assertEquals(Weekday.F, Weekday.fromJava(DayOfWeek.FRIDAY));
    assertEquals(Weekday.S, Weekday.fromJava(DayOfWeek.SATURDAY));
    assertEquals(Weekday.U, Weekday.fromJava(DayOfWeek.SUNDAY));
  }

  @Test(expected = IllegalArgumentException.class)
  public void fromJavaNullThrowsIllegalArgumentException() {
    Weekday.fromJava(null);
  }

  @Test
  public void testFromJavaAllRealDaysMapped() {
    for (DayOfWeek dow : DayOfWeek.values()) {
      Weekday w = Weekday.fromJava(dow);
      Assert.assertNotNull("Mapping should never return null for " + dow, w);
    }
  }
}
