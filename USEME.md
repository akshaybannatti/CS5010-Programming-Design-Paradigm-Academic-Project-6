# build.gradle code block if required
jar {
    manifest {
        attributes(
            'Main-Class': 'CalendarRunner'
        )
    }
    archiveBaseName.set('f25-hw4-calendar')
    archiveVersion.set('')
}

# generate the jar file by executing commands from view -> tool windows -> terminal
From the project root:
./gradlew jar

# run application in GUI mode
Terminal command:
java -jar build/libs/calendar-1.0.jar

# run program in interactive mode
Terminal command:
java -jar build/libs/calendar-1.0.jar --mode interactive

# run program in headless mode
Terminal command:
java -jar build/libs/calendar-1.0.jar --mode headless commands.txt

# other terminal commands
.\gradlew run --args="--mode interactive"
.\gradlew run --args="--mode headless commands.txt" 

# Location of Design Change notes (Assignment 6)
<project root>/res//Misc.md

# Other Files
commands.txt – examples of valid commands for interactive/headless modes.
invalid.txt – examples of invalid commands and expected error behavior.
Misc.md – design changes, feature matrix, and grading notes.

# Location of Design Change notes (Assignment 5)
<project root>/res/DESIGN_CHANGES.md

# How to use the GUI
When you run the JAR with no arguments, the GUI opens.

## Layout Overview
### Top bar:
**Calendar:** dropdown – select the current calendar.
**New Calendar** button – create a new calendar in a chosen timezone.
**Navigation** buttons: ←, Today, →.
**Header label:** <Calendar Name> (Zone) – MONTH YEAR.

### Center:
Month grid on the left:
- Each cell is a day button.
- Selected day is highlighted.
- Days with events display a small dot indicator.
Event list on the right:
- Shows all events for the selected day.
- Each entry is displayed as:
  - hh:mm AM/PM - hh:mm AM/PM Title
  - For all-day events: hh:mm AM - hh:mm PM Title (All day).

### Bottom bar:
Create Event
Create Recurring Event
Edit Selected Event
Delete Selected Event (extra feature)

# Working with Calendars
## Use the default calendar
- On first launch, a calendar named Default is created in your system timezone.
- You can use it immediately without creating anything else.

## Create a new calendar
- Click New Calendar.
- Enter a name and select a timezone (e.g., America/Los_Angeles).
- Press OK.
- The new calendar will appear in the Calendar dropdown and is selected automatically.

## Switch calendars
- Use the Calendar dropdown to choose another calendar.
- The month grid and events reflect the selected calendar’s data and timezone.

# Navigating the Month View
- Previous month: click <.
- Next month: click >.
- Jump to today: click **Today**.
- The header text updates to show the currently viewed month and year for the selected calendar.

# Selecting a Day & Viewing Events
- Click on a day in the month grid.
- The selected day is highlighted.
- The event list on the right shows all events scheduled on that day in the calendar’s timezone.
- A small dot in a day cell indicates that there is at least one event on that date.

# Creating a Single Event
Select the desired day in the month grid.
Click Create Event.
In the dialog:
- Title: required.
- Description: optional.
- Location: optional.
- Date: defaults to the selected day (you can adjust if needed).
- Start time / End time:
  - Enter times in HH:MM (24-hour) format (e.g., 09:00, 17:30).
- All day:
  - If checked, start/end fields are greyed out and the event is treated as 8:00–17:00 internally.
- Privacy: choose Public or Private.
Click OK to create the event, or Cancel to abandon it.
If there is a validation problem (empty title, end before start, time format error, conflict, etc.), an error dialog is shown.

# Creating a Recurring Event
Select the base day in the month grid.
Click Create Recurring Event.
Fill in:
- Title, Description, Location.
- Date and Start / End time (same rules as single event).
- All day checkbox (same behavior).
- Weekdays: check all days of the week on which this event should repeat.
- Repetition limit:
  - Either specify a count (number of occurrences), or
  - Choose an Until date using the mini calendar button next to the date field.
