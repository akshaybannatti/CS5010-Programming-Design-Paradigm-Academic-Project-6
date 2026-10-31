package calendar.view;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * A simple modal date picker dialog that lets the user pick a LocalDate
 * from a small month-view calendar.
 *
 * <p>These classes do not require interfaces because they represent fixed-value data types or
 * concrete view helpers with no alternative implementations, so introducing interfaces would
 * add unnecessary indirection without improving flexibility or testability.</p>
 */
public class MiniDatePicker extends JDialog {

  private LocalDate selectedDate;
  private YearMonth currentMonth;

  private final JLabel monthLabel;
  private final JPanel daysPanel;

  private MiniDatePicker(java.awt.Frame owner, LocalDate initial) {
    super(owner, "Select Date", true);
    this.currentMonth = YearMonth.from(initial != null ? initial : LocalDate.now());

    setLayout(new BorderLayout());
    setSize(420, 360);
    setLocationRelativeTo(owner);

    final JPanel top = new JPanel(new BorderLayout());
    add(top, BorderLayout.NORTH);

    JButton prev = new JButton("<");
    JButton next = new JButton(">");
    monthLabel = new JLabel("", SwingConstants.CENTER);

    prev.addActionListener(e -> {
      currentMonth = currentMonth.minusMonths(1);
      refreshCalendar();
    });
    next.addActionListener(e -> {
      currentMonth = currentMonth.plusMonths(1);
      refreshCalendar();
    });

    top.add(prev, BorderLayout.WEST);
    top.add(monthLabel, BorderLayout.CENTER);
    top.add(next, BorderLayout.EAST);

    daysPanel = new JPanel(new GridLayout(7, 7));
    add(daysPanel, BorderLayout.CENTER);

    refreshCalendar();
  }

  private void refreshCalendar() {
    daysPanel.removeAll();

    monthLabel.setText(currentMonth.getMonth() + " " + currentMonth.getYear());

    String[] headers = {"M", "T", "W", "T", "F", "S", "S"};
    for (String h : headers) {
      JLabel hdr = new JLabel(h, SwingConstants.CENTER);
      hdr.setFont(new Font("SansSerif", Font.BOLD, 12));
      daysPanel.add(hdr);
    }

    LocalDate first = currentMonth.atDay(1);
    int firstColumn = first.getDayOfWeek().getValue() - 1;

    int daysInMonth = currentMonth.lengthOfMonth();
    int cellCount = 7 * 6;

    int dayNum = 1;

    for (int cell = 0; cell < cellCount; cell++) {

      if (cell < firstColumn || dayNum > daysInMonth) {
        JLabel empty = new JLabel("");
        daysPanel.add(empty);
      } else {
        LocalDate date = currentMonth.atDay(dayNum);
        JButton btn = new JButton(String.valueOf(dayNum));
        btn.setMargin(new Insets(0, 0, 0, 0));

        btn.addActionListener(e -> {
          selectedDate = date;
          dispose();
        });

        daysPanel.add(btn);
        dayNum++;
      }
    }

    daysPanel.revalidate();
    daysPanel.repaint();
  }

  /**
   * Shows the date picker dialog and returns the chosen date, or null if cancelled.
   */
  public static LocalDate showDialog(java.awt.Frame owner, LocalDate initial) {
    MiniDatePicker picker = new MiniDatePicker(owner, initial);
    picker.setVisible(true);
    return picker.selectedDate;
  }
}
