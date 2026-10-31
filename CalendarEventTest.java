import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import calendar.model.entity.CalendarEvent;
import calendar.model.entity.PrivacyStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link CalendarEvent} covering equals/hashCode, shiftToStart, toZoned, and privacy().
 *
 * <p>Notes:
 * - Uses the 8-arg constructor (with explicit privacy) and the 7-arg constructor (default privacy).
 * - Avoids nulls; constructor enforces non-null via Objects.requireNonNull.
 */
public class CalendarEventTest {

  private static CalendarEvent ev(
      String name,
      String desc,
      String loc,
      LocalDateTime start,
      LocalDateTime end,
      Optional<UUID> seriesId,
      PrivacyStatus privacy) {
    return new CalendarEvent(
        name,
        desc,
        loc,
        start,
        end,
        Optional.empty(),
        seriesId,
        privacy);
  }

  private static CalendarEvent evDefaultPrivacy(
      String name,
      String desc,
      String loc,
      LocalDateTime start,
      LocalDateTime end,
      Optional<UUID> seriesId) {
    return new CalendarEvent(
        name,
        desc,
        loc,
        start,
        end,
        Optional.empty(),
        seriesId);
  }

  @Test
  public void equalsAndHashCode_identicalObjects_areEqualAndHashMatch() {
    LocalDateTime s = LocalDateTime.of(2025, 1, 10, 9, 0);
    LocalDateTime e = LocalDateTime.of(2025, 1, 10, 10, 30);
    Optional<UUID> sid = Optional.of(UUID.randomUUID());

    CalendarEvent a = ev("Sprint", "demo", "Zoom", s, e, sid, PrivacyStatus.PUBLIC);
    CalendarEvent b = ev("Sprint", "demo", "Zoom", s, e, sid, PrivacyStatus.PUBLIC);

    assertEquals(a, b);
    assertEquals(b, a);
    assertEquals(a.hashCode(), b.hashCode());
    assertEquals(a, a);
  }

  @Test
  public void equals_differentField_breaksEquality() {
    LocalDateTime s = LocalDateTime.of(2025, 1, 10, 9, 0);
    LocalDateTime e = LocalDateTime.of(2025, 1, 10, 10, 0);
    Optional<UUID> sid = Optional.of(UUID.randomUUID());

    CalendarEvent base = new CalendarEvent(
        "A", "d", "L", s, e, Optional.empty(), sid,
        PrivacyStatus.PUBLIC);

    CalendarEvent diffName = new CalendarEvent(
        "B", "d", "L", s, e, Optional.empty(), sid,
        PrivacyStatus.PUBLIC);
    assertNotEquals(base, diffName);

    CalendarEvent diffStart = new CalendarEvent(
        "A", "d", "L", s.plusMinutes(1), e, Optional.empty(), sid,
        PrivacyStatus.PUBLIC);
    assertNotEquals(base, diffStart);

    CalendarEvent same = new CalendarEvent(
        "A", "d", "L", s, e, Optional.empty(), sid,
        PrivacyStatus.PUBLIC);
    assertEquals(base, same);
  }


