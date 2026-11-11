package com.adags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Springdoc can emit response maps in different orders across JVM runs.
// Keep the committed spec stable for CI's exact diff check.
final class OpenApiYamlNormalizer {
    private OpenApiYamlNormalizer() {}

    static String sortResponseCodes(String yaml) {
        List<String> lines = new ArrayList<>(List.of(yaml.split("\n", -1)));
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).equals("      responses:")) continue;

            int start = i + 1;
            int end = start;
            while (end < lines.size() && (lines.get(end).isBlank() || lines.get(end).startsWith("        "))) {
                end++;
            }

            List<List<String>> blocks = new ArrayList<>();
            for (int j = start; j < end; j++) {
                String line = lines.get(j);
                if (line.matches("        (?:\"[0-9]{3}\"|default):")) {
                    blocks.add(new ArrayList<>());
                } else if (blocks.isEmpty()) {
                    break;
                }
                blocks.getLast().add(line);
            }
            if (blocks.isEmpty()) continue;

            blocks.sort(Comparator.comparing(block -> block.getFirst()));
            int position = start;
            for (List<String> block : blocks) {
                for (String line : block) lines.set(position++, line);
            }
            i = end - 1;
        }
        return String.join("\n", lines);
    }
}
