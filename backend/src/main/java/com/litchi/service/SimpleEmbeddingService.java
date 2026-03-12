package com.litchi.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SimpleEmbeddingService {

    public static final int DIMENSION = 1024;

    public float[] embed(String text) {
        float[] vector = new float[DIMENSION];
        if (text == null || text.isBlank()) {
            return vector;
        }

        for (String token : tokenize(text)) {
            int index = Math.floorMod(token.hashCode(), DIMENSION);
            vector[index] += 1.0f;
        }

        normalize(vector);
        return vector;
    }

    private List<String> tokenize(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        List<String> tokens = new ArrayList<>();
        if (normalized.isEmpty()) {
            return tokens;
        }

        String compact = normalized.replace(" ", "");
        for (int i = 0; i < compact.length(); i++) {
            int end = Math.min(compact.length(), i + 2);
            tokens.add(compact.substring(i, end));
        }

        for (String word : normalized.split(" ")) {
            if (!word.isBlank()) {
                tokens.add(word);
            }
        }

        return tokens;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }

        if (sum == 0) {
            return;
        }

        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }
}
