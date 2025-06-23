package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A tool to autonomously generate and maintain high-quality JUnit 5 tests.
 *
 * Workflow:
 * 1. Runs EvoSuite to generate a set of test cases with high coverage.
 * 2. Provides the EvoSuite test, the original source code, and any existing handwritten test
 *    to a Large Language Model (LLM).
 * 3. The LLM synthesizes a clean, readable, standard JUnit 5 test file, merging new test
 *    cases with existing ones.
 * 4. The tool attempts to compile the synthesized test file.
 * 5. If compilation fails due to a missing dependency ("package ... does not exist"),
 *    it automatically searches Maven Central for the required JAR, downloads it, and
 *    retries compilation.
 */
public class SingleFile {

    // --- Configuration ---
    private static final String EVOSUITE_JAR = "evosuite-master.jar";
    private static final String SOURCE_DIR = "src/main/java";
    private static final String TEST_SRC_DIR = "src/test/java";
    private static final String PROJECT_CLASSPATH = "target/classes";
    private static final String CLASS_TO_TEST = "org.example.Calculator";
    private static final String TEST_OUTPUT_DIR = "evosuite-tests"; // Temporary dir for EvoSuite output

    // --- Self-healing Compilation Config ---
    private static final int MAX_COMPILE_RETRIES = 5; // Max number of dependencies to auto-resolve
    private static final Path DEPENDENCY_CACHE_DIR = Paths.get(".m2_local_cache"); // A folder to store downloaded JARs
    private static final String MAVEN_SEARCH_URL = "https://search.maven.org/solrsearch/select?q=%s&rows=1&wt=json";

    // --- Initial Base Dependencies (place these in your project root) ---
    private static final List<String> BASE_JARS = List.of(
            "junit-jupiter-api-5.10.2.jar",
            "junit-jupiter-engine-5.10.2.jar",
            "opentest4j-1.3.0.jar",
            "junit-platform-commons-1.10.2.jar"
    );

    // --- API Configuration ---
    private static final String GROQ_API_KEY = Dotenv.load().get("GROQ_API_KEY");
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

    // --- Helpers and Data Classes ---
    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    static class Message { String role; String content; Message(String r, String c) { role=r; content=c; } }
    static class Choice { Message message; }
    static class ChatResponse { List<Choice> choices; }
    static class ChatRequest { List<Message> messages; String model; ChatRequest(List<Message> m, String mo) { messages=m; model=mo; } }
    static class MavenDoc { String g; String a; String v; }
    static class MavenResponse { List<MavenDoc> docs; }
    static class MavenSearchResponse { MavenResponse response; }


