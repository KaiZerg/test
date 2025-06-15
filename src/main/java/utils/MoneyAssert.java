package utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneyAssert {
    public static void assertMoneyEquals(double actual, String expectedStr) {
        assertMoneyEquals(Double.toString(actual), expectedStr);
    }

    public static void assertMoneyEquals(String actualStr, String expectedStr) {
        BigDecimal actualBD = new BigDecimal(actualStr).setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedBD = new BigDecimal(expectedStr).setScale(2, RoundingMode.HALF_UP);
        if (actualBD.compareTo(expectedBD) != 0) {
            throw new AssertionError("Expected: " + expectedBD + ", but was: " + actualBD);
        }
    }

    public static void assertMoneyEquals(BigDecimal actual, String expectedStr) {
        assertMoneyEquals(actual.toString(), expectedStr);
    }
}
