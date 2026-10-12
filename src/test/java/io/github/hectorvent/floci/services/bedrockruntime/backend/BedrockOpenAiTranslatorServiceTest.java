package io.github.hectorvent.floci.services.bedrockruntime.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.testutil.LogCapture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockOpenAiTranslatorServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "{}", "\"\"", "\"private-invalid-effort\""})
    void invalidEffortIsLoggedWithoutPayload(String effort) throws IOException {
        ObjectNode request = (ObjectNode) mapper.readTree("{\"outputConfig\":{\"effort\":" + effort + "}}");
        for (boolean stream : new boolean[]{false, true}) {
            List<LogRecord> records = LogCapture.capture(BedrockOpenAiTranslator.class, () -> {
                AwsException error = assertThrows(AwsException.class,
                        () -> BedrockOpenAiTranslator.toOpenAiRequest(mapper, request, "model", stream));
                assertEquals("ValidationException", error.getErrorCode());
                assertEquals(400, error.getHttpStatus());
            });
            assertTrue(records.stream().anyMatch(record -> record.getLevel().equals(Level.WARNING)
                    && record.getMessage().contains("outputConfig")), "invalid effort must be logged");
            assertTrue(records.stream().allMatch(record -> !record.getMessage().contains("private-invalid-effort")));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidSchemaIsLoggedWithoutPayload(boolean stream) {
        ObjectNode request = mapper.createObjectNode();
        request.putObject("outputConfig").putObject("textFormat").put("type", "json_schema")
                .putObject("structure").putObject("jsonSchema").put("schema", "private-invalid-schema");
        List<LogRecord> records = LogCapture.capture(BedrockOpenAiTranslator.class, () -> {
            AwsException error = assertThrows(AwsException.class,
                    () -> BedrockOpenAiTranslator.toOpenAiRequest(mapper, request, "model", stream));
            assertEquals("ValidationException", error.getErrorCode());
            assertEquals(400, error.getHttpStatus());
        });
        assertTrue(records.stream().anyMatch(record -> record.getLevel().equals(Level.WARNING)
                && record.getMessage().contains("outputConfig")), "invalid schema must be logged");
        assertTrue(records.stream().allMatch(record -> !record.getMessage().contains("private-invalid-schema")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void toolResultImageRejectionIsLoggedWithoutPayload(boolean stream) {
        ObjectNode request = mapper.createObjectNode();
        request.putArray("messages").addObject().put("role", "user").putArray("content")
                .addObject().putObject("toolResult").put("toolUseId", "call_1")
                .putArray("content").addObject().putObject("image").put("format", "png")
                .putObject("source").put("bytes", "private-image-content");
        List<LogRecord> records = LogCapture.capture(BedrockOpenAiTranslator.class, () -> {
            AwsException error = assertThrows(AwsException.class,
                    () -> BedrockOpenAiTranslator.toOpenAiRequest(mapper, request, "model", stream));
            assertEquals("ValidationException", error.getErrorCode());
            assertEquals(400, error.getHttpStatus());
        });
        assertTrue(records.stream().anyMatch(record -> record.getLevel().equals(Level.WARNING)
                && record.getMessage().contains("toolResult")), "image rejection must be logged");
        assertTrue(records.stream().allMatch(record -> !record.getMessage().contains("private-image-content")));
    }
}
