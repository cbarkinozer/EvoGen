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


Our Prompt:
```txt
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
```


Result:
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

@RunWith(EvoRunner.class) 
@EvoRunnerParameters(mockJVMNonDeterminism = true, useVFS = true, useVNET = true, resetStaticState = true, separateClassLoader = true) 
public class Calculator_ESTest extends Calculator_ESTest_scaffolding {

    /**
     * Test subtracting zero from zero.
     */
    @Test(timeout = 4000)
    public void testSubtract_WithZeroNumbers_ShouldReturnZero() throws Throwable {
        Calculator calculator = new Calculator();
        int result = calculator.subtract(0, 0);
        assertEquals(0, result);
    }

    /**
     * Test subtracting a positive number from zero.
     */
    @Test(timeout = 4000)
    public void testSubtract_WithZeroAndPositiveNumber_ShouldReturnNegative() throws Throwable {
        Calculator calculator = new Calculator();
        int result = calculator.subtract(0, 1638);
        assertEquals((-1638), result);
    }

    /**
     * Test dividing a small number by a larger number.
     */
    @Test(timeout = 4000)
    public void testDivide_WithSmallNumeratorAndLargeDenominator_ShouldReturnZero() throws Throwable {
        Calculator calculator = new Calculator();
        int result = calculator.divide(1, 1662);
        assertEquals(0, result);
    }

    /**
     * Test dividing a positive number by a negative number.
     */
    @Test(timeout = 4000)
    public void testDivide_WithPositiveNumeratorAndNegativeDenominator_ShouldReturnNegativeQuotient() throws Throwable {
        Calculator calculator = new Calculator();
        int result = calculator.divide(1662, (-123));
        assertEquals((-13), result);
    }

    /**
     * Test adding zero to zero.
     */
    @Test(timeout = 4000)
    public void testAdd_WithZeroNumbers_ShouldReturnZero() throws Throwable {
        Calculator calculator = new Calculator();
        int result = calculator.add(0, 0);
        assertEquals(0, result);
    }

    /**
     * Test adding a negative number to a positive number.
     */
    @Test(timeout = 4000)
    public void testAdd_WithNegativeAndPositiveNumbers_ShouldReturnCorrectSum() throws Throwable {
        Calculator calculator = new Calculator();
        int result = calculator.add((-123), 131);
        assertEquals(8, result);
    }

    /**
     * Test dividing by zero, expecting an IllegalArgumentException.
     */
    @Test(timeout = 4000)
    public void testDivide_ByZero_ShouldThrowIllegalArgumentException() throws Throwable {
        Calculator calculator = new Calculator();
        try {
            calculator.divide(0, 0);
            fail("Expecting exception: IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            verifyException("org.example.Calculator", e);
        }
    }

    /**
     * Test dividing a number by itself.
     */
    @Test(timeout = 4000)
    public void testDivide_WithSameNumeratorAndDenominator_ShouldReturnOne() throws Throwable {
        Calculator calculator = new Calculator();
        int result = calculator.divide(3192, 3192);
        assertEquals(1, result);
    }

    /**
     * Test subtracting a negative number from a positive number.
     */
    @Test(timeout = 4000)
    public void testSubtract_WithPositiveAndNegativeNumbers_ShouldReturnCorrectDifference() throws Throwable {
        Calculator calculator = new Calculator();
        int result = calculator.subtract(3161, (-1898));
        assertEquals(5059, result);
    }

    /**
     * Test adding two negative numbers.
     */
    @Test(timeout = 4000)
    public void testAdd_WithTwoNegativeNumbers_ShouldReturnCorrectSum() throws Throwable {
        Calculator calculator = new Calculator();
        int result = calculator.add((-2336), (-2336));
        assertEquals((-4672), result);
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