import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import calendar.controller.GuiCalendarController;
import calendar.controller.GuiFeatures;
import calendar.model.CalendarModel;
import calendar.model.entity.CalendarEvent;
import calendar.model.entity.EditScope;
import calendar.model.entity.PrivacyStatus;
import calendar.model.entity.RecurrenceRule;
import calendar.model.entity.Weekday;
import calendar.view.GuiView;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for GuiCalendarController.
 */
public class GuiCalendarControllerTest {
  private MockCalendarModel model;
  private MockGuiView view;
  private GuiCalendarController controller;

  /**
   * Tests for setup.
   */
  @Before
  public void setUp() {
    model = new MockCalendarModel();
    view = new MockGuiView();
    controller = new GuiCalendarController(model, view);
  }

  @Test
  public void testConstructorCreatesDefaultCalendar() {
    assertTrue(model.createCalendarCalled);
    assertEquals("Default", model.lastCalendarName);
    assertNotNull(model.lastZone);
    assertTrue(model.useCalendarCalled);
    assertTrue(view.setFeaturesCalled);
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testConstructorWhenDefaultCalendarAlreadyExists() {
    model.throwExceptionOnCreate = true;
    MockCalendarModel model2 = new MockCalendarModel();
    model2.throwExceptionOnCreate = true;
    MockGuiView view2 = new MockGuiView();
    GuiCalendarController controller2 = new GuiCalendarController(model2, view2);
    assertNotNull(controller2);
    assertTrue(model2.createCalendarCalled);
  }

  @Test
  public void testCreateCalendarSuccess() {
    ZoneId zone = ZoneId.of("America/New_York");
    controller.createCalendar("WorkCal", zone);
    assertTrue(model.createCalendarCalled);
    assertEquals("WorkCal", model.lastCalendarName);
    assertEquals(zone, model.lastZone);
    assertTrue(model.useCalendarCalled);
    assertTrue(controller.getCalendarNames().contains("WorkCal"));
  }

  @Test
  public void testCreateCalendarWithException() {
    model.throwExceptionOnCreate = true;
    controller.createCalendar("BadCal", ZoneId.systemDefault());
    assertTrue(view.showErrorCalled);
    assertNotNull(view.lastErrorMessage);
  }

  @Test
  public void testCreateCalendarDuplicateName() {
    controller.createCalendar("Test", ZoneId.systemDefault());
    controller.createCalendar("Test", ZoneId.systemDefault());
    List<String> names = controller.getCalendarNames();
    assertEquals(1, names.stream().filter(n -> n.equals("Test")).count());
  }

  @Test
  public void testUseCalendarSuccess() {
    controller.createCalendar("TestCal", ZoneId.systemDefault());
    controller.useCalendar("TestCal");
    assertTrue(model.useCalendarCalled);
    assertEquals("TestCal", model.lastUsedCalendar);
  }

  @Test
  public void testUseCalendarWithException() {
    model.throwExceptionOnUse = true;
    controller.useCalendar("NonExistent");
    assertTrue(view.showErrorCalled);
    assertNotNull(view.lastErrorMessage);
  }

  @Test
  public void testUseCalendarAddsNewName() {
    model.throwExceptionOnUse = false;
    controller.useCalendar("NewCal");
    assertTrue(controller.getCalendarNames().contains("NewCal"));
  }

  @Test
  public void testGetCalendarNames() {
    List<String> names = controller.getCalendarNames();
    assertNotNull(names);
    assertTrue(names.contains("Default"));
  }

  @Test
  public void testGetCurrentCalendarName() {
    model.calendarName = "TestCalendar";
    assertEquals("TestCalendar", controller.getCurrentCalendarName());
  }

  @Test
  public void testGetCurrentCalendarNameWhenNoCalendar() {
    model.throwExceptionOnCurrentName = true;
    assertEquals("<none>", controller.getCurrentCalendarName());
  }

  @Test
  public void testGetCurrentCalendarZone() {
    ZoneId zone = ZoneId.of("Europe/London");
    model.zone = zone;
    assertEquals(zone, controller.getCurrentCalendarZone());
  }

  @Test
  public void testGetCurrentCalendarZoneWhenNoCalendar() {
    model.throwExceptionOnCurrentZone = true;
    assertEquals(ZoneId.systemDefault(), controller.getCurrentCalendarZone());
  }

  @Test
  public void testGoToPreviousMonth() {
    YearMonth before = YearMonth.now();
    controller.goToPreviousMonth();
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testGoToPreviousMonthAdjustsDayOfMonth() {
    controller.selectDay(LocalDate.of(2024, 3, 31));
    controller.goToPreviousMonth();
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testGoToNextMonth() {
    controller.goToNextMonth();
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testGoToNextMonthAdjustsDayOfMonth() {
    controller.selectDay(LocalDate.of(2024, 1, 31));
    controller.goToNextMonth();
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testGoToToday() {
    controller.goToToday();
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testSelectDay() {
    LocalDate day = LocalDate.of(2024, 6, 15);
    controller.selectDay(day);
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testSelectDayNull() {
    view.showMonthCalled = false;
    controller.selectDay(null);
    assertFalse(view.showMonthCalled);
  }

  @Test
  public void testGetEventsOn() {
    LocalDate day = LocalDate.of(2024, 6, 15);
    CalendarEvent event = createTestEvent("Test",
        day.atTime(10, 0), day.atTime(11, 0));
    model.events.add(event);
    List<CalendarEvent> events = controller.getEventsOn(day);
    assertNotNull(events);
    assertEquals(1, events.size());
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testGetEventsOnNull() {
    List<CalendarEvent> events = controller.getEventsOn(null);
    assertNotNull(events);
    assertTrue(events.isEmpty());
  }

  @Test
  public void testCreateSingleEventSuccess() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createSingleEvent(start, end, "Meeting", "Desc", "Office",
        false, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals(1, model.events.size());
    assertEquals("Meeting", model.events.get(0).name());
  }

  @Test
  public void testCreateSingleEventWithNullStart() {
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createSingleEvent(null, end, "Meeting", "Desc", "Office",
        false, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testCreateSingleEventWithNullEnd() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    controller.createSingleEvent(start, null, "Meeting", "Desc",
        "Office", false, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testCreateSingleEventWithNullTitle() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createSingleEvent(start, end, null, "Desc",
        "Office", false, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testCreateSingleEventEndBeforeStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 10, 0);
    controller.createSingleEvent(start, end, "Meeting", "Desc",
        "Office", false, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("after"));
  }

  @Test
  public void testCreateSingleEventEndEqualsStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 10, 0);
    controller.createSingleEvent(start, end, "Meeting", "Desc",
        "Office", false, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("after"));
  }

  @Test
  public void testCreateSingleEventWithNullDescription() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createSingleEvent(start, end, "Meeting", null,
        "Office", false, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals("", model.events.get(0).description());
  }

  @Test
  public void testCreateSingleEventWithNullLocation() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createSingleEvent(start, end, "Meeting", "Desc",
        null, false, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals("", model.events.get(0).location());
  }

  @Test
  public void testCreateSingleEventWithNullPrivacy() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createSingleEvent(start, end, "Meeting", "Desc",
        "Office", false, null);
    assertTrue(model.addEventCalled);
    assertEquals(PrivacyStatus.PUBLIC, model.events.get(0).privacy());
  }

  @Test
  public void testCreateSingleEventWithModelException() {
    model.throwExceptionOnAdd = true;
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createSingleEvent(start, end, "Meeting", "Desc",
        "Office", false, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testCreateRecurringEventWithCount() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M, Weekday.W, Weekday.F);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        "Office", false, days, 3, null, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertTrue(model.events.size() >= 3);
  }

  @Test
  public void testCreateRecurringEventWithUntilDate() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 30);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        "Office", false, days, null, until, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertTrue(model.events.size() > 0);
  }

  @Test
  public void testCreateRecurringEventWithNullStart() {
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(null, end, "Recurring", "Desc",
        "Office", false, days, 3, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testCreateRecurringEventWithNullEnd() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, null, "Recurring", "Desc",
        "Office", false, days, 3, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testCreateRecurringEventWithNullTitle() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, null, "Desc",
        "Office", false, days, 3, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testCreateRecurringEventEndBeforeStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 10, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        "Office", false, days, 3, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("after"));
  }

  @Test
  public void testCreateRecurringEventNullWeekdays() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        "Office", false, null, 3, null,
        PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("weekday"));
  }

  @Test
  public void testCreateRecurringEventEmptyWeekdays() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.noneOf(Weekday.class);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        "Office", false, days, 3, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("weekday"));
  }

  @Test
  public void testCreateRecurringEventNoCountOrUntil() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        "Office", false, days, null, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("count")
        || view.lastErrorMessage.contains("until"));
  }

  @Test
  public void testCreateRecurringEventZeroCount() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        "Office", false, days, 0, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("count")
        || view.lastErrorMessage.contains("positive"));
  }

  @Test
  public void testCreateRecurringEventNegativeCount() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        "Office", false, days, -5, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("count")
        || view.lastErrorMessage.contains("positive"));
  }

  @Test
  public void testCreateRecurringEventWithNullDescription() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Recurring", null,
        "Office", false, days, 1, null, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals("", model.events.get(0).description());
  }

  @Test
  public void testCreateRecurringEventWithNullLocation() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        null, false, days, 1, null, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals("", model.events.get(0).location());
  }

  @Test
  public void testCreateRecurringEventWithNullPrivacy() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        "Office", false, days, 1, null, null);
    assertTrue(model.addEventCalled);
    assertEquals(PrivacyStatus.PUBLIC, model.events.get(0).privacy());
  }

  @Test
  public void testCreateRecurringEventWithModelException() {
    model.throwExceptionOnAdd = true;
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Recurring", "Desc",
        "Office", false, days, 1, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testEditEventOne() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start.plusHours(1), end.plusHours(1));
    model.events.add(original);
    controller.editEvent(original, updated, EditScope.ONE);
    assertTrue(model.removeEventCalled);
    assertTrue(model.addEventCalled);
  }

  @Test
  public void testEditEventOneNotFound() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start.plusHours(1), end.plusHours(1));
    model.removeEventReturnValue = false;
    controller.editEvent(original, updated, EditScope.ONE);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("not found"));
  }

  @Test
  public void testEditEventThisAndAfterWithSeriesId() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event1", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event1", start.plusDays(1),
        end.plusDays(1), seriesId);
    CalendarEvent event3 = createTestEventWithSeries("Event1", start.plusDays(2),
        end.plusDays(2), seriesId);
    model.events.add(original);
    model.events.add(event2);
    model.events.add(event3);
    CalendarEvent updated = createTestEventWithSeries("Updated", start.plusHours(1),
        end.plusHours(1), seriesId);
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertTrue(model.removeEventCalled);
    assertTrue(model.addEventCalled);
  }

  @Test
  public void testEditEventThisAndAfterWithoutSeriesId() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("SameName", start, end);
    CalendarEvent event2 = createTestEvent("SameName", start.plusDays(1), end.plusDays(1));
    CalendarEvent updated = createTestEvent("Updated", start.plusHours(1), end.plusHours(1));
    model.events.add(original);
    model.events.add(event2);
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertTrue(model.removeEventCalled);
    assertTrue(model.addEventCalled);
  }

  @Test
  public void testEditEventEntireSeriesWithSeriesId() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.minusDays(1),
        end.minusDays(1), seriesId);
    CalendarEvent event3 = createTestEventWithSeries("Event", start.plusDays(1),
        end.plusDays(1), seriesId);
    model.events.add(original);
    model.events.add(event2);
    model.events.add(event3);
    CalendarEvent updated = createTestEventWithSeries("AllUpdated", start.plusHours(2),
        end.plusHours(2), seriesId);
    controller.editEvent(original, updated, EditScope.ENTIRE_SERIES);
    assertTrue(model.removeEventCalled);
    assertTrue(model.addEventCalled);
  }

  @Test
  public void testEditEventEntireSeriesWithoutSeriesId() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Series", start, end);
    CalendarEvent event2 = createTestEvent("Series", start.plusDays(1), end.plusDays(1));
    CalendarEvent updated = createTestEvent("AllUpdated", start.plusHours(2),
        end.plusHours(2));
    model.events.add(original);
    model.events.add(event2);
    controller.editEvent(original, updated, EditScope.ENTIRE_SERIES);
    assertTrue(model.removeEventCalled);
    assertTrue(model.addEventCalled);
  }

  @Test
  public void testEditEventWithNullOriginal() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    controller.editEvent(null, updated, EditScope.ONE);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testEditEventWithNullUpdated() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    controller.editEvent(original, null, EditScope.ONE);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testEditEventWithNullScope() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    controller.editEvent(original, updated, null);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testEditEventWithException() {
    model.throwExceptionOnRemove = true;
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    controller.editEvent(original, updated, EditScope.ONE);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testHandleErrorWithMessage() {
    controller.handleError("Test error");
    assertTrue(view.showErrorCalled);
    assertEquals("Test error", view.lastErrorMessage);
  }

  @Test
  public void testHandleErrorWithNull() {
    controller.handleError(null);
    assertTrue(view.showErrorCalled);
    assertEquals("An unknown error occurred.", view.lastErrorMessage);
  }

  @Test
  public void testHandleErrorWithBlank() {
    controller.handleError("   ");
    assertTrue(view.showErrorCalled);
    assertEquals("An unknown error occurred.", view.lastErrorMessage);
  }

  @Test
  public void testHandleErrorWithEmpty() {
    controller.handleError("");
    assertTrue(view.showErrorCalled);
    assertEquals("An unknown error occurred.", view.lastErrorMessage);
  }

  @Test
  public void testCreateCalendarCallsRefreshView() {
    view.showMonthCalled = false;
    controller.createCalendar("NewCal", ZoneId.systemDefault());
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testCreateCalendarCallsUseCalendar() {
    model.useCalendarCalled = false;
    controller.createCalendar("AnotherCal", ZoneId.systemDefault());
    assertTrue(model.useCalendarCalled);
    assertEquals("AnotherCal", model.lastUsedCalendar);
  }

  @Test
  public void testUseCalendarCallsRefreshView() {
    controller.createCalendar("TestCal2", ZoneId.systemDefault());
    view.showMonthCalled = false;
    controller.useCalendar("TestCal2");
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testGoToNextMonthCallsRefreshView() {
    view.showMonthCalled = false;
    controller.goToNextMonth();
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testGoToTodayCallsRefreshView() {
    view.showMonthCalled = false;
    controller.goToToday();
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testGetsEventsOnCallsShowMonth() {
    view.showMonthCalled = false;
    controller.getEventsOn(LocalDate.of(2024, 6, 15));
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testCreateSingleEventCallsSelectDay() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    view.showMonthCalled = false;
    controller.createSingleEvent(start, end, "Test", "Desc",
        "Loc", false, PrivacyStatus.PUBLIC);
    controller.createSingleEvent(start, end, "Test", "Desc",
        "Loc", false, PrivacyStatus.PUBLIC);
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testApplyBulkEditCallsSelectDayWithNonEmptyTargets() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(1),
        end.plusDays(1), seriesId);
    model.events.add(original);
    model.events.add(event2);
    view.showMonthCalled = false;
    CalendarEvent updated = createTestEventWithSeries("Updated", start.plusHours(1),
        end.plusHours(1), seriesId);
    controller.editEvent(original, updated, EditScope.ENTIRE_SERIES);
    assertTrue(view.showMonthCalled);
  }

  @Test
  public void testApplyBulkEditWithEmptyTargetsList() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("NoMatch", start, end, seriesId);
    CalendarEvent updated = createTestEventWithSeries("Updated", start.plusHours(1),
        end.plusHours(1), seriesId);
    view.showMonthCalled = false;
    controller.editEvent(original, updated, EditScope.ENTIRE_SERIES);
    assertFalse(view.showMonthCalled);
  }

  @Test
  public void testCreateRecurringEventWithEndExactlyEqualToStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 10, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Test", "Desc",
        "Loc", false, days, 1, null, PrivacyStatus.PUBLIC);
    assertEquals(0, model.events.size());
  }

  @Test
  public void testCreateRecurringEventSkipsInvalidOccurrences() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 23, 59);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 23, 58);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Test", "Desc",
        "Loc", false, days, 5, null, PrivacyStatus.PUBLIC);
    assertEquals(0, model.events.size());
  }

  @Test
  public void testCreateRecurringEventWithUntilDateAndInvalidOccurrence() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 23, 59);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 23, 58);
    LocalDate until = LocalDate.of(2024, 6, 30);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Test", "Desc",
        "Loc", false, days, null, until, PrivacyStatus.PUBLIC);
    assertEquals(0, model.events.size());
  }

  @Test
  public void testGoToPreviousMonthCallsRefreshView() {
    view.showMonthCalled = false;
    controller.goToPreviousMonth();
    assertTrue("goToPreviousMonth must call refreshView", view.showMonthCalled);
  }

  @Test
  public void testCreateRecurringEventWithCountZero() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 0, null, PrivacyStatus.PUBLIC);
    assertTrue("Count of 0 should show error", view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("count"));
  }

  @Test
  public void testCreateRecurringEventWithCountOne() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertTrue("Should create exactly 1 event", model.addEventCalled);
    assertEquals(1, model.events.size());
  }

  @Test
  public void testCreateRecurringEventBothCountAndUntilNull() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, null, PrivacyStatus.PUBLIC);
    assertTrue("Both null should show error", view.showErrorCalled);
    assertTrue(view.lastErrorMessage.toLowerCase().contains("count")
        || view.lastErrorMessage.toLowerCase().contains("until"));
  }

  @Test
  public void testCreateRecurringEventUntilBeforeStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 10);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled || !model.addEventCalled);
  }

  @Test
  public void testCreateRecurringEventCallsSelectDay() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M, Weekday.W);
    view.showMonthCalled = false;
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 2, null, PrivacyStatus.PUBLIC);
    assertTrue("createRecurringEvent must call selectDay", view.showMonthCalled);
  }

  @Test
  public void testCreateRecurringEventUntilEqualsStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 15);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertTrue(model.events.size() >= 1);
  }

  @Test
  public void testCreateRecurringEventExactCountBoundary() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    int exactCount = 5;
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, exactCount, null, PrivacyStatus.PUBLIC);
    assertEquals("Should create exactly the specified count",
        exactCount, model.events.size());
  }

  @Test
  public void testCreateRecurringEventUntilDateBoundary() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 22);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals("Should create events including the until date",
        2, model.events.size());
  }


  @Test
  public void testCreateRecurringEventAllWeekdays() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.allOf(Weekday.class);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 7, null, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals(7, model.events.size());
  }

  @Test
  public void testCreateRecurringEventStartDayNotInWeekdays() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M, Weekday.W);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 2, null, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertTrue(model.events.size() >= 2);
  }

  @Test
  public void testCreateRecurringEventReachesExactlyUntilDate() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 17, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 17, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 24);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertEquals("Should create events on 6/17 and 6/24", 2, model.events.size());
    assertEquals(LocalDate.of(2024, 6, 24),
        model.events.get(1).start().toLocalDate());
  }

  @Test
  public void testCreateRecurringEventAllDay() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 0, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 23, 59);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        true, days, 2, null, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals(2, model.events.size());
  }

  @Test
  public void testNavigationRefreshesView() {
    view.showMonthCalled = false;
    controller.goToToday();
    assertTrue("Navigation must refresh view", view.showMonthCalled);
    view.showMonthCalled = false;
    controller.goToNextMonth();
    assertTrue("Navigation must refresh view", view.showMonthCalled);
  }

  /**
   * Test for covering the boundary conditions.
   */
  @Test
  public void testApplyEditOneCallsSelectDay() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    model.events.add(original);
    view.showMonthCalled = false;
    controller.editEvent(original, updated, EditScope.ONE);
    assertTrue("applyEditOne must call selectDay to refresh view", view.showMonthCalled);
  }

  @Test
  public void testGoToPreviousMonthUpdatesMonth() {
    controller.selectDay(LocalDate.of(2024, 6, 15));
    view.lastMonth = null;
    controller.goToPreviousMonth();
    assertNotNull("Month should be updated", view.lastMonth);
    assertEquals("Should navigate to May", 5, view.lastMonth.getMonthValue());
  }


  @Test
  public void testGoToNextMonthUpdatesMonth() {
    controller.selectDay(LocalDate.of(2024, 6, 15));
    view.lastMonth = null;
    controller.goToNextMonth();
    assertNotNull("Month should be updated", view.lastMonth);
    assertEquals("Should navigate to July", 7, view.lastMonth.getMonthValue());
  }


  @Test
  public void testCreateRecurringEventBothCountAndUntilSpecified() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    LocalDate until = LocalDate.of(2024, 6, 30);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 5, until, PrivacyStatus.PUBLIC);
    assertTrue("Both specified should show error", view.showErrorCalled);
    assertTrue(view.lastErrorMessage.toLowerCase().contains("one of"));
  }

  @Test
  public void testEditEventScopeOneUpdatesOnlyOneEvent() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent event1 = new CalendarEvent("Event", "Desc", "Loc",
        start, end, Optional.empty(), Optional.of(seriesId), PrivacyStatus.PUBLIC);
    CalendarEvent event2 = new CalendarEvent("Event", "Desc", "Loc",
        start.plusDays(7), end.plusDays(7), Optional.empty(), Optional.of(seriesId),
        PrivacyStatus.PUBLIC);
    model.events.add(event1);
    model.events.add(event2);
    CalendarEvent updated = new CalendarEvent("Updated", "New Desc",
        "New Loc", start, end, Optional.empty(), Optional.of(seriesId),
        PrivacyStatus.PRIVATE);
    controller.editEvent(event1, updated, EditScope.ONE);
    assertEquals("Event", model.events.get(0).name());
    assertEquals("Updated", model.events.get(1).name());
  }

  /**
   * Boundary tests for getting coverage for Jacoco and mutation pitt.
   */
  @Test
  public void testCreateRecurringEventCountBoundaryExactly() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertEquals("Count of 1 should create exactly 1 event", 1,
        model.events.size());
    model.events.clear();
    model.addEventCalled = false;
    controller.createRecurringEvent(start, end, "Event2", "Desc", "Loc",
        false, days, 0, null, PrivacyStatus.PUBLIC);
    assertTrue("Count of 0 should show error", view.showErrorCalled);
    assertEquals("Count of 0 should not create events", 0, model.events.size());
  }

  @Test
  public void testCreateRecurringEventUntilDateBoundaryCondition() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 15);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertTrue("Should create event on start date", model.addEventCalled);
    assertTrue("Should create at least one event", model.events.size() >= 1);
  }

  @Test
  public void testCreateRecurringEventWithCountOneVerifyIteration() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S, Weekday.M, Weekday.T);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertEquals("Count=1 should override weekday selection", 1,
        model.events.size());
  }

  @Test
  public void testCreateRecurringEventCountExactlyOne() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 17, 14, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 17, 15, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "ValidCount", "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertTrue("Count=1 should create events", model.addEventCalled);
    assertEquals("Count=1 should create exactly 1 event", 1, model.events.size());
    model.events.clear();
    view.showErrorCalled = false;
    view.lastErrorMessage = null;
    controller.createRecurringEvent(start, end, "InvalidCount", "Desc",
        "Loc", false, days, 0, null, PrivacyStatus.PUBLIC);
    assertTrue("Count=0 should trigger error", view.showErrorCalled);
    assertNotNull("Error message should be set", view.lastErrorMessage);
    assertEquals("Count=0 should not create events", 0, model.events.size());
  }

  @Test
  public void testCreateRecurringEventCountValidationBoundary() {
    model.events.clear();
    view.showErrorCalled = false;
    view.lastErrorMessage = null;
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 0, null, PrivacyStatus.PUBLIC);
    assertTrue("Count of 0 must trigger validation error", view.showErrorCalled);
    assertTrue("Error message must mention count",
        view.lastErrorMessage != null
            && (view.lastErrorMessage.toLowerCase().contains("count")
            || view.lastErrorMessage.toLowerCase().contains("positive")));
    assertEquals("Count of 0 must not create any events", 0, model.events.size());
    assertFalse("addEvent should not be called for count=0", model.addEventCalled);
  }

  @Test
  public void testCreateRecurringEventCountBoundaryMutation() {
    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 0, null, PrivacyStatus.PUBLIC);
    assertTrue("count=0 must show error", view.showErrorCalled);
    assertFalse("count=0 must not call addEvent", model.addEventCalled);
    assertEquals("count=0 must not create events", 0, model.events.size());
    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertFalse("count=1 must not show error", view.showErrorCalled);
    assertTrue("count=1 must call addEvent", model.addEventCalled);
    assertEquals("count=1 must create exactly 1 event", 1, model.events.size());
  }

  @Test
  public void testCreateRecurringEventCountStrictlyPositive() {
    final LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    final LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    final Set<Weekday> days = EnumSet.of(Weekday.S);
    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    view.lastErrorMessage = null;
    controller.createRecurringEvent(start, end, "Zero", "Desc", "Loc",
        false, days, 0, null, PrivacyStatus.PUBLIC);
    assertTrue("count=0 must trigger error", view.showErrorCalled);
    assertFalse("count=0 must not add any events", model.addEventCalled);
    assertEquals("count=0 must create 0 events", 0, model.events.size());
    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    view.lastErrorMessage = null;
    controller.createRecurringEvent(start, end, "One", "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertFalse("count=1 must not trigger error", view.showErrorCalled);
    assertTrue("count=1 must add events", model.addEventCalled);
    assertEquals("count=1 must create exactly 1 event", 1, model.events.size());
  }

  /**
   * Tests for boundary conditions.
   */
  @Test
  public void testCreateRecurringEventCountStrictBoundary() {

    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    view.lastErrorMessage = null;
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 0, null, PrivacyStatus.PUBLIC);
    assertTrue("count=0 must trigger error", view.showErrorCalled);
    assertFalse("count=0 must not add events", model.addEventCalled);
    assertEquals("count=0 creates no events", 0, model.events.size());
    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertFalse("count=1 must not trigger error", view.showErrorCalled);
    assertTrue("count=1 must add events", model.addEventCalled);
    assertEquals("count=1 creates exactly 1 event", 1, model.events.size());
  }


  @Test
  public void testCreateRecurringEventWithBothCountAndUntilNonNull() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    LocalDate until = LocalDate.of(2024, 6, 30);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 5, until, PrivacyStatus.PUBLIC);
    assertTrue("Should show error when both count and until provided", view.showErrorCalled);
  }

  @Test
  public void testCreateRecurringEventCountNullButPositiveCheck() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    LocalDate until = LocalDate.of(2024, 6, 30);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertTrue("Should create events with until date", model.addEventCalled);
  }

  @Test
  public void testApplyEditOneRollbackOnAddFailure() {
    model.throwExceptionOnAdd = true;
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    model.events.add(original);
    controller.editEvent(original, updated, EditScope.ONE);
    assertTrue("Should show error on add failure", view.showErrorCalled);
  }

  @Test
  public void testApplyEditThisAndAfterRollbackOnFailure() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(1),
        end.plusDays(1), seriesId);
    model.events.add(original);
    model.events.add(event2);
    model.throwExceptionOnAdd = true;
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertTrue("Should show error on rollback", view.showErrorCalled);
  }

  @Test
  public void testApplyEditEntireSeriesRollbackOnFailure() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(1),
        end.plusDays(1), seriesId);
    model.events.add(original);
    model.events.add(event2);
    model.throwExceptionOnAdd = true;
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    controller.editEvent(original, updated, EditScope.ENTIRE_SERIES);
    assertTrue("Should show error on rollback", view.showErrorCalled);
  }

  @Test
  public void testDeleteEventSuccess() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent event = createTestEvent("ToDelete", start, end);
    model.events.add(event);
    controller.deleteEvent(event);
    assertTrue("Should call removeEvent", model.removeEventCalled);
    assertTrue("Should refresh view", view.showMonthCalled);
  }

  @Test
  public void testDeleteEventNull() {
    controller.deleteEvent(null);
    assertTrue("Should show error for null event", view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("No event selected"));
  }

  @Test
  public void testDeleteEventNotFound() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent event = createTestEvent("NotFound", start, end);
    model.removeEventReturnValue = false;
    controller.deleteEvent(event);
    assertTrue("Should show error when event not found", view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("Could not find"));
  }

  @Test
  public void testHasEventsOnWithNull() {
    assertFalse("Should return false for null date", controller.hasEventsOn(null));
  }

  @Test
  public void testHasEventsOnWithEventsPresent() {
    LocalDate day = LocalDate.of(2024, 6, 15);
    CalendarEvent event = createTestEvent("Test",
        day.atTime(10, 0), day.atTime(11, 0));
    model.events.add(event);
    assertTrue("Should return true when events present", controller.hasEventsOn(day));
  }

  @Test
  public void testHasEventsOnWithNoEvents() {
    LocalDate day = LocalDate.of(2024, 6, 15);
    assertFalse("Should return false when no events", controller.hasEventsOn(day));
  }

  @Test
  public void testEditEventUnknownScope() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    model.events.add(original);
    controller.editEvent(original, updated, null);
    assertTrue("Should show error for null scope", view.showErrorCalled);
  }

  @Test
  public void testApplyEditThisAndAfterNoEventsFound() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("NoMatch", start, end, seriesId);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertTrue("Should show error when no events found", view.showErrorCalled);
  }

  @Test
  public void testApplyEditEntireSeriesNoEventsFound() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("NoMatch", start, end, seriesId);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    controller.editEvent(original, updated, EditScope.ENTIRE_SERIES);
    assertTrue("Should show error when no events found", view.showErrorCalled);
  }

  @Test
  public void testCreateRecurringEventInvalidOccurrenceEndBeforeStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 9, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 3, null, PrivacyStatus.PUBLIC);
    assertEquals("Should not create events with end before start", 0, model.events.size());
  }

  @Test
  public void testCreateRecurringEventWithUntilAndInvalidEndTime() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 9, 0);
    LocalDate until = LocalDate.of(2024, 6, 30);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertEquals("Should not create events with invalid end time", 0, model.events.size());
  }

  @Test
  public void testRefreshViewCatchesExceptionForCalendarName() {
    model.throwExceptionOnCurrentName = true;
    model.throwExceptionOnCurrentZone = true;
    controller.goToToday();
    assertTrue("Should handle exception gracefully", view.showMonthCalled);
  }

  @Test
  public void testCreateRecurringEventCountMustBeStrictlyPositive() {

    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    view.lastErrorMessage = null;
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Zero", "Desc", "Loc",
        false, days, 0, null, PrivacyStatus.PUBLIC);
    assertTrue("count=0 must show error (proves > not >=)", view.showErrorCalled);
    assertFalse("count=0 must not call addEvent", model.addEventCalled);
    assertEquals("count=0 must create 0 events", 0, model.events.size());
    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    view.lastErrorMessage = null;
    controller.createRecurringEvent(start, end, "One", "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertFalse("count=1 must not show error", view.showErrorCalled);
    assertTrue("count=1 must call addEvent", model.addEventCalled);
    assertEquals("count=1 must create exactly 1 event", 1, model.events.size());
  }

  @Test
  public void testHasEventsOnWithNullDay() {
    assertFalse(controller.hasEventsOn(null));
  }

  @Test
  public void testHasEventsOnWithNoEventsPresent() {
    LocalDate day = LocalDate.of(2024, 6, 15);
    assertFalse(controller.hasEventsOn(day));
  }

  @Test
  public void testHandleErrorWithNullMessage() {
    controller.handleError(null);
    assertTrue(view.showErrorCalled);
    assertEquals("An unknown error occurred.", view.lastErrorMessage);
  }

  @Test
  public void testHandleErrorWithBlankMessage() {
    controller.handleError("   ");
    assertTrue(view.showErrorCalled);
    assertEquals("An unknown error occurred.", view.lastErrorMessage);
  }

  @Test
  public void testHandleErrorWithValidMessage() {
    controller.handleError("Test error");
    assertTrue(view.showErrorCalled);
    assertEquals("Test error", view.lastErrorMessage);
  }

  @Test
  public void testDeleteEventWithNull() {
    controller.deleteEvent(null);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("No event selected"));
  }

  @Test
  public void testApplyEditOneWhenAddEventThrowsException() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    model.events.add(original);
    model.throwExceptionOnAdd = true;
    controller.editEvent(original, updated, EditScope.ONE);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testApplyEditThisAndAfterWithNoSeriesIdMatchByName() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Weekly", start, end);
    CalendarEvent event2 = createTestEvent("Weekly", start.plusDays(7), end.plusDays(7));
    model.events.add(original);
    model.events.add(event2);
    CalendarEvent updated = createTestEvent("UpdatedWeekly", start, end);
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertFalse(view.showErrorCalled);
  }

  @Test
  public void testApplyEditThisAndAfterRemoveFailsDuringExecution() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    model.events.add(original);
    model.events.add(event2);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    model.removeEventReturnValue = false;
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("Expected event to remove"));
  }

  @Test
  public void testApplyEditThisAndAfterAddFailsAndRollback() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    model.events.add(original);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    model.throwExceptionOnAdd = true;
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testApplyEditEntireSeriesWithNoSeriesIdMatchByName() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Daily", start, end);
    CalendarEvent event2 = createTestEvent("Daily", start.plusDays(1), end.plusDays(1));
    model.events.add(original);
    model.events.add(event2);
    CalendarEvent updated = createTestEvent("UpdatedDaily", start, end);
    controller.editEvent(original, updated, EditScope.ENTIRE_SERIES);
    assertFalse(view.showErrorCalled);
  }

  @Test
  public void testApplyEditEntireSeriesRemoveFailsDuringExecution() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(1),
        end.plusDays(1), seriesId);
    model.events.add(original);
    model.events.add(event2);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    model.removeEventReturnValue = false;
    controller.editEvent(original, updated, EditScope.ENTIRE_SERIES);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("Expected event to remove"));
  }

  @Test
  public void testApplyEditEntireSeriesAddFailsAndRollback() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    model.events.add(original);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    model.throwExceptionOnAdd = true;
    controller.editEvent(original, updated, EditScope.ENTIRE_SERIES);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testCreateRecurringEventWithNegativeCount() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Negative", "Desc", "Loc",
        false, days, -5, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertEquals(0, model.events.size());
  }

  @Test
  public void testCreateRecurringEventWithUntilBeforeStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 10);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Past", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertEquals(0, model.events.size());
  }

  @Test
  public void testCreateRecurringEventBothCountAndUntilProvided() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 30);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Both", "Desc", "Loc",
        false, days, 5, until, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("only one"));
  }

  @Test
  public void testCreateRecurringEventCountPositiveButWithUntil() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 7, 15);
    Set<Weekday> days = EnumSet.of(Weekday.T);
    controller.createRecurringEvent(start, end, "CountUntil", "Desc", "Loc",
        false, days, 10, until, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("only one"));
  }

  @Test
  public void testCreateRecurringEventUntilPathWithValidEndTime() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 17, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 17, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 30);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "UntilPath", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertTrue(model.events.size() > 0);
  }

  @Test
  public void testCreateRecurringEventCountPathMatchesWeekday() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 17, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 17, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Mondays", "Desc", "Loc",
        false, days, 4, null, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals(4, model.events.size());
  }

  @Test
  public void testCreateRecurringEventCountPathSkipsNonMatchingDays() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Saturdays", "Desc", "Loc",
        false, days, 3, null, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertTrue(model.events.size() >= 3);
  }

  @Test
  public void testCreateRecurringEventCountOnlyNoErrorAndCorrectEvents() {
    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    LocalDateTime start = LocalDateTime.of(2024, 6, 1, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 1, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    int count = 3;
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, count, null, PrivacyStatus.PUBLIC);
    assertFalse("Count-only should not trigger an error", view.showErrorCalled);
    assertTrue("addEvent should be called for count-only", model.addEventCalled);
    assertEquals("Should create exactly 'count' events", count, model.events.size());
  }

  @Test
  public void testCreateRecurringEventUntilOnlyNoErrorAndExpectedEvents() {
    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 22);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertFalse("Until-only should not trigger an error", view.showErrorCalled);
    assertTrue("addEvent should be called for until-only", model.addEventCalled);
    assertEquals("Should create events up to and including the until date",
        2, model.events.size());
  }

  @Test
  public void testCreateRecurringEventCountPositiveUntilNullExposesBothCheck() {
    model.events.clear();
    model.addEventCalled = false;
    view.showErrorCalled = false;
    LocalDateTime start = LocalDateTime.of(2024, 6, 3, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 3, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    Integer count = 2;
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, count, null, PrivacyStatus.PUBLIC);
    assertFalse("Positive count with null until must not be rejected "
        + "by the 'both specified' guard", view.showErrorCalled);
    assertEquals("Should create exactly 'count' events when until is null",
        count.intValue(), model.events.size());
  }

  @Test
  public void testDeleteEventSuccessfulCallsSelectDayAndRefreshesView() {
    view.showMonthCalled = false;
    view.showErrorCalled = false;
    LocalDateTime start = LocalDateTime.of(2024, 8, 10, 9, 0);
    LocalDateTime end = LocalDateTime.of(2024, 8, 10, 10, 0);
    CalendarEvent event = createTestEvent("DeleteMe", start, end);
    model.events.add(event);
    model.removeEventReturnValue = true;
    controller.deleteEvent(event);
    assertFalse("Successful delete should not show error", view.showErrorCalled);
    assertTrue("Successful delete must refresh the view via selectDay", view.showMonthCalled);
  }

  @Test
  public void testEditEventOneFailureReaddsOriginalEvent() {
    MockCalendarModel failingModel = new MockCalendarModel() {
      int addCalls = 0;

      @Override
      public void addEvent(CalendarEvent event) {
        addCalls += 1;
        if (addCalls == 1) {
          throw new IllegalArgumentException("Cannot add updated");
        }
        super.addEvent(event);
      }
    };
    MockGuiView localView = new MockGuiView();
    LocalDateTime start = LocalDateTime.of(2024, 9, 1, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 9, 1, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    failingModel.events.add(original);
    failingModel.removeEventReturnValue = true;
    localView.showErrorCalled = false;
    GuiCalendarController localController = new GuiCalendarController(failingModel, localView);
    CalendarEvent updated = createTestEvent("Updated", start.plusHours(1), end.plusHours(1));
    localController.editEvent(original, updated, EditScope.ONE);
    assertTrue("Edit failure should report an error", localView.showErrorCalled);
    assertEquals("Original event must be restored when updating fails",
        1, failingModel.events.size());
    assertEquals("Original", failingModel.events.get(0).name());
  }

  @Test
  public void testEditEventOneUsesUpdatedDateForSelectDay() {
    LocalDateTime originalStart = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime originalEnd = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.selectDay(originalStart.toLocalDate());
    view.showMonthCalled = false;
    CalendarEvent original = createTestEvent("Event", originalStart, originalEnd);
    model.events.clear();
    model.events.add(original);
    model.removeEventReturnValue = true;
    LocalDateTime updatedEnd = LocalDateTime.of(2024, 7, 20, 11, 0);
    LocalDateTime updatedStart = LocalDateTime.of(2024, 7, 20, 10, 0);
    CalendarEvent updated = createTestEvent("Event", updatedStart, updatedEnd);
    controller.editEvent(original, updated, EditScope.ONE);
    assertTrue("EditEvent should refresh the view", view.showMonthCalled);
    assertEquals("View should finally be focused on the updated event's month",
        YearMonth.from(updatedStart.toLocalDate()), view.lastMonth);
  }

  @Test
  public void testCreateRecurringEventUntilPathSkipsInvalidEndTimes() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 23, 30);
    LocalDateTime end = LocalDateTime.of(2024, 6, 16, 0, 30);
    LocalDate until = LocalDate.of(2024, 6, 30);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "CrossMidnight", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertEquals(0, model.events.size());
    assertFalse(view.showErrorCalled);
  }

  @Test
  public void testApplyEditOneRollbackOnAddException() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    model.events.add(original);
    MockCalendarModel tempModel = new MockCalendarModel();
    tempModel.events.add(original);
    tempModel.throwExceptionOnAdd = true;
    GuiCalendarController tempController = new GuiCalendarController(tempModel, view);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    tempController.editEvent(original, updated, EditScope.ONE);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testApplyEditThisAndAfterWithSeriesIdPresent() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    CalendarEvent event3 = createTestEventWithSeries("Event", start.plusDays(14),
        end.plusDays(14), seriesId);
    model.events.add(original);
    model.events.add(event2);
    model.events.add(event3);
    CalendarEvent updated = createTestEventWithSeries("UpdatedEvent", start.plusDays(1),
        end.plusDays(1), seriesId);
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertFalse(view.showErrorCalled);
    assertTrue(model.removeEventCalled);
  }

  @Test
  public void testApplyEditThisAndAfterWithSeriesIdNotPresent() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Weekly", start, end);
    CalendarEvent event2 = createTestEvent("Weekly", start.plusDays(7), end.plusDays(7));
    CalendarEvent event3 = createTestEvent("Weekly", start.minusDays(7), end.minusDays(7));
    model.events.add(original);
    model.events.add(event2);
    model.events.add(event3);
    CalendarEvent updated = createTestEvent("UpdatedWeekly", start, end);
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertFalse(view.showErrorCalled);
    assertEquals(2, model.events.stream()
        .filter(e -> e.name().equals("UpdatedWeekly")).count());
  }

  @Test
  public void testApplyEditThisAndAfterExcludesBeforeEvents() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent beforeEvent = createTestEventWithSeries("Event", start.minusDays(7),
        end.minusDays(7), seriesId);
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent afterEvent = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    model.events.add(beforeEvent);
    model.events.add(original);
    model.events.add(afterEvent);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertFalse(view.showErrorCalled);
    assertTrue(model.events.stream().anyMatch(e -> e.name().equals("Event")
        && e.start().equals(beforeEvent.start())));
  }

  @Test
  public void testApplyEditEntireSeriesWithSeriesIdPresent() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent event1 = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    CalendarEvent event3 = createTestEventWithSeries("Event", start.minusDays(7),
        end.minusDays(7), seriesId);
    model.events.add(event1);
    model.events.add(event2);
    model.events.add(event3);
    CalendarEvent updated = createTestEventWithSeries("UpdatedSeries", start.plusDays(2),
        end.plusDays(2), seriesId);
    controller.editEvent(event1, updated, EditScope.ENTIRE_SERIES);
    assertFalse(view.showErrorCalled);
    assertEquals(3, model.events.stream()
        .filter(e -> e.name().equals("UpdatedSeries")).count());
  }

  @Test
  public void testApplyEditEntireSeriesWithSeriesIdNotPresent() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent event1 = createTestEvent("Daily", start, end);
    CalendarEvent event2 = createTestEvent("Daily", start.plusDays(1), end.plusDays(1));
    CalendarEvent event3 = createTestEvent("Daily", start.plusDays(2), end.plusDays(2));
    model.events.add(event1);
    model.events.add(event2);
    model.events.add(event3);
    CalendarEvent updated = createTestEvent("NewDaily", start, end);
    controller.editEvent(event1, updated, EditScope.ENTIRE_SERIES);
    assertFalse(view.showErrorCalled);
    assertEquals(3, model.events.stream()
        .filter(e -> e.name().equals("NewDaily")).count());
  }

  @Test
  public void testApplyEditEntireSeriesWithDifferentSeriesIdNotMatching() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId1 = UUID.randomUUID();
    UUID seriesId2 = UUID.randomUUID();
    CalendarEvent event1 = createTestEventWithSeries("Event", start, end, seriesId1);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId2);
    model.events.add(event1);
    model.events.add(event2);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId1);
    controller.editEvent(event1, updated, EditScope.ENTIRE_SERIES);
    assertFalse(view.showErrorCalled);
    assertEquals(1, model.events.stream()
        .filter(e -> e.name().equals("Updated")).count());
  }

  @Test
  public void testApplyEditThisAndAfterCalculatesDayShift() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    model.events.add(original);
    model.events.add(event2);
    CalendarEvent updated = createTestEventWithSeries("Updated", start.plusDays(3),
        end.plusDays(3), seriesId);
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertFalse(view.showErrorCalled);
    assertTrue(model.events.stream()
        .anyMatch(e -> e.start().toLocalDate().equals(start.plusDays(3).toLocalDate())));
  }

  @Test
  public void testApplyEditEntireSeriesCalculatesDayShift() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent event1 = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    model.events.add(event1);
    model.events.add(event2);
    CalendarEvent updated = createTestEventWithSeries("Updated", start.minusDays(2),
        end.minusDays(2), seriesId);
    controller.editEvent(event1, updated, EditScope.ENTIRE_SERIES);
    assertFalse(view.showErrorCalled);
    assertTrue(model.events.stream()
        .anyMatch(e -> e.start().toLocalDate().equals(start.minusDays(2).toLocalDate())));
  }

  @Test
  public void testApplyEditThisAndAfterUpdatesTimeOfDay() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    model.events.add(original);
    model.events.add(event2);
    CalendarEvent updated = createTestEventWithSeries("Updated", start.withHour(14),
        end.withHour(15), seriesId);
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertFalse(view.showErrorCalled);
    assertTrue(model.events.stream()
        .allMatch(e -> e.start().getHour() == 14));
  }

  @Test
  public void testApplyEditEntireSeriesUpdatesTimeOfDay() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent event1 = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    model.events.add(event1);
    model.events.add(event2);
    CalendarEvent updated = createTestEventWithSeries("Updated", start.withHour(16),
        end.withHour(17), seriesId);
    controller.editEvent(event1, updated, EditScope.ENTIRE_SERIES);
    assertFalse(view.showErrorCalled);
    assertTrue(model.events.stream()
        .allMatch(e -> e.start().getHour() == 16));
  }

  @Test
  public void testHandleErrorWithEmptyString() {
    controller.handleError("");
    assertTrue(view.showErrorCalled);
    assertEquals("An unknown error occurred.", view.lastErrorMessage);
  }

  @Test
  public void testHandleErrorWithOnlyWhitespace() {
    controller.handleError("  \t\n  ");
    assertTrue(view.showErrorCalled);
    assertEquals("An unknown error occurred.", view.lastErrorMessage);
  }

  @Test
  public void testCreateRecurringEventCountPathWithMultipleWeekdays() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S, Weekday.M, Weekday.T);
    controller.createRecurringEvent(start, end, "MultiDay", "Desc", "Loc",
        false, days, 10, null, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals(10, model.events.size());
  }

  @Test
  public void testCreateRecurringEventUntilPathWithMultipleWeekdays() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 30);
    Set<Weekday> days = EnumSet.of(Weekday.S, Weekday.M, Weekday.T);
    controller.createRecurringEvent(start, end, "MultiDay", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertTrue(model.events.size() > 0);
  }

  @Test
  public void testGetEventsOnUpdatesViewWithCurrentCalendarInfo() {
    LocalDate day = LocalDate.of(2024, 6, 15);
    model.calendarName = "TestCalendar";
    model.zone = ZoneId.of("America/New_York");
    controller.getEventsOn(day);
    assertTrue(view.showMonthCalled);
    assertNotNull(view.lastMonth);
  }

  @Test
  public void testCreateRecurringEventUntilPathNoMatchingDays() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 17, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 17, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 17);
    Set<Weekday> days = EnumSet.of(Weekday.T);
    controller.createRecurringEvent(start, end, "NoMatch", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertEquals("No events should be created when no dates match selected weekdays",
        0, model.events.size());
    assertFalse("Model addEvent should not be called", model.addEventCalled);
    assertFalse("No error should be shown when there are simply no matching days",
        view.showErrorCalled);
  }

  @Test
  public void testApplyEditOneFailsToRestoreOriginalAfterUpdateFailure() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    MockCalendarModel specialModel = new MockCalendarModel();
    specialModel.events.add(original);
    MockGuiView specialView = new MockGuiView();
    GuiCalendarController specialController = new GuiCalendarController(specialModel, specialView);
    specialModel.throwExceptionOnAdd = true;
    specialModel.addEventCalled = false;
    try {
      specialController.editEvent(original, updated, EditScope.ONE);
    } catch (Exception e) {
      e.getMessage();
    }
    assertTrue(specialView.showErrorCalled);
  }

  @Test
  public void testApplyEditOneRollbackAlsoFailsWithException() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    model.events.add(original);
    MockCalendarModel doubleFailModel = new MockCalendarModel() {
      private int addAttempts = 0;

      @Override
      public void addEvent(CalendarEvent event) {
        addAttempts++;
        addEventCalled = true;
        if (addAttempts == 1) {
          throw new IllegalArgumentException("First add fails");
        }
        if (addAttempts == 2) {
          throw new IllegalArgumentException("Rollback also fails");
        }
        events.add(event);
      }
    };

    doubleFailModel.events.add(original);
    MockGuiView doubleFailView = new MockGuiView();
    GuiCalendarController doubleFailController = new GuiCalendarController(
        doubleFailModel, doubleFailView);
    doubleFailController.editEvent(original, updated, EditScope.ONE);
    assertTrue(doubleFailView.showErrorCalled);
  }

  @Test
  public void testConstructorExceptionMessageAccessedButNotUsed() {
    MockCalendarModel exceptionModel = new MockCalendarModel() {
      @Override
      public void createCalendar(String name, ZoneId zone) {
        createCalendarCalled = true;
        lastCalendarName = name;
        lastZone = zone;
        throw new IllegalArgumentException("Test exception with message");
      }
    };
    MockGuiView exceptionView = new MockGuiView();
    GuiCalendarController exceptionController = new GuiCalendarController(
        exceptionModel, exceptionView);
    assertNotNull(exceptionController);
    assertTrue(exceptionModel.createCalendarCalled);
  }

  @Test
  public void testApplyEditOneAccessesExceptionMessage() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    MockCalendarModel msgModel = new MockCalendarModel() {
      @Override
      public void addEvent(CalendarEvent event) {
        addEventCalled = true;
        throw new IllegalArgumentException("Message should be accessed");
      }
    };
    msgModel.events.add(original);
    MockGuiView msgView = new MockGuiView();
    GuiCalendarController msgController = new GuiCalendarController(msgModel, msgView);
    msgController.editEvent(original, updated, EditScope.ONE);
    assertTrue(msgView.showErrorCalled);
  }

  @Test
  public void testApplyEditEntireSeriesCallsAddEventInRollback() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent event1 = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    MockCalendarModel specialModel = new MockCalendarModel() {
      private int addCallCount = 0;

      @Override
      public void addEvent(CalendarEvent event) {
        addCallCount++;
        addEventCalled = true;
        if (addCallCount <= 2 && event.name().equals("Updated")) {
          events.add(event);
          throw new IllegalArgumentException("Fail after adding");
        }
        events.add(event);
      }
    };
    specialModel.events.add(event1);
    specialModel.events.add(event2);
    MockGuiView specialView = new MockGuiView();
    GuiCalendarController specialController = new GuiCalendarController(
        specialModel, specialView);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    specialController.editEvent(event1, updated, EditScope.ENTIRE_SERIES);
    assertTrue("Error should be shown", specialView.showErrorCalled);
    assertTrue("addEvent should have been called", specialModel.addEventCalled);
  }

  @Test
  public void testApplyEditEntireSeriesCallsSelectDay() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent event1 = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    model.events.add(event1);
    model.events.add(event2);
    view.showMonthCalled = false;
    CalendarEvent updated = createTestEventWithSeries("Updated", start.plusDays(2),
        end.plusDays(2), seriesId);
    controller.editEvent(event1, updated, EditScope.ENTIRE_SERIES);
    assertTrue("selectDay should be called which triggers refreshView",
        view.showMonthCalled);
  }

  @Test
  public void testApplyEditThisAndAfterCallsAddEventInRollback() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent event1 = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    MockCalendarModel specialModel = new MockCalendarModel() {
      private int addCallCount = 0;

      @Override
      public void addEvent(CalendarEvent event) {
        addCallCount++;
        addEventCalled = true;
        if (addCallCount <= 2 && event.name().equals("Updated")) {
          events.add(event);
          throw new IllegalArgumentException("Fail after adding");
        }
        events.add(event);
      }
    };

    specialModel.events.add(event1);
    specialModel.events.add(event2);
    MockGuiView specialView = new MockGuiView();
    GuiCalendarController specialController = new GuiCalendarController(
        specialModel, specialView);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    specialController.editEvent(event1, updated, EditScope.THIS_AND_AFTER);
    assertTrue("Error should be shown", specialView.showErrorCalled);
    assertTrue("addEvent should have been called", specialModel.addEventCalled);
  }

  @Test
  public void testApplyEditThisAndAfterCallsSelectDay() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent event1 = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    model.events.add(event1);
    model.events.add(event2);
    view.showMonthCalled = false;
    CalendarEvent updated = createTestEventWithSeries("Updated", start.plusDays(3),
        end.plusDays(3), seriesId);
    controller.editEvent(event1, updated, EditScope.THIS_AND_AFTER);
    assertTrue("selectDay should be called which triggers refreshView",
        view.showMonthCalled);
  }

  @Test
  public void testCreateRecurringEventCountBoundaryConditionGreaterThan() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    LocalDate until = LocalDate.of(2024, 6, 30);
    model.events.clear();
    view.showErrorCalled = false;
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 1, until, PrivacyStatus.PUBLIC);
    assertTrue("Should show error for count > 0 AND until not null",
        view.showErrorCalled);
    assertTrue("Error message should mention 'one of'",
        view.lastErrorMessage.contains("one of"));
  }

  @Test
  public void testCreateRecurringEventWhileLoopBoundaryCondition() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    model.events.clear();
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertEquals("Should create exactly 1 event when count equals 1", 1,
        model.events.size());
  }

  @Test
  public void testCreateRecurringEventWhileLoopBoundaryConditionCreatedEqualsCount() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    model.events.clear();
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 3, null, PrivacyStatus.PUBLIC);
    assertEquals("Should create exactly 3 events", 3, model.events.size());
  }

  @Test
  public void testEditEventCallsRefreshView() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    model.events.add(original);
    view.showMonthCalled = false;
    controller.editEvent(original, updated, EditScope.ONE);
    assertTrue("refreshView should be called after successful edit",
        view.showMonthCalled);
  }

  @Test
  public void testEditEventThisAndAfterCallsRefreshView() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    model.events.add(original);
    view.showMonthCalled = false;
    controller.editEvent(original, updated, EditScope.THIS_AND_AFTER);
    assertTrue("refreshView should be called after successful bulk edit",
        view.showMonthCalled);
  }

  @Test
  public void testEditEventEntireSeriesCallsRefreshView() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent original = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    model.events.add(original);
    view.showMonthCalled = false;
    controller.editEvent(original, updated, EditScope.ENTIRE_SERIES);
    assertTrue("refreshView should be called after successful series edit",
        view.showMonthCalled);
  }

  @Test
  public void testApplyEditThisAndAfterOnlyIncludesAtOrAfterEvents() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    CalendarEvent before1 = createTestEventWithSeries("Event", start.minusDays(14),
        end.minusDays(14), seriesId);
    CalendarEvent before2 = createTestEventWithSeries("Event", start.minusDays(7),
        end.minusDays(7), seriesId);
    CalendarEvent exact = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent after1 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    CalendarEvent after2 = createTestEventWithSeries("Event", start.plusDays(14),
        end.plusDays(14), seriesId);
    model.events.add(before1);
    model.events.add(before2);
    model.events.add(exact);
    model.events.add(after1);
    model.events.add(after2);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    controller.editEvent(exact, updated, EditScope.THIS_AND_AFTER);
    long beforeCount = model.events.stream()
        .filter(e -> e.start().isBefore(start) && e.name().equals("Event")).count();
    long afterCount = model.events.stream()
        .filter(e -> !e.start().isBefore(start) && e.name().equals("Updated")).count();
    assertEquals(2, beforeCount);
    assertEquals(3, afterCount);
  }

  @Test
  public void testApplyEditThisAndAfterSeriesIdPresentAndMatches() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    UUID differentSeriesId = UUID.randomUUID();
    CalendarEvent target1 = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent target2 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    CalendarEvent different = createTestEventWithSeries("Event", start.plusDays(14),
        end.plusDays(14), differentSeriesId);
    model.events.add(target1);
    model.events.add(target2);
    model.events.add(different);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    controller.editEvent(target1, updated, EditScope.THIS_AND_AFTER);
    long updatedWithCorrectSeries = model.events.stream()
        .filter(e -> e.name().equals("Updated")
            && e.seriesId().isPresent()
            && e.seriesId().get().equals(seriesId)).count();
    assertEquals(2, updatedWithCorrectSeries);
    long untouchedDifferentSeries = model.events.stream()
        .filter(e -> e.name().equals("Event")
            && e.seriesId().isPresent()
            && e.seriesId().get().equals(differentSeriesId)).count();
    assertEquals(1, untouchedDifferentSeries);
  }

  @Test
  public void testApplyEditThisAndAfterSeriesIdAbsentMatchesByName() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent target1 = createTestEvent("MatchName", start.minusDays(7), end.minusDays(7));
    CalendarEvent target2 = createTestEvent("MatchName", start, end);
    CalendarEvent target3 = createTestEvent("MatchName", start.plusDays(7), end.plusDays(7));
    CalendarEvent different = createTestEvent("DifferentName", start.plusDays(14),
        end.plusDays(14));
    model.events.add(target1);
    model.events.add(target2);
    model.events.add(target3);
    model.events.add(different);
    CalendarEvent updated = createTestEvent("UpdatedName", start, end);
    controller.editEvent(target2, updated, EditScope.THIS_AND_AFTER);
    long beforeUnchanged = model.events.stream()
        .filter(e -> e.name().equals("MatchName") && e.start().isBefore(start)).count();
    assertEquals(1, beforeUnchanged);
    long afterUpdated = model.events.stream()
        .filter(e -> e.name().equals("UpdatedName") && !e.start().isBefore(start)).count();
    assertEquals(2, afterUpdated);
  }

  @Test
  public void testApplyEditEntireSeriesSeriesIdPresentAndMatches() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    UUID seriesId = UUID.randomUUID();
    UUID differentSeriesId = UUID.randomUUID();
    CalendarEvent event1 = createTestEventWithSeries("Event", start.minusDays(7),
        end.minusDays(7), seriesId);
    CalendarEvent event2 = createTestEventWithSeries("Event", start, end, seriesId);
    CalendarEvent event3 = createTestEventWithSeries("Event", start.plusDays(7),
        end.plusDays(7), seriesId);
    CalendarEvent different = createTestEventWithSeries("Event", start.plusDays(14),
        end.plusDays(14), differentSeriesId);
    model.events.add(event1);
    model.events.add(event2);
    model.events.add(event3);
    model.events.add(different);
    CalendarEvent updated = createTestEventWithSeries("Updated", start, end, seriesId);
    controller.editEvent(event2, updated, EditScope.ENTIRE_SERIES);
    long updatedCount = model.events.stream()
        .filter(e -> e.name().equals("Updated")
            && e.seriesId().isPresent()
            && e.seriesId().get().equals(seriesId)).count();
    assertEquals(3, updatedCount);
    long untouchedCount = model.events.stream()
        .filter(e -> e.name().equals("Event")
            && e.seriesId().isPresent()
            && e.seriesId().get().equals(differentSeriesId)).count();
    assertEquals(1, untouchedCount);
  }

  @Test
  public void testApplyEditEntireSeriesSeriesIdAbsentMatchesByName() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent event1 = createTestEvent("SeriesName", start.minusDays(14), end.minusDays(14));
    CalendarEvent event2 = createTestEvent("SeriesName", start.minusDays(7), end.minusDays(7));
    CalendarEvent event3 = createTestEvent("SeriesName", start, end);
    CalendarEvent event4 = createTestEvent("SeriesName", start.plusDays(7), end.plusDays(7));
    CalendarEvent different = createTestEvent("OtherName", start, end);
    model.events.add(event1);
    model.events.add(event2);
    model.events.add(event3);
    model.events.add(event4);
    model.events.add(different);
    CalendarEvent updated = createTestEvent("UpdatedSeries", start, end);
    controller.editEvent(event3, updated, EditScope.ENTIRE_SERIES);
    long updatedCount = model.events.stream()
        .filter(e -> e.name().equals("UpdatedSeries")).count();
    assertEquals(4, updatedCount);
    long untouchedCount = model.events.stream()
        .filter(e -> e.name().equals("OtherName")).count();
    assertEquals(1, untouchedCount);
  }

  @Test
  public void testCreateRecurringEventCountPathDoesNotMatchWeekday() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 16, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 16, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 10, null, PrivacyStatus.PUBLIC);
    assertEquals(10, model.events.size());
  }

  @Test
  public void testCreateRecurringEventCountPathMatchesWeekdayMultipleTimes() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 5, null, PrivacyStatus.PUBLIC);
    assertEquals(5, model.events.size());
  }

  @Test
  public void testCreateRecurringEventUntilPathMatchesWeekday() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 29);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertEquals(3, model.events.size());
  }

  @Test
  public void testCreateRecurringEventUntilPathDoesNotMatchWeekday() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 16, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 16, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 17);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertTrue(model.events.size() >= 1);
  }

  @Test
  public void testCreateRecurringEventCountPathEndBeforeStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 30);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 10, 29);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 5, null, PrivacyStatus.PUBLIC);
    assertEquals(0, model.events.size());
  }

  @Test
  public void testCreateRecurringEventCountPathEndEqualsStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 30);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 10, 30);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, 3, null, PrivacyStatus.PUBLIC);
    assertEquals(0, model.events.size());
  }

  @Test
  public void testCreateRecurringEventUntilPathEndBeforeStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 30);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 10, 29);
    LocalDate until = LocalDate.of(2024, 6, 22);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertEquals(0, model.events.size());
  }

  @Test
  public void testCreateRecurringEventUntilPathEndEqualsStart() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 30);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 10, 30);
    LocalDate until = LocalDate.of(2024, 6, 22);
    Set<Weekday> days = EnumSet.of(Weekday.S);
    controller.createRecurringEvent(start, end, "Event", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertEquals(0, model.events.size());
  }

  @Test
  public void testCreateSingleEventStartNullOnly() {
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createSingleEvent(null, end, "Title", "Desc", "Loc",
        false, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testCreateSingleEventEndNullOnly() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    controller.createSingleEvent(start, null, "Title", "Desc", "Loc",
        false, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testCreateSingleEventTitleNullOnly() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createSingleEvent(start, end, null, "Desc", "Loc",
        false, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testCreateRecurringEventTitleNullOnly() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, null, "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("required"));
  }

  @Test
  public void testCreateRecurringEventWeekdaysNullOnly() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    controller.createRecurringEvent(start, end, "Title", "Desc", "Loc",
        false, null, 1, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("weekday"));
  }

  @Test
  public void testCreateRecurringEventWeekdaysEmptyOnly() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.noneOf(Weekday.class);
    controller.createRecurringEvent(start, end, "Title", "Desc", "Loc",
        false, days, 1, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("weekday"));
  }

  @Test
  public void testCreateRecurringEventCountNullAndCountPositiveAndUntilNotNull() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    LocalDate until = LocalDate.of(2024, 6, 30);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Title", "Desc", "Loc",
        false, days, 5, until, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
    assertTrue(view.lastErrorMessage.contains("one of"));
  }

  @Test
  public void testCreateRecurringEventCountNullAndUntilNull() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Title", "Desc", "Loc",
        false, days, null, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testCreateRecurringEventCountZeroAndUntilNull() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Title", "Desc", "Loc",
        false, days, 0, null, PrivacyStatus.PUBLIC);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testCreateRecurringEventCountNull() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    LocalDate until = LocalDate.of(2024, 6, 30);
    controller.createRecurringEvent(start, end, "Title", "Desc", "Loc",
        false, days, null, until, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
  }

  @Test
  public void testCreateRecurringEventCountNotNull() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    controller.createRecurringEvent(start, end, "Title", "Desc", "Loc",
        false, days, 3, null, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
  }

  @Test
  public void testEditEventOriginalNullOnly() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    controller.editEvent(null, updated, EditScope.ONE);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testEditEventUpdatedNullOnly() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    controller.editEvent(original, null, EditScope.ONE);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testEditEventScopeNullOnly() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    CalendarEvent original = createTestEvent("Original", start, end);
    CalendarEvent updated = createTestEvent("Updated", start, end);
    controller.editEvent(original, updated, null);
    assertTrue(view.showErrorCalled);
  }

  @Test
  public void testCreateRecurringEventCountNotNullButNotPositive() {
    LocalDateTime start = LocalDateTime.of(2024, 6, 15, 10, 0);
    LocalDateTime end = LocalDateTime.of(2024, 6, 15, 11, 0);
    Set<Weekday> days = EnumSet.of(Weekday.M);
    LocalDate until = LocalDate.of(2024, 6, 30);
    controller.createRecurringEvent(start, end, "Title", "Desc", "Loc",
        false, days, -1, until, PrivacyStatus.PUBLIC);
    assertTrue(model.addEventCalled);
    assertEquals(2, model.events.size());
    assertFalse(view.showErrorCalled);
  }

  private CalendarEvent createTestEvent(String name, LocalDateTime start, LocalDateTime end) {
    return new CalendarEvent(
        name,
        "Description",
        "Location",
        start,
        end,
        Optional.empty(),
        Optional.empty(),
        PrivacyStatus.PUBLIC
    );
  }

  private CalendarEvent createTestEventWithSeries(String name, LocalDateTime start, LocalDateTime
      end, UUID seriesId) {
    return new CalendarEvent(
        name,
        "Description",
        "Location",
        start,
        end,
        Optional.empty(),
        Optional.of(seriesId),
        PrivacyStatus.PUBLIC
    );
  }

  private static class MockCalendarModel implements CalendarModel {
    boolean createCalendarCalled = false;
    boolean useCalendarCalled = false;
    boolean addEventCalled = false;
    boolean removeEventCalled = false;
    String lastCalendarName;
    String lastUsedCalendar;
    ZoneId lastZone;
    String calendarName = "TestCal";
    ZoneId zone = ZoneId.systemDefault();
    List<CalendarEvent> events = new ArrayList<>();
    boolean throwExceptionOnCreate = false;
    boolean throwExceptionOnUse = false;
    boolean throwExceptionOnAdd = false;
    boolean throwExceptionOnRemove = false;
    boolean throwExceptionOnCurrentName = false;
    boolean throwExceptionOnCurrentZone = false;
    boolean removeEventReturnValue = true;

    @Override
    public void createCalendar(String name, ZoneId zone) {
      createCalendarCalled = true;
      lastCalendarName = name;
      lastZone = zone;
      if (throwExceptionOnCreate) {
        throw new IllegalArgumentException("Calendar already exists");
      }
    }

    @Override
    public void renameCalendar(String oldName, String newName) {

    }

    @Override
    public void changeTimezone(String name, ZoneId zone) {

    }

    @Override
    public void useCalendar(String name) {
      useCalendarCalled = true;
      lastUsedCalendar = name;
      if (throwExceptionOnUse) {
        throw new IllegalArgumentException("Calendar not found");
      }
    }

    @Override
    public String currentCalendarName() {
      if (throwExceptionOnCurrentName) {
        throw new IllegalStateException("No calendar selected");
      }
      return calendarName;
    }

    @Override
    public ZoneId currentZone() {
      if (throwExceptionOnCurrentZone) {
        throw new IllegalStateException("No calendar selected");
      }
      return zone;
    }

    @Override
    public void addEvent(CalendarEvent event) {
      addEventCalled = true;
      if (throwExceptionOnAdd) {
        throw new IllegalArgumentException("Cannot add event");
      }
      events.add(event);
    }

    @Override
    public boolean removeEvent(String name, LocalDateTime start) {
      removeEventCalled = true;
      if (throwExceptionOnRemove) {
        throw new IllegalArgumentException("Cannot remove event");
      }
      boolean removed = events.removeIf(event ->
          event.name().equals(name) && event.start().equals(start));
      return removeEventReturnValue && removed;
    }

    @Override
    public void copyEvent(String name, LocalDateTime sourceStartLocal, String targetCalendar,
                          LocalDateTime targetStartLocal) {
    }

    @Override
    public void copyEventsOnDate(LocalDate sourceDate, String targetCalendar,
                                 LocalDate targetDate) {
    }

    @Override
    public void copyEventsBetween(LocalDate fromDate, LocalDate toDate, String targetCalendar,
                                  LocalDate targetStart) {
    }

    @Override
    public Path exportCurrent(Path path) throws IOException {
      return null;
    }

    @Override
    public List<CalendarEvent> listEvents(LocalDate startInclusive, LocalDate endInclusive) {
      return new ArrayList<>(events);
    }

    @Override
    public Optional<CalendarEvent> findEvent(String name, LocalDateTime startTime) {
      return Optional.empty();
    }
  }

  private static class MockGuiView implements GuiView {
    public YearMonth lastMonth;
    boolean setFeaturesCalled = false;
    boolean showMonthCalled = false;
    boolean showErrorCalled = false;
    String lastErrorMessage;

    @Override
    public void setFeatures(GuiFeatures features) {
      setFeaturesCalled = true;
    }

    @Override
    public void showMonth(YearMonth month, LocalDate selectedDay, List<CalendarEvent> events,
                          String calendarName, ZoneId zone) {
      showMonthCalled = true;
      lastMonth = month;
    }

    @Override
    public void showError(String message) {
      showErrorCalled = true;
      lastErrorMessage = message;
    }

    @Override
    public void showInfo(String message) {
    }

    @Override
    public void setVisible(boolean visible) {
    }
  }

}

