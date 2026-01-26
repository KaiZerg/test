package iteration2_junior_level;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.MoneyAssert;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;

public class DepositMoneyTest {
    private static String userAuthHeader;
    private static int accountId;

    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter()));

        // Создание тестового пользователя
        // Генерация короткого случайного имени пользователя
        String randomSuffix = String.valueOf((int)(Math.random() * 900) + 100); // 100-999
        String username = "u" + randomSuffix;
        String password = "Test123$";

        // Создание пользователя через админский API
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body(String.format("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "USER"
                        }
                        """, username, password))
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .statusCode(HttpStatus.SC_CREATED);

        // Получение токена для созданного пользователя
        userAuthHeader = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(String.format("""
                        {
                          "username": "%s",
                          "password": "%s"
                        }
                        """, username, password))
                .post("http://localhost:4111/api/v1/auth/login")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .header("Authorization");

        // Создание аккаунта для пользователя
        accountId = given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .path("id");

        // Проверка, что аккаунт был создан
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(accountId));
    }

    @Test
    public void authorizedUserCanDepositMoneyToOwnAccount(){
        BigDecimal depositAmount = new BigDecimal("1000.00");

        String response = given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": %s
                        }
                        """.formatted(accountId, depositAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .asString();

        JsonPath jsonPath = JsonPath.from(response);

        // Проверка последней транзакции
        double lastTransactionAmount = jsonPath.getDouble("transactions.max { it.id }.amount");
        MoneyAssert.assertMoneyEquals(lastTransactionAmount, String.valueOf(depositAmount));

        // Проверка, что деньги поступили на счет через GET запрос
        String accountResponse = given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", accountId)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .asString();

        JsonPath accountJsonPath = JsonPath.from(accountResponse);
        double maxTransactionAmount = accountJsonPath.getDouble("max { it.id }.amount");
        MoneyAssert.assertMoneyEquals(maxTransactionAmount, String.valueOf(depositAmount));
    }

    @Test
    public void authorizedUserCannotDepositNegativeAmount(){
        BigDecimal depositAmount = new BigDecimal("-100.00");
        String expectedErrorMessage = "Deposit amount must be at least 0.01";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": %s
                        }
                        """.formatted(accountId, depositAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));

        // Проверка, что деньги не поступили на счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", accountId)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("findAll { it.amount < 0 }.size()", Matchers.equalTo(0));
    }

    @Test
    public void authorizedUserCannotDepositToNonExistentAccount(){
        int accountId = 999;
        BigDecimal depositAmount = new BigDecimal("1000.00");
        String expectedErrorMessage = "Unauthorized access to account";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": %s
                        }
                        """.formatted(accountId, depositAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN)
                .body(Matchers.equalTo(expectedErrorMessage));

        // Проверка, что деньги не поступили на счет (проверяем доступ к транзакциям)
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", accountId)
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    @Test
    public void authorizedUserCannotDepositMoneyExceedingMaximum(){
        BigDecimal depositAmount = new BigDecimal("5001.00");
        String expectedErrorMessage = "Deposit amount cannot exceed 5000";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": %s
                        }
                        """.formatted(accountId, depositAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));

        // Проверка, что деньги не поступили на счет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", accountId)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("findAll { it.amount == 5001.0 }.size()", Matchers.equalTo(0));
    }
}
