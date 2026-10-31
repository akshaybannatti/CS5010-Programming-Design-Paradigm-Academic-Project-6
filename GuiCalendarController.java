package calendar.controller;

import calendar.model.CalendarModel;
import calendar.model.entity.CalendarEvent;
import calendar.model.entity.EditScope;
import calendar.model.entity.PrivacyStatus;
import calendar.model.entity.RecurrenceRule;
import calendar.model.entity.Weekday;
import calendar.view.GuiView;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * GUI-specific controller that bridges Swing user actions to the CalendarModel.
 *
 * <p>This controller is independent from the text-based CalendarController and
 * does not perform any command parsing. It exposes a higher-level feature set
 * tailored to GUI interactions.</p>
 */
public class GuiCalendarController implements GuiFeatures {

  private static final LocalDate GLOBAL_START = LocalDate.of(1900, 1, 1);
  private static final LocalDate GLOBAL_END = LocalDate.of(2100, 12, 31);

  private final CalendarModel model;
  private final GuiView view;

  /**
   * The GUI keeps track of calendar names it has created/used so that it
   * can populate the calendar selector. The model itself does not currently
   * expose a "list calendars" operation.
   */
  private final List<String> calendarNames = new ArrayList<>();

  private LocalDate selectedDay;
  private YearMonth currentMonth;

  /**
   * Constructs a GUI controller and wires it to the given model and view.
   * A default calendar in the system time zone is created (if necessary)
   * so the user can start interacting with the GUI immediately.
   *
   * @param model the calendar model
   * @param view  the GUI view
   */
  public GuiCalendarController(CalendarModel model, GuiView view) {
    this.model = model;
    this.view = view;

    this.view.setFeatures(this);

    ZoneId systemZone = ZoneId.systemDefault();
    String defaultName = "Default";

    try {
      model.createCalendar(defaultName, systemZone);
    } catch (IllegalArgumentException e) {
      e.getMessage();
    }
    model.useCalendar(defaultName);
    calendarNames.add(defaultName);

    this.selectedDay = LocalDate.now(systemZone);
    this.currentMonth = YearMonth.from(this.selectedDay);

    refreshView();
  }

  private void refreshView() {
    List<CalendarEvent> events = model.listEvents(selectedDay, selectedDay);
    String calName;
    ZoneId zone;
    try {
      calName = model.currentCalendarName();
      zone = model.currentZone();
    } catch (IllegalStateException ex) {
      calName = "<none>";
      zone = ZoneId.systemDefault();
    }
    view.showMonth(
        currentMonth,
        selectedDay,
        events,
        calName,
        zone
    );
  }

  @Override
  public void createCalendar(String name, ZoneId zone) {
    try {
      model.createCalendar(name, zone);
      if (!calendarNames.contains(name)) {
        calendarNames.add(name);
      }
      model.useCalendar(name);
      selectedDay = LocalDate.now(zone);
      currentMonth = YearMonth.from(selectedDay);
      refreshView();
    } catch (Exception ex) {
      handleError(ex.getMessage());
    }
  }

  @Override
  public void useCalendar(String name) {
    try {
      model.useCalendar(name);
      if (!calendarNames.contains(name)) {
        calendarNames.add(name);
      }
      selectedDay = LocalDate.now(model.currentZone());
      currentMonth = YearMonth.from(selectedDay);
      refreshView();
    } catch (Exception ex) {
      handleError(ex.getMessage());
    }
  }

  @Override
  public List<String> getCalendarNames() {
    return new ArrayList<>(calendarNames);
  }

  @Override
  public String getCurrentCalendarName() {
    try {
      return model.currentCalendarName();
    } catch (IllegalStateException ex) {
      return "<none>";
    }
  }

  @Override
  public ZoneId getCurrentCalendarZone() {
    try {
      return model.currentZone();
    } catch (IllegalStateException ex) {
      return ZoneId.systemDefault();
    }
  }

