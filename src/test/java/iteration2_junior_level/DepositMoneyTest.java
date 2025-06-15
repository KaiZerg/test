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
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .statusCode(HttpStatus.SC_CREATED);
    }

    @Test
    public void AuthorizedUserCanDepositMoneyToOwnAccount(){
        int accountId = 1;
        BigDecimal depositAmount = new BigDecimal("1000.00");

        String response = given()
                .header("Authorization",userAuthHeader) // Добавляем заголовок авторизации
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": %s
                        }
                        """.formatted(accountId, depositAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit") // Используем относительный путь, так как baseURI и port уже установлены
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .asString();

        JsonPath jsonPath = JsonPath.from(response);

        // Проверка последней транзакции
        double lastTransactionAmount = jsonPath.getDouble("transactions.max { it.id }.amount");
        MoneyAssert.assertMoneyEquals(lastTransactionAmount, String.valueOf(depositAmount));
    }

    @Test
    public void AuthorizedUserCannotDepositNegativeAmount(){
        int accountId = 1;
        BigDecimal depositAmount = new BigDecimal("-100.00");
        String expectedErrorMessage = "Invalid account or amount";

        given()
                .header("Authorization",userAuthHeader) // Добавляем заголовок авторизации
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": %s
                        }
                        """.formatted(accountId, depositAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit") // Используем относительный путь, так как baseURI и port уже установлены
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));
    }

    @Test
    public void AuthorizedUserCannotDepositToNonExistentAccount(){
        int accountId = 999;
        BigDecimal depositAmount = new BigDecimal("1000.00");
        String expectedErrorMessage = "Unauthorized access to account";

        given()
                .header("Authorization",userAuthHeader) // Добавляем заголовок авторизации
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": %s
                        }
                        """.formatted(accountId, depositAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit") // Используем относительный путь, так как baseURI и port уже установлены
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN)
                .body(Matchers.equalTo(expectedErrorMessage));
    }

    @Test
    public void AuthorizedUserCannotDepositMoneyExceedingMaximum(){
        int accountId = 1;
        BigDecimal depositAmount = new BigDecimal("5001.00");
        String expectedErrorMessage = "Deposit amount exceeds the 5000 limit";

        given()
                .header("Authorization",userAuthHeader) // Добавляем заголовок авторизации
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": %s
                        }
                        """.formatted(accountId, depositAmount))
                .when()
                .post("http://localhost:4111/api/v1/accounts/deposit") // Используем относительный путь, так как baseURI и port уже установлены
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));
    }
}