- Privacy: Public or Private.
Click OK to create the series.
The system generates one event per matching date according to the chosen weekdays and count/until rule. All occurrences share a series identifier internally and can be edited together later.

# Editing Events
## Step 1 – Select an event
- Click the day in the month grid.
- In the event list, click the event you want to edit.
- Click Edit Selected Event.

## Step 2 – Modify event details
In the edit dialog you can change:
- Title, description, location.
- Date.
- Start / end times.
- Privacy.
- All-day status (via times; you can also adjust to or from the 8:00–17:00 convention).

## Step 3 – Choose edit scope
At the bottom of the dialog:
- This event only
    Edits only the selected occurrence.
- This and after
    If the event is part of a recurring series, edits all occurrences in that series from this one forward.
    The date change shifts all later occurrences by the same number of days.
    Conflicts with non-series events are detected; if a conflict occurs, the entire scope edit is rolled back and an error is shown.
- Entire series
    If the event is part of a series, edits all occurrences in that series (matched by series id).
    If it is not part of a series, operates on all events with the same name.
    Date edits shift all occurrences by the same number of days.
Click OK to apply the changes, or Cancel to keep the events unchanged.

# Deleting Events (extra feature)
Select the day.
Select the event in the list.
Click Delete Selected Event.
Confirm in the dialog.
The event is removed from the model, and the day view is refreshed.
Note: delete operates on a single event only; there is no series delete scope.

# Error Messages & Validation
Time format errors, missing title, invalid date ranges, and event conflicts all result in a user-friendly error dialog.
The GUI does not display stack traces or internal exception details.
In the text modes, errors are printed as Error: <message>.

# Example Commands
create calendar --name Work --timezone America/New_York
create calendar --name School --timezone America/Los_Angeles
use calendar --name Work

create event "Team Sync" from 2025-01-06T10:00 to 2025-01-06T10:30
create event "Onsite All Day" on 2025-01-07
create event "Standup" from 2025-01-08T09:00 to 2025-01-08T09:15 repeats MTWRF for 10 times
create event "Office Hours" from 2025-01-08T14:00 to 2025-01-08T15:00 repeats MR until 2025-02-05
create event "Holiday" on 2025-01-20 repeats MRU for 3 times
create event "Sprint Review" from 2025-01-10T11:00 to 2025-01-10T12:00

edit event description "Sprint Review" from 2025-01-10T11:00 to 2025-01-10T12:00 with "Review goals and demos"
edit events subject "Standup" from 2025-01-10T09:00 with "Daily Standup"
edit series location "Office Hours" from 2025-01-08T14:00 with "Zoom"
edit event start "Team Sync" from 2025-01-06T10:00 to 2025-01-06T10:30 with 2025-01-06T10:15

print events on 2025-01-10
print events from 2025-01-08T00:00 to 2025-01-15T23:59
show status on 2025-01-10T11:30
export cal work_jan.csv
export cal work_jan.ical

copy event "Sprint Review" on 2025-01-10T11:00 --target School to 2025-01-10T10:00
copy events on 2025-01-10 --target School to 2025-01-10
copy events between 2025-01-08 and 2025-01-20 --target School to 2025-01-13

use calendar --name School
print events on 2025-01-10
edit calendar --name School --property name Courses
use calendar --name Courses
edit calendar --name Courses --property timezone America/Chicago
print events from 2025-01-10T00:00 to 2025-01-20T23:59
use calendar --name Work

create event "Quarterly Planning" from 2025-03-03T13:00 to 2025-03-03T15:00
edit events start "Daily Standup" from 2025-01-13T09:00 with 2025-01-13T09:15
edit series subject "Office Hours" from 2025-01-08T14:00 with "CS 5010 Office Hours"
create event "Project Kickoff Day" on 2025-03-04 repeats TR until 2025-03-20
create event "Company Retreat" on 2025-04-01 repeats FSU for 3 times

show status on 2025-01-13T09:10
export cal all_work.ics
exit