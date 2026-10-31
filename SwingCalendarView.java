package calendar.view;

import calendar.controller.GuiFeatures;
import calendar.model.entity.CalendarEvent;
import calendar.model.entity.EditScope;
import calendar.model.entity.PrivacyStatus;
import calendar.model.entity.Weekday;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Swing implementation of the GUI calendar view.
 *
 * <p>Provides:
 * <ul>
 *   <li>A month grid with clickable days</li>
 *   <li>Calendar selector and creation</li>
 *   <li>Buttons for previous/next month and today</li>
 *   <li>Event list for the selected day</li>
 *   <li>Dialogs for creating single and recurring events</li>
 *   <li>Dialog for editing events with EditScope choices</li>
 * </ul>
 * </p>
 */
public class SwingCalendarView extends JFrame implements GuiView {

  private GuiFeatures features;

  private JComboBox<String> calendarSelector;
  private JLabel monthLabel;
  private boolean updatingCalendarSelector;

  private JList<String> eventList;

  /**
   * Panel containing MON–SUN header labels.
   */
  private JPanel monthHeader;

  /**
   * Panel containing the clickable day cells.
   */
  private JPanel monthGrid;

  private DefaultListModel<String> eventListModel;

  /**
   * Mirror of events currently shown in the event list.
   */
  private List<CalendarEvent> currentDayEvents = new ArrayList<>();

  /**
   * Remember the currently selected day so dialogs can default to it.
   */
  private LocalDate currentSelectedDay;

  /**
   * Constructs a new Swing-based calendar view and initializes its layout.
   *
   * <p>This constructor initializes all required Swing components. Swing does not
   * support deferred UI assembly cleanly, so the layout and panels must be
   * created here, resulting in an unavoidable long constructor.</p>
   */
  public SwingCalendarView() {
    super("Calendar");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(1100, 700);
    setLayout(new BorderLayout());

    add(buildTopPanel(), BorderLayout.NORTH);
    add(buildCenterPanel(), BorderLayout.CENTER);
    add(buildRightPanel(), BorderLayout.EAST);
    add(buildBottomPanel(), BorderLayout.SOUTH);
  }

  /**
   * Format a calendar event for display in the event list in a
   * user-friendly way instead of using CalendarEvent.toString().
   */
  private String formatEventForDisplay(CalendarEvent event) {
    if (event == null) {
      return "";
    }

    LocalTime startTime = event.start().toLocalTime();
    LocalTime endTime = event.end().toLocalTime();

    boolean isAllDay = startTime.equals(LocalTime.of(8, 0))
        && endTime.equals(LocalTime.of(17, 0));

    DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a");
    String timePart = startTime.format(timeFmt) + " - " + endTime.format(timeFmt);

    String base = timePart + "  " + event.name();

    if (isAllDay) {
      return base + " (All day)";
    }

    return base;
  }

  @Override
  public void setFeatures(GuiFeatures features) {
    this.features = features;
    refreshCalendarSelector();
  }

  private JPanel buildTopPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    panel.setBorder(new EmptyBorder(10, 10, 10, 10));

    calendarSelector = new JComboBox<>();
    calendarSelector.addActionListener(e -> {
      if (updatingCalendarSelector) {
        return;
      }
      if (features != null && calendarSelector.getSelectedItem() != null) {
        features.useCalendar(calendarSelector.getSelectedItem().toString());
      }
    });


    JButton newCalButton = new JButton("New Calendar");
    newCalButton.addActionListener(e -> showCreateCalendarDialog());

    JButton prev = new JButton("<");
    prev.addActionListener(e -> {
      if (features != null) {
        features.goToPreviousMonth();
      }
    });

    JButton today = new JButton("Today");
    today.addActionListener(e -> {
      if (features != null) {
        features.goToToday();
      }
    });

    JButton next = new JButton(">");
    next.addActionListener(e -> {
      if (features != null) {
        features.goToNextMonth();
      }
    });

    monthLabel = new JLabel("Month");
    monthLabel.setFont(new Font("SansSerif", Font.BOLD, 18));

