package calendar.view;

import calendar.controller.GuiFeatures;
import calendar.model.entity.CalendarEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Represents the graphical view component of the calendar application.
 *
 * <p>This interface defines all interactions that a GUI implementation must
 * support in order for a {@link GuiFeatures} controller to drive user-facing
 * updates. Implementations should display calendar data, surface errors to the
 * user in a non-technical way, and remain completely free of business logic.</p>
 *
 * <p>The view does not modify the model directly; instead, it reacts only to
 * data supplied by the controller. All user interactions (button presses,
 * selections, dialogs) should be forwarded to the controller via
 * {@link #setFeatures(GuiFeatures)}.</p>
 */
public interface GuiView {

  /**
   * Injects the set of controller callbacks that the view uses to forward
   * user interactions (e.g., clicking a day, navigating months, creating
   * events).
   *
   * <p>This method must be called exactly once before the GUI becomes visible.
   * The view must not hold or modify any model-related state on its own and
   * should rely entirely on these features to trigger controller logic.</p>
   *
   * @param features the controller-provided callbacks for all user actions;
   *                 must not be {@code null}
   * @throws IllegalArgumentException if {@code features} is {@code null}
   */
  void setFeatures(GuiFeatures features);

  /**
   * Renders the month view in the GUI, including:.
   * <ul>
   *   <li>the month grid (days laid out in weeks),</li>
   *   <li>a highlight for the selected day,</li>
   *   <li>the list of events occurring on that day,</li>
   *   <li>the active calendar's name and time zone.</li>
   * </ul>
   *
   * <p>The controller calls this method whenever navigating between months,
   * selecting a new day, switching calendars, or after creating/editing/deleting
   * events. Implementations should redraw all relevant UI components based on
   * the supplied data.</p>
   *
   * @param month the year–month being displayed; must not be {@code null}
   * @param selectedDay the currently selected day within the given month;
   *                          must not be {@code null}
   * @param eventsForDay all events occurring on {@code selectedDay}, supplied by the controller;
   *                      never {@code null} (but may be empty)
   * @param calendarName the name of the active calendar; must not be {@code null}
   * @param calendarZone the time zone of the active calendar; must not be {@code null}
   *
   * @throws IllegalArgumentException if any argument is {@code null}
   */
  void showMonth(YearMonth month,
                 LocalDate selectedDay,
                 List<CalendarEvent> eventsForDay,
                 String calendarName,
                 ZoneId calendarZone);

  /**
   * Displays an error message to the user—typically in a dialog box,
   * toast, banner, or similar UI component.
   *
   * <p>Error messages should be phrased in a user-friendly, non-technical
   * manner. The view must never show stack traces or internal exception data.</p>
   *
   * @param message the human-readable message to display; if {@code null} or blank,
   *                the view should display a generic error message
   */
  void showError(String message);

  /**
   * Optional: show informational message.
   */
  void showInfo(String message);

  /**
   * Make the window visible.
   */
  void setVisible(boolean visible);
}