  @Override
  public void goToPreviousMonth() {
    currentMonth = currentMonth.minusMonths(1);
    selectedDay = currentMonth.atDay(
        Math.min(selectedDay.getDayOfMonth(), currentMonth.lengthOfMonth()));
    refreshView();
  }

  @Override
  public void goToNextMonth() {
    currentMonth = currentMonth.plusMonths(1);
    selectedDay = currentMonth.atDay(
        Math.min(selectedDay.getDayOfMonth(), currentMonth.lengthOfMonth()));
    refreshView();
  }

  @Override
  public void goToToday() {
    ZoneId zone = getCurrentCalendarZone();
    LocalDate today = LocalDate.now(zone);
    selectedDay = today;
    currentMonth = YearMonth.from(today);
    refreshView();
  }

  @Override
  public void selectDay(LocalDate day) {
    if (day == null) {
      return;
    }
    selectedDay = day;
    currentMonth = YearMonth.from(day);
    refreshView();
  }

  @Override
  public boolean hasEventsOn(LocalDate day) {
    if (day == null) {
      return false;
    }
    List<CalendarEvent> events = model.listEvents(day, day);
    return !events.isEmpty();
  }

  @Override
  public List<CalendarEvent> getEventsOn(LocalDate day) {
    if (day == null) {
      return List.of();
    }
    selectedDay = day;
    currentMonth = YearMonth.from(day);
    List<CalendarEvent> events = model.listEvents(day, day);
    String calName = getCurrentCalendarName();
    ZoneId zone = getCurrentCalendarZone();
    view.showMonth(currentMonth, selectedDay, events, calName, zone);
    return events;
  }

  @Override
  public void createSingleEvent(LocalDateTime start,
                                LocalDateTime end,
                                String title,
                                String description,
                                String location,
                                boolean allDay,
                                PrivacyStatus privacy) {
    try {
      if (start == null || end == null || title == null) {
        throw new IllegalArgumentException("Start, end, and title are required.");
      }
      if (!start.isBefore(end)) {
        throw new IllegalArgumentException("End time must be after start time.");
      }
      if (description == null) {
        description = "";
      }
      if (location == null) {
        location = "";
      }
      if (privacy == null) {
        privacy = PrivacyStatus.PUBLIC;
      }

      CalendarEvent ev = new CalendarEvent(
          title,
          description,
          location,
          start,
          end,
          Optional.empty(),
          Optional.empty(),
          privacy
      );
      model.addEvent(ev);
      selectDay(start.toLocalDate());
    } catch (Exception ex) {
      handleError(ex.getMessage());
    }
  }

  @Override
  @SuppressWarnings("java:S107")
  public void createRecurringEvent(LocalDateTime start,
                                   LocalDateTime end,
                                   String title,
                                   String description,
                                   String location,
                                   boolean allDay,
                                   Set<Weekday> weekdays,
                                   Integer count,
                                   LocalDate untilInclusive,
                                   PrivacyStatus privacy) {
    try {
      if (start == null || end == null || title == null) {
        throw new IllegalArgumentException("Start, end, and title are required.");
      }
      if (!start.isBefore(end)) {
        throw new IllegalArgumentException("End time must be after start time.");
      }
      if (weekdays == null || weekdays.isEmpty()) {
        throw new IllegalArgumentException("At least one weekday must be selected.");
      }
      if (count != null && count > 0 && untilInclusive != null) {
        throw new IllegalArgumentException(
            "Please specify only one of count or until date, not both.");
      }
      if ((count == null || count <= 0) && untilInclusive == null) {
        throw new IllegalArgumentException("Provide either a positive count or an 'until' date.");
      }
      if (description == null) {
        description = "";
      }
      if (location == null) {
        location = "";
      }
      if (privacy == null) {
        privacy = PrivacyStatus.PUBLIC;
      }

      EnumSet<Weekday> days = EnumSet.copyOf(weekdays);
      LocalTime seriesStartTime = start.toLocalTime();
      RecurrenceRule rule = new RecurrenceRule(days, seriesStartTime, allDay);
      UUID seriesId = UUID.randomUUID();

      LocalDate cursor = start.toLocalDate();
      int created = 0;

      if (count != null && count > 0) {
        while (created < count) {
          if (rule.matches(cursor)) {
            LocalDateTime s = cursor.atTime(start.toLocalTime());
            LocalDateTime e = cursor.atTime(end.toLocalTime());
            if (!e.isBefore(s)) {
              CalendarEvent ev = new CalendarEvent(
                  title,
                  description,
                  location,
                  s,
                  e,
                  Optional.empty(),
                  Optional.of(seriesId),
                  privacy
              );
              model.addEvent(ev);
              created++;
            }
          }
          cursor = cursor.plusDays(1);
        }
      } else {
        while (!cursor.isAfter(untilInclusive)) {
          if (rule.matches(cursor)) {
            LocalDateTime s = cursor.atTime(start.toLocalTime());
            LocalDateTime e = cursor.atTime(end.toLocalTime());
            if (!e.isBefore(s)) {
              CalendarEvent ev = new CalendarEvent(
                  title,
                  description,
                  location,
                  s,
                  e,
                  Optional.empty(),
                  Optional.of(seriesId),
                  privacy
              );
              model.addEvent(ev);
            }
          }
          cursor = cursor.plusDays(1);
        }
      }

      selectDay(start.toLocalDate());

    } catch (Exception ex) {
      handleError(ex.getMessage());
    }
  }

