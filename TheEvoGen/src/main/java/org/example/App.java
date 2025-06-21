package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.github.cdimascio.dotenv.Dotenv;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class App {

    private static final String PROJECT_TO_TEST_PATH = "C:\\Repos\\OnlineBankingRestAPI";
    // Configuration
    private static final String EVOSUITE_JAR = "evosuite-master.jar";

    // API Configuration - USE AN ENVIRONMENT VARIABLE!
    private static final String GROQ_API_KEY = Dotenv.load().get("GROQ_API_KEY");
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "meta-llama/llama-4-maverick-17b-128e-instruct"; // A very capable model

    /** The path to the EvoSuite runtime JAR, relative to our project's root. */
    private static final String EVOSUITE_RUNTIME_JAR = "evosuite-standalone-runtime-1.2.0.jar";

    /** How many classes to process in parallel. Set to the number of cores on your machine for best results. */
    private static final int PARALLEL_THREADS = 4;

    // --- Global Helpers ---
    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    static class Message { String role; String content; Message(String r, String c) { role=r; content=c; } }
    static class Choice { Message message; }
    static class ChatResponse { List<Choice> choices; }
    static class ChatRequest { List<Message> messages; String model; ChatRequest(List<Message> m, String mo) { messages=m; model=mo; } }

    public static void main(String[] args) throws InterruptedException {
        if (GROQ_API_KEY == null || GROQ_API_KEY.isBlank()) {
            System.err.println("❌ FATAL ERROR: GROQ_API_KEY environment variable not set.");
            return;
        }

        System.out.println("🚀 Starting Test Generation Bot for project: " + PROJECT_TO_TEST_PATH);

        List<String> classesToTest = discoverClasses(PROJECT_TO_TEST_PATH);
        if (classesToTest.isEmpty()) {
            System.err.println("❌ No classes found to test. Did you build the target project with 'mvn install'?");
            return;
        }

        System.out.println("✅ Found " + classesToTest.size() + " classes to test. Starting parallel processing...");

        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_THREADS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (String className : classesToTest) {
            executor.submit(() -> {
                try {
                    boolean success = processClass(className);
                    if (success) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("🔥 UNCAUGHT EXCEPTION for class " + className + ": " + e.getMessage());
                    e.printStackTrace();
                    failureCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS); // Wait up to an hour for all tasks to finish

        System.out.println("\n\n=======================================================");
        System.out.println("🎉 All tasks completed!");
        System.out.println("   >>> Successful refactorings: " + successCount.get());
        System.out.println("   >>> Failures: " + failureCount.get());
        System.out.println("=======================================================");
    }

    /**
     * Scans the target project's build directory to find all .class files.
     * @param projectPath Path to the root of the target Maven project.
     * @return A list of fully qualified class names.
     */
    private static List<String> discoverClasses(String projectPath) {
        List<String> classNames = new ArrayList<>();
        Path classesDir = Paths.get(projectPath, "target", "classes");
        if (!Files.exists(classesDir)) {
            return classNames; // Return empty list
        }

        try {
            Files.walk(classesDir)
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        String relativePath = classesDir.relativize(path).toString();
                        String className = relativePath.replace(File.separatorChar, '.')
                                .replaceAll("\\.class$", "");
                        classNames.add(className);
                    });
        } catch (IOException e) {
            System.err.println("🚨 Error discovering classes: " + e.getMessage());
        }
        return classNames;
    }

    /**
     * The main processing pipeline for a single class.
     * @param className The fully qualified name of the class to process.
     * @return true if the entire process succeeded, false otherwise.
     */
    private static boolean processClass(String className) {
        System.out.println("▶️  Processing class: " + className);
        try {
            // Step 1: Run EvoSuite
            String rawTestPath = runEvoSuite(className);
            if (rawTestPath == null) {
                System.out.println("   - ❌ EvoSuite failed to generate tests for " + className);
                return false;
            }

            // Step 2: Refactor with LLM
            String refactoredCode = refactorWithLLM(rawTestPath, className);
            if (refactoredCode == null) {
                System.out.println("   - ❌ LLM failed to refactor tests for " + className);
                return false;
            }

            // Step 3: Validate the refactored code
            boolean validationSuccess = validateAndSave(refactoredCode, rawTestPath, className);
            if (validationSuccess) {
                System.out.println("   - ✅ Successfully generated and refactored tests for " + className);
            } else {
                System.out.println("   - ❌ Validation failed for refactored tests of " + className);
            }
            return validationSuccess;

        } catch (Exception e) {
            System.err.println("   - 🔥 UNCAUGHT EXCEPTION for class " + className + ": " + e.getMessage());
            return false;
        }
    }

    // --- Core Methods (now parameterized) ---

    private static String runEvoSuite(String className) throws IOException, InterruptedException {
        String projectCP = Paths.get(PROJECT_TO_TEST_PATH, "target", "classes").toString();
        String testOutputDir = Paths.get(PROJECT_TO_TEST_PATH, "evosuite-tests").toString();

        List<String> command = List.of("java", "-jar", EVOSUITE_JAR, "-class", className,
                "-projectCP", projectCP, "-Dtest_dir=" + testOutputDir);

        ProcessBuilder pb = new ProcessBuilder(command).inheritIO(); // We can silence this later if it's too noisy
        Process process = pb.start();
        process.waitFor(5, TimeUnit.MINUTES);

        String expectedFilePath = testOutputDir + File.separator + className.replace('.', File.separatorChar) + "_ESTest.java";
        if (process.exitValue() == 0 && Files.exists(Paths.get(expectedFilePath))) {
            return expectedFilePath;
        }
        return null;
    }

    private static String refactorWithLLM(String rawTestFilePath, String className) throws IOException, InterruptedException {
        String sourceFilePath = Paths.get(PROJECT_TO_TEST_PATH, "src", "main", "java", className.replace('.', File.separatorChar) + ".java").toString();
        if (!Files.exists(Paths.get(sourceFilePath))) {
            System.out.println("   - ⚠️ Source file not found for " + className + ", refactoring without context.");
            return null; // Or refactor without source code context
        }

        String rawCode = Files.readString(Paths.get(rawTestFilePath));
        String sourceCode = Files.readString(Paths.get(sourceFilePath));

        String prompt = "You are an expert senior Java developer assigned to refactor a JUnit test file. The original test was generated by EvoSuite and is difficult to read.\n\n"
                + "You will be given two pieces of code:\n"
                + "1. The original Java class under test.\n"
                + "2. The raw EvoSuite-generated JUnit test for that class.\n\n"
                + "Your task is to refactor the JUnit test code using the original source as context. Make the test readable, maintainable, and clean for professional developers. Follow these rules STRICTLY:\n"
                + "1.  **Rename Test Methods:** Change method names like `test01` to a descriptive pattern: `test<MethodName>_<Scenario>_<ExpectedResult>`. E.g., `test01` → `testAdd_WithPositiveNumbers_ShouldReturnCorrectSum`.\n"
                + "2.  **Rename Variables:** Change cryptic names like `calculator0`, `int0`, etc., to meaningful names like `calculator`, `firstNumber`, `expectedSum`.\n"
                + "3.  **Add Javadoc Comments:** Add a short Javadoc above each test method describing what is being tested and why.\n"
                + "4.  **DO NOT CHANGE LOGIC:** Do not modify any assertions (`assertEquals`, `assertThrows`, etc.), method calls, or test behavior. Preserve all logic exactly as-is.\n"
                + "5.  **PRESERVE EVO SUITE SCAFFOLDING:** Keep EvoSuite-specific methods (e.g., `EvoSuite.setClassLoader()`, `EvoSuite.resetClasses()`, `verifyException`). Do not change or remove them.\n"
                + "6.  **DO NOT CHANGE THE CLASS NAME.**\n"
                + "7.  **CODE STYLE:** Use standard Java formatting, consistent indentation, and place `@Test` annotations directly above each method.\n"
                + "8.  **OUTPUT FORMAT:** Output ONLY the full refactored Java code in a single ```java code block. No explanations, commentary, or markdown outside the code block.\"\n\n"
                + "---\n"
                + "**Original Java Class Under Test:**\n"
                + "```java\n" + sourceCode + "\n```\n"
                + "---\n"
                + "**EvoSuite-Generated Test to Refactor:**\n"
                + "```java\n" + rawCode + "\n```";

        ChatRequest requestPayload = new ChatRequest(List.of(new Message("user", prompt)), GROQ_MODEL);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(GROQ_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + GROQ_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestPayload))).build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            ChatResponse chatResponse = GSON.fromJson(response.body(), ChatResponse.class);
            String llmMessageContent = chatResponse.choices.get(0).message.content;
            String lowerCaseContent = llmMessageContent.toLowerCase();
            int startBlock = lowerCaseContent.indexOf("```java");
            int endBlock = lowerCaseContent.lastIndexOf("```");
            if (startBlock != -1 && endBlock > startBlock) {
                return llmMessageContent.substring(startBlock + 7, endBlock).trim();
            }
            return llmMessageContent.trim();
        }
        return null;
    }

    private static boolean validateAndSave(String refactoredCode, String originalFilePath, String className) throws IOException, InterruptedException {
        Path originalFile = Paths.get(originalFilePath);
        Path tempDir = Files.createTempDirectory("validation-" + className);
        Path tempFile = tempDir.resolve(originalFile.getFileName());
        Files.writeString(tempFile, refactoredCode);

        Path scaffoldingFile = originalFile.getParent().resolve(originalFile.getFileName().toString().replace(".java", "_scaffolding.java"));
        Path tempScaffoldingFile = null;
        if (Files.exists(scaffoldingFile)) {
            tempScaffoldingFile = tempDir.resolve(scaffoldingFile.getFileName());
            Files.copy(scaffoldingFile, tempScaffoldingFile);
        }

        // --- The working classpath strategy ---
        String currentClasspath = System.getProperty("java.class.path");
        String evosuiteRuntimeJarPath = new File(EVOSUITE_RUNTIME_JAR).getAbsolutePath();
        // **NEW**: We also need the target project's own classes on the classpath!
        String targetProjectClassesPath = Paths.get(PROJECT_TO_TEST_PATH, "target", "classes").toAbsolutePath().toString();
        String fullClasspath = String.join(File.pathSeparator, currentClasspath, evosuiteRuntimeJarPath, targetProjectClassesPath);

        List<String> filesToCompile = new ArrayList<>();
        filesToCompile.add(tempFile.toString());
        if(tempScaffoldingFile != null) {
            filesToCompile.add(tempScaffoldingFile.toString());
        }

        List<String> compileCommand = new ArrayList<>();
        compileCommand.add("javac");
        compileCommand.add("-cp");
        compileCommand.add(fullClasspath);
        compileCommand.addAll(filesToCompile);

        ProcessBuilder pb = new ProcessBuilder(compileCommand);
        Process process = pb.start();
        process.waitFor(1, TimeUnit.MINUTES);

        if (process.exitValue() == 0) {
            Files.writeString(originalFile, refactoredCode); // Overwrite the original test with the good one
            tempDir.toFile().deleteOnExit(); // Clean up
            return true;
        } else {
            // Save the failed code for debugging
            String failedCodeOutput = new String(process.getErrorStream().readAllBytes());
            if (failedCodeOutput.isBlank()) {
                failedCodeOutput = new String(process.getInputStream().readAllBytes());
            }
            System.err.println("   - ❌ Validation failed for " + className + ". Compiler output:\n" + failedCodeOutput);
            return false;
        }
    }
}