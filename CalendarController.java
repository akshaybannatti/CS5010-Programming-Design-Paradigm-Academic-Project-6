package calendar.controller;

import calendar.export.CalendarExporter;
import calendar.export.CalendarExporterImpl;
import calendar.model.CalendarModel;
import calendar.model.entity.CalendarEvent;
import calendar.model.entity.EditScope;
import calendar.model.entity.PrivacyStatus;
import calendar.view.EventView;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller for the calendar application (MVC).
 *
 * <p>Parses text commands, delegates operations to the {@link calendar.model.CalendarModel},
 * and performs all user interaction through an {@link calendar.view.EventView}.
 * This keeps the model free of I/O and enforces the separation of concerns
 * required by the assignment.</p>
 *
 * <p><strong>Modes:</strong> Designed to work in both interactive (REPL) and headless
 * (scripted) modes. Commands are generally case-insensitive for keywords and ignore
 * blank lines and lines starting with {@code #}.</p>
 *
 * <p><strong>Supported commands (high level):</strong></p>
 * <ul>
 *   <li>{@code create calendar --name <calName> --timezone <Area/Location>}</li>
 *   <li>{@code edit calendar --name <calName> --property <name|timezone> <value>}</li>
 *   <li>{@code use calendar --name <calName>}</li>
 *   <li>{@code create event ...} (single or series, including all-day)</li>
 *   <li>{@code edit event|events|series ...} (subject, start, end, description,
 *       location, status)</li>
 *   <li>{@code delete event ...}, {@code delete series ...}</li>
 *   <li>{@code print events on <YYYY-MM-DD>}</li>
 *   <li>{@code print events from <YYYY-MM-DDThh:mm> to <YYYY-MM-DDThh:mm>}</li>
 *   <li>{@code show status on <YYYY-MM-DDThh:mm>}</li>
 *   <li>{@code export cal <file.csv|file.ical|file.ics>} (export delegated outside the model)</li>
 *   <li>{@code copy event ...}, {@code copy events on ...}, {@code copy events between ...}</li>
 *   <li>{@code help}, {@code exit}</li>
 * </ul>
 *
 * <p>All file-system paths accepted by commands must be platform independent.</p>
 */

public final class CalendarController implements TextController {

  private static final Pattern DT = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})T(\\d{2}:\\d{2})");
  private static final LocalTime ALL_DAY_START = LocalTime.of(8, 0);
  private static final LocalTime ALL_DAY_END = LocalTime.of(17, 0);

  private final CalendarModel model;
  private final EventView view;
  private final calendar.export.CalendarExporter exporter;

  /**
   * Creates a controller that coordinates a calendar {@link calendar.model.CalendarModel}
   * with an {@link calendar.view.EventView}.
   *
   * @param model the backing model to execute calendar operations; must not be {@code null}
   * @param view  the view used for all input and output; must not be {@code null}
   * @throws NullPointerException if {@code model} or {@code view} is {@code null}
   */
  public CalendarController(final CalendarModel model, final EventView view) {
    this(model, view, new CalendarExporterImpl());
  }

  /**
   * Creates a new {@code CalendarController} instance that coordinates interactions
   * between the {@link calendar.model.CalendarModel}, {@link calendar.view.EventView},
   * and {@link calendar.export.CalendarExporter}.
   *
   * <p>This constructor wires together the three core MVC components:</p>
   * <ul>
   *   <li><strong>Model</strong> – executes all calendar logic and state management.</li>
   *   <li><strong>View</strong> – handles all user-facing input/output (interactive or
   *       headless).</li>
   *   <li><strong>Exporter</strong> – handles file-based export operations (CSV / iCal).</li>
   * </ul>
   *
   * <p>All parameters must be non-{@code null}. The controller itself performs no validation
   * of external command formats until {@link #runInteractive()}
   *     or {@link #runHeadless(java.util.List)} is called.</p>
   *
   * @param model the backing calendar model that performs core data operations;
   *                     must not be {@code null}
   * @param view the view through which user interaction occurs (CLI, REPL, etc.);
   *                     must not be {@code null}
   * @param exporter the exporter used for saving calendar data to files; must not be {@code null}
   * @throws NullPointerException if any argument is {@code null}
   */

  public CalendarController(final CalendarModel model, final EventView view,
                            final CalendarExporter exporter) {
    this.model = model;
    this.view = view;
    this.exporter = exporter;
  }

  /**
   * Runs the controller in interactive REPL mode until the user enters {@code exit}
   * or the input stream ends.
   *
   * <p>Behavior:</p>
   * <ul>
   *   <li>Reads one line at a time from the {@link calendar.view.EventView}.</li>
   *   <li>Ignores blank lines and lines beginning with {@code #}.</li>
   *   <li>On {@code exit}, prints a farewell and returns.</li>
   *   <li>For invalid commands, prints an error and continues the loop.</li>
   * </ul>
   */
  @Override
  public void runInteractive() {
    printWelcome();
    while (true) {
      try {
        this.view.print("> ");
        final String line = this.view.readLine();
        if (line == null) {
          this.view.println("bye");
          return;
        }
        final String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        if ("exit".equalsIgnoreCase(trimmed)) {
          this.view.println("bye");
          return;
        }
        executeOne(trimmed);
      } catch (IOException ioe) {
        this.view.println("I/O error: " + ioe.getMessage());
        return;
      } catch (RuntimeException re) {
        this.view.println("Error: " + re.getMessage());
      }
    }
  }

  /**
   * Executes a finite list of commands in headless mode.
   *
   * <p>Behavior:</p>
   * <ul>
   *   <li>Processes the list in order; trims each line.</li>
   *   <li>Ignores blank lines and lines beginning with {@code #}.</li>
   *   <li>Stops early if a line equals {@code exit} (case-insensitive).</li>
   *   <li>For invalid commands, prints an error and continues with the next line.</li>
   * </ul>
   *
   * @param commands lines from a script, in order; must not be {@code null}
   * @throws NullPointerException if {@code commands} is {@code null}
   */
  @Override
  public void runHeadless(final List<String> commands) {
    for (String raw : commands) {
      final String line = raw == null ? "" : raw.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      if ("exit".equalsIgnoreCase(line)) {
        this.view.println("Bye.");
        return;
      }
      executeOne(line);
    }
    this.view.println("Error: headless file ended without 'exit'.");
  }

  /**
   * Extracts a quoted token and returns { token, remainder-after-quote }.
   *
   * @param s input string that must contain a quoted token
   * @return a 2-element array with token and remainder
   */
  private static String[] readQuotedToken(final String s) {
    final int first = s.indexOf('\"');
    if (first < 0) {
      throw new IllegalArgumentException("missing quoted title");
    }
    final int second = s.indexOf('\"', first + 1);
    if (second < 0) {
      throw new IllegalArgumentException("missing quoted title");
    }
    final String token = s.substring(first + 1, second);
    final String remainder = s.substring(second + 1).trim();
    return new String[] {token, remainder};
  }

  /**
   * Reads an event title token that may be either quoted (multi-word) or unquoted (single word).
   * Returns a two-element array: index 0 is the title,
   * index 1 is the unconsumed remainder (trimmed).
   * Examples:
   * readTitleToken("\"Office Hours\" from ...") -> {"Office Hours", "from ..."}
   * readTitleToken("Standup from ...") -> {"Standup", "from ..."}
   *
   * @param s input string (non-null)
   * @return {title, remainder}
   * @throws IllegalArgumentException if the input is empty or only a quote with no closing quote
   */
  private static String[] readTitleToken(final String s) {
    final String src = s.trim();
    if (src.isEmpty()) {
      throw new IllegalArgumentException("missing title");
    }
    if (src.startsWith("\"")) {
      return readQuotedToken(src);
    }
    final int space = src.indexOf(' ');
    if (space < 0) {
      return new String[] {src, ""};
    }
    return new String[] {src.substring(0, space), src.substring(space + 1).trim()};
  }

  private void executeOne(final String line) {
    try {
      final Runnable cmd = parse(line);
      cmd.run();
    } catch (IllegalArgumentException ex) {
      this.view.println("Error: " + ex.getMessage());
    }
  }

  private Runnable parse(final String line) {
    final String lower = line.toLowerCase(Locale.ROOT);

    if (lower.startsWith("help")) {
      return this::printHelp;
    }
    if (lower.startsWith("create calendar")) {
      return () -> doCreateCalendar(line);
    }
    if (lower.startsWith("edit calendar")) {
      return () -> doEditCalendar(line);
    }
    if (lower.startsWith("use calendar")) {
      return () -> doUseCalendar(line);
    }

    if (lower.startsWith("create event")) {
      return () -> doCreateEvent(line);
    }
    if (lower.startsWith("edit event ")) {
      return () -> doEditEvent(line.substring("edit ".length()));
    }
    if (lower.startsWith("edit events ")) {
      return () -> doBulkEdit(line.substring("edit events ".length()));
    }
    if (lower.startsWith("edit series ")) {
      return () -> doSeriesEdit(line.substring("edit series ".length()));
    }
    if (lower.startsWith("delete event ")) {
      return () -> doDeleteEvent(line);
    }
    if (lower.startsWith("delete series ")) {
      return () -> doDeleteSeries(line);
    }
    if (lower.startsWith("print events on ")) {
      return () -> doPrintDay(line);
    }
    if (lower.startsWith("print events from ")) {
      return () -> doPrintRange(line);
    }
    if (lower.startsWith("show status on ")) {
      return () -> doShowStatus(line);
    }
    if (lower.startsWith("export cal ")) {
      return () -> doExport(line);
    }
    if (lower.startsWith("copy event ")) {
      return () -> doCopyEvent(line);
    }
    if (lower.startsWith("copy events on ")) {
      return () -> doCopyEventsOn(line);
    }
    if (lower.startsWith("copy events between ")) {
      return () -> doCopyEventsBetween(line);
    }

    throw new IllegalArgumentException("Unknown command. Type 'help'.");
  }

  /**
   * Split once on the first occurrence of the delimiter (regex). Returns a.
   * two-element array: index 0 is the left part, index 1 is the right part.
   *
   * @param s the source string (non-null)
   * @param delimiterRegex the delimiter regex (non-null)
   * @return a {left, right} pair, both trimmed
   * @throws IllegalArgumentException if the delimiter is not present
   */
  private static String[] splitOnce(final String s, final String delimiterRegex) {
    final String[] parts = s.split(delimiterRegex, 2);
    if (parts.length < 2) {
      throw new IllegalArgumentException("missing delimiter: " + delimiterRegex);
    }
    return new String[] {parts[0].trim(), parts[1].trim()};
  }

  private void doCreateCalendar(final String line) {
    final String name = flagValue(line, "--name");
    final String tz = flagValue(line, "--timezone");
    this.model.createCalendar(name, ZoneId.of(tz));
    this.view.println("Created calendar '" + name + "' in zone " + tz);
  }

  private void doEditCalendar(final String line) {
    final String name = flagValue(line, "--name");
    final String prop = flagValue(line, "--property").toLowerCase(Locale.ROOT);
    final String value = lastTokenAfter(line, "--property");
    switch (prop) {
      case "name":
        this.model.renameCalendar(name, value);
        this.view.println("Renamed calendar to '" + value + "'");
        break;
      case "timezone":
        this.model.changeTimezone(name, ZoneId.of(value));
        this.view.println("Changed timezone to " + value);
        break;
      default:
        throw new IllegalArgumentException("unsupported property: " + prop);
    }
  }

  private void doUseCalendar(final String line) {
    final String name = flagValue(line, "--name");
    this.model.useCalendar(name);
    this.view.println("Using calendar: " + this.model.currentCalendarName()
        + " (" + this.model.currentZone().getId() + ")");
  }

  private void doCreateEvent(final String line) {

    final String rest = line.substring("create event ".length()).trim();
    final String[] titleAndTail = readTitleToken(rest);
    final String title = titleAndTail[0];
    String tail = titleAndTail[1];

    if (tail.startsWith("from ")) {
      final String afterFrom = tail.substring(5).trim();

      final int toIdx = afterFrom.toLowerCase(Locale.ROOT).indexOf(" to ");
      if (toIdx < 0) {
        throw new IllegalArgumentException("expected 'to' in create event");
      }

      final LocalDateTime start = parseDateTime(afterFrom.substring(0, toIdx).trim());

      final String afterTo = afterFrom.substring(toIdx + 4).trim();
      final int repIdx = afterTo.toLowerCase(Locale.ROOT).indexOf(" repeats ");

      final String endPart = (repIdx < 0) ? afterTo : afterTo.substring(0, repIdx).trim();
      final LocalDateTime end = parseDateTime(endPart);

      PrivacyStatus privacy = PrivacyStatus.PUBLIC;
      final Matcher privM = Pattern.compile("--privacy\\s+(public|private)",
          Pattern.CASE_INSENSITIVE).matcher(tail);
      if (privM.find()) {
        final String p = privM.group(1).toLowerCase(Locale.ROOT);
        privacy = p.equals("private") ? PrivacyStatus.PRIVATE : PrivacyStatus.PUBLIC;
        tail = tail.replace(privM.group(0), "").trim();
      }

      if (repIdx < 0) {
        addSingle(title, start, end, "", "");
        this.view.println("Created event '" + title + "' " + start + " -> " + end);
        return;
      }

      final String repeatsPart = afterTo.substring(repIdx + " repeats ".length()).trim();
      createRepeatingDateTime(title, start, end, repeatsPart);
      return;
    }

    if (tail.startsWith("on ")) {
      final String afterOn = tail.substring(3).trim();

      final String lowerAfterOn = afterOn.toLowerCase(Locale.ROOT);
      final String repeatsToken = " repeats ";
      final int repIdx = lowerAfterOn.indexOf(repeatsToken);

      final String dateStr;
      final String repeatsPart;

      if (repIdx >= 0) {
        dateStr = afterOn.substring(0, repIdx).trim();
        repeatsPart = afterOn.substring(repIdx + repeatsToken.length()).trim();
      } else {
        dateStr = afterOn.trim();
        repeatsPart = null;
      }

      final LocalDate day = LocalDate.parse(dateStr);
      final LocalDateTime start = day.atTime(java.time.LocalTime.of(8, 0));
      final LocalDateTime end = day.atTime(java.time.LocalTime.of(17, 0));

      PrivacyStatus privacy = PrivacyStatus.PUBLIC;
      final Matcher privM = Pattern.compile("--privacy\\s+(public|private)",
          Pattern.CASE_INSENSITIVE).matcher(tail);
      if (privM.find()) {
        final String p = privM.group(1).toLowerCase(Locale.ROOT);
        privacy = p.equals("private") ? PrivacyStatus.PRIVATE : PrivacyStatus.PUBLIC;
        tail = tail.replace(privM.group(0), "").trim();
      }

      if (repeatsPart == null || repeatsPart.isEmpty()) {
        addSingle(title, start, end, "", "");
        this.view.println("Created event '" + title + "' " + start + " -> " + end);
        return;
      }

      createRepeatingAllDay(title, day, repeatsPart);
      return;
    }

    throw new IllegalArgumentException("bad create event format");
  }

  /**
   * Formats:.
   * edit event {@literal <prop>} "Title" from {@literal <start>} [to {@literal <end>}] with.
   * {@literal <value>}
   * edit events {@literal <prop>} "Title" from {@literal <start>} with {@literal <value>}
   * edit series {@literal <prop>} "Title" from {@literal <start>} with {@literal <value>}
   */
  private void doEditEvent(final String restLine) {
    String rest = restLine.trim();
    final EditScope scope;
    if (rest.toLowerCase(Locale.ROOT).startsWith("event ")) {
      scope = EditScope.ONE;
      rest = rest.substring(6).trim();
    } else if (rest.toLowerCase(Locale.ROOT).startsWith("events ")) {
      scope = EditScope.THIS_AND_AFTER;
      rest = rest.substring(7).trim();
    } else if (rest.toLowerCase(Locale.ROOT).startsWith("series ")) {
      scope = EditScope.ENTIRE_SERIES;
      rest = rest.substring(7).trim();
    } else {
      throw new IllegalArgumentException("Missing scope: event|events|series");
    }

    final int sp = rest.indexOf(' ');
    if (sp < 0) {
      throw new IllegalArgumentException("Missing property");
    }
    final String prop = rest.substring(0, sp).toLowerCase(Locale.ROOT).trim();
    rest = rest.substring(sp + 1).trim();

    final String[] parsed = readTitleToken(rest);
    final String title = parsed[0];
    rest = parsed[1];

    if (!rest.toLowerCase(Locale.ROOT).startsWith("from ")) {
      throw new IllegalArgumentException("Expected 'from'");
    }
    rest = rest.substring(5).trim();

    String startStr;
    String afterStart;
    final String lower = rest.toLowerCase(Locale.ROOT);
    final int toIx = lower.indexOf(" to ");
    if (toIx >= 0) {
      startStr = rest.substring(0, toIx).trim();
      afterStart = rest.substring(toIx + 4).trim();
    } else {
      afterStart = rest;
      startStr = null;
    }

    String endStr = null;
    String valueStr;
    final String l2 = afterStart.toLowerCase(Locale.ROOT);
    final int withIx = l2.indexOf(" with ");
    if (withIx < 0) {
      throw new IllegalArgumentException("missing 'with'");
    }
    if (toIx >= 0) {
      endStr = afterStart.substring(0, withIx).trim();
    } else {
      if (startStr == null) {
        startStr = afterStart.substring(0, withIx).trim();
      }
    }
    valueStr = afterStart.substring(withIx + 6).trim();
    if (valueStr.isEmpty()) {
      throw new IllegalArgumentException("missing value");
    }
    if (valueStr.startsWith("\"") && valueStr.endsWith("\"") && valueStr.length() >= 2) {
      valueStr = valueStr.substring(1, valueStr.length() - 1);
    }

    final LocalDateTime startDt = parseDateTime(startStr);
    final Optional<LocalDateTime> endDt =
        (endStr == null || endStr.isEmpty()) ? Optional.empty() :
            Optional.of(parseDateTime(endStr));

    switch (prop) {
      case "subject":
        applyEdit("subject", title, startDt, endDt, valueStr);
        break;
      case "description":
        applyEdit("description", title, startDt, endDt, valueStr);
        break;
      case "location":
        applyEdit("location", title, startDt, endDt, valueStr);
        break;
      case "start":
        applyEdit("start", title, startDt, endDt, valueStr);
        break;
      case "end":
        applyEdit("end", title, startDt, endDt, valueStr);
        break;
      case "status":
        applyEdit("status", title, startDt, endDt, valueStr);
        break;
      default:
        throw new IllegalArgumentException("Unsupported property: " + prop);
    }
  }

  private void doBulkEdit(final String restLine) {
    final String[] parts = restLine.trim().split("\\s+", 2);
    if (parts.length < 2) {
      throw new IllegalArgumentException("missing property");
    }
    final String prop = parts[0].toLowerCase(Locale.ROOT);
    final String rest = parts[1];

    final String[] titleAndRest = readTitleToken(rest);
    final String title = titleAndRest[0];
    String afterTitle = titleAndRest[1];

    if (!afterTitle.toLowerCase(java.util.Locale.ROOT).startsWith("from ")) {
      throw new IllegalArgumentException("missing 'from'");
    }

    afterTitle = afterTitle.substring(5).trim();

    final String afterFrom = afterTitle;

    final int withIdx = afterFrom.toLowerCase(Locale.ROOT).indexOf(" with ");
    if (withIdx < 0) {
      throw new IllegalArgumentException("missing 'with'");
    }

    final LocalDateTime threshold = parseDateTime(afterFrom.substring(0, withIdx).trim());

    final String value = afterFrom.substring(withIdx + 6).trim();

    final LocalDate fromDate = threshold.toLocalDate();
    final List<CalendarEvent> events = this.model.listEvents(fromDate, fromDate.plusYears(50));
    for (CalendarEvent ev : events) {
      if (ev.name().equals(title) && !ev.start().isBefore(threshold)) {
        final CalendarEvent updated = mutate(ev, prop, value, Optional.empty());
        replaceSingle(ev, updated);
      }
    }
    this.view.println("OK");
  }

  /**
   * Handles {@code edit series} operations.
   *
   * <p>Expected syntax:</p>
   * <pre>
   * edit series {subject|description|location|start|end} "Title"
   *     from {@code yyyy-MM-dd'T'HH:mm} with {@literal VALUE}
   * </pre>
   *
   * <p>The edit is applied to all occurrences belonging to the same series as the event
   * identified by the given Title and Start datetime.</p>
   *
   * @param restLine the text following the initial {@code "edit series "} prefix; never null
   * @throws IllegalArgumentException if parsing fails or the referenced event cannot be found
   */
  private void doSeriesEdit(final String restLine) {
    final String[] parts = restLine.trim().split("\\s+", 2);
    if (parts.length < 2) {
      throw new IllegalArgumentException("missing property");
    }
    final String prop = parts[0].toLowerCase(java.util.Locale.ROOT);
    final String rest = parts[1];

    final String[] titleAndRest = readTitleToken(rest);
    final String title = titleAndRest[0];
    String afterTitle = titleAndRest[1].trim();

    if (!afterTitle.toLowerCase(java.util.Locale.ROOT).startsWith("from ")) {
      throw new IllegalArgumentException("missing 'from'");
    }
    afterTitle = afterTitle.substring(5).trim();

    final int withIdx = afterTitle.toLowerCase(java.util.Locale.ROOT).indexOf(" with ");
    if (withIdx < 0) {
      throw new IllegalArgumentException("missing 'with'");
    }
    final String startStr = afterTitle.substring(0, withIdx).trim();
    String value = afterTitle.substring(withIdx + 6).trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("missing value");
    }
    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
      value = value.substring(1, value.length() - 1);
    }

    final java.time.LocalDateTime startDt = parseDateTime(startStr);

    final java.time.LocalDate day = startDt.toLocalDate();
    final java.util.List<CalendarEvent> todays = this.model.listEvents(day, day);
    CalendarEvent seed = null;
    for (CalendarEvent ev : todays) {
      if (ev.name().equals(title) && ev.start().equals(startDt)) {
        seed = ev;
        break;
      }
    }
    if (seed == null) {
      throw new IllegalArgumentException("event not found");
    }
    if (seed.seriesId() == null) {
      throw new IllegalArgumentException("not a series event");
    }
    final Optional<UUID> seriesId = seed.seriesId();

    final java.time.LocalDate from = day.minusYears(10);
    final java.time.LocalDate to = day.plusYears(50);
    final java.util.List<CalendarEvent> all = this.model.listEvents(from, to);
    for (CalendarEvent ev : all) {
      if (seriesId.equals(ev.seriesId())) {
        final CalendarEvent updated = mutate(ev, prop, value, java.util.Optional.empty());
        replaceSingle(ev, updated);
      }
    }
    this.view.println("OK");
  }

  private void doDeleteEvent(final String line) {
    final String name = flagValue(line, "--name");
    final String startStr = flagValue(line, "--start");
    final boolean removed = this.model.removeEvent(name, parseDateTime(startStr));
    if (!removed) {
      throw new IllegalArgumentException("event not found");
    }
    this.view.println("Deleted");
  }

  private void doDeleteSeries(final String line) {
    final String name = flagValue(line, "--name");
    final LocalDateTime threshold = parseDateTime(flagValue(line, "--start"));
    final LocalDate from = threshold.toLocalDate();
    final List<CalendarEvent> events = this.model.listEvents(from, from.plusYears(50));
    int n = 0;
    for (CalendarEvent ev : new ArrayList<>(events)) {
      if (ev.name().equals(name) && !ev.start().isBefore(threshold)) {
        if (this.model.removeEvent(ev.name(), ev.start())) {
          n++;
        }
      }
    }
    this.view.println("Deleted " + n + " events");
  }

  private void doPrintDay(final String line) {
    final LocalDate day = LocalDate.parse(line.substring("print events on ".length()).trim());
    final List<CalendarEvent> events = this.model.listEvents(day, day);
    if (events.isEmpty()) {
      this.view.println("(no events)");
      return;
    }
    for (CalendarEvent ev : events) {
      this.view.println("- " + formatEvent(ev));
    }
  }

  private void doPrintRange(final String line) {
    final String after = line.substring("print events from ".length());
    final int toIdx = after.toLowerCase(Locale.ROOT).indexOf(" to ");
    if (toIdx < 0) {
      throw new IllegalArgumentException("missing 'to'");
    }
    final LocalDateTime a = parseDateTime(after.substring(0, toIdx).trim());
    final LocalDateTime b = parseDateTime(after.substring(toIdx + 4).trim());
    final LocalDate start = a.toLocalDate();
    final LocalDate end = b.toLocalDate();
    final List<CalendarEvent> events = this.model.listEvents(start, end);
    boolean any = false;
    for (CalendarEvent ev : events) {
      if (!ev.end().isBefore(a) && !ev.start().isAfter(b)) {
        this.view.println("- " + formatEvent(ev));
        any = true;
      }
    }
    if (!any) {
      this.view.println("(no events)");
    }
  }

  private void doShowStatus(final String line) {
    final LocalDateTime t = parseDateTime(line.substring("show status on ".length()).trim());
    final LocalDate day = t.toLocalDate();
    final List<CalendarEvent> events = this.model.listEvents(day, day);
    for (CalendarEvent ev : events) {
      if (!t.isBefore(ev.start()) && t.isBefore(ev.end())) {
        this.view.println("BUSY");
        return;
      }
    }
    this.view.println("FREE");
  }

  private void doExport(final String line) {
    final String after = line.substring("export cal ".length()).trim();
    final Path path = Paths.get(after).toAbsolutePath();

    try {
      final var events = this.model.listEvents(LocalDate.MIN, LocalDate.MAX);
      final var zone = this.model.currentZone();

      final String lower = after.toLowerCase(Locale.ROOT);
      if (lower.endsWith(".csv")) {
        this.exporter.writeCsv(path, events, zone);
      } else if (lower.endsWith(".ical") || lower.endsWith(".ics")) {
        this.exporter.writeIcal(path, events, zone);
      } else {
        throw new IllegalArgumentException("unsupported export format (use .csv or .ical/.ics)");
      }

      this.view.println("Exported: " + path);
    } catch (Exception ex) {
      throw new IllegalArgumentException("export failed: " + ex.getMessage());
    }
  }

  private void doCopyEvent(final String line) {
    final String rest = line.substring("copy event ".length()).trim();
    final String title = quotedFirst(rest);
    final String afterTitle = rest.substring(firstClosingQuoteIndex(rest) + 1).trim();
    if (!afterTitle.toLowerCase(Locale.ROOT).startsWith("on ")) {
      throw new IllegalArgumentException("missing 'on'");
    }
    final String afterOn = afterTitle.substring(3).trim();
    final int tgtIdx = afterOn.toLowerCase(Locale.ROOT).indexOf(" --target ");
    if (tgtIdx < 0) {
      throw new IllegalArgumentException("missing --target");
    }
    final LocalDateTime srcStart = parseDateTime(afterOn.substring(0, tgtIdx).trim());
    final String afterTarget = afterOn.substring(tgtIdx + " --target ".length());
    final int toIdx = afterTarget.toLowerCase(Locale.ROOT).indexOf(" to ");
    if (toIdx < 0) {
      throw new IllegalArgumentException("missing 'to'");
    }
    final String targetCal = afterTarget.substring(0, toIdx).trim();
    final LocalDateTime dstStart = parseDateTime(afterTarget.substring(toIdx + 4).trim());

    this.model.copyEvent(title, srcStart, targetCal, dstStart);
    this.view.println(
        "Copied event '" + title + "' to calendar '" + targetCal + "' at " + dstStart);
  }

  private void doCopyEventsOn(final String line) {
    final String afterOn = line.substring("copy events on ".length());
    final int tgtIdx = afterOn.toLowerCase(Locale.ROOT).indexOf(" --target ");
    if (tgtIdx < 0) {
      throw new IllegalArgumentException("missing --target");
    }
    final LocalDate srcDay = LocalDate.parse(afterOn.substring(0, tgtIdx).trim());
    final String afterTarget = afterOn.substring(tgtIdx + " --target ".length());
    final int toIdx = afterTarget.toLowerCase(Locale.ROOT).indexOf(" to ");
    if (toIdx < 0) {
      throw new IllegalArgumentException("missing 'to'");
    }
    final String targetCal = afterTarget.substring(0, toIdx).trim();
    final LocalDate dstDay = LocalDate.parse(afterTarget.substring(toIdx + 4).trim());
    this.model.copyEventsOnDate(srcDay, targetCal, dstDay);
    this.view.println("Copied events on " + srcDay + " to '" + targetCal + "' at " + dstDay);
  }

  private void doCopyEventsBetween(final String line) {
    final String after = line.substring("copy events between ".length());
    final int andIdx = after.toLowerCase(Locale.ROOT).indexOf(" and ");
    if (andIdx < 0) {
      throw new IllegalArgumentException("missing 'and'");
    }
    final LocalDate d1 = LocalDate.parse(after.substring(0, andIdx).trim());
    final String afterAnd = after.substring(andIdx + 5).trim();
    final int tgtIdx = afterAnd.toLowerCase(Locale.ROOT).indexOf(" --target ");
    if (tgtIdx < 0) {
      throw new IllegalArgumentException("missing --target");
    }
    final LocalDate d2 = LocalDate.parse(afterAnd.substring(0, tgtIdx).trim());
    final String afterTarget = afterAnd.substring(tgtIdx + " --target ".length());
    final int toIdx = afterTarget.toLowerCase(Locale.ROOT).indexOf(" to ");
    if (toIdx < 0) {
      throw new IllegalArgumentException("missing 'to'");
    }
    final String targetCal = afterTarget.substring(0, toIdx).trim();
    final LocalDate dstStart = LocalDate.parse(afterTarget.substring(toIdx + 4).trim());
    this.model.copyEventsBetween(d1, d2, targetCal, dstStart);
    this.view.println("Copied events " + d1 + " to " + d2 + " -> '"
        + targetCal + "' starting " + dstStart);
  }

  private static LocalDateTime parseDateTime(final String s) {
    final Matcher m = DT.matcher(s);
    if (!m.matches()) {
      throw new IllegalArgumentException("Expect YYYY-MM-DDThh:mm");
    }
    return LocalDate.parse(m.group(1)).atTime(LocalTime.parse(m.group(2)));
  }

  private static String quotedFirst(final String rest) {
    if (!rest.startsWith("\"")) {
      throw new IllegalArgumentException("missing quoted title");
    }
    final int end = firstClosingQuoteIndex(rest);
    return rest.substring(1, end);
  }

  private static int firstClosingQuoteIndex(final String rest) {
    final int end = rest.indexOf("\"", 1);
    if (end < 0) {
      throw new IllegalArgumentException("missing closing quote");
    }
    return end;
  }

  private static String flagValue(final String line, final String flag) {
    final int i =
        line.toLowerCase(Locale.ROOT).indexOf((" " + flag + " ").toLowerCase(Locale.ROOT));
    if (i < 0) {
      throw new IllegalArgumentException("missing " + flag);
    }
    final String after = line.substring(i + flag.length() + 2);
    final String[] parts = after.trim().split("\\s+");
    if (parts.length < 1) {
      throw new IllegalArgumentException("missing value for " + flag);
    }
    String v = parts[0];
    if (v.startsWith("\"")) {
      final int start = line.indexOf('"', i);
      final int end = line.indexOf('"', start + 1);
      if (start >= 0 && end > start) {
        return line.substring(start + 1, end);
      }
    }
    return v;
  }

  private static String lastTokenAfter(final String line, final String flag) {
    final int i =
        line.toLowerCase(Locale.ROOT).indexOf((" " + flag + " ").toLowerCase(Locale.ROOT));
    if (i < 0) {
      throw new IllegalArgumentException("missing " + flag);
    }
    final String after = line.substring(i + flag.length() + 2).trim();
    final String[] parts = after.split("\\s+");
    if (parts.length < 2) {
      throw new IllegalArgumentException("missing new property value");
    }
    return parts[1];
  }

  private static DayOfWeek letterToDow(final char c) {
    switch (Character.toUpperCase(c)) {
      case 'M':
        return DayOfWeek.MONDAY;
      case 'T':
        return DayOfWeek.TUESDAY;
      case 'W':
        return DayOfWeek.WEDNESDAY;
      case 'R':
        return DayOfWeek.THURSDAY;
      case 'F':
        return DayOfWeek.FRIDAY;
      case 'S':
        return DayOfWeek.SATURDAY;
      case 'U':
        return DayOfWeek.SUNDAY;
      default:
        throw new IllegalArgumentException("bad weekday letter: " + c);
    }
  }

  private static EnumSet<DayOfWeek> parseLetters(final String letters) {
    if (letters == null || letters.trim().isEmpty()) {
      throw new IllegalArgumentException("empty weekday letters");
    }
    final EnumSet<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
    for (char c : letters.trim().toCharArray()) {
      set.add(letterToDow(c));
    }
    return set;
  }

  private void createRepeatingDateTime(final String title,
                                       final LocalDateTime start,
                                       final LocalDateTime end,
                                       final String repeatsPart) {
    final String rp = repeatsPart.trim();
    if (rp.isEmpty()) {
      addSingle(title, start, end, "", "");
      return;
    }
    if (!rp.toLowerCase(Locale.ROOT).startsWith("repeats ")) {
      addSingle(title, start, end, "", "");
      return;
    }

    final String afterRepeats = rp.substring("repeats ".length()).trim();
    if (afterRepeats.toLowerCase(Locale.ROOT).contains(" for ")) {
      final String[] p = afterRepeats.split("\\s+for\\s+");
      final EnumSet<DayOfWeek> dows = parseLetters(p[0].trim());
      final String countPart = p[1].trim();
      final int times = Integer.parseInt(countPart.replaceAll("\\D+", ""));
      expandByCount(title, start, end, dows, times);
      this.view.println("Created series '" + title + "' count=" + times);
      return;
    }
    if (afterRepeats.toLowerCase(Locale.ROOT).contains(" until ")) {
      final String[] p = afterRepeats.split("\\s+until\\s+");
      final EnumSet<DayOfWeek> dows = parseLetters(p[0].trim());
      final LocalDate until = LocalDate.parse(p[1].trim());
      expandUntil(title, start, end, dows, until);
      this.view.println("Created series '" + title + "' until=" + until);
      return;
    }
    throw new IllegalArgumentException("bad repeats clause");
  }

  private void createRepeatingAllDay(final String title,
                                     final LocalDate day,
                                     final String repeatsPart) {
    final LocalDateTime s0 = day.atTime(ALL_DAY_START);
    final LocalDateTime e0 = day.atTime(ALL_DAY_END);
    createRepeatingDateTime(title, s0, e0, "repeats " + repeatsPart);
  }

  private void expandByCount(final String title,
                             final LocalDateTime firstStart,
                             final LocalDateTime firstEnd,
                             final EnumSet<DayOfWeek> dows,
                             final int times) {
    int made = 0;
    LocalDate cursor = firstStart.toLocalDate();
    while (made < times) {
      final DayOfWeek dow = cursor.getDayOfWeek();
      if (dows.contains(dow)) {
        final LocalDateTime s = cursor.atTime(firstStart.toLocalTime());
        final LocalDateTime e = cursor.atTime(firstEnd.toLocalTime());
        if (!e.isBefore(s)) {
          addSingle(title, s, e, "", "");
          made++;
        }
      }
      cursor = cursor.plusDays(1);
    }
  }

  private void expandUntil(final String title,
                           final LocalDateTime firstStart,
                           final LocalDateTime firstEnd,
                           final EnumSet<DayOfWeek> dows,
                           final LocalDate untilInclusive) {
    LocalDate cursor = firstStart.toLocalDate();
    while (!cursor.isAfter(untilInclusive)) {
      final DayOfWeek dow = cursor.getDayOfWeek();
      if (dows.contains(dow)) {
        final LocalDateTime s = cursor.atTime(firstStart.toLocalTime());
        final LocalDateTime e = cursor.atTime(firstEnd.toLocalTime());
        if (!e.isBefore(s)) {
          addSingle(title, s, e, "", "");
        }
      }
      cursor = cursor.plusDays(1);
    }
  }

  private void addSingle(final String title,
                         final LocalDateTime start,
                         final LocalDateTime end,
                         final String desc,
                         final String loc) {
    if (end.isBefore(start)) {
      throw new IllegalArgumentException("end before start");
    }
    final CalendarEvent ev = new CalendarEvent(
        title,
        desc,
        loc,
        start,
        end,
        Optional.empty(),
        Optional.<UUID>empty());
    this.model.addEvent(ev);
  }

  private void applyEdit(final String prop,
                         final String title,
                         final LocalDateTime start,
                         final Optional<LocalDateTime> endIfProvided,
                         final String value) {
    final CalendarEvent existing = this.model.findEvent(title, start)
        .orElseThrow(() -> new IllegalArgumentException("event not found"));

    CalendarEvent updated;
    if ("start".equals(prop)) {
      final LocalDateTime newStart = parseDateTime(value);
      updated = new CalendarEvent(
          existing.name(), existing.description(), existing.location(),
          newStart, endIfProvided.orElse(existing.end()), existing.recurrence(),
          existing.seriesId());
    } else {
      updated = mutate(existing, prop, value, endIfProvided);
    }
    replaceSingle(existing, updated);
    this.view.println("Edited '" + title + "' property " + prop);
  }

  private CalendarEvent mutate(final CalendarEvent ev,
                               final String prop,
                               final String value,
                               final Optional<LocalDateTime> endIfProvided) {
    final String name = ev.name();
    final String desc = ev.description();
    final String loc = ev.location();
    final LocalDateTime s = ev.start();
    final LocalDateTime e = endIfProvided.orElse(ev.end());

    switch (prop) {
      case "description":
        return new CalendarEvent(name, value, loc, s, e, ev.recurrence(), ev.seriesId());
      case "subject":
        return new CalendarEvent(value, desc, loc, s, e, ev.recurrence(), ev.seriesId());
      case "location":
        return new CalendarEvent(name, desc, value, s, e, ev.recurrence(), ev.seriesId());
      case "start":
        {
        final LocalDateTime newStart = parseDateTime(value);
        return new CalendarEvent(name, desc, loc, newStart, e, ev.recurrence(), ev.seriesId());
        }
      case "end":
        {
        final LocalDateTime newEnd = parseDateTime(value);
        return new CalendarEvent(name, desc, loc, s, newEnd, ev.recurrence(), ev.seriesId());
        }
      case "status":
        {
        final PrivacyStatus ps = PrivacyStatus.fromString(value);
        return new CalendarEvent(name, desc, loc, s, e, ev.recurrence(), ev.seriesId(), ps);
        }
      default:
        throw new IllegalArgumentException("unsupported edit property: " + prop);
    }
  }

  private void replaceSingle(final CalendarEvent oldEv, final CalendarEvent updated) {
    final boolean removed = this.model.removeEvent(oldEv.name(), oldEv.start());
    if (!removed) {
      throw new IllegalStateException("failed to replace event");
    }
    this.model.addEvent(updated);
  }

  private String formatEvent(final CalendarEvent ev) {
    final ZoneId zone = this.model.currentZone();
    final ZonedDateTime zStart = ev.start().atZone(zone);
    final ZonedDateTime zEnd = ev.end().atZone(zone);

    final String startDate = zStart.toLocalDate().toString();
    final String startTime = zStart.toLocalTime().toString();
    final String endDate = zEnd.toLocalDate().toString();
    final String endTime = zEnd.toLocalTime().toString();

    final String loc = (ev.location() == null || ev.location().isBlank())
        ? ""
        : " at " + ev.location();

    return String.format("%s starting on %s at %s, ending on %s at %s%s",
        ev.name(), startDate, startTime, endDate, endTime, loc);
  }

  private void printWelcome() {
    this.view.println("Calendar ready. Type 'help' for commands, 'exit' to quit.");
  }

  private void printHelp() {
    this.view.println("Commands:");
    this.view.println("  create calendar --name <calName> --timezone <Area/Location>");
    this.view.println("  edit calendar --name <calName> --property name <newName>");
    this.view.println("  edit calendar --name <calName> --property timezone <Area/Location>");
    this.view.println("  use calendar --name <calName>");
    this.view.println("  create event \"Title\" from YYYY-MM-DDThh:mm to YYYY-MM-DDThh:mm");
    this.view.println("  create event \"Title\" on YYYY-MM-DD");
    this.view.println("  create event ... repeats MTWRFSU for N times");
    this.view.println("  create event ... repeats MTWRFSU until YYYY-MM-DD");
    this.view.println(
        "  edit event subject|description|location|start \"Title\" from <start> [to <end>] "
            + "with <value>");
    this.view.println("  edit events subject|location \"Title\" from <start> with <value>");
    this.view.println("  delete event --name \"Title\" --start <YYYY-MM-DDThh:mm>");
    this.view.println("  delete series --name \"Title\" --start <YYYY-MM-DDThh:mm>");
    this.view.println("  print events on YYYY-MM-DD");
    this.view.println("  print events from YYYY-MM-DDThh:mm to YYYY-MM-DDThh:mm");
    this.view.println("  show status on YYYY-MM-DDThh:mm");
    this.view.println("  export cal <file.csv|file.ical>");
    this.view.println("  copy event \"Title\" on <start> --target <cal> to <YYYY-MM-DDThh:mm>");
    this.view.println("  copy events on <YYYY-MM-DD> --target <cal> to <YYYY-MM-DD>");
    this.view.println(
        "  copy events between <YYYY-MM-DD> and <YYYY-MM-DD> --target <cal> to <YYYY-MM-DD>");
    this.view.println("  help");
    this.view.println("  exit");
  }
}
