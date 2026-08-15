package com.idp.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.StringJoiner;

/**
 * Encodes an embedding as a comma-separated float list for storage in a portable
 * TEXT column.
 *
 * <p>This is the single place the on-disk embedding format is known. Moving the
 * store to a real {@code vector(N)} column once the corpus outgrows an exact scan
 * means replacing this converter — the retrieval code never sees the encoding.
 */
@Converter
public class FloatVectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null || attribute.length == 0) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (float value : attribute) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String[] parts = dbData.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }
}