  @Override
  public void editEvent(CalendarEvent original, CalendarEvent updated, EditScope scope) {
    if (original == null || updated == null || scope == null) {
      handleError("Original event, updated event, and scope are required.");
      return;
    }

    try {
      switch (scope) {
        case ONE:
          applyEditOne(original, updated);
          break;
        case THIS_AND_AFTER:
          applyEditThisAndAfter(original, updated);
          break;
        case ENTIRE_SERIES:
          applyEditEntireSeries(original, updated);
          break;
        default:
          handleError("Unknown edit scope: " + scope);
          return;
      }
    } catch (IllegalArgumentException ex) {
      handleError(ex.getMessage());
      return;
    }

    refreshView();
  }

  @Override
  public void deleteEvent(CalendarEvent event) {
    if (event == null) {
      handleError("No event selected to delete.");
      return;
    }

    boolean removed = model.removeEvent(event.name(), event.start());
    if (!removed) {
      handleError("Could not find the selected event to delete.");
      return;
    }

    selectDay(event.start().toLocalDate());
  }

  private void applyEditOne(CalendarEvent original, CalendarEvent updated) {
    boolean removed = model.removeEvent(original.name(), original.start());
    if (!removed) {
      throw new IllegalArgumentException("Original event not found.");
    }

    try {
      model.addEvent(updated);
    } catch (IllegalArgumentException ex) {
      ex.getMessage();
      try {
        model.addEvent(original);
      } catch (IllegalArgumentException e) {
        e.getMessage();
      }
      throw ex;
    }

    selectDay(updated.start().toLocalDate());
  }

