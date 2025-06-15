package iteration2_junior_level;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
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
    public void AuthorizedUserCanUpdateProfileNameWithValidFormatTest(){
        String validName = "John Johnny";

        given()
                .header("Authorization",userAuthHeader) // Добавляем заголовок авторизации
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(validName))
                .when()
                .put("http://localhost:4111/api/v1/customer/profile") // Используем относительный путь, так как baseURI и port уже установлены
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("customer.name", Matchers.equalTo("John Johnny")) // Проверяем, что имя изменилось
                .body("message", Matchers.equalTo("Profile updated successfully")); // Дополнительная проверка сообщения
    }

    @Test
    public void AuthorizedUserCannotUpdateProfileNameWithSingleWord(){
        String expectedErrorMessage = "Name must contain two words with letters only";
        String invalidName = "John";

        given()
                .header("Authorization",userAuthHeader) // Добавляем заголовок авторизации
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(invalidName))
                .when()
                .put("http://localhost:4111/api/v1/customer/profile") // Используем относительный путь, так как baseURI и port уже установлены
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));   // Проверяем точное соответствие текста ошибки
    }

    @Test
    public void AuthorizedUserCannotUpdateProfileNameWithNumbers(){
        String invalidName = "John 123";
        String expectedErrorMessage = "Name must contain two words with letters only";

        given()
                .header("Authorization",userAuthHeader) // Добавляем заголовок авторизации
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(invalidName))
                .when()
                .put("http://localhost:4111/api/v1/customer/profile") // Используем относительный путь, так как baseURI и port уже установлены
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));   // Проверяем точное соответствие текста ошибки
    }

    @Test
    public void AuthorizedUserCannotUpdateProfileNameWithSpecialCharacters(){
        String invalidName = "John @#$%";
        String expectedErrorMessage = "Name must contain two words with letters only";

        given()
                .header("Authorization",userAuthHeader) // Добавляем заголовок авторизации
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(invalidName))
                .when()
                .put("http://localhost:4111/api/v1/customer/profile") // Используем относительный путь, так как baseURI и port уже установлены
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));   // Проверяем точное соответствие текста ошибки
    }

    @Test
    public void AuthorizedUserCannotUpdateProfileNameWithEmptyString(){
        String invalidName = "";
        String expectedErrorMessage = "Name must contain two words with letters only";

        given()
                .header("Authorization",userAuthHeader) // Добавляем заголовок авторизации
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(invalidName))
                .when()
                .put("http://localhost:4111/api/v1/customer/profile") // Используем относительный путь, так как baseURI и port уже установлены
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(Matchers.equalTo(expectedErrorMessage));   // Проверяем точное соответствие текста ошибки
    }
}
