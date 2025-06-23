# EvoGen

**Production Code -> EvoSuite -> LLM -> Runnable automatized unit tests for Java**

# 🧪 Motivation
EvoSuite has been generating functional, high-coverage unit tests using evolutionary algorithms since 2011. However, its test outputs often suffer from:
Obscure method names like test01
Unreadable variable names like int0 or calculator0
Poor maintainability for developers
Meanwhile, Large Language Models (LLMs) like GPT or LLaMA can generate human-readable tests, but:
They often hallucinate logic, leading to tests that don’t compile or pass
They require multiple prompts or agentic correction cycles to fix issues
They still struggle to match EvoSuite's coverage and reliability

# Proposed Solution
Why not combine both?
Generate tests with EvoSuite – ensuring correctness and high coverage.
Use an LLM to refactor those tests – improving readability and developer experience.
This approach leverages the strengths of both systems:
EvoSuite ensures solid functional coverage.
The LLM ensures human-readability and maintainability.

# ✅ Proof of Concept
This method was validated with 2 simple examples: A toy Calculator class and a Spring Boot Rest API. EvoSuite produced solid tests, and the LLM (via Groq's meta-llama/llama-4-maverick-17b-128e-instruct) was able to cleanly refactor them without changing logic.

# 🎯 Goal
The next step is scaling this approach to medium and large-scale enterprise applications, automating readable, production-grade unit test generation with:
Zero manual editing
High maintainability
Trustworthy logic from EvoSuite

# 📄 Related Papers
* Lior Broide, Roni Stern, (18 May 2025), EvoGPT: Enhancing Test Suite Robustness via  LLM-Based Generation and Genetic Optimization: [https://arxiv.org/abs/2505.12424]
* Matteo Biagiola, Gianluca Ghislotti, Paolo Tonella, (25 Dec 2024), Improving the Readability of Automatically  Generated Tests using Large Language Models: [https://arxiv.org/abs/2412.18843]
* Zhichao Zhou, Yutian Tang, Yun Lin, Jingzhu He, (19 Aug 2024), An LLM-based Readability Measurement for  Unit Tests’ Context-aware Inputs: [https://arxiv.org/abs/2407.21369]

# How EvoSuite Works?
* Genetic algorithms are a specific type of evolutionary algorithm that use selection, crossover, and mutation to evolve solutions.
* EvoSuite works on CPU, no GPU is needed.
* 🧬 Chromosome = Test Case: EvoSuite models each test case as a "chromosome"—a sequence of Java statements like constructor calls, method invocations, and value assignments.  Each statement in the test is a gene. The sequence defines the behavior and effect of the test on the system under test (SUT).
* 👤 Individual = Test Suite: An individual in the genetic population is a TestSuite, which is simply a set of test cases (chromosomes).  The fitness evaluation operates at the suite level, considering all contained tests together.
* 👥 Population = Test Suites: EvoSuite starts with a randomly generated population of test suites, often very simple, to explore the search space broadly.  This initial population typically just instantiates objects with default constructors.
* 📈 Fitness Function = Multi-Objective Optimization: EvoSuite uses multi-objective fitness, evaluating how well a test suite meets coverage goals (e.g., lines, branches, exceptions).  Fitness is a vector, not a scalar. For instance, covering line 42, both branches of an if-statement, and triggering specific exceptions are treated as separate goals.  EvoSuite uses Pareto dominance to compare suites: one suite dominates another if it’s better in at least one objective and no worse in others.  Secondary objective: Minimize test size. Shorter tests are favored if they achieve the same coverage, improving readability and maintainability. 
* 🏛️ Archive = Elitism: A special archive stores the best test case found for each coverage goal (e.g., the shortest test that covers line 45).  Selection for crossover comes from both the population and the archive, preserving good past solutions and encouraging stable progress.
* 🔧 Genetic Operators: Crossover: Combines test cases from two parent test suites to produce new offspring. This can include merging or recombining test case sequences.  Mutation: Applies small random edits to test cases. Types include: Parameter changes (e.g., "Mars" → null) Statement insertion (e.g., adding rocket.calculateFuel()) Statement removal (e.g., deleting a redundant line) Constructor swapping (e.g., switching to overloaded constructors) These operators explore the space of possible tests and introduce new behaviors.
* 🔁 Evolution Loop & Termination: EvoSuite runs multiple generations, evolving test suites via selection, crossover, mutation, and evaluation.  The process stops when: A search budget (time limit) is reached,  A coverage target is achieved,  Or stagnation occurs (no improvement for several generations).
* ✅ Finalization: After termination, EvoSuite minimizes test cases and adds assertions using dynamic analysis, converting raw test sequences into valid, readable, and assertive JUnit tests.
* Configuring EvoSuite: To maximize EvoSuite's effectiveness, it's crucial to provide accurate configuration, starting with the classpath—EvoSuite must access both your compiled code and all its dependencies using the -cp argument. Next, define a reasonable search budget using -Dsearch_budget to balance time and test thoroughness, ideally starting with 60–180 seconds. Choose a coverage criterion like BRANCH (recommended) to guide what EvoSuite should aim to test. Finally, handle external dependencies using automatic or manual mocking if your code interacts with systems like databases or networks, ensuring EvoSuite can generate tests successfully.

# 📌 How It Works
EvoSuite generates the initial raw test file.
The tool reads the source code and the raw test.
A structured prompt is sent to the LLM, including:
Source class
EvoSuite test
Clear instructions for refactoring (naming, comments, formatting, etc.)
The refactored output is validated and saved if it compiles.

Example:
```java
package org.example;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return a / b;
    }
}

```

EvoSuite:
```java
/*
 * This file was automatically generated by EvoSuite
 * Sat Jun 21 21:49:10 GMT 2025
 */

package org.example;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.evosuite.runtime.EvoAssertions.*;
import org.evosuite.runtime.EvoRunner;
import org.evosuite.runtime.EvoRunnerParameters;
import org.example.Calculator;
import org.junit.runner.RunWith;

@RunWith(EvoRunner.class) @EvoRunnerParameters(mockJVMNonDeterminism = true, useVFS = true, useVNET = true, resetStaticState = true, separateClassLoader = true) 
public class Calculator_ESTest extends Calculator_ESTest_scaffolding {

  @Test(timeout = 4000)
  public void test0()  throws Throwable  {
      Calculator calculator0 = new Calculator();
      int int0 = calculator0.subtract(0, 0);
      assertEquals(0, int0);
  }

  @Test(timeout = 4000)
  public void test1()  throws Throwable  {
      Calculator calculator0 = new Calculator();
      int int0 = calculator0.subtract(0, 1638);
      assertEquals((-1638), int0);
  }

  @Test(timeout = 4000)
  public void test2()  throws Throwable  {
      Calculator calculator0 = new Calculator();
      int int0 = calculator0.divide(1, 1662);
      assertEquals(0, int0);
  }

  @Test(timeout = 4000)
  public void test3()  throws Throwable  {
      Calculator calculator0 = new Calculator();
      int int0 = calculator0.divide(1662, (-123));
      assertEquals((-13), int0);
  }

  @Test(timeout = 4000)
  public void test4()  throws Throwable  {
      Calculator calculator0 = new Calculator();
      int int0 = calculator0.add(0, 0);
      assertEquals(0, int0);
  }

  @Test(timeout = 4000)
  public void test5()  throws Throwable  {
      Calculator calculator0 = new Calculator();
      int int0 = calculator0.add((-123), 131);
      assertEquals(8, int0);
  }

  @Test(timeout = 4000)
  public void test6()  throws Throwable  {
      Calculator calculator0 = new Calculator();
      // Undeclared exception!
      try { 
        calculator0.divide(0, 0);
        fail("Expecting exception: IllegalArgumentException");
      
      } catch(IllegalArgumentException e) {
         //
         // Cannot divide by zero
         //
         verifyException("org.example.Calculator", e);
      }
  }

  @Test(timeout = 4000)
  public void test7()  throws Throwable  {
      Calculator calculator0 = new Calculator();
      int int0 = calculator0.divide(3192, 3192);
      assertEquals(1, int0);
  }

  @Test(timeout = 4000)
  public void test8()  throws Throwable  {
      Calculator calculator0 = new Calculator();
      int int0 = calculator0.subtract(3161, (-1898));
      assertEquals(5059, int0);
  }

  @Test(timeout = 4000)
  public void test9()  throws Throwable  {
      Calculator calculator0 = new Calculator();
      int int0 = calculator0.add((-2336), (-2336));
      assertEquals((-4672), int0);
  }
}
```

Already Existing Test if it does exist:
```java
package org.example;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for the Calculator class.
 * This class demonstrates best practices for modern JUnit 5 testing.
 */
@DisplayName("Calculator Tests")
class CalculatorTest {

    private Calculator calculator;

    @BeforeEach
    void setUp() {
        // Arrange: Create a new Calculator instance before each test
        // This ensures test isolation, preventing side effects between tests.
        calculator = new Calculator();
    }

    @Nested
    @DisplayName("Addition Method")
    class AddTests {

        @ParameterizedTest(name = "{0} + {1} = {2}")
        @CsvSource({
                "0,    1,   1",
                "1,    2,   3",
                "5,    5,   10",
                "-1,   1,   0",
                "-5,  -5, -10",
                "1000, 2000, 3000"
        })
        @DisplayName("Should return the correct sum for a variety of integer inputs")
        void shouldReturnCorrectSum(int first, int second, int expectedResult) {
            // Act: Call the add method
            int actualResult = calculator.add(first, second);

            // Assert: Verify the result is as expected
            assertEquals(expectedResult, actualResult,
                    () -> first + " + " + second + " should equal " + expectedResult);
        }

        @Test
        @DisplayName("Should handle addition resulting in integer overflow")
        void shouldHandleIntegerOverflow() {
            // Act
            int result = calculator.add(Integer.MAX_VALUE, 1);

            // Assert: Java's int overflow wraps around
            // This test documents the current behavior of the system.
            assertEquals(Integer.MIN_VALUE, result, "Integer.MAX_VALUE + 1 should overflow to Integer.MIN_VALUE");
        }
    }

    @Nested
    @DisplayName("Subtraction Method")
    class SubtractTests {

        @ParameterizedTest(name = "{0} - {1} = {2}")
        @CsvSource({
                "1,    1,   0",
                "10,   5,   5",
                "5,   10,  -5",
                "-5,   -5,  0",
                "-5,   5,  -10"
        })
        @DisplayName("Should return the correct difference for a variety of integer inputs")
        void shouldReturnCorrectDifference(int first, int second, int expectedResult) {
            // Act
            int actualResult = calculator.subtract(first, second);
            // Assert
            assertEquals(expectedResult, actualResult);
        }
    }

    @Nested
    @DisplayName("Division Method")
    class DivideTests {

        @Test
        @DisplayName("Should return the correct quotient for valid division")
        void shouldReturnCorrectQuotient() {
            // Act & Assert
            assertEquals(5, calculator.divide(10, 2));
        }

        @Test
        @DisplayName("Should handle integer division resulting in truncation")
        void shouldTruncateResultOfIntegerDivision() {
            // Integer division in Java truncates the decimal part.
            // This test ensures that behavior is covered.
            assertEquals(2, calculator.divide(5, 2));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when dividing by zero")
        void shouldThrowExceptionWhenDividingByZero() {
            // Arrange
            int dividend = 10;
            int divisor = 0;
            String expectedMessage = "Cannot divide by zero";

            // Act & Assert
            // We verify both the exception type and the message for completeness.
            IllegalArgumentException thrownException = assertThrows(
                    IllegalArgumentException.class,
                    () -> calculator.divide(dividend, divisor),
                    "Dividing by zero should throw IllegalArgumentException"
            );

            assertEquals(expectedMessage, thrownException.getMessage(),
                    "The exception message should match the expected text.");
        }
    }
}
```


Our Prompt:
```txt
You are an expert Java developer specializing in writing clean, modern, and maintainable JUnit 5 tests.\n\n"
Your mission is to synthesize a single, comprehensive JUnit 5 test file for a given Java class. You will be provided with up to three pieces of information:\n");
1. **The Source Code:** The Java class that needs to be tested.\n");
2. **EvoSuite Test (Inspiration):** A test file generated by the EvoSuite tool. This file is NOT to be refactored. Instead, treat it as a 'source of truth' for test cases. Extract the core logic from it (e.g., method calls, inputs, expected outputs, and exceptions) to ensure high test coverage.\n");
3. **Existing JUnit 5 Test (Style Guide & Base):** A handwritten test file that already exists. Use this as a guide for style, naming conventions, and structure. Your final output should be a MERGE of the tests in this file and the new tests inspired by the EvoSuite output. Do not remove existing tests.\n\n");
3. No existing test file was found. You will create a new one from scratch based on the EvoSuite inspiration.\n\n"

Follow these rules STRICTLY:\n
1.  **OUTPUT JUNIT 5 ONLY:** The final code MUST use JUnit 5. Use imports from `org.junit.jupiter.api.*`. If you need parameterized tests, use `org.junit.jupiter.params.*`. DO NOT include any EvoSuite annotations or imports.\n");
2.  **DESCRIPTIVE NAMING:** Create clear, descriptive test method names, like `testAdd_WithPositiveNumbers_ShouldReturnCorrectSum`. If a style guide is provided, match its naming patterns.\n");
3.  **MERGE, DON'T REPLACE:** If an existing test file is provided, add the new test cases inspired by EvoSuite. If a test case seems redundant, prefer the existing handwritten version.\n");
4.  **ASSERTIONS:** Use standard JUnit 5 assertions (`Assertions.assertEquals`, `Assertions.assertThrows`, etc.).\n");
5.  **NO EVO-SPECIFICS:** Ignore and discard any EvoSuite-specific scaffolding, setup, or verification calls. Re-implement exception tests using `assertThrows`.\n");
6.  **CLASS NAME:** The test class name should follow the standard convention (e.g., `CalculatorTest.java`).\n");
 7.  **OUTPUT FORMAT:** Provide ONLY the complete, final Java code in a single ```java code block. Do not include any explanations or commentary outside the code block.\n\n");

Source Code Under Test:
<production_code_that_is_getting_tested>
EvoSuite Test (Inspiration for Test Cases):
<evosuite_tests>
Existing Modern JUnit Test (Style Guide & Base):
<already_existing_tests>
```


Result:
```java
package org.example;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Test suite for the Calculator class.
 * This class demonstrates best practices for modern JUnit5 testing.
 */
@DisplayName("Calculator Tests")
class CalculatorTest {

  private Calculator calculator;

  @BeforeEach
  void setUp() {
    // Arrange: Create a new Calculator instance before each test
    // This ensures test isolation, preventing side effects between tests.
    calculator = new Calculator();
  }

  @Nested
  @DisplayName("Addition Method")
  class AddTests {

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource({
            "0,1,1",
            "1,2,3",
            "5,5,10",
            "-1,1,0",
            "-5, -5, -10",
            "1000,2000,3000"
    })
    @DisplayName("Should return the correct sum for a variety of integer inputs")
    void shouldReturnCorrectSum(int first, int second, int expectedResult) {
      // Act: Call the add method
      int actualResult = calculator.add(first, second);

      // Assert: Verify the result is as expected
      assertEquals(expectedResult, actualResult,
              () -> first + " + " + second + " should equal " + expectedResult);
    }

    @Test
    @DisplayName("Should handle addition resulting in integer overflow")
    void shouldHandleIntegerOverflow() {
      // Act
      int result = calculator.add(Integer.MAX_VALUE, 1);

      // Assert: Java's int overflow wraps around
      // This test documents the current behavior of the system.
      assertEquals(Integer.MIN_VALUE, result, "Integer.MAX_VALUE +1 should overflow to Integer.MIN_VALUE");
    }

    @Test
    @DisplayName("Should add two positive numbers")
    void testAdd_WithPositiveNumbers_ShouldReturnCorrectSum() {
      // Act
      int result = calculator.add(10, 20);

      // Assert
      assertEquals(30, result, "10 + 20 should equal 30");
    }

    @Test
    @DisplayName("Should add two negative numbers")
    void testAdd_WithNegativeNumbers_ShouldReturnCorrectSum() {
      // Act
      int result = calculator.add(-10, -20);

      // Assert
      assertEquals(-30, result, "-10 + -20 should equal -30");
    }

    @Test
    @DisplayName("Should add a positive and a negative number")
    void testAdd_WithPositiveAndNegativeNumbers_ShouldReturnCorrectSum() {
      // Act
      int result = calculator.add(10, -20);

      // Assert
      assertEquals(-10, result, "10 + -20 should equal -10");
    }
  }

  @Nested
  @DisplayName("Subtraction Method")
  class SubtractTests {

    @ParameterizedTest(name = "{0} - {1} = {2}")
    @CsvSource({
            "1,1,0",
            "10,5,5",
            "5,10, -5",
            "-5, -5,0",
            "-5,5, -10"
    })
    @DisplayName("Should return the correct difference for a variety of integer inputs")
    void shouldReturnCorrectDifference(int first, int second, int expectedResult) {
      // Act
      int actualResult = calculator.subtract(first, second);
      // Assert
      assertEquals(expectedResult, actualResult);
    }

    @Test
    @DisplayName("Should subtract two positive numbers")
    void testSubtract_WithPositiveNumbers_ShouldReturnCorrectDifference() {
      // Act
      int result = calculator.subtract(20, 10);

      // Assert
      assertEquals(10, result, "20 - 10 should equal 10");
    }

    @Test
    @DisplayName("Should subtract two negative numbers")
    void testSubtract_WithNegativeNumbers_ShouldReturnCorrectDifference() {
      // Act
      int result = calculator.subtract(-10, -20);

      // Assert
      assertEquals(10, result, "-10 - -20 should equal 10");
    }

    @Test
    @DisplayName("Should subtract a positive and a negative number")
    void testSubtract_WithPositiveAndNegativeNumbers_ShouldReturnCorrectDifference() {
      // Act
      int result = calculator.subtract(10, -20);

      // Assert
      assertEquals(30, result, "10 - -20 should equal 30");
    }
  }

  @Nested
  @DisplayName("Division Method")
  class DivideTests {

    @Test
    @DisplayName("Should return the correct quotient for valid division")
    void shouldReturnCorrectQuotient() {
      // Act & Assert
      assertEquals(5, calculator.divide(10, 2));
    }

    @Test
    @DisplayName("Should handle integer division resulting in truncation")
    void shouldTruncateResultOfIntegerDivision() {
      // Integer division in Java truncates the decimal part.
      // This test ensures that behavior is covered.
      assertEquals(2, calculator.divide(5, 2));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when dividing by zero")
    void shouldThrowExceptionWhenDividingByZero() {
      // Arrange
      int dividend = 10;
      int divisor = 0;
      String expectedMessage = "Cannot divide by zero";

      // Act & Assert
      // We verify both the exception type and the message for completeness.
      IllegalArgumentException thrownException = assertThrows(
              IllegalArgumentException.class,
              () -> calculator.divide(dividend, divisor),
              "Dividing by zero should throw IllegalArgumentException"
      );

      assertEquals(expectedMessage, thrownException.getMessage(),
              "The exception message should match the expected text.");
    }

    @Test
    @DisplayName("Should divide two positive numbers")
    void testDivide_WithPositiveNumbers_ShouldReturnCorrectQuotient() {
      // Act
      int result = calculator.divide(20, 2);

      // Assert
      assertEquals(10, result, "20 / 2 should equal 10");
    }

    @Test
    @DisplayName("Should divide two negative numbers")
    void testDivide_WithNegativeNumbers_ShouldReturnCorrectQuotient() {
      // Act
      int result = calculator.divide(-20, -2);

      // Assert
      assertEquals(10, result, "-20 / -2 should equal 10");
    }

    @Test
    @DisplayName("Should divide a positive and a negative number")
    void testDivide_WithPositiveAndNegativeNumbers_ShouldReturnCorrectQuotient() {
      // Act
      int result = calculator.divide(20, -2);

      // Assert
      assertEquals(-10, result, "20 / -2 should equal -10");
    }
  }
}
```


# The following results belong to a project that had no existing tests (it has but not given), no EvoSuite configuration, and no prompt engineering, using parallel LLM API calls causing to timeouts, first try results of a larger project:

* Search finished after 61s and 2360 generations, 412961 statements, best individual has fitness: 3.0
* Minimizing test suite
* Going to analyze the coverage criteria
* Coverage analysis for criterion LINE
* Coverage of criterion LINE: 67%
* Total number of goals: 3
* Number of covered goals: 2
* Coverage analysis for criterion BRANCH
* Coverage of criterion BRANCH: 100%
* Total number of goals: 2
* Number of covered goals: 2
* Coverage analysis for criterion EXCEPTION
* Coverage of criterion EXCEPTION: 100%
* Total number of goals: 1
* Number of covered goals: 1
* Coverage analysis for criterion WEAKMUTATION
* Coverage of criterion WEAKMUTATION: 100% (no goals)
* Coverage analysis for criterion OUTPUT
* Coverage of criterion OUTPUT: 100% (no goals)
* Coverage analysis for criterion METHOD
* Coverage of criterion METHOD: 100%
* Total number of goals: 2
* Number of covered goals: 2
* Coverage analysis for criterion METHODNOEXCEPTION
* Coverage of criterion METHODNOEXCEPTION: 50%
* Total number of goals: 2
* Number of covered goals: 1
* Coverage analysis for criterion CBRANCH
* Coverage of criterion CBRANCH: 100%
* Total number of goals: 2
* Number of covered goals: 2
* Generated 2 tests with total length 2
* Resulting test suite's coverage: 83% (average coverage for all fitness functions)
* Generating assertions
* Resulting test suite's mutation score: 100%
* Compiling and checking tests
* Writing tests to file
* Writing JUnit test case 'OnlinebankingrestapiApplication_ESTest' to C:\Repos\OnlineBankingRestAPI\evosuite-tests
* Done!

* Computation finished
    - ✅ Successfully generated and refactored tests for com.cbarkinozer.onlinebankingrestapi.OnlinebankingrestapiApplication


>🎉 All tasks completed!
> Successful refactorings: 36  
> LLM Failure: 29  
> EvoSuite Failure: 56  


**The following are the successfully generated running, coverage increasing and readable classes in a real Java project:**

com.cbarkinozer.onlinebankingrestapi.app.acc.dto.AccAccountSaveDto
com.cbarkinozer.onlinebankingrestapi.app.acc.dto.AccAccountActivityDto
com.cbarkinozer.onlinebankingrestapi.app.acc.dto.AccMoneyActivityDto
com.cbarkinozer.onlinebankingrestapi.app.acc.dto.AccMoneyActivityRequestDto
com.cbarkinozer.onlinebankingrestapi.app.acc.entity.AccAccountActivity
com.cbarkinozer.onlinebankingrestapi.app.acc.enums.AccErrorMessage
com.cbarkinozer.onlinebankingrestapi.app.acc.service.AccAccountActivityService
com.cbarkinozer.onlinebankingrestapi.app.acc.service.AccMoneyTransferService
com.cbarkinozer.onlinebankingrestapi.app.crd.dto.CrdCreditCardPaymentDto
com.cbarkinozer.onlinebankingrestapi.app.crd.dto.CrdCreditCardSpendDto
com.cbarkinozer.onlinebankingrestapi.app.crd.enums.CrdErrorMessage
com.cbarkinozer.onlinebankingrestapi.app.crd.service.CrdCreditCardActivityService
com.cbarkinozer.onlinebankingrestapi.app.crd.service.CrdCreditCardActivityValidationService
com.cbarkinozer.onlinebankingrestapi.app.cus.dto.CusCustomerDto
com.cbarkinozer.onlinebankingrestapi.app.cus.dto.CusCustomerSaveDto
com.cbarkinozer.onlinebankingrestapi.app.cus.entity.CusCustomer
com.cbarkinozer.onlinebankingrestapi.app.cus.service.CusCustomerValidationService
com.cbarkinozer.onlinebankingrestapi.app.gen.entity.BaseAdditionalFields
com.cbarkinozer.onlinebankingrestapi.app.gen.entity.BaseEntity
com.cbarkinozer.onlinebankingrestapi.app.gen.enums.GenErrorMessage
com.cbarkinozer.onlinebankingrestapi.app.gen.exceptions.IllegalFieldException
com.cbarkinozer.onlinebankingrestapi.app.gen.exceptions.ItemNotFoundException
com.cbarkinozer.onlinebankingrestapi.app.gen.util.StringUtil
com.cbarkinozer.onlinebankingrestapi.app.kafka.consumer.KafkaListenerService
com.cbarkinozer.onlinebankingrestapi.app.kafka.dto.LogMessage
com.cbarkinozer.onlinebankingrestapi.app.kafka.producer.KafkaMessageController
com.cbarkinozer.onlinebankingrestapi.app.loa.dto.LoaApplyLoanDto
com.cbarkinozer.onlinebankingrestapi.app.loa.dto.LoaCalculateLateFeeResponseDto
com.cbarkinozer.onlinebankingrestapi.app.loa.entity.LoaLoan
com.cbarkinozer.onlinebankingrestapi.app.loa.entity.LoaLoanPayment
com.cbarkinozer.onlinebankingrestapi.app.loa.enums.LoaErrorMessage
com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanService
com.cbarkinozer.onlinebankingrestapi.app.log.entity.LogDetail
com.cbarkinozer.onlinebankingrestapi.app.sec.dto.SecLoginRequestDto
com.cbarkinozer.onlinebankingrestapi.app.sec.enums.EnumJwtConstant
com.cbarkinozer.onlinebankingrestapi.OnlinebankingrestapiApplication

**For Example below is the unit test of LoaLoanService:**
```java
/*
 * This file was automatically generated by EvoSuite
 * Sat Jun21 21:22:52 GMT 2025
 */

package com.cbarkinozer.onlinebankingrestapi.app.loa.service;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.evosuite.runtime.EvoAssertions.*;
import com.cbarkinozer.onlinebankingrestapi.app.cus.service.entityservice.CusCustomerEntityService;
import com.cbarkinozer.onlinebankingrestapi.app.loa.dto.LoaApplyLoanDto;
import com.cbarkinozer.onlinebankingrestapi.app.loa.dto.LoaCalculateLoanResponseDto;
import com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanService;
import com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanValidationService;
import com.cbarkinozer.onlinebankingrestapi.app.loa.service.entityservice.LoaLoanEntityService;
import com.cbarkinozer.onlinebankingrestapi.app.loa.service.entityservice.LoaLoanPaymentEntityService;
import java.math.BigDecimal;
import org.evosuite.runtime.EvoRunner;
import org.evosuite.runtime.EvoRunnerParameters;
import org.junit.runner.RunWith;

@RunWith(EvoRunner.class) 
@EvoRunnerParameters(mockJVMNonDeterminism = true, useVFS = true, useVNET = true, resetStaticState = true, separateClassLoader = true) 
public class LoaLoanService_ESTest extends LoaLoanService_ESTest_scaffolding {

    /**
     * Test calculateLoan method with valid installment count and principal loan amount.
     */
    @Test(timeout = 4000)
    public void testCalculateLoan_WithValidInstallmentAndPrincipalLoanAmount_ShouldReturnCorrectResponse() throws Throwable {
        LoaLoanValidationService loanValidationService = new LoaLoanValidationService((CusCustomerEntityService) null);
        LoaLoanService loanService = new LoaLoanService(loanValidationService, (LoaLoanEntityService) null, (LoaLoanPaymentEntityService) null);
        BigDecimal principalLoanAmount = BigDecimal.TEN;
        Integer installmentCount = 3100;
        LoaCalculateLoanResponseDto response = loanService.calculateLoan(installmentCount, principalLoanAmount);
        assertEquals("LoaCalculateLoanResponseDto(interestRate=0.0159, totalInterest=1478.7000, monthlyInstallmentAmount=0.4948, totalPayment=1533.7000, annualCostRate=0.1908, allocationFee=45)", response.toString());
    }

    /**
     * Test calculateLoan method with null installment count.
     */
    @Test(timeout = 4000)
    public void testCalculateLoan_WithNullInstallmentCount_ShouldThrowNullPointerException() throws Throwable {
        LoaLoanService loanService = new LoaLoanService((LoaLoanValidationService) null, (LoaLoanEntityService) null, (LoaLoanPaymentEntityService) null);
        BigDecimal principalLoanAmount = BigDecimal.TEN;
        try {
            loanService.calculateLoan((Integer) null, principalLoanAmount);
            fail("Expecting exception: NullPointerException");
        } catch (NullPointerException e) {
            verifyException("com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanService", e);
        }
    }

    /**
     * Test calculateLoan method with zero installment count and principal loan amount.
     */
    @Test(timeout = 4000)
    public void testCalculateLoan_WithZeroInstallmentCountAndPrincipalLoanAmount_ShouldThrowArithmeticException() throws Throwable {
        LoaLoanValidationService loanValidationService = new LoaLoanValidationService((CusCustomerEntityService) null);
        LoaLoanService loanService = new LoaLoanService(loanValidationService, (LoaLoanEntityService) null, (LoaLoanPaymentEntityService) null);
        Integer installmentCount = 0;
        BigDecimal principalLoanAmount = BigDecimal.ZERO;
        try {
            loanService.calculateLoan(installmentCount, principalLoanAmount);
            fail("Expecting exception: ArithmeticException");
        } catch (ArithmeticException e) {
            verifyException("java.math.BigDecimal", e);
        }
    }

    /**
     * Test applyLoan method with null LoaApplyLoanDto.
     */
    @Test(timeout = 4000)
    public void testApplyLoan_WithNullLoaApplyLoanDto_ShouldThrowNullPointerException() throws Throwable {
        LoaLoanService loanService = new LoaLoanService((LoaLoanValidationService) null, (LoaLoanEntityService) null, (LoaLoanPaymentEntityService) null);
        LoaApplyLoanDto applyLoanDto = new LoaApplyLoanDto();
        try {
            loanService.applyLoan(applyLoanDto);
            fail("Expecting exception: NullPointerException");
        } catch (NullPointerException e) {
            verifyException("com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanService", e);
        }
    }

    /**
     * Test calculateLateFee method with null loan id.
     */
    @Test(timeout = 4000)
    public void testCalculateLateFee_WithNullLoanId_ShouldThrowNullPointerException() throws Throwable {
        LoaLoanService loanService = new LoaLoanService((LoaLoanValidationService) null, (LoaLoanEntityService) null, (LoaLoanPaymentEntityService) null);
        try {
            loanService.calculateLateFee((Long) null);
            fail("Expecting exception: NullPointerException");
        } catch (NullPointerException e) {
            verifyException("com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanService", e);
        }
    }

    /**
     * Test payInstallment method with null loan id.
     */
    @Test(timeout = 4000)
    public void testPayInstallment_WithNullLoanId_ShouldThrowNullPointerException() throws Throwable {
        LoaLoanService loanService = new LoaLoanService((LoaLoanValidationService) null, (LoaLoanEntityService) null, (LoaLoanPaymentEntityService) null);
        try {
            loanService.payInstallment((Long) null);
            fail("Expecting exception: NullPointerException");
        } catch (NullPointerException e) {
            verifyException("com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanService", e);
        }
    }

    /**
     * Test findLoanById method with null loan id.
     */
    @Test(timeout = 4000)
    public void testFindLoanById_WithNullLoanId_ShouldThrowNullPointerException() throws Throwable {
        LoaLoanService loanService = new LoaLoanService((LoaLoanValidationService) null, (LoaLoanEntityService) null, (LoaLoanPaymentEntityService) null);
        try {
            loanService.findLoanById((Long) null);
            fail("Expecting exception: NullPointerException");
        } catch (NullPointerException e) {
            verifyException("com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanService", e);
        }
    }

    /**
     * Test applyLoan method with empty LoaApplyLoanDto.
     */
    @Test(timeout = 4000)
    public void testApplyLoan_WithEmptyLoaApplyLoanDto_ShouldThrowRuntimeException() throws Throwable {
        LoaLoanValidationService loanValidationService = new LoaLoanValidationService((CusCustomerEntityService) null);
        LoaLoanService loanService = new LoaLoanService(loanValidationService, (LoaLoanEntityService) null, (LoaLoanPaymentEntityService) null);
        LoaApplyLoanDto applyLoanDto = new LoaApplyLoanDto();
        try {
            loanService.applyLoan(applyLoanDto);
            fail("Expecting exception: RuntimeException");
        } catch (RuntimeException e) {
            verifyException("com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanValidationService", e);
        }
    }

    /**
     * Test calculateLoan method with negative installment count.
     */
    @Test(timeout = 4000)
    public void testCalculateLoan_WithNegativeInstallmentCount_ShouldThrowRuntimeException() throws Throwable {
        LoaLoanValidationService loanValidationService = new LoaLoanValidationService((CusCustomerEntityService) null);
        LoaLoanService loanService = new LoaLoanService(loanValidationService, (LoaLoanEntityService) null, (LoaLoanPaymentEntityService) null);
        Integer installmentCount = -2652;
        BigDecimal principalLoanAmount = BigDecimal.TEN;
        try {
            loanService.calculateLoan(installmentCount, principalLoanAmount);
            fail("Expecting exception: RuntimeException");
        } catch (RuntimeException e) {
            verifyException("com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanValidationService", e);
        }
    }

    /**
     * Test payLoanOff method with null loan id.
     */
    @Test(timeout = 4000)
    public void testPayLoanOff_WithNullLoanId_ShouldThrowNullPointerException() throws Throwable {
        LoaLoanService loanService = new LoaLoanService((LoaLoanValidationService) null, (LoaLoanEntityService) null, (LoaLoanPaymentEntityService) null);
        try {
            loanService.payLoanOff((Long) null);
            fail("Expecting exception: NullPointerException");
        } catch (NullPointerException e) {
            verifyException("com.cbarkinozer.onlinebankingrestapi.app.loa.service.LoaLoanService", e);
        }
    }
}
```