  private void applyEditThisAndAfter(CalendarEvent original, CalendarEvent updated) {
    List<CalendarEvent> all = model.listEvents(GLOBAL_START, GLOBAL_END);
    List<CalendarEvent> targets = new ArrayList<>();

    Optional<UUID> seriesId = original.seriesId();

    for (CalendarEvent ev : all) {
      boolean inSeries;
      if (seriesId.isPresent()) {
        inSeries = ev.seriesId().isPresent()
            && ev.seriesId().get().equals(seriesId.get());
      } else {
        inSeries = ev.name().equals(original.name());
      }

      boolean atOrAfter = !ev.start().isBefore(original.start());
      if (inSeries && atOrAfter) {
        targets.add(ev);
      }
    }

    if (targets.isEmpty()) {
      throw new IllegalArgumentException("No matching events found to edit.");
    }

    LocalDate originalDate = original.start().toLocalDate();
    LocalDate updatedDate = updated.start().toLocalDate();
    long dayShift = Duration.between(
        originalDate.atStartOfDay(),
        updatedDate.atStartOfDay()
    ).toDays();

    LocalTime newStartTime = updated.start().toLocalTime();
    LocalTime newEndTime = updated.end().toLocalTime();

    List<CalendarEvent> snapshot = new ArrayList<>(targets);

    for (CalendarEvent ev : targets) {
      boolean removed = model.removeEvent(ev.name(), ev.start());
      if (!removed) {
        throw new IllegalArgumentException("Expected event to remove but did not find it.");
      }
    }

    List<CalendarEvent> added = new ArrayList<>();

    try {
      for (CalendarEvent ev : snapshot) {
        LocalDate baseDate = ev.start().toLocalDate().plusDays(dayShift);
        LocalDateTime newStart = baseDate.atTime(newStartTime);
        LocalDateTime newEnd = baseDate.atTime(newEndTime);

        CalendarEvent newEvent = new CalendarEvent(
            updated.name(),
            updated.description(),
            updated.location(),
            newStart,
            newEnd,
            ev.recurrence(),
            ev.seriesId(),
            updated.privacy()
        );

        model.addEvent(newEvent);
        added.add(newEvent);
      }
    } catch (IllegalArgumentException ex) {
      for (CalendarEvent ev : added) {
        model.removeEvent(ev.name(), ev.start());
      }
      for (CalendarEvent ev : snapshot) {
        model.addEvent(ev);
      }
      throw ex;
    }

    selectDay(updated.start().toLocalDate());
  }

  private void applyEditEntireSeries(CalendarEvent original, CalendarEvent updated) {
    List<CalendarEvent> all = model.listEvents(GLOBAL_START, GLOBAL_END);
    List<CalendarEvent> targets = new ArrayList<>();

    Optional<UUID> seriesId = original.seriesId();

    for (CalendarEvent ev : all) {
      boolean inSeries;
      if (seriesId.isPresent()) {
        inSeries = ev.seriesId().isPresent()
            && ev.seriesId().get().equals(seriesId.get());
      } else {
        inSeries = ev.name().equals(original.name());
      }

      if (inSeries) {
        targets.add(ev);
      }
    }

    if (targets.isEmpty()) {
      throw new IllegalArgumentException("No matching events found to edit.");
    }

    LocalDate originalDate = original.start().toLocalDate();
    LocalDate updatedDate = updated.start().toLocalDate();
    long dayShift = Duration.between(
        originalDate.atStartOfDay(),
        updatedDate.atStartOfDay()
    ).toDays();

    LocalTime newStartTime = updated.start().toLocalTime();
    LocalTime newEndTime = updated.end().toLocalTime();

    List<CalendarEvent> snapshot = new ArrayList<>(targets);

    for (CalendarEvent ev : targets) {
      boolean removed = model.removeEvent(ev.name(), ev.start());
      if (!removed) {
        throw new IllegalArgumentException("Expected event to remove but did not find it.");
      }
    }

    List<CalendarEvent> added = new ArrayList<>();

    try {
      for (CalendarEvent ev : snapshot) {
        LocalDate baseDate = ev.start().toLocalDate().plusDays(dayShift);
        LocalDateTime newStart = baseDate.atTime(newStartTime);
        LocalDateTime newEnd = baseDate.atTime(newEndTime);

        CalendarEvent newEvent = new CalendarEvent(
            updated.name(),
            updated.description(),
            updated.location(),
            newStart,
            newEnd,
            ev.recurrence(),
            ev.seriesId(),
            updated.privacy()
        );

        model.addEvent(newEvent);
        added.add(newEvent);
      }
    } catch (IllegalArgumentException ex) {
      for (CalendarEvent ev : added) {
        model.removeEvent(ev.name(), ev.start());
      }
      for (CalendarEvent ev : snapshot) {
        model.addEvent(ev);
      }
      throw ex;
    }

    selectDay(updated.start().toLocalDate());
  }

  @Override
  public void handleError(String message) {
    if (message == null || message.isBlank()) {
      message = "An unknown error occurred.";
    }
    view.showError(message);
  }
}
