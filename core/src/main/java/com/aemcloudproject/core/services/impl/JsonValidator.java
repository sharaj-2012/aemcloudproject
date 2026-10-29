package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.services.SaveResult;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Strictly validates JSON file content: UTF-8, a single JSON value, no duplicate keys.
 */
final class JsonValidator {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /**
     * @param content raw bytes
     * @return the bytes without a leading UTF-8 byte order mark
     */
    static byte[] stripBom(byte[] content) {
        if (content.length >= UTF8_BOM.length && Arrays.equals(Arrays.copyOf(content, UTF8_BOM.length), UTF8_BOM)) {
            return Arrays.copyOfRange(content, UTF8_BOM.length, content.length);
        }
        return content;
    }

    /**
     * @param content BOM-free UTF-8 bytes
     * @return empty when valid, otherwise an invalid JSON result with the parser position
     */
    Optional<SaveResult> validate(byte[] content) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            return Optional.of(SaveResult.invalidJson("File is not valid UTF-8.", null, null));
        }
        try {
            JsonNode node = mapper.readTree(text);
            if (node == null || node.isMissingNode()) {
                return Optional.of(SaveResult.invalidJson("Empty document.", null, null));
            }
            return Optional.empty();
        } catch (JsonProcessingException e) {
            JsonLocation location = e.getLocation();
            return Optional.of(SaveResult.invalidJson(e.getOriginalMessage(),
                    location == null ? null : location.getLineNr(),
                    location == null ? null : location.getColumnNr()));
        }
    }
}
