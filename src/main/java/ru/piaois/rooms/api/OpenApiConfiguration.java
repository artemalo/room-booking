package ru.piaois.rooms.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI openApi() {
        var error = new ObjectSchema();
        error.addProperty("message", new StringSchema().example("Проверьте параметры запроса"));
        var responses = new Components()
                .addSecuritySchemes("basicAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"))
                .addResponses("BadRequest", response("Некорректные параметры или тело запроса", error))
                .addResponses("Unauthorized", response("Неверный логин или пароль", error))
                .addResponses("Forbidden", response("Недостаточно прав", error))
                .addResponses("NotFound", response("Аудитория или бронь не найдена", error))
                .addResponses("Conflict", response("Конфликт брони или состояния источника", error));
        return new OpenAPI().info(new Info().title("API помощника по бронированию аудиторий")
                .version("0.2.0").description("Учебное приложение. Данные хранятся в памяти. " +
                        "Для методов бронирования нажмите Authorize и введите reader / reader или member / member. " +
                        "Время в примерах указано в UTC; московское время на три часа больше."))
                .components(responses);
    }

    private ApiResponse response(String description, ObjectSchema schema) {
        return new ApiResponse().description(description).content(new Content()
                .addMediaType("application/json", new MediaType().schema(schema)));
    }
}
