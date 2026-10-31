# Overview

In this iteration of this project, you will build a view
for the calendar application, featuring a graphical user interface.
This will allow a user to interactively create,edit, and view events in
a digital calendar. The result of this iteration will be a calendar that
a user can interact with in a text-based interface, a GUI, as well as use scripting in headless mode.

# 1 Graphical View

## 1.1 General Constraints

1. You must use the Java Swing library to build the user interface of this application. To this end, you can use the examples discussed in the view module and any class in the official Java Swing library. You are not allowed to use any component or class that is not part of the JDK. 

2. The GUI should, at a minimum, support a *month view* of a calendar. A month view shows all the days of the current month. A user can navigate to another month in the future or in the past. You are free to add more views (e.g., weekly view, days view, etc.).

3. The GUI must expose features listed and described in the next section.

4. The GUI should have support for multiple calendars in any timezone chosen by the user.

5. You are expected to handle invalid user input via the GUI gracefully. Graceful error handling means that you must detect the cause of the error and inform the user with a useful message that does not leak implementation details but lets the user know how to fix the error.

6. The layout of the UI should be reasonable. Things should be in proper proportion, and laid out in a reasonable manner. **Buttons/text fields/labels that are oversized, or haphazardly arranged, even if functional, will result in a point deduction.**

7. Each user interaction or user input must be reasonably user-friendly  (e.g. making the user type something when a less error-prone method is  possible is not good UI design). We do not expect snazzy, sophisticated  user-friendly programs. Our standard is: can a user unfamiliar with your code and technical documentation operate the program correctly **without reading your code and technical documentation?**

8. Keep in mind that this is a graphical user interface for your program.  It is not a graphical way to use the same interaction as the text mode. The expectations of the user, and what the user is expected to enter, are not the same as when specifying script commands!

## 1.2 Expected Feature Set

The following features must be usable via your graphical user interface.

1. A user should be able to create a new calendar for a particular timezone.

2. A user should be able to select a calendar and create, edit, view events for the selected calendar.

3. A user should know which calendar they are on when interacting with the GUI. The way you distinguish a calendar is upto you. One example would be to color code the different calendars.

4. A user should not be forced to create a new calendar. Instead, the GUI should allow a user to work with a default calendar in the user's current timezone based on their system setting.

5. A user should be able to select a specific day of a month and view all events scheduled on that day in the calendar's timezone.

6. A user should be able to create a new event on a selected day of a month. The event can be a single or recurring event. For recurring events, a user should be able to specify the weekdays on which the event will repeat and the frequency in terms of number of occurrences or until an end date.

7. A user should be able to select a specific day of a month and edit events.

The user should be able to identify a single event and edit it. The user should also be able to identify multiple events with the same name, possibly from a user-specific point in time, and edit them together.


## 1.3 Design Considerations

Carefully design the interaction between a view and a controller,
and formalize the interactions with view and controller interfaces.
You may design a single controller that manages the program in
interactive, headless and GUI modes. Different controllers for different views are also possible if the views are very different from each other.
However, be mindful of the MVC principles and separation between  the model, view and controller. When designing, always ask: "can I change one part with no/minimal changes to the others?"

## 1.4 Testing

Think carefully about which parts of the program require testing. For example, you are not expected to test whether a particular button click produces the desired result. In that sense, testing the actual GUI is optional. However, you should test whether the controller does what it is supposed to in reaction to this happening.

# 2 Program Execution

## 2.1 Creating a JAR File

A user should be able to run your application using a JAR file. To create a JAR file run the command ./gradlew jar. This will create a JAR file in the build/libs directory. You can run the jar using the command java -jar build/libs/JARNAME.jar. You can provide arguments after the jar file path.

You should assume that the user will run your program from this project's root. You must ensure that file paths that your program relies on are platform independent.

## 2.2 Command-line arguments

Your program (from IntelliJ or the JAR file) should accept command-line inputs. Three command-line inputs are valid:

* `java -jar JARNAME.jar --mode headless path-of-script-file`: when invoked in this manner the program should open the script file, execute it and then exit. Invalid commands should be handled gracefully with appropriate error messages. This is how the program worked in the previous iteration.

* `java -jar JARNAME.jar --mode interactive`: when invoked in this manner the program should open in an interactive text mode, allowing the user to type the script and execute it one line at a time. This is how the program worked in the previous iteration.

* `java -jar JARNAME.jar`: when invoked in this manner the program should open the graphical user interface. This is what will happen if you simply double-click on the jar file.

Any other command-line arguments are invalid: in these cases the program should display an error message suitably and quit.

# 3 What to submit

- Submit a res/ folder with the following:
  - A screenshot showing your GUI. 
  - A `Misc.md` file with the following information:
    - `A list of changes to the design of your program, along with a brief justification of each. **Describing changes only in paragraph form will result in a point deduction.**
    - Which features work and which do not. 
    - Anything else you need us to know when we grade.
  - A txt file, commands.txt, with the list of valid commands.
  - A txt file, invalid.txt with a list of commands where at least one command is invalid.
- A USEME.md file that contains:
  - Instructions to run your program in different modes using examples.
  - a bullet-point list of how to use your GUI to use each operation supported by your program. Screenshots would be helpful, but not necessary.
- The main method must be in the class 'src/main/java/CalendarRunner.java'.
- Complete the [anonymous peer evaluation survey](https://forms.gle/11qoosf7ukmVFWuT9). You do not need to take the survey if you are working alone.

# Grading Criteria

1. The completeness, layout, and behavior of your GUI.

2. Whether your design aligns with MVC and SOLID principles.

3. Whether you have addressed issues in the previous version.

4. Well-structured and clean code with relevant documentation.

5. Avoid code smells wherever relevant.

6. Completeness and correctness of your tests as evidenced by running them and coverage metrics for the controller and model.

7. Proper access modifiers.

8. Expected formatting style.