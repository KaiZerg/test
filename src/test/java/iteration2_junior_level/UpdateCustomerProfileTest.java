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

import java.util.List;

import static io.restassured.RestAssured.given;

public class UpdateCustomerProfileTest {
    private static String userAuthHeader;

    @BeforeAll
    public static void setup() {
        // Настройка логгирования
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter())); //фильтры нужны для показа запроса и ответа в результате

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
    }

    @Test
    public void authorizedUserCanUpdateProfileNameWithValidFormatTest(){
        String validName = "John Johnny";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(validName))
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверка, что профиль обновился через GET запрос
        String profileResponse = given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .asString();

        // Проверяем, что имя обновилось
        JsonPath profileJsonPath = JsonPath.from(profileResponse);
        String actualName = profileJsonPath.getString("customer.name");
        if (actualName == null) {
            actualName = profileJsonPath.getString("name");
        }
        if (actualName == null || !validName.equals(actualName)) {
            throw new AssertionError("Expected name: " + validName + ", but was: " + actualName);
        }
    }

    @Test
    public void authorizedUserCannotUpdateProfileNameWithSingleWord(){
        String expectedErrorMessage = "Name must contain two words with letters only";
        String invalidName = "John";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(invalidName))
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));
    }

    @Test
    public void authorizedUserCannotUpdateProfileNameWithNumbers(){
        String invalidName = "John 123";
        String expectedErrorMessage = "Name must contain two words with letters only";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(invalidName))
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));
    }

    @Test
    public void authorizedUserCannotUpdateProfileNameWithSpecialCharacters(){
        String invalidName = "John @#$%";
        String expectedErrorMessage = "Name must contain two words with letters only";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(invalidName))
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));
    }

    @Test
    public void authorizedUserCannotUpdateProfileNameWithEmptyString(){
        String invalidName = "";
        String expectedErrorMessage = "Name must contain two words with letters only";

        given()
                .header("Authorization",userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(invalidName))
                .when()
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));
    }
}