  @Test
  public void hashCode_changesWhenSignificantFieldChanges() {
    LocalDateTime s = LocalDateTime.of(2025, 1, 10, 9, 0);
    LocalDateTime e = LocalDateTime.of(2025, 1, 10, 10, 0);

    CalendarEvent a = ev("X", "d", "L", s, e, Optional.empty(), PrivacyStatus.PUBLIC);
    CalendarEvent b = ev("Y", "d", "L", s, e, Optional.empty(), PrivacyStatus.PUBLIC);

    assertNotEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void shiftToStart_movesStartAndPreservesDurationAndCoreFields() {
    LocalDateTime s = LocalDateTime.of(2025, 1, 10, 9, 15);
    LocalDateTime e = LocalDateTime.of(2025, 1, 10, 10, 45);
    Optional<UUID> sid = Optional.of(UUID.randomUUID());

    CalendarEvent original = new CalendarEvent(
        "Meet", "desc", "Room 1", s, e, Optional.empty(), sid,
        PrivacyStatus.PRIVATE);

    LocalDateTime newStart = LocalDateTime.of(2025, 1, 11, 8, 0);
    CalendarEvent shifted = original.shiftToStart(newStart);

    assertEquals(newStart, shifted.start());
    assertEquals(newStart.plusMinutes(90), shifted.end());

    assertEquals(original.name(), shifted.name());
    assertEquals(original.description(), shifted.description());
    assertEquals(original.location(), shifted.location());
    assertEquals(original.seriesId(), shifted.seriesId());

    assertEquals(PrivacyStatus.PUBLIC, shifted.privacy());
  }


  @Test
  public void toZoned_returnsStartAndEndAsZonedDateTimes() {
    LocalDateTime s = LocalDateTime.of(2025, 3, 1, 14, 0);
    LocalDateTime e = LocalDateTime.of(2025, 3, 1, 15, 30);
    CalendarEvent ev = ev("Z", "d", "L", s, e, Optional.empty(),
        PrivacyStatus.PUBLIC);

    ZoneId ny = ZoneId.of("America/New_York");
    ZonedDateTime[] arr = ev.toZoned(ny);

    assertNotNull(arr);
    assertEquals(2, arr.length);
    assertEquals(s.atZone(ny), arr[0]);
    assertEquals(e.atZone(ny), arr[1]);
  }

  @Test
  public void privacy_defaultConstructorIsPublic_andExplicitPrivateIsPrivate() {
    LocalDateTime s = LocalDateTime.of(2025, 2, 1, 9, 0);
    LocalDateTime e = LocalDateTime.of(2025, 2, 1, 10, 0);

    CalendarEvent def = evDefaultPrivacy("D", "d", "L", s, e, Optional.empty());
    assertEquals("Default privacy should be PUBLIC", PrivacyStatus.PUBLIC, def.privacy());

    CalendarEvent priv = ev("D", "d", "L", s, e, Optional.empty(), PrivacyStatus.PRIVATE);
    assertEquals(PrivacyStatus.PRIVATE, priv.privacy());
  }

  @Test
  public void equals_transitiveAndConsistent() {
    LocalDateTime s = LocalDateTime.of(2025, 4, 5, 10, 0);
    LocalDateTime e = LocalDateTime.of(2025, 4, 5, 11, 0);
    Optional<UUID> sid = Optional.of(UUID.randomUUID());

    CalendarEvent a = ev("N", "d", "L", s, e, sid, PrivacyStatus.PUBLIC);
    CalendarEvent b = ev("N", "d", "L", s, e, sid, PrivacyStatus.PUBLIC);
    CalendarEvent c = ev("N", "d", "L", s, e, sid, PrivacyStatus.PUBLIC);

    assertEquals(a, b);
    assertEquals(b, c);
    assertEquals(a, c);

    assertEquals(a, b);
    assertEquals(a, b);
  }

  @Test
  public void equals_nonCalendarEvent_returnsFalse() {
    LocalDateTime s = LocalDateTime.of(2025, 1, 10, 9, 0);
    LocalDateTime e = LocalDateTime.of(2025, 1, 10, 10, 0);
    CalendarEvent ev = new CalendarEvent("Task", "desc", "loc", s, e,
        Optional.empty(), Optional.empty(), PrivacyStatus.PUBLIC);

    assertFalse(ev.equals("not an event"));
  }

  @Test
  public void equals_sameNameStart_differentOtherFields_returnsFalse() {
    LocalDateTime s = LocalDateTime.of(2025, 1, 10, 9, 0);
    LocalDateTime e1 = LocalDateTime.of(2025, 1, 10, 10, 0);
    LocalDateTime e2 = LocalDateTime.of(2025, 1, 10, 11, 0);

    CalendarEvent base = new CalendarEvent("Meeting", "desc", "loc", s, e1,
        Optional.empty(), Optional.empty(), PrivacyStatus.PUBLIC);

    CalendarEvent diffEnd = new CalendarEvent("Meeting", "desc", "loc", s, e2,
        Optional.empty(), Optional.empty(), PrivacyStatus.PUBLIC);
    CalendarEvent diffDesc = new CalendarEvent("Meeting", "changed", "loc", s, e1,
        Optional.empty(), Optional.empty(), PrivacyStatus.PUBLIC);
    CalendarEvent diffLoc = new CalendarEvent("Meeting", "desc", "Room B", s, e1,
        Optional.empty(), Optional.empty(), PrivacyStatus.PUBLIC);

    assertFalse(base.equals(diffEnd));
    assertFalse(base.equals(diffDesc));
    assertFalse(base.equals(diffLoc));
  }

  @Test
  public void equals_seriesId_equalAndDifferent() {
    LocalDateTime s = LocalDateTime.of(2025, 1, 10, 9, 0);
    LocalDateTime e = LocalDateTime.of(2025, 1, 10, 10, 0);
    UUID id = UUID.randomUUID();
    UUID other = UUID.randomUUID();

    CalendarEvent a = new CalendarEvent("A", "d", "loc", s, e,
        Optional.empty(), Optional.of(id));
    CalendarEvent b = new CalendarEvent("A", "d", "loc", s, e,
        Optional.empty(), Optional.of(id));
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    CalendarEvent c = new CalendarEvent("A", "d", "loc", s, e,
        Optional.empty(), Optional.of(other));
    assertNotEquals(a, c);
  }

  @Test
  public void equals_recurrence_bothEmptyEvaluatesTruePath() {
    LocalDateTime s = LocalDateTime.of(2025, 1, 10, 9, 0);
    LocalDateTime e = LocalDateTime.of(2025, 1, 10, 10, 0);

    CalendarEvent a = new CalendarEvent("A", "d", "loc", s, e,
        Optional.empty(), Optional.empty());
    CalendarEvent b = new CalendarEvent("A", "d", "loc", s, e,
        Optional.empty(), Optional.empty());
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void equals_recurrence_emptyVsNonEmpty_breaksEquality() {
    LocalDateTime s = LocalDateTime.of(2025, 1, 10, 9, 0);
    LocalDateTime e = LocalDateTime.of(2025, 1, 10, 10, 0);

    CalendarEvent a = new CalendarEvent("A", "d", "loc", s, e,
        Optional.empty(), Optional.empty());

    CalendarEvent.RecurrenceRule rule =
        new CalendarEvent.RecurrenceRule(
            CalendarEvent.RecurrenceRule.Freq.DAILY,
            1,
            java.util.Optional.<java.time.LocalDate>empty());

    CalendarEvent b = new CalendarEvent("A", "d", "loc", s, e,
        Optional.of(rule), Optional.empty());

    assertNotEquals(a, b);
  }

  @Test
  public void testRecurrenceRuleInvalidIntervalThrows() {
    try {
      new CalendarEvent.RecurrenceRule(
          CalendarEvent.RecurrenceRule.Freq.DAILY,
          0,
          Optional.empty()
      );
      Assert.fail("Expected IllegalArgumentException for interval < 1");
    } catch (IllegalArgumentException e) {
      Assert.assertTrue(e.getMessage().contains("interval must be >= 1"));
    }
  }

  @Test
  public void testRecurrenceRuleGettersAndEventGettersAndToString() {
    LocalDate untilDate = LocalDate.of(2024, 11, 30);
    CalendarEvent.RecurrenceRule rule = new CalendarEvent.RecurrenceRule(
        CalendarEvent.RecurrenceRule.Freq.WEEKLY,
        2,
        Optional.of(untilDate)
    );

    Assert.assertEquals(CalendarEvent.RecurrenceRule.Freq.WEEKLY, rule.freq());
    Assert.assertEquals(2, rule.interval());
    Assert.assertEquals(Optional.of(untilDate), rule.until());

    LocalDateTime start = LocalDateTime.of(2024, 11, 15, 9, 0);
    LocalDateTime end   = LocalDateTime.of(2024, 11, 15, 17, 0);
    UUID seriesId = UUID.randomUUID();

    CalendarEvent ev = new CalendarEvent(
        "Meeting",
        "Discuss design",
        "Room 101",
        start,
        end,
        Optional.of(rule),
        Optional.of(seriesId)
    );

    Assert.assertTrue(ev.recurrence().isPresent());
    Assert.assertSame(rule, ev.recurrence().get());

    Assert.assertTrue(ev.seriesId().isPresent());
    Assert.assertEquals(seriesId, ev.seriesId().get());

    String repr = ev.toString();
    Assert.assertTrue("toString should contain name", repr.contains("Meeting"));
    Assert.assertTrue("toString should contain start", repr.contains(start.toString()));
    Assert.assertTrue("toString should contain end", repr.contains(end.toString()));
  }
}
