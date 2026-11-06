package com.g4vrk.react.inference.json;

import com.g4vrk.react.player.model.rotation.Rotation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InferenceJsonCodecTest {

    @Test
    void writesInferenceRequestWithRotationFrames() {
        final Rotation rotation = new Rotation(
                1.0F,
                2.0F,
                3.0F,
                4.0F,
                5.0F,
                6.0F,
                7.0F,
                8.0F
        );

        final Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Steve");
        request.put("frames", new Rotation[]{rotation});

        assertEquals(
                "{\"name\":\"Steve\",\"frames\":[{\"y\":1.0,\"x\":2.0,\"deltaY\":3.0,\"deltaX\":4.0,"
                        + "\"jerkY\":5.0,\"jerkX\":6.0,\"gcdErrorY\":7.0,\"gcdErrorX\":8.0}]}",
                InferenceJsonCodec.writeObject(request)
        );
    }

    @Test
    void escapesRequestStrings() {
        final String json = InferenceJsonCodec.writeObject(
                Map.of("name", "Steve \"The Brave\"\\\n")
        );

        assertTrue(json.contains("Steve \\\"The Brave\\\"\\\\\\n"));
    }

    @Test
    void readsNestedObjectsArraysAndLiterals() {
        final Map<String, Object> response = InferenceJsonCodec.readObject(
                "{\"server\":\"Lobby\",\"models\":[{\"id\":\"flower/baseline\",\"enabled\":true},null],"
                        + "\"cheat_probability\":0.875,\"detail\":\"a\\\"b\"}");

        assertEquals("Lobby", response.get("server"));
        assertEquals(0.875D, response.get("cheat_probability"));
        assertEquals("a\"b", response.get("detail"));
        assertEquals(List.of(Map.of("id", "flower/baseline", "enabled", true)), List.of(((List<?>) response.get("models")).get(0)));
        assertNull(((List<?>) response.get("models")).get(1));
    }

    @Test
    void rejectsMalformedJsonAndTrailingContent() {
        assertThrows(IllegalArgumentException.class, () -> InferenceJsonCodec.readObject("{\"probability\":}"));
        assertThrows(IllegalArgumentException.class, () -> InferenceJsonCodec.readObject("[1,2,3]"));
        assertThrows(IllegalArgumentException.class, () -> InferenceJsonCodec.readObject("{\"probability\":0.5} trailing"));
    }

    @Test
    void rejectsNonFiniteNumbers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> InferenceJsonCodec.writeObject(Map.of("value", Double.NaN))
        );
    }
}
