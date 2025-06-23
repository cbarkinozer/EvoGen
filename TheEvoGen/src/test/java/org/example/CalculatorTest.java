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