    public static void main(String[] args) throws IOException, InterruptedException {
        if (GROQ_API_KEY == null || GROQ_API_KEY.isBlank()) {
            System.err.println("❌ FATAL ERROR: GROQ_API_KEY environment variable not set.");
            return;
        }

        Files.createDirectories(DEPENDENCY_CACHE_DIR); // Ensure the local dependency cache exists

        System.out.println("Starting JUnit 5 Synthesis Process...");
        String rawEvoSuiteTestPath = runEvoSuite();

        if (rawEvoSuiteTestPath != null && !rawEvoSuiteTestPath.isBlank()) {
            System.out.println("\n✅ EvoSuite generation successful.");
            synthesizeJUnit5Test(rawEvoSuiteTestPath);
        } else {
            System.err.println("\n❌ EvoSuite generation failed. Aborting.");
        }

        cleanupDirectory(Paths.get(TEST_OUTPUT_DIR)); // Clean up temp EvoSuite files
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

    private static void synthesizeJUnit5Test(String rawTestFilePath) throws IOException, InterruptedException {
        System.out.println("\n--- Step 2: Synthesizing JUnit 5 test with Groq LLM ---");

        String rawEvoSuiteCode = Files.readString(Paths.get(rawTestFilePath));
        String sourceFilePath = SOURCE_DIR + File.separator + CLASS_TO_TEST.replace('.', File.separatorChar) + ".java";
        String sourceCode = Files.readString(Paths.get(sourceFilePath));

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

        String prompt = createSynthesisPrompt(sourceCode, rawEvoSuiteCode, existingTestCode);

        ChatRequest requestPayload = new ChatRequest(List.of(new Message("user", prompt)), GROQ_MODEL);
        String jsonBody = GSON.toJson(requestPayload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + GROQ_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        System.out.println("Sending request to Groq API for test synthesis...");
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            ChatResponse chatResponse = GSON.fromJson(response.body(), ChatResponse.class);
            String llmMessageContent = chatResponse.choices.get(0).message.content;
            String synthesizedCode;
            int startBlock = llmMessageContent.indexOf("```java");

            if (startBlock != -1) {
                // A "```java" block was found.
                // Find the closing fence, but make sure it's AFTER the opening one.
                int endBlock = llmMessageContent.indexOf("```", startBlock + 7);
                if (endBlock != -1) {
                    // Both fences found, extract the content between them.
                    synthesizedCode = llmMessageContent.substring(startBlock + 7, endBlock).trim();
                } else {
                    // An opening fence was found, but no closing one.
                    // Assume the rest of the response is the code.
                    synthesizedCode = llmMessageContent.substring(startBlock + 7).trim();
                }
            } else if (llmMessageContent.trim().startsWith("package")) {
                // No "```java" block, but the response looks like raw Java code.
                // Use the whole response.
                synthesizedCode = llmMessageContent.trim();
            } else {
                // The response format is completely unrecognized.
                System.err.println("Warning: Could not parse LLM response to extract code block.");
                synthesizedCode = ""; // Assign empty to fail gracefully in the next step.
            }

            if (!synthesizedCode.isBlank()) {
                validateAndSaveJUnit5Test(synthesizedCode, existingTestPath);
            } else {
                System.err.println("❌ Error: LLM returned an empty or invalid response.");
            }
        } else {
            System.err.println("❌ API Error: " + response.statusCode() + " | " + response.body());
        }
    }

    private static String createSynthesisPrompt(String sourceCode, String evosuiteCode, String existingTestCode) {
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
        promptBuilder.append("1.  **OUTPUT JUNIT 5 ONLY:** The final code MUST use JUnit 5. Use imports from `org.junit.jupiter.api.*`. If you need parameterized tests, use `org.junit.jupiter.params.*`. DO NOT include any EvoSuite annotations or imports.\n");
        promptBuilder.append("2.  **DESCRIPTIVE NAMING:** Create clear, descriptive test method names, like `testAdd_WithPositiveNumbers_ShouldReturnCorrectSum`. If a style guide is provided, match its naming patterns.\n");
        promptBuilder.append("3.  **MERGE, DON'T REPLACE:** If an existing test file is provided, add the new test cases inspired by EvoSuite. If a test case seems redundant, prefer the existing handwritten version.\n");
        promptBuilder.append("4.  **ASSERTIONS:** Use standard JUnit 5 assertions (`Assertions.assertEquals`, `Assertions.assertThrows`, etc.).\n");
        promptBuilder.append("5.  **NO EVO-SPECIFICS:** Ignore and discard any EvoSuite-specific scaffolding, setup, or verification calls. Re-implement exception tests using `assertThrows`.\n");
        promptBuilder.append("6.  **CLASS NAME:** The test class name should follow the standard convention (e.g., `CalculatorTest.java`).\n");
        promptBuilder.append("7.  **OUTPUT FORMAT:** Provide ONLY the complete, final Java code in a single ```java code block. Do not include any explanations or commentary outside the code block.\n\n");

        promptBuilder.append("---\n**Source Code Under Test:**\n```java\n").append(sourceCode).append("\n```\n\n");
        promptBuilder.append("---\n**EvoSuite Test (Inspiration for Test Cases):**\n```java\n").append(evosuiteCode).append("\n```\n\n");
        if (existingTestCode != null) {
            promptBuilder.append("---\n**Existing JUnit 5 Test (Style Guide & Base):**\n```java\n").append(existingTestCode).append("\n```\n");
        }
        return promptBuilder.toString();
    }

    private static void validateAndSaveJUnit5Test(String synthesizedCode, Path targetSavePath) throws IOException, InterruptedException {
        System.out.println("\n--- Step 3: Validating synthesized JUnit 5 code (with self-healing) ---");
        Path tempDir = Files.createTempDirectory("junit5-validation");
        Path tempFile = tempDir.resolve(targetSavePath.getFileName());
        Files.writeString(tempFile, synthesizedCode);

        List<Path> classpathJars = new ArrayList<>();
        classpathJars.add(Paths.get(PROJECT_CLASSPATH));
        for (String jar : BASE_JARS) {
            classpathJars.add(Paths.get(jar));
        }
        try (Stream<Path> stream = Files.walk(DEPENDENCY_CACHE_DIR)) {
            stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".jar")).forEach(classpathJars::add);
        }

        Set<String> resolvedPackages = new HashSet<>();
        String finalCompilerOutput = "";

