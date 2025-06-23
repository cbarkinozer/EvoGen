package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class App {

    // Configuration
    private static final String EVOSUITE_JAR = "evosuite-master.jar";
    private static final String SOURCE_DIR = "src/main/java";
    private static final String TEST_SRC_DIR = "src/test/java"; // NEW: Standard test directory
    private static final String PROJECT_CLASSPATH = "target/classes";
    private static final String CLASS_TO_TEST = "org.example.Calculator";
    private static final String TEST_OUTPUT_DIR = "evosuite-tests"; // Temporary dir for EvoSuite output

    // NEW: JUnit 5 Dependencies - Place these JARs in your project root or provide correct paths.
    // Download from Maven Central if you don't have them.
    private static final String JUNIT_JUPITER_API_JAR = "junit-jupiter-api-5.10.2.jar";
    private static final String JUNIT_JUPITER_ENGINE_JAR = "junit-jupiter-engine-5.10.2.jar";
    private static final String OPENTEST4J_JAR = "opentest4j-1.3.0.jar";
    private static final String JUNIT_PLATFORM_COMMONS_JAR = "junit-platform-commons-1.10.2.jar";

    // API Configuration
    private static final String GROQ_API_KEY = Dotenv.load().get("GROQ_API_KEY");
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

    private static final Gson GSON = new GsonBuilder().create();
    static class Message { String role; String content; Message(String r, String c) { role=r; content=c; } }
    static class Choice { Message message; }
    static class ChatResponse { List<Choice> choices; }
    static class ChatRequest { List<Message> messages; String model; ChatRequest(List<Message> m, String mo) { messages=m; model=mo; } }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (GROQ_API_KEY == null || GROQ_API_KEY.isBlank()) {
            System.err.println("❌ FATAL ERROR: GROQ_API_KEY environment variable not set.");
            return;
        }

        System.out.println("Starting JUnit 5 Synthesis Process...");
        String rawEvoSuiteTestPath = runEvoSuite();

        if (rawEvoSuiteTestPath != null && !rawEvoSuiteTestPath.isBlank()) {
            System.out.println("\n✅ EvoSuite generation successful.");
            synthesizeJUnit5Test(rawEvoSuiteTestPath);
        } else {
            System.err.println("\n❌ EvoSuite generation failed. Aborting.");
        }

        // NEW: Clean up the temporary EvoSuite directory
        cleanupDirectory(Paths.get(TEST_OUTPUT_DIR));
        System.out.println("\n✨ Process finished.");
    }

    private static String runEvoSuite() throws IOException, InterruptedException {
        System.out.println("--- Step 1: Running EvoSuite to generate inspiration tests ---");
        List<String> command = List.of("java", "-jar", EVOSUITE_JAR, "-class", CLASS_TO_TEST,
                "-projectCP", PROJECT_CLASSPATH, "-Dtest_dir=" + TEST_OUTPUT_DIR);

        ProcessBuilder pb = new ProcessBuilder(command).inheritIO();
        Process process = pb.start();
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);

        if (!finished || process.exitValue() != 0) {
            System.err.println("EvoSuite process did not finish successfully.");
            return null;
        }

        String expectedFilePath = TEST_OUTPUT_DIR + File.separator + CLASS_TO_TEST.replace('.', File.separatorChar) + "_ESTest.java";
        return Files.exists(Paths.get(expectedFilePath)) ? expectedFilePath : null;
    }

    // UPDATED: Renamed from refactorWithLLM to reflect new purpose
    private static void synthesizeJUnit5Test(String rawTestFilePath) throws IOException, InterruptedException {
        System.out.println("\n--- Step 2: Synthesizing JUnit 5 test with Groq LLM ---");

        String rawEvoSuiteCode = Files.readString(Paths.get(rawTestFilePath));
        String sourceFilePath = SOURCE_DIR + File.separator + CLASS_TO_TEST.replace('.', File.separatorChar) + ".java";
        String sourceCode = Files.readString(Paths.get(sourceFilePath));

        // NEW: Logic to find and read an existing JUnit 5 test
        String simpleClassName = CLASS_TO_TEST.substring(CLASS_TO_TEST.lastIndexOf('.') + 1);
        String existingTestFileName = simpleClassName + "Test.java";
        Path packagePath = Paths.get(TEST_SRC_DIR, CLASS_TO_TEST.substring(0, CLASS_TO_TEST.lastIndexOf('.')).replace('.', File.separatorChar));
        Path existingTestPath = packagePath.resolve(existingTestFileName);
        String existingTestCode = null;

        if (Files.exists(existingTestPath)) {
            System.out.println("Found existing test file to use as style guide: " + existingTestPath);
            existingTestCode = Files.readString(existingTestPath);
        } else {
            System.out.println("No existing test file found. Will create a new one.");
        }

        // UPDATED: Use the new dynamic prompt
        String prompt = createSynthesisPrompt(sourceCode, rawEvoSuiteCode, existingTestCode);

        ChatRequest requestPayload = new ChatRequest(List.of(new Message("user", prompt)), GROQ_MODEL);
        String jsonBody = GSON.toJson(requestPayload);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + GROQ_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        System.out.println("Sending request to Groq API for test synthesis...");
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            ChatResponse chatResponse = GSON.fromJson(response.body(), ChatResponse.class);
            String llmMessageContent = chatResponse.choices.get(0).message.content;

            // Extract code from markdown block
            String synthesizedCode = llmMessageContent.substring(llmMessageContent.indexOf("```java") + 7, llmMessageContent.lastIndexOf("```")).trim();

            if (!synthesizedCode.isBlank()) {
                // UPDATED: Pass the target path for the new JUnit 5 test
                validateAndSaveJUnit5Test(synthesizedCode, existingTestPath);
            } else {
                System.err.println("❌ Error: LLM returned an empty or invalid response.");
            }
        } else {
            System.err.println("❌ API Error: " + response.statusCode() + " | " + response.body());
        }
    }

    // NEW: Prompt creation method as defined in section 1
    private static String createSynthesisPrompt(String sourceCode, String evosuiteCode, String existingTestCode) {
        // ... (Paste the prompt creation code from Section 1 here) ...
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are an expert Java developer specializing in writing clean, modern, and maintainable JUnit 5 tests.\n\n");
        promptBuilder.append("Your mission is to synthesize a single, comprehensive JUnit 5 test file for a given Java class. You will be provided with up to three pieces of information:\n");
        promptBuilder.append("1. **The Source Code:** The Java class that needs to be tested.\n");
        promptBuilder.append("2. **EvoSuite Test (Inspiration):** A test file generated by the EvoSuite tool. This file is NOT to be refactored. Instead, treat it as a 'source of truth' for test cases. Extract the core logic from it (e.g., method calls, inputs, expected outputs, and exceptions) to ensure high test coverage.\n");
        if (existingTestCode != null) {
            promptBuilder.append("3. **Existing JUnit 5 Test (Style Guide & Base):** A handwritten test file that already exists. Use this as a guide for style, naming conventions, and structure. Your final output should be a MERGE of the tests in this file and the new tests inspired by the EvoSuite output. Do not remove existing tests.\n\n");
        } else {
            promptBuilder.append("3. No existing test file was found. You will create a new one from scratch based on the EvoSuite inspiration.\n\n");
        }

        promptBuilder.append("Follow these rules STRICTLY:\n");
        promptBuilder.append("1.  **OUTPUT JUNIT 5 ONLY:** The final code MUST use JUnit 5. Use imports from `org.junit.jupiter.api.*`. DO NOT include any EvoSuite annotations (`@RunWith`, `@EvoRunnerParameters`) or imports (`org.evosuite.*`). The test must be runnable with a standard JUnit 5 runner.\n");
        promptBuilder.append("2.  **DESCRIPTIVE NAMING:** Create clear, descriptive test method names, like `testAdd_WithPositiveNumbers_ShouldReturnCorrectSum`. If a style guide is provided, match its naming patterns.\n");
        promptBuilder.append("3.  **MERGE, DON'T REPLACE:** If an existing test file is provided, add the new test cases inspired by EvoSuite. If a test case seems redundant, prefer the existing handwritten version.\n");
        promptBuilder.append("4.  **ASSERTIONS:** Use standard JUnit 5 assertions (`Assertions.assertEquals`, `Assertions.assertThrows`, etc.).\n");
        promptBuilder.append("5.  **NO EVO-SPECIFICS:** Ignore and discard any EvoSuite-specific scaffolding, setup, or verification calls (like `verifyException`). Re-implement exception tests using `assertThrows`.\n");
        promptBuilder.append("6.  **CLASS NAME:** The test class name should follow the standard convention (e.g., `CalculatorTest.java`), not the EvoSuite convention (`Calculator_ESTest.java`).\n");
        promptBuilder.append("7.  **IMPORTS:** Be sure only adding necessary, correct and required imports.\n");
        promptBuilder.append("8.  **OUTPUT FORMAT:** Provide ONLY the complete, final Java code in a single ```java code block. Do not include any explanations or commentary outside the code block.\n\n");

        promptBuilder.append("---\n");
        promptBuilder.append("**Source Code Under Test:**\n");
        promptBuilder.append("```java\n").append(sourceCode).append("\n```\n\n");

        promptBuilder.append("---\n");
        promptBuilder.append("**EvoSuite Test (Inspiration for Test Cases):**\n");
        promptBuilder.append("```java\n").append(evosuiteCode).append("\n```\n\n");

        if (existingTestCode != null) {
            promptBuilder.append("---\n");
            promptBuilder.append("**Existing JUnit 5 Test (Style Guide & Base):**\n");
            promptBuilder.append("```java\n").append(existingTestCode).append("\n```\n");
        }

        return promptBuilder.toString();
    }

    // UPDATED: This method is now completely different. It validates against JUnit 5.
    private static void validateAndSaveJUnit5Test(String synthesizedCode, Path targetSavePath) throws IOException, InterruptedException {
        System.out.println("\n--- Step 3: Validating synthesized JUnit 5 code by compiling ---");
        Path tempDir = Files.createTempDirectory("junit5-validation");
        Path tempFile = tempDir.resolve(targetSavePath.getFileName());
        Files.writeString(tempFile, synthesizedCode);

        // NEW: Build the classpath for JUnit 5 compilation
        String classpath = String.join(File.pathSeparator,
                PROJECT_CLASSPATH,
                new File(JUNIT_JUPITER_API_JAR).getAbsolutePath(),
                new File(JUNIT_JUPITER_ENGINE_JAR).getAbsolutePath(),
                new File(OPENTEST4J_JAR).getAbsolutePath(),
                new File(JUNIT_PLATFORM_COMMONS_JAR).getAbsolutePath()
        );

        System.out.println("Using JUnit 5 classpath for validation: " + classpath);
        List<String> compileCommand = List.of("javac", "-cp", classpath, tempFile.toString());

        ProcessBuilder pb = new ProcessBuilder(compileCommand).redirectErrorStream(true);
        Process process = pb.start();
        String compilerOutput = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);

        if (finished && process.exitValue() == 0) {
            System.out.println("✅✅✅ VICTORY! Validation successful! The synthesized code compiles correctly. ✅✅✅");
            // Ensure the target directory exists
            Files.createDirectories(targetSavePath.getParent());
            Files.writeString(targetSavePath, synthesizedCode);
            System.out.println("Saved new/updated JUnit 5 test to: " + targetSavePath);
        } else {
            System.err.println("❌ Validation FAILED. The LLM-generated code has compilation errors.");
            System.err.println("Compiler Output:\n" + compilerOutput);
            Path failedFile = targetSavePath.getParent().resolve(targetSavePath.getFileName().toString().replace(".java", "_synthesis_failed.java"));
            Files.writeString(failedFile, synthesizedCode);
            System.out.println("The faulty synthesized code has been saved to: " + failedFile);
        }
        cleanupDirectory(tempDir);
    }

    // NEW: Utility to clean up directories
    private static void cleanupDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            System.out.println("Cleaned up directory: " + dir);
        }
    }
}