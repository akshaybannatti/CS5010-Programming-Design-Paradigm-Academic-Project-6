import static org.junit.Assert.assertEquals;

import calendar.model.entity.PrivacyStatus;
import org.junit.Test;

/**
 * Unit tests for {@link PrivacyStatus}.
 */
public class PrivacyStatusTest {

  @Test
  public void fromStringRecognizesPrivateCaseInsensitive() {
    assertEquals(PrivacyStatus.PRIVATE, PrivacyStatus.fromString("private"));
    assertEquals(PrivacyStatus.PRIVATE, PrivacyStatus.fromString("PRIVATE"));
    assertEquals(PrivacyStatus.PRIVATE, PrivacyStatus.fromString("PrIvAtE"));
  }

  @Test
  public void fromStringReturnsPublicForNullOrNonPrivate() {
    assertEquals(PrivacyStatus.PUBLIC, PrivacyStatus.fromString(null));
    assertEquals(PrivacyStatus.PUBLIC, PrivacyStatus.fromString("public"));
    assertEquals(PrivacyStatus.PUBLIC, PrivacyStatus.fromString(""));
    assertEquals(PrivacyStatus.PUBLIC, PrivacyStatus.fromString("abc"));
  }

  @Test
  public void enumValuesAreCorrect() {
    assertEquals("PUBLIC", PrivacyStatus.PUBLIC.name());
    assertEquals("PRIVATE", PrivacyStatus.PRIVATE.name());
  }
}
