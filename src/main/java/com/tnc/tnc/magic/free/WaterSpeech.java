package com.tnc.tnc.magic.free;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Repairs Mandarin speech recognition variants for the water shapes and modifiers while preserving the surrounding speech. */
public final class WaterSpeech {
    private static final String RESOURCE_ROOT = "/data/tnc/free_magic/";
    private static final Pattern CLAUSE = Pattern.compile("[^\\p{P}\\r\\n]+");
    private static final Lexicon LEXICON = loadLexicon();

    private WaterSpeech() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        var clauses = CLAUSE.matcher(raw);
        StringBuilder normalized = new StringBuilder(raw.length());
        int copied = 0;
        while (clauses.find()) {
            normalized.append(raw, copied, clauses.start());
            normalizeClause(clauses.group(), normalized);
            copied = clauses.end();
        }
        return normalized.append(raw, copied, raw.length()).toString();
    }

    private static void normalizeClause(String clause, StringBuilder normalized) {
        // Some CTC paths put a word boundary in the middle of a shape name.
        // Keep the original offsets so whitespace outside a matched word survives.
        StringBuilder letters = new StringBuilder(clause.length());
        int[] originalOffsets = new int[clause.length()];
        for (int index = 0; index < clause.length(); index++) {
            char character = clause.charAt(index);
            if (Character.isWhitespace(character) || Character.isSpaceChar(character)) continue;
            originalOffsets[letters.length()] = index;
            letters.append(character);
        }

        String compact = letters.toString();
        Reading[] readings = new Reading[compact.length()];
        for (int index = 0; index < readings.length; index++) {
            readings[index] = LEXICON.readings.get(compact.charAt(index));
        }
        int copied = 0;
        for (int start = 0; start < compact.length();) {
            Spelling exact = exactMatch(compact, start);
            int exactLength = exact == null ? 0 : exact.text.length();
            Word phonetic = phoneticMatch(readings, start, exactLength);
            String canonical;
            int length;
            if (phonetic != null) {
                canonical = phonetic.canonical;
                length = phonetic.syllables.size();
            } else if (exact != null) {
                canonical = exact.canonical;
                length = exactLength;
            } else {
                start++;
                continue;
            }
            normalized.append(clause, copied, originalOffsets[start]).append(canonical);
            start += length;
            copied = originalOffsets[start - 1] + 1;
        }
        normalized.append(clause, copied, clause.length());
    }

    private static Spelling exactMatch(String clause, int start) {
        for (Spelling spelling : LEXICON.spellings) {
            if (clause.startsWith(spelling.text, start)) return spelling;
        }
        return null;
    }

    private static Word phoneticMatch(Reading[] readings, int start, int exactLength) {
        Word best = null;
        int bestDistance = 2;
        boolean ambiguous = false;
        for (Word word : LEXICON.words) {
            int length = word.syllables.size();
            if (length <= exactLength || start + length > readings.length) continue;
            if (best != null && length < best.syllables.size()) break;
            int distance = phoneticDistance(readings, start, word.syllables);
            if (distance < bestDistance) {
                best = word;
                bestDistance = distance;
                ambiguous = false;
            } else if (distance == bestDistance && best != null) {
                ambiguous = true;
            }
        }
        return ambiguous ? null : best;
    }

    private static int phoneticDistance(Reading[] readings, int start, List<Reading> expected) {
        int distance = 0;
        for (int offset = 0; offset < expected.size(); offset++) {
            Reading actual = readings[start + offset];
            Reading target = expected.get(offset);
            if (actual == null || !actual.dialect.equals(target.dialect)) return 2;
            if (!actual.standard.equals(target.standard) && ++distance > 1) return 2;
        }
        return distance;
    }

    private static String dialectKey(String reading) {
        if (reading.startsWith("zh") || reading.startsWith("ch") || reading.startsWith("sh")) {
            reading = reading.charAt(0) + reading.substring(2);
        }
        if (reading.endsWith("ang") || reading.endsWith("eng") || reading.endsWith("ing")) {
            reading = reading.substring(0, reading.length() - 1);
        }
        return reading;
    }

    private record Word(String canonical, List<Reading> syllables) {}
    private record Spelling(String text, String canonical) {}
    private record Reading(String standard, String dialect) {
        private Reading(String standard) { this(standard, dialectKey(standard)); }
    }
    private record Lexicon(List<Word> words, List<Spelling> spellings, Map<Character, Reading> readings) {}

    private static Lexicon loadLexicon() {
        List<Word> words = new ArrayList<>();
        List<Spelling> spellings = new ArrayList<>();
        Map<Character, Reading> readings = new HashMap<>();
        for (String[] row : readRows("water_speech_words.tsv")) {
            String canonical = row[0];
            List<Reading> syllables = Pattern.compile(" ").splitAsStream(row[1]).map(Reading::new).toList();
            words.add(new Word(canonical, syllables));
            spellings.add(new Spelling(canonical, canonical));
            if (row.length == 3) {
                for (String alias : row[2].split(",")) {
                    spellings.add(new Spelling(alias, canonical));
                }
            }
        }
        for (String[] row : readRows("water_speech_pinyin.tsv")) {
            readings.put(row[0].charAt(0), new Reading(row[1]));
        }
        words.sort(Comparator.comparingInt((Word word) -> word.syllables.size()).reversed());
        spellings.sort(Comparator.comparingInt((Spelling spelling) -> spelling.text.length()).reversed());
        return new Lexicon(List.copyOf(words), List.copyOf(spellings), Map.copyOf(readings));
    }

    private static List<String[]> readRows(String filename) {
        var source = WaterSpeech.class.getResourceAsStream(RESOURCE_ROOT + filename);
        if (source == null) throw new IllegalStateException("Missing water speech resource " + filename);
        try (var reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.split("\\t")).toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
