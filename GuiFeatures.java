package calendar.controller;

import calendar.model.entity.CalendarEvent;
import calendar.model.entity.EditScope;
import calendar.model.entity.PrivacyStatus;
import calendar.model.entity.Weekday;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Features exposed to the GUI calendar view.
 *
 * <p>This interface is implemented by {@link GuiCalendarController} and is
 * called by the Swing-based view implementation.</p>
 */
public interface GuiFeatures {

  /**
   * Creates a new calendar with the given name and time zone and switches
   * the model to use it.
   *
   * @param name the calendar name
   * @param zone the calendar time zone
   */
  void createCalendar(String name, ZoneId zone);

  /**
   * Switches the model to use an existing calendar with the given name.
   *
   * @param name the calendar name
   */
  void useCalendar(String name);

  /**
   * Returns the list of known calendar names for display in the GUI.
   *
   * @return a list of calendar names
   */
  List<String> getCalendarNames();

  /**
   * Returns the name of the currently active calendar.
   *
   * @return the current calendar name
   */
  String getCurrentCalendarName();

  /**
   * Returns the time zone of the currently active calendar.
   *
   * @return the current calendar time zone
   */
  ZoneId getCurrentCalendarZone();

  /**
   * Navigates to the previous month relative to the month currently shown
   * in the GUI.
   */
  void goToPreviousMonth();

  /**
   * Navigates to the next month relative to the month currently shown
   * in the GUI.
   */
  void goToNextMonth();

  /**
   * Jumps to the current date in the active calendar's time zone.
   */
  void goToToday();

  /**
   * Selects the given day in the current calendar and updates the view.
   *
   * @param day the day to select
   */
  void selectDay(LocalDate day);

  /**
   * Returns true if there is at least one event on the given day
   * in the currently selected calendar.
   *
   * @param day the date to check
   * @return true if there is at least one event
   */
  boolean hasEventsOn(LocalDate day);

  /**
   * Returns all events on the given date in the current calendar and updates
   * the selected day in the view.
   *
   * @param day the date to query
   * @return the list of events on that date
   */
  List<CalendarEvent> getEventsOn(LocalDate day);

  /**
   * Creates a single (non-recurring) event in the current calendar.
   *
   * <p>This method has a long parameter list because the Swing view supplies each
   * field individually. The controller must match that API, so refactoring the
   * parameters is not possible without breaking the required interface.</p>
   *
   * @param start the event start date/time
   * @param end the event end date/time
   * @param title the event title
   * @param description the event description (maybe empty)
   * @param location the event location (maybe empty)
   * @param allDay whether the event is all-day
   * @param privacy the event privacy status
   */
  void createSingleEvent(LocalDateTime start,
                         LocalDateTime end,
                         String title,
                         String description,
                         String location,
                         boolean allDay,
                         PrivacyStatus privacy);

  /**
   * Creates a recurring event series starting at the given date/time.
   *
   * <p>This method has multiple parameters because it forms the main
   * boundary between the GUI and controller for recurrence creation.
   * Grouping everything into an extra parameter object would add
   * indirection without improving clarity at this scale.</p>
   *
   * @param start start date/time of the first occurrence
   * @param end end date/time of the first occurrence
   * @param title event title (required)
   * @param description optional event description
   * @param location optional event location
   * @param allDay whether the event should be treated as all day
   * @param weekdays weekdays on which the event recurs
   * @param count optional number of occurrences (may be {@code null})
   * @param untilInclusive optional end date for the recurrence (may be {@code null})
   * @param privacy privacy setting for the event
   */
  @SuppressWarnings("java:S107")
  void createRecurringEvent(LocalDateTime start,
                            LocalDateTime end,
                            String title,
                            String description,
                            String location,
                            boolean allDay,
                            Set<Weekday> weekdays,
                            Integer count,
                            LocalDate untilInclusive,
                            PrivacyStatus privacy);

  /**
   * Applies an edit to one or more events depending on the given scope.
   *
   * @param original the original event selected by the user
   * @param updated  the updated event information
   * @param scope    how broadly to apply the change
   */
  void editEvent(CalendarEvent original, CalendarEvent updated, EditScope scope);

  /**
   * Delete the given event from the current calendar.
   *
   * @param event the event to delete
   */
  void deleteEvent(CalendarEvent event);

  /**
   * Notifies the view that an error has occurred so that an appropriate
   * message may be shown to the user.
   *
   * @param message the human-readable error message
   */
  void handleError(String message);
}
