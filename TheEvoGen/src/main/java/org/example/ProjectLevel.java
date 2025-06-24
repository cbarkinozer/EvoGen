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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * An agentic tool to autonomously generate and maintain high-quality JUnit 5 tests for a full Java project.
 *
 * Workflow for each class:
 * 1. Discovers all compilable classes in a target Maven project.
 * 2. Runs EvoSuite to generate a set of test cases for inspiration.
 * 3. Provides the EvoSuite test, the original source code, and any existing handwritten test to a Large Language Model (LLM).
 * 4. The LLM synthesizes a clean, readable, standard JUnit 5 test file, merging new test cases with existing ones.
 * 5. The tool attempts to compile the synthesized test file. If compilation fails due to a missing dependency,
 *    it automatically searches Maven Central, downloads the required JAR, and retries compilation (self-healing).
 * 6. If successful, it overwrites the existing test file. If it fails, it logs the detailed error.
 * 7. This process is executed in parallel for all discovered classes.
 */
public class ProjectLevel {

    // --- High-Level Project Configuration ---
    private static final String PROJECT_TO_TEST_PATH = "C:\\Repos\\bitirmeprojesi-cbarkinozer"; // <--- IMPORTANT: SET THIS
    private static final int PARALLEL_THREADS = 4; // Number of classes to process in parallel

    // --- EvoSuite Configuration ---
    private static final String EVOSUITE_JAR = "evosuite-master.jar";

    // --- Self-healing Compilation Config ---
    private static final int MAX_COMPILE_RETRIES = 5;
    private static final Path DEPENDENCY_CACHE_DIR = Paths.get(".m2_local_cache");
    private static final String MAVEN_SEARCH_URL = "https://search.maven.org/solrsearch/select?q=%s&rows=1&wt=json";
    private static final List<String> BASE_JARS = List.of(
            "junit-jupiter-api-5.10.2.jar",
            "junit-jupiter-engine-5.10.2.jar",
            "opentest4j-1.3.0.jar",
            "junit-platform-commons-1.10.2.jar"
    );

    // --- API Configuration ---
    private enum ApiProvider { GROQ, GEMINI }
    private static final ApiProvider API_PROVIDER = ApiProvider.GROQ; // Gemini pass the quota and fail
    private static final String GROQ_API_KEY = Dotenv.load().get("GROQ_API_KEY");
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";
    private static final String GEMINI_API_KEY = Dotenv.load().get("GEMINI_API_KEY");
    private static final String GEMINI_MODEL = "gemini-2.5-pro";

    // Rate Limiting: 30 RPM for the model. 60,000ms / 30 = 2,000ms per request. Add a buffer.
    private static final long API_REQUEST_DELAY_MS = 2100;

    // --- Helpers and Data Classes ---
    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    // OpenAI/Groq-style classes
    static class Message { String role; String content; Message(String r, String c) { role=r; content=c; } }
    static class Choice { Message message; }
    static class ChatResponse { List<Choice> choices; }
    static class ChatRequest { List<Message> messages; String model; ChatRequest(List<Message> m, String mo) { messages=m; model=mo; } }
    // Gemini-style classes
    static class GeminiPart { String text; GeminiPart(String t) { this.text = t; } }
    static class GeminiContent { List<GeminiPart> parts; GeminiContent(List<GeminiPart> p) { this.parts = p; } }
    static class GeminiCandidate { GeminiContent content; }
    static class GeminiResponse { List<GeminiCandidate> candidates; }
    static class GeminiRequest { List<GeminiContent> contents; GeminiRequest(List<GeminiContent> c) { this.contents = c; } }
    // Maven search classes
    static class MavenDoc { String g; String a; String v; }
    static class MavenResponse { List<MavenDoc> docs; }
    static class MavenSearchResponse { MavenResponse response; }


    // --- Thread-Safe Logging for Summary ---
    private static final List<String> successLog = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, String> failureLog = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        long startTime = System.nanoTime();

        // Check for required API keys based on the selected provider
        if (API_PROVIDER == ApiProvider.GROQ && (GROQ_API_KEY == null || GROQ_API_KEY.isBlank())) {
            System.err.println("❌ FATAL ERROR: GROQ_API_KEY environment variable not set for Groq provider.");
            return;
        }
        if (API_PROVIDER == ApiProvider.GEMINI && (GEMINI_API_KEY == null || GEMINI_API_KEY.isBlank())) {
            System.err.println("❌ FATAL ERROR: GEMINI_API_KEY environment variable not set for Gemini provider.");
            return;
        }