    panel.add(new JLabel("Calendar:"));
    panel.add(calendarSelector);
    panel.add(newCalButton);
    panel.add(prev);
    panel.add(today);
    panel.add(next);
    panel.add(Box.createHorizontalStrut(16));
    panel.add(monthLabel);

    return panel;
  }

  /**
   * Center panel with a dedicated header row (MON–SUN) and a 6x7 grid for days.
   */
  private JPanel buildCenterPanel() {
    JPanel container = new JPanel();
    container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

    monthHeader = new JPanel(new GridLayout(1, 7));
    monthGrid = new JPanel(new GridLayout(6, 7));

    Dimension pref = new Dimension(700, 30);
    Dimension gridPref = new Dimension(700, 600);

    monthHeader.setPreferredSize(pref);
    monthGrid.setPreferredSize(gridPref);

    monthHeader.setMaximumSize(pref);
    monthGrid.setMaximumSize(gridPref);

    container.add(monthHeader);
    container.add(monthGrid);

    return container;
  }

  private JScrollPane buildRightPanel() {
    eventListModel = new DefaultListModel<>();
    eventList = new JList<>(eventListModel);
    eventList.setPreferredSize(new Dimension(350, 0));
    return new JScrollPane(eventList);
  }

  private JPanel buildBottomPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    JButton createEvent = new JButton("Create Event");
    createEvent.addActionListener(e -> showCreateEventDialog());
    panel.add(createEvent);

    JButton createRecurring = new JButton("Create Recurring Event");
    createRecurring.addActionListener(e -> showCreateRecurringDialog());
    panel.add(createRecurring);

    JButton editEventButton = new JButton("Event Editor");
    editEventButton.addActionListener(e -> showEditSelectedEventDialog());
    panel.add(editEventButton);

    JButton deleteEventButton = new JButton("Delete Selected Event");
    deleteEventButton.addActionListener(e -> {
      if (features == null) {
        return;
      }

      int idx = eventList.getSelectedIndex();
      if (idx < 0 || idx >= currentDayEvents.size()) {
        showError("Please select an event to delete.");
        return;
      }

      CalendarEvent target = currentDayEvents.get(idx);

      int confirm = javax.swing.JOptionPane.showConfirmDialog(
          this,
          "Delete the selected event?\n\n" + target.name(),
          "Confirm Delete",
          javax.swing.JOptionPane.OK_CANCEL_OPTION,
          javax.swing.JOptionPane.WARNING_MESSAGE
      );

      if (confirm == javax.swing.JOptionPane.OK_OPTION) {
        features.deleteEvent(target);
      }
    });
    panel.add(deleteEventButton);

    return panel;
  }

  /**
   * Displays the given month and the events for the selected day in the GUI.
   *
   * @param month month to show in the grid
   * @param selectedDay currently selected day within that month
   * @param eventsForDay events scheduled on the selected day
   * @param calendarName name of the active calendar
   * @param calendarZone time zone of the active calendar
   */
  @SuppressWarnings("java:S107")
  @Override
  public void showMonth(YearMonth month,
                        LocalDate selectedDay,
                        List<CalendarEvent> eventsForDay,
                        String calendarName,
                        ZoneId calendarZone) {

    if (month == null || selectedDay == null) {
      return;
    }

    if (this.currentSelectedDay == null) {
      this.currentSelectedDay = selectedDay;
    }

    this.currentDayEvents = new ArrayList<>();
    if (eventsForDay != null) {
      this.currentDayEvents.addAll(eventsForDay);
    }

    String monthName = month.getMonth().toString();
    String prettyMonth = monthName.substring(0, 1)
        + monthName.substring(1).toLowerCase();

    monthLabel.setText(
        calendarName + " (" + calendarZone + ") - " + prettyMonth + " " + month.getYear());

    refreshCalendarSelector();

    monthHeader.removeAll();
    String[] headers = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
    for (String h : headers) {
      JLabel lbl = new JLabel(h, SwingConstants.CENTER);
      lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
      monthHeader.add(lbl);
    }

    monthGrid.removeAll();

    LocalDate firstOfMonth = month.atDay(1);
    int dayOfWeekIndex = firstOfMonth.getDayOfWeek().getValue();

    for (int i = 1; i < dayOfWeekIndex; i++) {
      monthGrid.add(new JLabel(""));
    }

    int daysInMonth = month.lengthOfMonth();
    for (int day = 1; day <= daysInMonth; day++) {
      LocalDate date = month.atDay(day);
      JButton btn = new JButton();
      btn.setMargin(new Insets(2, 4, 2, 4));

      boolean hasEvents = false;
      if (features != null) {
        try {
          hasEvents = features.hasEventsOn(date);
        } catch (Exception ex) {
          hasEvents = false;
        }
      }

      if (hasEvents) {
        btn.setText(
            "<html>"
                + "<div style='text-align:right; font-size:10px;'>&#9679;</div>"
                + "<div style='text-align:center;'>" + day + "</div>"
                + "</html>");
      } else {
        btn.setText(String.valueOf(day));
      }

      if (date.equals(selectedDay)) {
        btn.setBackground(new Color(173, 216, 230));
      }

      btn.addActionListener(e -> {
        currentSelectedDay = date;
        if (features != null) {
          features.selectDay(date);
        }
      });

      monthGrid.add(btn);
    }

    int cellsUsed = (dayOfWeekIndex - 1) + daysInMonth;
    int totalCells = 6 * 7;
    for (int i = cellsUsed; i < totalCells; i++) {
      monthGrid.add(new JLabel(""));
    }

    monthHeader.revalidate();
    monthHeader.repaint();
    monthGrid.revalidate();
    monthGrid.repaint();

    eventListModel.clear();
    if (eventsForDay != null) {
      for (CalendarEvent ev : eventsForDay) {
        eventListModel.addElement(formatEventForDisplay(ev));
      }
    }
  }

  private void showCreateCalendarDialog() {
    if (features == null) {
      return;
    }

    final JTextField nameField = new JTextField();
    final JTextField zoneField = new JTextField();
    zoneField.setText(ZoneId.systemDefault().getId());

    JPanel panel = new JPanel(new GridLayout(0, 1));
    panel.add(new JLabel("Calendar name:"));
    panel.add(nameField);
    panel.add(new JLabel("Time zone ID (e.g., America/New_York):"));
    panel.add(zoneField);

    int result = JOptionPane.showConfirmDialog(
        this,
        panel,
        "Create New Calendar",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE
    );

    if (result != JOptionPane.OK_OPTION) {
      return;
    }

    String name = nameField.getText();
    String zoneIdStr = zoneField.getText();

    if (name == null || name.isBlank()) {
      showError("Calendar name is required.");
      return;
    }

    ZoneId zone;
    try {
      zone = ZoneId.of(zoneIdStr.trim());
    } catch (Exception ex) {
      showError("Invalid time zone ID: " + zoneIdStr);
      return;
    }

    features.createCalendar(name, zone);
  }

  private void showCreateEventDialog() {
    if (features == null) {
      return;
    }

    final JTextField titleField = new JTextField();
    final JTextField descField = new JTextField();
    final JTextField locField = new JTextField();
    final JTextField startTimeField = new JTextField("09:00");
    final JTextField endTimeField = new JTextField("10:00");
    final JCheckBox allDayBox = new JCheckBox("All day");
    final JComboBox<String> privacyBox =
        new JComboBox<>(new String[] {"Public", "Private"});

    allDayBox.addItemListener(e -> {
      boolean selected = allDayBox.isSelected();
      if (selected) {
        startTimeField.setText("08:00");
        endTimeField.setText("17:00");
      }
      startTimeField.setEnabled(!selected);
      endTimeField.setEnabled(!selected);
    });

    JPanel panel = new JPanel(new GridLayout(0, 1));
    panel.add(new JLabel("Title:"));
    panel.add(titleField);
    panel.add(new JLabel("Description:"));
    panel.add(descField);
    panel.add(new JLabel("Location:"));
    panel.add(locField);
    panel.add(new JLabel("Start time (HH:MM):"));
    panel.add(startTimeField);
    panel.add(new JLabel("End time (HH:MM):"));
    panel.add(endTimeField);
    panel.add(allDayBox);
    panel.add(new JLabel("Privacy:"));
    panel.add(privacyBox);

    int result = JOptionPane.showConfirmDialog(
        this,
        panel,
        "Create Event",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE
    );

    if (result != JOptionPane.OK_OPTION) {
      JOptionPane.showMessageDialog(
          this,
          "Event not created.",
          "Cancelled",
          JOptionPane.WARNING_MESSAGE
      );
      return;
    }

    final String title = titleField.getText();
    if (title == null || title.isBlank()) {
      showError("Title is required.");
      return;
    }

    final boolean allDay = allDayBox.isSelected();

    final LocalTime startTime;
    final LocalTime endTime;
    try {
      if (allDay) {
        startTime = LocalTime.of(8, 0);
        endTime = LocalTime.of(17, 0);
      } else {
        startTime = parseTime(startTimeField.getText().trim(), LocalTime.of(9, 0));
        endTime = parseTime(endTimeField.getText().trim(), LocalTime.of(10, 0));
      }
    } catch (IllegalArgumentException ex) {
      showError(ex.getMessage());
      return;
    }

    final ZoneId zone = features.getCurrentCalendarZone();
    final LocalDate baseDate = (currentSelectedDay != null)
        ? currentSelectedDay
        : LocalDate.now(zone);

    final LocalDateTime start = baseDate.atTime(startTime);
    final LocalDateTime end = baseDate.atTime(endTime);
    if (!start.isBefore(end)) {
      showError("End time must be after start time.");
      return;
    }

    final String desc = descField.getText();
    final String loc = locField.getText();
    final String privacyStr = (String) privacyBox.getSelectedItem();
    final PrivacyStatus privacy = "Private".equalsIgnoreCase(privacyStr)
        ? PrivacyStatus.PRIVATE : PrivacyStatus.PUBLIC;

    features.createSingleEvent(start, end, title, desc, loc, allDay, privacy);
  }

  private void showCreateRecurringDialog() {
    if (features == null) {
      return;
    }

    final JTextField titleField = new JTextField();
    final JTextField descField = new JTextField();
    final JTextField locField = new JTextField();

    final JCheckBox monBox = new JCheckBox("Mon (M)");
    final JCheckBox tueBox = new JCheckBox("Tue (T)");
    final JCheckBox wedBox = new JCheckBox("Wed (W)");
    final JCheckBox thuBox = new JCheckBox("Thu (R)");
    final JCheckBox friBox = new JCheckBox("Fri (F)");
    final JCheckBox satBox = new JCheckBox("Sat (S)");
    final JCheckBox sunBox = new JCheckBox("Sun (U)");

    final JTextField countField = new JTextField();

    ZoneId zone = features.getCurrentCalendarZone();
    LocalDate baseForSuggestion = (currentSelectedDay != null)
        ? currentSelectedDay
        : LocalDate.now(zone);
    LocalDate defaultUntil = baseForSuggestion.plusWeeks(4);

    final JTextField untilField = new JTextField(defaultUntil.toString());

    JPanel untilRow = new JPanel(new BorderLayout());
    untilRow.add(untilField, BorderLayout.CENTER);
    JButton untilPickButton = new JButton("...");
    untilPickButton.setToolTipText("Open mini calendar");
    untilPickButton.setMargin(new Insets(2, 4, 2, 4));
    untilPickButton.addActionListener(e -> {
      LocalDate initial = defaultUntil;
      String txt = untilField.getText().trim();
      if (!txt.isEmpty()) {
        try {
          initial = LocalDate.parse(txt);
        } catch (Exception ex) {
          ex.getMessage();
        }
      }
      LocalDate picked = MiniDatePicker.showDialog(this, initial);
      if (picked != null) {
        untilField.setText(picked.toString());
      }
    });
    untilRow.add(untilPickButton, BorderLayout.EAST);

    final JTextField startTimeField = new JTextField("09:00");
    final JTextField endTimeField = new JTextField("10:00");

    final JCheckBox allDayBox = new JCheckBox("All day");
    final JComboBox<String> privacyBox =
        new JComboBox<>(new String[] {"Public", "Private"});

    allDayBox.addItemListener(e -> {
      boolean selected = allDayBox.isSelected();
      if (selected) {
        startTimeField.setText("08:00");
        endTimeField.setText("17:00");
      }
      startTimeField.setEnabled(!selected);
      endTimeField.setEnabled(!selected);
    });

    JPanel panel = new JPanel(new GridLayout(0, 1));
    panel.add(new JLabel("Title:"));
    panel.add(titleField);
    panel.add(new JLabel("Description:"));
    panel.add(descField);
    panel.add(new JLabel("Location:"));
    panel.add(locField);

    panel.add(new JLabel("Weekdays:"));
    JPanel daysPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    daysPanel.add(monBox);
    daysPanel.add(tueBox);
    daysPanel.add(wedBox);
    daysPanel.add(thuBox);
    daysPanel.add(friBox);
    daysPanel.add(satBox);
    daysPanel.add(sunBox);
    panel.add(daysPanel);

    panel.add(new JLabel("Count (number of occurrences, optional):"));
    panel.add(countField);
    panel.add(new JLabel("Until date (YYYY-MM-DD, optional):"));
    panel.add(untilRow);

    panel.add(new JLabel("Start time (HH:MM):"));
    panel.add(startTimeField);
    panel.add(new JLabel("End time (HH:MM):"));
    panel.add(endTimeField);

    panel.add(allDayBox);
    panel.add(new JLabel("Privacy:"));
    panel.add(privacyBox);

    int result = JOptionPane.showConfirmDialog(
        this,
        panel,
        "Create Recurring Event",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE
    );

    if (result != JOptionPane.OK_OPTION) {
      JOptionPane.showMessageDialog(
          this,
          "Events not created.",
          "Cancelled",
          JOptionPane.WARNING_MESSAGE
      );
      return;
    }

    final String title = titleField.getText();
    if (title == null || title.isBlank()) {
      showError("Title is required.");
      return;
    }

    final List<Weekday> selected = new ArrayList<>();
    if (monBox.isSelected()) {
      selected.add(Weekday.M);
    }
    if (tueBox.isSelected()) {
      selected.add(Weekday.T);
    }
    if (wedBox.isSelected()) {
      selected.add(Weekday.W);
    }
    if (thuBox.isSelected()) {
      selected.add(Weekday.R);
    }
    if (friBox.isSelected()) {
      selected.add(Weekday.F);
    }
    if (satBox.isSelected()) {
      selected.add(Weekday.S);
    }
    if (sunBox.isSelected()) {
      selected.add(Weekday.U);
    }

    if (selected.isEmpty()) {
      showError("Select at least one weekday.");
      return;
    }

    Integer count = null;
    final String countText = countField.getText().trim();
    if (!countText.isEmpty()) {
      try {
        count = Integer.parseInt(countText);
        if (count <= 0) {
          showError("Count must be a positive integer.");
          return;
        }
      } catch (NumberFormatException ex) {
        showError("Invalid count: " + countText);
        return;
      }
    }

    LocalDate until = null;
    final String untilText = untilField.getText().trim();
    if (!untilText.isEmpty()) {
      try {
        until = LocalDate.parse(untilText);
      } catch (Exception ex) {
        showError("Invalid until date: " + untilText);
        return;
      }
    }

    if (count == null && until == null) {
      showError("Provide either a count or an until date.");
      return;
    }
    if (count != null && until != null) {
      showError("Please provide either count OR until date, not both.");
      return;
    }

    final boolean allDay = allDayBox.isSelected();

    final LocalTime startTime;
    final LocalTime endTime;
    try {
      if (allDay) {
        startTime = LocalTime.of(8, 0);
        endTime = LocalTime.of(17, 0);
      } else {
        startTime = parseTime(startTimeField.getText().trim(), LocalTime.of(9, 0));
        endTime = parseTime(endTimeField.getText().trim(), LocalTime.of(10, 0));
      }
    } catch (IllegalArgumentException ex) {
      showError(ex.getMessage());
      return;
    }

    LocalDate baseDate = (currentSelectedDay != null)
        ? currentSelectedDay
        : LocalDate.now(zone);

    final LocalDateTime start = baseDate.atTime(startTime);
    final LocalDateTime end = baseDate.atTime(endTime);
    if (!start.isBefore(end)) {
      showError("End time must be after start time.");
      return;
    }

    final String desc = descField.getText();
    final String loc = locField.getText();
    final String privacyStr = (String) privacyBox.getSelectedItem();
    final PrivacyStatus privacy = "Private".equalsIgnoreCase(privacyStr)
        ? PrivacyStatus.PRIVATE : PrivacyStatus.PUBLIC;

    features.createRecurringEvent(
        start,
        end,
        title,
        desc,
        loc,
        allDay,
        EnumSet.copyOf(selected),
        count,
        until,
        privacy
    );
  }

  private void showEditSelectedEventDialog() {
    if (features == null) {
      return;
    }

    ZoneId zone = features.getCurrentCalendarZone();
    LocalDate defaultDate = (currentSelectedDay != null)
        ? currentSelectedDay
        : LocalDate.now(zone);

    String dateInput = JOptionPane.showInputDialog(
        this,
        "Enter date to edit (YYYY-MM-DD):",
        defaultDate.toString()
    );

    if (dateInput == null) {
      return;
    }

    LocalDate targetDate;
    try {
      targetDate = LocalDate.parse(dateInput.trim());
    } catch (Exception ex) {
      showError("Invalid date: " + dateInput);
      return;
    }

    List<CalendarEvent> events = features.getEventsOn(targetDate);
    currentSelectedDay = targetDate;
    currentDayEvents = new ArrayList<>(events);

    if (currentDayEvents.isEmpty()) {
      showError("No events to edit on " + targetDate + ".");
      return;
    }

    String[] options = new String[currentDayEvents.size()];
    for (int i = 0; i < currentDayEvents.size(); i++) {
      options[i] = currentDayEvents.get(i).toString();
    }

    String chosen = (String) JOptionPane.showInputDialog(
        this,
        "Select an event to edit:",
        "Edit Event",
        JOptionPane.PLAIN_MESSAGE,
        null,
        options,
        options[0]);

    if (chosen == null) {
      return;
    }

    int index = -1;
    for (int i = 0; i < options.length; i++) {
      if (options[i].equals(chosen)) {
        index = i;
        break;
      }
    }
    if (index < 0) {
      showError("Unable to determine selected event.");
      return;
    }

    CalendarEvent original = currentDayEvents.get(index);
    showEditEventDialog(original);
  }

  private void showEditEventDialog(CalendarEvent original) {
    if (features == null || original == null) {
      return;
    }

    final JTextField titleField = new JTextField(original.name());
    final JTextField descField = new JTextField(original.description());
    final JTextField locField = new JTextField(original.location());

    final LocalDate originalDate = original.start().toLocalDate();
    final JTextField dateField = new JTextField(originalDate.toString());

    JPanel dateRow = new JPanel(new BorderLayout());
    dateRow.add(dateField, BorderLayout.CENTER);
    JButton datePickButton = new JButton("...");
    datePickButton.setToolTipText("Open mini calendar");
    datePickButton.setMargin(new Insets(2, 4, 2, 4));
    datePickButton.addActionListener(e -> {
      LocalDate base = originalDate;
      String txt = dateField.getText().trim();
      if (!txt.isEmpty()) {
        try {
          base = LocalDate.parse(txt);
        } catch (Exception ex) {
          ex.getMessage();
        }
      }
      LocalDate picked = MiniDatePicker.showDialog(this, base);
      if (picked != null) {
        dateField.setText(picked.toString());
      }
    });
    dateRow.add(datePickButton, BorderLayout.EAST);

    final JTextField startTimeField =
        new JTextField(original.start().toLocalTime().toString());
    final JTextField endTimeField =
        new JTextField(original.end().toLocalTime().toString());

    final JComboBox<String> privacyBox =
        new JComboBox<>(new String[] {"Public", "Private"});
    privacyBox.setSelectedItem(
        original.privacy() == PrivacyStatus.PRIVATE ? "Private" : "Public");

    final JRadioButton scopeOne = new JRadioButton("This event only", true);
    final JRadioButton scopeThisAfter = new JRadioButton("This and after");
    final JRadioButton scopeSeries = new JRadioButton("Entire series");

    ButtonGroup scopeGroup = new ButtonGroup();
    scopeGroup.add(scopeOne);
    scopeGroup.add(scopeThisAfter);
    scopeGroup.add(scopeSeries);

    JPanel panel = new JPanel(new GridLayout(0, 1));
    panel.add(new JLabel("Date (YYYY-MM-DD):"));
    panel.add(dateRow);
    panel.add(new JLabel("Title:"));
    panel.add(titleField);
    panel.add(new JLabel("Description:"));
    panel.add(descField);
    panel.add(new JLabel("Location:"));
    panel.add(locField);
    panel.add(new JLabel("Start time (HH:MM):"));
    panel.add(startTimeField);
    panel.add(new JLabel("End time (HH:MM):"));
    panel.add(endTimeField);
    panel.add(new JLabel("Privacy:"));
    panel.add(privacyBox);
    panel.add(new JLabel("Apply change to:"));
    panel.add(scopeOne);
    panel.add(scopeThisAfter);
    panel.add(scopeSeries);

    int result = JOptionPane.showConfirmDialog(
        this,
        panel,
        "Edit Event",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE
    );

    if (result != JOptionPane.OK_OPTION) {
      JOptionPane.showMessageDialog(
          this,
          "Event not edited.",
          "Cancelled",
          JOptionPane.INFORMATION_MESSAGE
      );
      return;
    }

    final String title = titleField.getText();
    final String desc = descField.getText();
    final String loc = locField.getText();
    final String privacyStr = (String) privacyBox.getSelectedItem();
    final PrivacyStatus privacy = "Private".equalsIgnoreCase(privacyStr)
        ? PrivacyStatus.PRIVATE : PrivacyStatus.PUBLIC;

    if (title == null || title.isBlank()) {
      showError("Title is required.");
      return;
    }

    final LocalDate date;
    try {
      date = LocalDate.parse(dateField.getText().trim());
    } catch (Exception ex) {
      showError("Invalid date: " + dateField.getText().trim());
      return;
    }

    final LocalTime startTime;
    final LocalTime endTime;
    try {
      startTime = parseTime(
          startTimeField.getText().trim(), original.start().toLocalTime());
      endTime = parseTime(
          endTimeField.getText().trim(), original.end().toLocalTime());
    } catch (IllegalArgumentException ex) {
      showError(ex.getMessage());
      return;
    }

    final LocalDateTime newStart = date.atTime(startTime);
    final LocalDateTime newEnd = date.atTime(endTime);
    if (!newStart.isBefore(newEnd)) {
      showError("End time must be after start time.");
      return;
    }

    CalendarEvent updated = new CalendarEvent(
        title,
        desc,
        loc,
        newStart,
        newEnd,
        original.recurrence(),
        original.seriesId(),
        privacy
    );

    EditScope scope;
    if (scopeThisAfter.isSelected()) {
      scope = EditScope.THIS_AND_AFTER;
    } else if (scopeSeries.isSelected()) {
      scope = EditScope.ENTIRE_SERIES;
    } else {
      scope = EditScope.ONE;
    }

    features.editEvent(original, updated, scope);
  }

  private LocalTime parseTime(String text, LocalTime defaultTime) {
    if (text == null || text.isBlank()) {
      return defaultTime;
    }
    try {
      return LocalTime.parse(text);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid time: " + text);
    }
  }

  private void refreshCalendarSelector() {
    if (features == null) {
      return;
    }

    updatingCalendarSelector = true;
    try {
      calendarSelector.removeAllItems();
      List<String> names = features.getCalendarNames();
      for (String name : names) {
        calendarSelector.addItem(name);
      }

      String current = features.getCurrentCalendarName();
      if (current != null) {
        calendarSelector.setSelectedItem(current);
      }
    } finally {
      updatingCalendarSelector = false;
    }
  }

  @Override
  public void showError(String message) {
    JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
  }

  @Override
  public void showInfo(String message) {
    JOptionPane.showMessageDialog(this, message, "Info", JOptionPane.INFORMATION_MESSAGE);
  }
}
