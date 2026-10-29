package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.services.SaveResult;
import com.aemcloudproject.core.services.SaveStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonValidatorTest {

    private final JsonValidator validator = new JsonValidator();

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void acceptsObject() {
        assertTrue(validator.validate(b("{\"a\":1}")).isEmpty());
    }

    @Test
    void acceptsTopLevelScalars() {
        for (String s : new String[]{"42", "\"text\"", "null"}) {
            assertTrue(validator.validate(b(s)).isEmpty(), s);
        }
    }

    @Test
    void trailingCommaReportsLineAndColumn() {
        SaveResult r = validator.validate(b("{\n  \"a\": 1,\n}")).orElseThrow();
        assertEquals(SaveStatus.INVALID_JSON, r.getStatus());
        assertEquals(3, r.getLine());
        assertEquals(1, r.getColumn());
        assertNotNull(r.getMessage());
    }

    @Test
    void rejectsTrailingContent() {
        assertTrue(validator.validate(b("{} {}")).isPresent());
    }

    @Test
    void rejectsDuplicateKeys() {
        assertTrue(validator.validate(b("{\"a\":1,\"a\":2}")).orElseThrow().getMessage().contains("a"));
    }

    @Test
    void rejectsEmptyAndWhitespace() {
        assertTrue(validator.validate(b("")).isPresent());
        assertTrue(validator.validate(b("  \n ")).isPresent());
    }

    @Test
    void rejectsMalformedUtf8() {
        assertEquals("File is not valid UTF-8.",
                validator.validate(new byte[]{'"', (byte) 0xC3, '"'}).orElseThrow().getMessage());
    }

    @Test
    void stripBomRemovesLeadingBom() {
        assertArrayEquals(b("{}"), JsonValidator.stripBom(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{', '}'}));
    }

    @Test
    void stripBomKeepsOtherContent() {
        assertArrayEquals(b("{}"), JsonValidator.stripBom(b("{}")));
    }
}