        for (int attempt = 1; attempt <= MAX_COMPILE_RETRIES; attempt++) {
            String classpath = classpathJars.stream().map(Path::toAbsolutePath).map(Path::toString).collect(Collectors.joining(File.pathSeparator));
            System.out.printf("\n[Attempt %d/%d] Compiling with %d dependency JARs...\n", attempt, MAX_COMPILE_RETRIES, (int) classpathJars.stream().filter(p -> p.toString().endsWith(".jar")).count());

            List<String> compileCommand = List.of("javac", "-cp", classpath, tempFile.toString());
            ProcessBuilder pb = new ProcessBuilder(compileCommand).redirectErrorStream(true);
            Process process = pb.start();
            String compilerOutput = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            finalCompilerOutput = compilerOutput;

            if (finished && process.exitValue() == 0) {
                System.out.println("✅✅✅ VICTORY! Validation successful! The synthesized code compiles correctly. ✅✅✅");
                Files.createDirectories(targetSavePath.getParent());
                Files.writeString(targetSavePath, synthesizedCode);
                System.out.println("Saved new/updated JUnit 5 test to: " + targetSavePath);
                cleanupDirectory(tempDir);
                return;
            }

            System.err.println("❌ Compilation failed. Analyzing output for missing packages...");
            String missingPackage = parseMissingPackage(compilerOutput);

            if (missingPackage == null) {
                System.err.println("Could not identify a missing package. The error is likely a syntax issue. Aborting retries.");
                break;
            }

            if (resolvedPackages.contains(missingPackage)) {
                System.err.printf("Already tried to resolve package '%s' without success. Aborting to prevent loop.\n", missingPackage);
                break;
            }

            System.out.printf("Identified missing package: '%s'. Attempting to find and download the required JAR.\n", missingPackage);
            resolvedPackages.add(missingPackage);
            Path downloadedJar = findAndDownloadJar(missingPackage);

            if (downloadedJar != null) {
                System.out.printf("✅ Successfully downloaded '%s'. Adding to classpath and retrying.\n", downloadedJar.getFileName());
                classpathJars.add(downloadedJar);
            } else {
                System.err.printf("Could not find or download a JAR for package '%s'. Aborting retries.\n", missingPackage);
                break;
            }
        }

        System.err.println("\n❌ Validation FAILED permanently after all attempts.");
        System.err.println("Final Compiler Output:\n" + finalCompilerOutput);
        Path failedFile = targetSavePath.getParent().resolve(targetSavePath.getFileName().toString().replace(".java", "_synthesis_failed.java"));
        Files.writeString(failedFile, synthesizedCode);
        System.out.println("The faulty synthesized code has been saved to: " + failedFile);
        cleanupDirectory(tempDir);
    }

    private static String parseMissingPackage(String compilerOutput) {
        Pattern pattern = Pattern.compile("error: package ([\\w.]+) does not exist");
        Matcher matcher = pattern.matcher(compilerOutput);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Path findAndDownloadJar(String packageName) throws IOException, InterruptedException {
        String searchUri = String.format(MAVEN_SEARCH_URL, URLEncoder.encode("fc:\"" + packageName + "\"", StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(searchUri)).build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println("Maven Central search API returned status: " + response.statusCode());
            return null;
        }

        MavenSearchResponse searchResponse = GSON.fromJson(response.body(), MavenSearchResponse.class);
        if (searchResponse.response == null || searchResponse.response.docs.isEmpty()) {
            System.err.println("No artifact found for package: " + packageName);
            return null;
        }

        MavenDoc doc = searchResponse.response.docs.get(0);
        String groupId = doc.g.replace('.', '/');
        String artifactId = doc.a;
        String version = doc.v;
        String jarFileName = String.format("%s-%s.jar", artifactId, version);
        Path localJarPath = DEPENDENCY_CACHE_DIR.resolve(jarFileName);

        if (Files.exists(localJarPath)) {
            System.out.printf("Found JAR in local cache: %s\n", localJarPath);
            return localJarPath;
        }

        String downloadUrl = String.format("https://repo1.maven.org/maven2/%s/%s/%s/%s", groupId, artifactId, version, jarFileName);
        System.out.printf("Downloading from: %s\n", downloadUrl);

        HttpRequest downloadRequest = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).build();
        HttpResponse<Path> downloadResponse = HTTP_CLIENT.send(downloadRequest, HttpResponse.BodyHandlers.ofFile(localJarPath));

        if (downloadResponse.statusCode() == 200) {
            return downloadResponse.body();
        } else {
            System.err.printf("Failed to download JAR. Status code: %d\n", downloadResponse.statusCode());
            Files.deleteIfExists(localJarPath); // Clean up failed download
            return null;
        }
    }

    private static void cleanupDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            System.out.println("Cleaned up directory: " + dir);
        } catch (IOException e) {
            System.err.println("Failed to clean up directory " + dir + ": " + e.getMessage());
        }
    }
}