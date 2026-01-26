package iteration1_junior_level;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class CreateUserTest {
    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter()));
    }

    public static Stream<Arguments> userValidData() {
        return Stream.of(
                //username field validation
                Arguments.of("kate2000111", "Kate2000#", "USER"),
                Arguments.of("abc", "Password33$", "USER"),
                Arguments.of("abcdeabcdeab-._", "Password33$", "USER")
        );
    }

    @MethodSource("userValidData")
    @ParameterizedTest
    public void adminCanCreateUserWithValidData(String username, String password, String role){
        String requestBody = String.format(
                """
                         {
                           "username": "%s",
                           "password": "%s",
                           "role": "%s"
                         }
                         """, username, password, role);
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body(requestBody)
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);
    }


    public static Stream<Arguments> userInvalidData() {
        return Stream.of(
                //username field validation
                Arguments.of(" ", "Password33$", "USER", "username", "Username cannot be blank"),
                Arguments.of(" ", "Password33$", "USER", "username", "Username must be between 3 and 15 characters"),
                Arguments.of(" ", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("   ", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("ab", "Password33$", "USER", "username", "Username must be between 3 and 15 characters"),
                Arguments.of("abcdeabcdeabcdea", "Password33$", "USER", "username", "Username must be between 3 and 15 characters"),
                Arguments.of("B1C%2CDE", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("1B1C2CDE", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"), //не разработано
                Arguments.of("_B1C2CDE", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots") //не разработано
        );
    }

    @MethodSource("userInvalidData")
    @ParameterizedTest
    public void adminCanNotCreateUserWithInvalidData(String username, String password, String role, String errorKey, String errorValue){
        String requestBody = String.format(
                """
                         {
                           "username": "%s",
                           "password": "%s",
                           "role": "%s"
                         }
                         """, username, password, role);
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body(requestBody)
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(errorKey, Matchers.hasItem(containsString(errorValue)));
    }
}