        Files.createDirectories(DEPENDENCY_CACHE_DIR);

        System.out.println("🚀 Starting Test Generation Bot for project: " + PROJECT_TO_TEST_PATH);

        List<String> classesToTest = discoverClasses(PROJECT_TO_TEST_PATH);
        if (classesToTest.isEmpty()) {
            System.err.println("❌ No classes found to test. Did you build the target project (e.g., with 'mvn install')?");
            return;
        }

        System.out.println("✅ Found " + classesToTest.size() + " classes. Starting parallel processing with " + PARALLEL_THREADS + " threads using " + API_PROVIDER + "...");


        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_THREADS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🚨 Shutdown signal received. Forcibly terminating executor and child processes...");
            executor.shutdownNow(); // Attempt to stop all actively executing tasks
            try {
                // Wait a bit for tasks to respond to cancellation
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate in the specified time.");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow(); // Re-cancel if current thread is interrupted
            }
            System.out.println("✅ Shutdown hook finished.");
        }));

        for (String className : classesToTest) {
            executor.submit(() -> {
                try {
                    String result = processClass(className, PROJECT_TO_TEST_PATH);
                    if (result == null) {
                        successLog.add(className);
                    } else {
                        failureLog.put(className, result);
                    }
                } catch (Exception e) {
                    String errorMsg = "FATAL EXCEPTION: " + e.getMessage();
                    failureLog.put(className, errorMsg);
                    System.err.printf("[%s] 🔥 Uncaught exception: %s\n", className, e.getMessage());
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.HOURS);

        long durationNanos = System.nanoTime() - startTime;

        printFinalSummary(durationNanos);
    }

    /**
     * Scans the target project's build directory to find all .class files.
     */
    private static List<String> discoverClasses(String projectPath) {
        List<String> classNames = new ArrayList<>();
        Path classesDir = Paths.get(projectPath, "target", "classes");
        if (!Files.exists(classesDir)) {
            return classNames;
        }

        try (Stream<Path> stream = Files.walk(classesDir)) {
            stream.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        String relativePath = classesDir.relativize(path).toString();
                        String className = relativePath.replace(File.separatorChar, '.').replaceAll("\\.class$", "");
                        // Basic filter to avoid inner classes, etc. Can be improved.
                        if (!className.contains("$")) {
                            classNames.add(className);
                        }
                    });
        } catch (IOException e) {
            System.err.println("🚨 Error discovering classes: " + e.getMessage());
        }
        return classNames;
    }

    /**
     * The main processing pipeline for a single class. Returns null on success or a String failure reason.
     */
    private static String processClass(String className, String projectPath) {
        System.out.printf("▶️  Processing class: %s\n", className);
        Path projectRoot = Paths.get(projectPath);
        // Unique temp dir for EvoSuite artifacts if they are created
        Path testOutputDir = projectRoot.resolve("evosuite-tests-" + className.replace('.', '_'));

        try {
            // Step 1: Attempt to run EvoSuite
            String rawEvoSuiteTestPath = runEvoSuite(className, projectRoot, testOutputDir);
            String rawEvoSuiteCode = null;

            if (rawEvoSuiteTestPath != null) {
                System.out.printf("[%s] -> EvoSuite generation successful.\n", className);
                rawEvoSuiteCode = Files.readString(Paths.get(rawEvoSuiteTestPath));
            } else {
                // THIS IS THE KEY CHANGE: Don't fail. Just warn and continue.
                System.out.printf("[%s] -> ⚠️ WARNING: EvoSuite failed to generate tests. Proceeding with LLM synthesis using only source code.\n", className);
            }

            // Step 2 & 3: Synthesize with LLM and Validate (this step now runs regardless of EvoSuite's success)
            String synthesisResult = synthesizeAndValidate(className, projectRoot, rawEvoSuiteCode);

            if (synthesisResult == null) {
                System.out.printf("✅ [%s] Successfully synthesized and validated test!\n", className);
                return null; // Success
            } else {
                // Prepend the EvoSuite failure warning to the final failure reason if it happened.
                String finalFailureReason = (rawEvoSuiteCode == null ? "[EvoSuite Failed] " : "") + synthesisResult;
                return finalFailureReason;
            }

        } catch (Exception e) {
            String errorMsg = "Unhandled Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            System.err.printf("[%s] 🔥 Uncaught exception: %s\n", className, e.getMessage());
            e.printStackTrace();
            return errorMsg;
        } finally {
            try {
                cleanupDirectory(testOutputDir);
            } catch (IOException e) {
                System.err.printf("[%s] Warning: Failed to cleanup temp directory %s\n", className, testOutputDir);
            }
        }
    }

    private static String runEvoSuite(String className, Path projectRoot, Path testOutputDir) throws IOException {
        Process mvnProcess = null;
        Process evosuiteProcess = null;
        try {
            System.out.printf("[%s] -> Generating full project classpath with Maven...\n", className);
            String classpathFile = testOutputDir.resolve("classpath.txt").toString();
            String mvnCommand = String.format(
                    "mvn dependency:build-classpath -Dmdep.outputFile=%s -DincludeScope=runtime -q",
                    classpathFile
            );

            ProcessBuilder mvnPb;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                mvnPb = new ProcessBuilder("cmd.exe", "/c", mvnCommand);
            } else {
                mvnPb = new ProcessBuilder("bash", "-c", mvnCommand);
            }
            mvnPb.directory(projectRoot.toFile());
            mvnProcess = mvnPb.start();

            // Wait for Maven, but handle interruption
            if (!mvnProcess.waitFor(2, TimeUnit.MINUTES)) {
                mvnProcess.destroyForcibly();
                System.err.printf("[%s] -> Maven process timed out.\n", className);
                return null;
            }

            if (mvnProcess.exitValue() != 0) {
                // Capture error output if maven fails
                String mvnErrorOutput = new String(mvnProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                System.err.printf("[%s] -> Maven dependency build failed. Error:\n%s\n", className, mvnErrorOutput);
                return null;
            }

            String dependencyClasspath = Files.readString(Paths.get(classpathFile));
            String projectCP = projectRoot.resolve("target/classes").toString();
            String fullClasspath = projectCP + File.pathSeparator + dependencyClasspath;

            System.out.printf("[%s] -> Classpath generated. Starting EvoSuite process...\n", className);
            List<String> command = List.of(
                    "java", "-jar", EVOSUITE_JAR,
                    "-class", className,
                    "-projectCP", fullClasspath,
                    "-Dtest_dir=" + testOutputDir.toString(),
                    "-Dmock_if_no_generator=true", // <-- Very important for Spring
                    "-Dsearch_budget=60" // Give it 60 seconds to run
            );

            ProcessBuilder evosuitePb = new ProcessBuilder(command);
            evosuitePb.inheritIO();
            evosuiteProcess = evosuitePb.start();

            // Wait for EvoSuite, but handle interruption
            if (!evosuiteProcess.waitFor(5, TimeUnit.MINUTES)) {
                evosuiteProcess.destroyForcibly();
                System.err.printf("[%s] -> EvoSuite process timed out.\n", className);
                return null;
            }

            System.out.printf("[%s] -> EvoSuite process finished with exit code: %d\n", className, evosuiteProcess.exitValue());
            if (evosuiteProcess.exitValue() != 0) return null;

            String expectedFilePath = testOutputDir.resolve(className.replace('.', File.separatorChar) + "_ESTest.java").toString();
            return Files.exists(Paths.get(expectedFilePath)) ? expectedFilePath : null;

        } catch (InterruptedException e) {
            // This block now executes when you press the stop button!
            System.err.printf("[%s] -> Interrupted while waiting for external process. Terminating...\n", className);
            if (mvnProcess != null) mvnProcess.destroyForcibly();
            if (evosuiteProcess != null) evosuiteProcess.destroyForcibly();
            // Preserve the interrupted status
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String synthesizeAndValidate(String className, Path projectRoot, String rawEvoSuiteCode) throws IOException, InterruptedException {
        System.out.printf("[%s] -> Synthesizing JUnit 5 test with LLM (%s)...%n", className, API_PROVIDER);

        Path sourceFilePath = projectRoot.resolve("src/main/java").resolve(className.replace('.', File.separatorChar) + ".java");
        if (!Files.exists(sourceFilePath)) {
            return "LLM Synthesis Failure: Source file not found at " + sourceFilePath;
        }
        String sourceCode = Files.readString(sourceFilePath);

        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        String packageName = className.contains(".") ? className.substring(0, className.lastIndexOf('.')) : "";
        Path testSrcDir = projectRoot.resolve("src/test/java");
        Path packagePath = testSrcDir.resolve(packageName.replace('.', File.separatorChar));
        Path existingTestPath = packagePath.resolve(simpleClassName + "Test.java");

        String existingTestCode = null;
        if (Files.exists(existingTestPath)) {
            System.out.printf("[%s] -> Found existing test file to use as style guide: %s%n", className, existingTestPath);
            existingTestCode = Files.readString(existingTestPath);
        }

        String prompt = createSynthesisPrompt(sourceCode, rawEvoSuiteCode, existingTestCode);

        // Call the new centralized API method
        String llmMessageContent = callLlmApi(prompt);

        // Check for errors from the API call
        if (llmMessageContent.startsWith("LLM API Error") || llmMessageContent.startsWith("LLM Synthesis Failure")) {
            return llmMessageContent;
        }

        // --- Parse LLM Response ---
        String synthesizedCode = parseCodeFromLLMResponse(llmMessageContent);

        if (synthesizedCode.isBlank()) {
            return "LLM Synthesis Failure: Could not extract a valid Java code block from the response.";
        }

        // --- Validate and Save ---
        return validateAndSaveJUnit5Test(synthesizedCode, existingTestPath, projectRoot);
    }

    private static String callLlmApi(String prompt) throws IOException, InterruptedException {
        // Respect rate limits
        Thread.sleep(API_REQUEST_DELAY_MS);

        if (API_PROVIDER == ApiProvider.GROQ) {
            System.out.println("      -> Calling Groq API...");
            ChatRequest requestPayload = new ChatRequest(List.of(new Message("user", prompt)), GROQ_MODEL);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(GROQ_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + GROQ_API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestPayload))).build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "LLM API Error (Groq): " + response.statusCode() + " | " + response.body();
            }

            ChatResponse chatResponse = GSON.fromJson(response.body(), ChatResponse.class);
            if (chatResponse.choices == null || chatResponse.choices.isEmpty() || chatResponse.choices.get(0).message == null) {
                return "LLM Synthesis Failure (Groq): API response was empty or malformed.";
            }
            return chatResponse.choices.get(0).message.content;

        } else if (API_PROVIDER == ApiProvider.GEMINI) {
            System.out.println("      -> Calling Gemini API...");
            String geminiUrl = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", GEMINI_MODEL, GEMINI_API_KEY);

            // Construct the Gemini-specific payload
            GeminiRequest requestPayload = new GeminiRequest(
                    List.of(new GeminiContent(List.of(new GeminiPart(prompt))))
            );

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(geminiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestPayload))).build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "LLM API Error (Gemini): " + response.statusCode() + " | " + response.body();
            }

            GeminiResponse geminiResponse = GSON.fromJson(response.body(), GeminiResponse.class);
            if (geminiResponse.candidates == null || geminiResponse.candidates.isEmpty()
                    || geminiResponse.candidates.get(0).content == null
                    || geminiResponse.candidates.get(0).content.parts == null
                    || geminiResponse.candidates.get(0).content.parts.isEmpty()
                    || geminiResponse.candidates.get(0).content.parts.get(0).text == null) {
                return "LLM Synthesis Failure (Gemini): API response was empty or malformed. Body: " + response.body();
            }
            return geminiResponse.candidates.get(0).content.parts.get(0).text;

        } else {
            return "LLM Synthesis Failure: Unknown API provider configured.";
        }
    }


    private static String createSynthesisPrompt(String sourceCode, String evosuiteCode, String existingTestCode) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are an expert Java developer specializing in writing clean, modern, and maintainable JUnit 5 tests.\n\n");
        promptBuilder.append("Your mission is to synthesize a single, comprehensive JUnit 5 test file for a given Java class. You will be provided with the following information:\n");
        promptBuilder.append("1. **The Source Code:** The Java class that needs to be tested.\n");

        // DYNAMICALLY CHANGE THE PROMPT BASED ON AVAILABLE INFORMATION
        if (evosuiteCode != null) {
            promptBuilder.append("2. **EvoSuite Test (Inspiration):** A test file generated by the EvoSuite tool. Treat this as a 'source of truth' for test cases. Extract the core logic (method calls, inputs, assertions) to ensure high test coverage.\n");
        }

        if (existingTestCode != null) {
            promptBuilder.append("3. **Existing JUnit 5 Test (Style Guide & Base):** A handwritten test file. Use this as a guide for style and structure. Your final output should MERGE the tests from this file with new tests. Do not remove existing tests.\n\n");
        } else {
            promptBuilder.append("3. No existing test file was found. You will create a new one from scratch.\n\n");
        }

        if (evosuiteCode == null) {
            promptBuilder.append("**IMPORTANT:** No EvoSuite test was provided. You must generate high-quality test cases based *only* on your analysis of the source code. Cover common scenarios, edge cases, and potential null inputs.\n\n");
        }

        promptBuilder.append("Follow these rules STRICTLY:\n");
        promptBuilder.append("1.  **OUTPUT JUNIT 5 ONLY:** The final code MUST use JUnit 5 (`org.junit.jupiter.api.*`).\n");
        promptBuilder.append("2.  **DESCRIPTIVE NAMING:** Create clear test method names (e.g., `testAdd_WithPositiveNumbers_ShouldReturnCorrectSum`).\n");
        promptBuilder.append("3.  **MERGE, DON'T REPLACE:** If an existing test file is provided, add new test cases to it.\n");
        promptBuilder.append("4.  **ASSERTIONS:** Use standard JUnit 5 assertions (`Assertions.assertEquals`, `Assertions.assertThrows`, etc.).\n");
        promptBuilder.append("5.  **NO EVO-SPECIFICS:** Discard any EvoSuite-specific code if it was provided.\n");
        promptBuilder.append("6.  **OUTPUT FORMAT:** Provide ONLY the complete Java code in a single ```java code block.\n\n");

        promptBuilder.append("---\n**Source Code Under Test:**\n```java\n").append(sourceCode).append("\n```\n\n");

        if (evosuiteCode != null) {
            promptBuilder.append("---\n**EvoSuite Test (Inspiration for Test Cases):**\n```java\n").append(evosuiteCode).append("\n```\n\n");
        }

        if (existingTestCode != null) {
            promptBuilder.append("---\n**Existing JUnit 5 Test (Style Guide & Base):**\n```java\n").append(existingTestCode).append("\n```\n");
        }
        return promptBuilder.toString();
    }

    private static String parseCodeFromLLMResponse(String content) {
        int startBlock = content.indexOf("```java");
        if (startBlock != -1) {
            int endBlock = content.indexOf("```", startBlock + 7);
            if (endBlock != -1) {
                return content.substring(startBlock + 7, endBlock).trim();
            }
            return content.substring(startBlock + 7).trim();
        }
        return content.trim().startsWith("package") ? content.trim() : "";
    }

    private static String validateAndSaveJUnit5Test(String synthesizedCode, Path targetSavePath, Path projectRoot) throws IOException, InterruptedException {
        String className = targetSavePath.getFileName().toString().replace("Test.java", "");
        System.out.printf("[%s] -> Validating synthesized code (with self-healing)...\n", className);

        Path tempDir = Files.createTempDirectory("junit-validation-" + className);
        Path tempFile = tempDir.resolve(targetSavePath.getFileName());
        Files.writeString(tempFile, synthesizedCode);

        List<Path> classpathJars = new ArrayList<>();
        classpathJars.add(projectRoot.resolve("target/classes"));
        for (String jar : BASE_JARS) {
            Path jarPath = Paths.get(jar);
            if (Files.exists(jarPath)) {
                classpathJars.add(jarPath);
            } else {
                System.err.printf("[%s] Warning: Base dependency JAR not found: %s\n", className, jar);
            }
        }
        try (Stream<Path> stream = Files.walk(DEPENDENCY_CACHE_DIR)) {
            stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".jar")).forEach(classpathJars::add);
        }

        Set<String> resolvedPackages = new HashSet<>();
        for (int attempt = 1; attempt <= MAX_COMPILE_RETRIES; attempt++) {
            String classpath = classpathJars.stream().map(Path::toAbsolutePath).map(Path::toString).collect(Collectors.joining(File.pathSeparator));
            System.out.printf("[%s] -> Compile attempt %d/%d with %d JARs on classpath.\n", className, attempt, MAX_COMPILE_RETRIES, (int) classpathJars.stream().filter(p -> !p.toString().contains("target")).count());

            List<String> compileCommand = List.of("javac", "-cp", classpath, tempFile.toString());
            ProcessBuilder pb = new ProcessBuilder(compileCommand).redirectErrorStream(true);
            Process process = pb.start();
            String compilerOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(30, TimeUnit.SECONDS);

            if (process.exitValue() == 0) {
                System.out.printf("[%s] -> ✅ VICTORY! Validation successful.\n", className);
                Files.createDirectories(targetSavePath.getParent());
                Files.writeString(targetSavePath, synthesizedCode);
                System.out.printf("[%s] -> Saved new/updated test to: %s\n", className, targetSavePath);
                cleanupDirectory(tempDir);
                return null; // SUCCESS
            }

            String missingPackage = parseMissingPackage(compilerOutput);
            if (missingPackage == null || resolvedPackages.contains(missingPackage)) {
                String failureReason = "Compilation Failure: " + (missingPackage == null ? "Syntax error or other non-dependency issue." : "Could not resolve '" + missingPackage + "' after repeated attempts.") + "\n--- Compiler Output ---\n" + compilerOutput;
                cleanupDirectory(tempDir);
                return failureReason;
            }

            System.out.printf("[%s] -> Identified missing package: '%s'. Searching Maven Central...\n", className, missingPackage);
            resolvedPackages.add(missingPackage);
            Path downloadedJar = findAndDownloadJar(missingPackage);

            if (downloadedJar != null) {
                System.out.printf("[%s] -> Successfully downloaded '%s'. Retrying compilation.\n", className, downloadedJar.getFileName());
                classpathJars.add(downloadedJar);
            } else {
                String failureReason = "Compilation Failure: Could not find or download a JAR for package '" + missingPackage + "'.\n--- Compiler Output ---\n" + compilerOutput;
                cleanupDirectory(tempDir);
                return failureReason;
            }
        }
        cleanupDirectory(tempDir);
        return "Compilation Failure: Exceeded max retry attempts.";
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

        if (response.statusCode() != 200) return null;

        MavenSearchResponse searchResponse = GSON.fromJson(response.body(), MavenSearchResponse.class);
        if (searchResponse.response == null || searchResponse.response.docs.isEmpty()) return null;

        MavenDoc doc = searchResponse.response.docs.get(0);
        String jarFileName = String.format("%s-%s.jar", doc.a, doc.v);
        Path localJarPath = DEPENDENCY_CACHE_DIR.resolve(jarFileName);

        if (Files.exists(localJarPath)) {
            System.out.printf("      Found JAR in local cache: %s\n", localJarPath);
            return localJarPath;
        }

        String downloadUrl = String.format("https://repo1.maven.org/maven2/%s/%s/%s/%s", doc.g.replace('.', '/'), doc.a, doc.v, jarFileName);
        System.out.printf("      Downloading from: %s\n", downloadUrl);
        HttpRequest downloadRequest = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).build();
        HttpResponse<Path> downloadResponse = HTTP_CLIENT.send(downloadRequest, HttpResponse.BodyHandlers.ofFile(localJarPath));

        return (downloadResponse.statusCode() == 200) ? downloadResponse.body() : null;
    }

    private static void cleanupDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    private static void printFinalSummary(long durationNanos) {
        System.out.println("\n\n=======================================================");
        System.out.println("🎉 All tasks completed!");
        System.out.println("=======================================================");
        System.out.printf("✅ Successful generations: %d\n", successLog.size());
        System.out.printf("❌ Failed generations: %d\n", failureLog.size());
        System.out.println("-------------------------------------------------------");

        if (!failureLog.isEmpty()) {
            System.out.println("\n📋 DETAILED FAILURE REPORT:");

            Map<String, List<String>> failuresByReason = failureLog.entrySet().stream()
                    .collect(Collectors.groupingBy(
                            entry -> {
                                if (entry.getValue().startsWith("EvoSuite")) return "EvoSuite Failures";
                                if (entry.getValue().startsWith("LLM")) return "LLM Synthesis Failures";
                                if (entry.getValue().startsWith("Compilation")) return "Compilation Failures";
                                return "Other Failures";
                            },
                            Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                    ));

            failuresByReason.forEach((reason, classes) -> {
                System.out.printf("\n--- %s (%d) ---\n", reason, classes.size());
                for (String className : classes) {
                    System.out.printf("  - %s:\n", className);
                    // Print failure reason with indentation
                    String[] lines = failureLog.get(className).split("\n");
                    for (String line : lines) {
                        System.out.printf("      %s\n", line);
                    }
                }
            });
        }
        long totalSeconds = TimeUnit.NANOSECONDS.toSeconds(durationNanos);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        String durationFormatted = String.format("%d minutes, %d seconds", minutes, seconds);

        System.out.println("-------------------------------------------------------");
        System.out.printf("⏱️  Total Execution Time: %s\n", durationFormatted);
        System.out.println("=======================================================");
        System.out.println("\n=======================================================");
    }
}