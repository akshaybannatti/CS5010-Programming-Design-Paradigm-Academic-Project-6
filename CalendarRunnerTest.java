import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test fixture for {@code CalendarRunner}.
 *
 * <p>This class captures {@code System.out} and {@code System.err} output streams
 * for verification during tests. It also restores the original streams after each
 * test case to prevent side effects between tests.</p>
 *
 * <p>Each test runs with:</p>
 * <ul>
 *   <li>A fresh {@link ByteArrayOutputStream} for standard output and error.</li>
 *   <li>Standard input optionally replaced by test data.</li>
 * </ul>
 */
public class CalendarRunnerTest {

  private PrintStream origOut;
  private PrintStream origErr;
  private InputStream origIn;

  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;

  /**
   * Sets up the test fixture by redirecting {@code System.out} and {@code System.err}
   * to in-memory buffers so printed output can be inspected.
   *
   * <p>Executed before each test method.</p>
   */
  @Before
  public void setUp() {
    origOut = System.out;
    origErr = System.err;
    origIn = System.in;

    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();

    System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
  }

  /**
   * Restores the original {@code System.out}, {@code System.err}, and {@code System.in}
   * streams after each test and closes temporary output buffers.
   *
   * @throws IOException if closing the output streams fails
   */
  @After
  public void tearDown() throws IOException {
    System.setOut(origOut);
    System.setErr(origErr);
    System.setIn(origIn);
    outBuf.close();
    errBuf.close();
  }

  private void runMain(String[] args, String stdin, long timeoutMillis) {
    final Thread t = new Thread(() -> {
      if (stdin != null) {
        System.setIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
      } else {
        System.setIn(new ByteArrayInputStream(new byte[0]));
      }
      CalendarRunner.main(args);
    });
    t.start();
    try {
      t.join(timeoutMillis);
      if (t.isAlive()) {
        t.interrupt();
        fail("main() appears to hang (exceeded " + timeoutMillis + " ms)");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail("test thread interrupted");
    }
  }

  private String stdout() {
    return new String(outBuf.toByteArray(), StandardCharsets.UTF_8);
  }

  private String stderr() {
    return new String(errBuf.toByteArray(), StandardCharsets.UTF_8);
  }

  /** Lowercases both streams and concatenates so we don’t care which stream was used. */
  private String combinedLower() {
    return (stdout() + "\n" + stderr()).toLowerCase();
  }

  private static Path writeTempFile(List<String> lines) throws IOException {
    Path dir = Files.createTempDirectory("runnerTests");
    Path file = dir.resolve("commands.txt");
    Files.write(file, lines, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    return file;
  }

  /** Asserts hay contains ANY of the needles. Shows the combined text on failure. */
  private void assertContainsAny(String hayLower, String... needlesLower) {
    for (String n : needlesLower) {
      if (hayLower.contains(n)) {
        return;
      }
    }
    fail("Expected one of " + Arrays.toString(needlesLower) + " in:\n" + hayLower);
  }

  @Test
  public void interactiveMode_isCaseInsensitive_andExitsOnExit() {
    String stdin = "# comment\n\nexit\n";
    runMain(new String[]{"--mode", "InTeRaCtIvE"}, stdin, 2000);
    String out = combinedLower();
    assertFalse(out.contains("unsupported"));
    assertFalse(out.contains("headless file"));
    assertTrue(stdout().length() >= 0);
  }

  @Test
  public void interactiveMode_ignoresBlankLinesAndComments_thenExit() {
    String stdin = String.join("\n", Arrays.asList("   ", "# comment", "", "exit", ""));
    runMain(new String[]{"--mode", "interactive"}, stdin, 2000);
    String out = combinedLower();
    assertFalse(out.contains("unsupported"));
    assertFalse(out.contains("headless file"));
  }

  @Test
  public void headlessMode_honorsCommentsAndBlanks_andStopsOnExit() throws IOException {
    Path script = writeTempFile(Arrays.asList(
        "# a comment line", "", "use calendar --name Work", "exit", ""
    ));
    runMain(new String[]{"--mode", "headless", script.toString()}, null, 2000);
    String out = combinedLower();
    assertFalse(out.contains("ended without 'exit'"));
  }

  @Test
  public void headlessMode_errorsWhenScriptMissingExit() throws IOException {
    Path script = writeTempFile(Arrays.asList(
        "# no exit here",
        "create calendar --name Work --timezone America/New_York"
    ));
    runMain(new String[]{"--mode", "headless", script.toString()}, null, 2000);
    String out = combinedLower();
    assertContainsAny(out,
        "ended without 'exit'",
        "without 'exit'",
        "must end with 'exit'",
        "file ended without",
        "missing exit");
  }

  @Test
  public void headlessMode_nonexistentFile_reportsReadableError() {
    String missing = Paths.get("definitely", "missing", "nope.txt").toString();
    runMain(new String[]{"--mode", "headless", missing}, null, 2000);
    String out = combinedLower();
    assertContainsAny(out, "error", "cannot", "failed", "no such file",
        "not found", "open", "read");
  }

  @Test
  public void modesAreCaseInsensitive_headlessAlsoWorks() throws IOException {
    Path script = writeTempFile(Arrays.asList("exit"));
    runMain(new String[]{"--mode", "HeAdLeSs", script.toString()}, null, 2000);
    String out = combinedLower();
    assertFalse(out.contains("unsupported"));
  }

  @Test
  public void headlessMode_missingScriptArgument_reportsFatalError() {
    runMain(new String[]{"--mode", "headless"}, null, 2000);
    String out = combinedLower();
    assertContainsAny(out,
        "fatal error",
        "requires a commands file path",
        "headless mode requires");
  }

  @Test
  public void invalidArguments_printsUsageAndError() {
    runMain(new String[]{"--notmode", "interactive"}, null, 2000);
    String out = combinedLower();
    assertContainsAny(out,
        "invalid command-line arguments",
        "usage:");
  }

  @Test
  public void unknownMode_printsUnknownModeAndUsage() {
    runMain(new String[]{"--mode", "gui"}, null, 2000);
    String out = combinedLower();
    assertContainsAny(out,
        "unknown mode",
        "usage:");
  }

  @Test
  public void runtimeExceptionOnNullMode_isReportedAsFatalError() {
    runMain(new String[]{"--mode", null}, null, 2000);
    String out = combinedLower();
    assertContainsAny(out,
        "fatal error");
  }

  @Test
  public void defaultMode_nullArgs_doesNotPrintUsageOrInvalidArgs() {
    runMain(null, null, 2000);
    String out = combinedLower();
    assertFalse(out.contains("invalid command-line arguments"));
    assertFalse(out.contains("usage:"));
  }

  @Test
  public void defaultMode_emptyArgs_doesNotPrintUsageOrInvalidArgs() {
    runMain(new String[]{}, null, 2000);
    String out = combinedLower();
    assertFalse(out.contains("invalid command-line arguments"));
    assertFalse(out.contains("usage:"));
  }

  @Test
  public void invalidArguments_withoutModeFlag_printsInvalidArgumentsAndUsage() {
    runMain(new String[]{"--notmode", "interactive"}, null, 2000);
    String out = combinedLower();
    assertTrue(out.contains("invalid command-line arguments"));
    assertTrue(out.contains("usage:"));
  }

  @Test
  public void invalidArguments_withTooFewTokens_printsInvalidArgumentsAndUsage() {
    runMain(new String[]{"--mode"}, null, 2000);
    String out = combinedLower();
    assertTrue(out.contains("invalid command-line arguments"));
    assertTrue(out.contains("usage:"));
  }
}
