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

public class TransferMoneyTest {
    private static String userAuthHeader;
    private static int accountId1;
    private static int accountId2;


    @BeforeAll
    public static void setup() {
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

        // Создание и сохранение ID счетов
        accountId1 = given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .path("id");

        accountId2 = given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .path("id");

        // Пополнение первого счета
        BigDecimal depositAmount = new BigDecimal("1000.00");
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(String.format("""
                    {
                      "id": %s,
                      "balance": %s
                    }
                    """, accountId1, depositAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверка, что деньги поступили на счет
        String depositResponse = given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", accountId1)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .asString();

        JsonPath depositJsonPath = JsonPath.from(depositResponse);
        double maxDepositAmount = depositJsonPath.getDouble("max { it.id }.amount");
        MoneyAssert.assertMoneyEquals(maxDepositAmount, String.valueOf(depositAmount));
    }

    @Test
    public void authorizedUserCanTransferMoneyBetweenAccounts(){
        int senderAccountId = accountId1;
        int receiverAccountId = accountId2;
        String transferAmount = "500.00";
        String successMessage = "Transfer successful";

        String response = given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %s,
                          "receiverAccountId": %s,
                          "amount": %s
                        }
                        """.formatted(senderAccountId, receiverAccountId, transferAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("message", Matchers.equalTo(successMessage))
                .body("receiverAccountId", Matchers.equalTo(receiverAccountId))
                .body("senderAccountId", Matchers.equalTo(senderAccountId))
                .extract()
                .asString();

        // Проверка суммы перевода
        double actualAmount = JsonPath.from(response).getDouble("amount");
        MoneyAssert.assertMoneyEquals(actualAmount, transferAmount);

        // Проверка, что деньги поступили на счет получателя
        String receiverResponse = given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", receiverAccountId)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .asString();

        JsonPath receiverJsonPath = JsonPath.from(receiverResponse);
        double maxReceiverAmount = receiverJsonPath.getDouble("max { it.id }.amount");
        MoneyAssert.assertMoneyEquals(maxReceiverAmount, transferAmount);
    }

    @Test
    public void authorizedUserCannotTransferMoneyExceedingMaximum(){
        int senderAccountId = accountId1;
        int receiverAccountId = accountId2;
        BigDecimal transferAmount = new BigDecimal("10001.00");
        String expectedErrorMessage = "Transfer amount cannot exceed 10000";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %s,
                          "receiverAccountId": %s,
                          "amount": %s
                        }
                        """.formatted(senderAccountId, receiverAccountId, transferAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));

        // Проверка, что деньги не поступили на счет получателя
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", receiverAccountId)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("findAll { it.amount == 10001.0 }.size()", Matchers.equalTo(0));
    }

    @Test
    public void authorizedUserCannotTransferMoreThanAccountBalance(){
        int senderAccountId = accountId1;
        int receiverAccountId = accountId2;
        BigDecimal transferAmount = new BigDecimal("10000.00");
        String expectedErrorMessage = "Invalid transfer: insufficient funds or invalid accounts";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %s,
                          "receiverAccountId": %s,
                          "amount": %s
                        }
                        """.formatted(senderAccountId, receiverAccountId, transferAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));

        // Проверка, что деньги не поступили на счет получателя
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", receiverAccountId)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("findAll { it.amount == 10000.0 }.size()", Matchers.equalTo(0));
    }

    @Test
    public void authorizedUserCannotTransferNegativeAmount(){
        int senderAccountId = accountId1;
        int receiverAccountId = accountId2;
        BigDecimal transferAmount = new BigDecimal("-100.00");
        String expectedErrorMessage = "Transfer amount must be at least 0.01";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %s,
                          "receiverAccountId": %s,
                          "amount": %s
                        }
                        """.formatted(senderAccountId, receiverAccountId, transferAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));

        // Проверка, что деньги не поступили на счет получателя
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", receiverAccountId)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("findAll { it.amount < 0 }.size()", Matchers.equalTo(0));
    }

    @Test
    public void authorizedUserCannotTransferFromNonExistentAccount(){
        int senderAccountId = 999;
        int receiverAccountId = accountId2;
        BigDecimal transferAmount = new BigDecimal("100.00");
        String expectedErrorMessage = "Unauthorized access to account";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %s,
                          "receiverAccountId": %s,
                          "amount": %s
                        }
                        """.formatted(senderAccountId, receiverAccountId, transferAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN)
                .body(Matchers.equalTo(expectedErrorMessage));

        // Проверка, что деньги не поступили на счет получателя (доступ запрещен)
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", receiverAccountId)
                .then()
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void authorizedUserCannotTransferToNonExistentAccount(){
        int senderAccountId = accountId1;
        int receiverAccountId = 999;
        BigDecimal transferAmount = new BigDecimal("100.00");
        String expectedErrorMessage = "Invalid transfer: insufficient funds or invalid accounts";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %s,
                          "receiverAccountId": %s,
                          "amount": %s
                        }
                        """.formatted(senderAccountId, receiverAccountId, transferAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));

        // Проверка, что деньги не поступили на счет получателя
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/{accountId}/transactions", receiverAccountId)
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);
    }
}
