package org.example;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A streamlined tool to generate JUnit 5 tests for a full Java project using the
 * EvoSuite MAVEN PLUGIN. This is the recommended, most robust approach.
 * This version provides DETAILED REAL-TIME LOGS from the underlying Maven process.
 */
public class ProjectLevelEvoSuite { // Renamed for clarity

    // --- High-Level Project Configuration ---
    private static final String PROJECT_TO_TEST_PATH = "C:\\Repos\\OnlineBankingRestAPI"; // <--- IMPORTANT: SET THIS
    private static final int PARALLEL_THREADS = 4;
    private static final int TIMEOUT_PER_CLASS_MINUTES = 5;

    // --- EvoSuite Configuration (passed via Maven properties) ---
    private static final int SEARCH_BUDGET_SECONDS = 60; // Search budget per class

    // --- Thread-Safe Logging for Summary (Unchanged) ---
    private static final List<String> successLog = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, String> failureLog = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        long startTime = System.nanoTime();

        System.out.println("🚀 Starting Parallel EvoSuite Test Generation using the MAVEN PLUGIN for project: " + PROJECT_TO_TEST_PATH);
        System.out.println("------------------------------------------------------------------------");
        System.out.println("✅ PREREQUISITE: Ensure the target project's pom.xml contains the evosuite-maven-plugin.");
        System.out.println("   For best results, run 'mvn clean install' in the target project's directory once.");
        System.out.println("------------------------------------------------------------------------");

        List<String> classesToTest = discoverClasses();
        if (classesToTest.isEmpty()) {
            System.err.println("❌ No classes found to test. Did you build the target project (e.g., with 'mvn install')?");
            return;
        }

        System.out.printf("✅ Found %d classes. Starting parallel processing with %d threads...%n", classesToTest.size(), PARALLEL_THREADS);

        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_THREADS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🚨 Shutdown signal received. Forcibly terminating executor...");
            executor.shutdownNow();
            System.out.println("✅ Executor shutdown complete.");
        }));

        for (String className : classesToTest) {
            executor.submit(() -> {
                try {
                    String result = generateTestsWithMavenPlugin(className);
                    if (result == null) {
                        successLog.add(className);
                    } else {
                        failureLog.put(className, result);
                    }
                } catch (Exception e) {
                    String errorMsg = "FATAL UNCAUGHT EXCEPTION: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                    failureLog.put(className, errorMsg);
                    System.err.printf("[%s] 🔥 %s%n", className, errorMsg);
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(8, TimeUnit.HOURS);

        long durationNanos = System.nanoTime() - startTime;
        printFinalSummary(durationNanos);
    }

    /**
     * Generates tests for a single class by invoking the evosuite-maven-plugin.
     * This replaces the old method that manually built a classpath and called the JAR.
     */
    private static String generateTestsWithMavenPlugin(String className) {
        System.out.printf("▶️  Processing class: %s%n", className);
        Path projectRoot = Paths.get(ProjectLevelEvoSuite.PROJECT_TO_TEST_PATH);
        Process mvnProcess = null;

        try {
            // --- STEP 1 (CHANGED): Construct a single Maven command ---
            // The plugin handles classpath, execution, and placing the test file automatically.
            int timeoutInSeconds = (int) TimeUnit.MINUTES.toSeconds(TIMEOUT_PER_CLASS_MINUTES);

            String mvnCommand = String.format(
                    "mvn -B org.evosuite.plugins:evosuite-maven-plugin:1.2.0:generate -Dclass=%s -Dsearch_budget=%d -Dtimeout=%d",
                    className,
                    SEARCH_BUDGET_SECONDS,
                    timeoutInSeconds
            );

            ProcessBuilder mvnPb;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                mvnPb = new ProcessBuilder("cmd.exe", "/c", mvnCommand);
            } else {
                mvnPb = new ProcessBuilder("bash", "-c", mvnCommand);
            }
            mvnPb.directory(projectRoot.toFile());
            mvnProcess = mvnPb.start();

            // *** LIVE LOGGING (Unchanged): Redirect Maven's output with a class-specific prefix ***
            // This will now show logs from both Maven and the EvoSuite process it spawns.
            redirectStream(mvnProcess.getInputStream(), String.format("[%s][mvn-evo] ", className), System.out);
            redirectStream(mvnProcess.getErrorStream(), String.format("[%s][mvn-evo] ", className), System.err);

            // --- STEP 2 (SIMPLIFIED): Wait for the single process to finish ---
            if (!mvnProcess.waitFor(TIMEOUT_PER_CLASS_MINUTES + 1, TimeUnit.MINUTES)) { // Add a grace minute
                mvnProcess.destroyForcibly();
                return "Maven/EvoSuite process timed out.";
            }

            if (mvnProcess.exitValue() != 0) {
                // The detailed error has already been printed to the console.
                return "Maven/EvoSuite process failed. See logs above for details.";
            }

            // --- STEP 3 (REMOVED): No need to move files! ---
            // The Maven plugin automatically places the generated test in src/test/java.
            System.out.printf("✅ [%s] Successfully generated test via Maven plugin.%n", className);

            return null; // SUCCESS

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "An exception occurred: " + e.getMessage();
        } finally {
            if (mvnProcess != null && mvnProcess.isAlive()) mvnProcess.destroyForcibly();
            // --- REMOVED: No temp directory to clean up ---
        }
    }

    // --- UNCHANGED HELPER METHODS ---

    private static List<String> discoverClasses() throws IOException {
        Path classesDir = Paths.get(ProjectLevelEvoSuite.PROJECT_TO_TEST_PATH, "target", "classes");
        if (!Files.exists(classesDir)) {
            return new ArrayList<>();
        }

        try (Stream<Path> stream = Files.walk(classesDir)) {
            return stream.filter(path -> path.toString().endsWith(".class"))
                    .map(path -> classesDir.relativize(path).toString())
                    .map(relativePath -> relativePath.replace(File.separator, ".").replaceAll("\\.class$", ""))
                    .filter(className -> !className.contains("$") && !className.endsWith("package-info"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static void redirectStream(InputStream stream, String prefix, PrintStream printStream) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    printStream.println(prefix + line);
                }
            } catch (IOException e) {
                // Can happen on abrupt termination, safe to ignore.
            }
        }).start();
    }

    private static void printFinalSummary(long durationNanos) {
        System.out.println("\n\n=======================================================");
        System.out.println("🎉 All tasks completed!");
        System.out.println("=======================================================");
        System.out.printf("✅ Successful generations: %d%n", successLog.size());
        System.out.printf("❌ Failed generations: %d%n", failureLog.size());
        System.out.println("-------------------------------------------------------");

        if (!failureLog.isEmpty()) {
            System.out.println("\n📋 DETAILED FAILURE REPORT:");
            failureLog.forEach((className, reason) -> {
                System.out.printf("\n--- Failure for class: %s ---\n", className);
                System.out.printf("  Reason: %s%n", reason);
            });
        }

        long totalSeconds = TimeUnit.NANOSECONDS.toSeconds(durationNanos);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        String durationFormatted = String.format("%d hours, %d minutes, %d seconds", hours, minutes, seconds);

        System.out.println("\n-------------------------------------------------------");
        System.out.printf("⏱️  Total Execution Time: %s%n", durationFormatted);
        System.out.println("=======================================================");
    }
}