package io.mosip.registration.util.transliteration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;

/**
 * Word for word translation of the configured text fields (by default the address lines)
 * between English and Portuguese,
 * based on the translations file {@value #TRANSLATIONS_FILE}. Words which are not in
 * the file (street and place names) are copied, with accents removed when
 * translating to English.
 */
@Component
public class PortugueseTranslator {

	private static final Logger LOGGER = AppConfig.getLogger(PortugueseTranslator.class);

	/**
	 * Provided by artifactory-ref-impl (regclient-i18n-bundle) like labels.properties. configure.sh merges
	 * resources.zip into the root of the registration-client jar, so the file is looked up at the classpath root.
	 */
	private static final String TRANSLATIONS_FILE = "portuguese-translations.txt";

	private static final String ENGLISH = "eng";

	private static final String PORTUGUESE = "por";

	/** A word starts with a letter and may contain letters, accents, apostrophes, hyphens and slashes (r/c) */
	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}][\\p{L}\\p{M}'’/\\-]*");

	/** Kept in lower case when every word of a translation is capitalized (Centro de Saúde) */
	private static final Set<String> CONNECTING_WORDS = new HashSet<>(Arrays.asList("de", "da", "do", "das", "dos",
			"e", "em", "a", "ao", "à", "of", "the", "and", "in", "to", "for", "on", "at"));

	@Value("${mosip.registration.transliteration.fields:addressLine1,addressLine2,addressLine3}")
	private String translationFields;

	private Set<String> fieldIds = new HashSet<>();

	private final Map<String, String> englishToPortuguese = new HashMap<>();

	private final Map<String, String> portugueseToEnglish = new HashMap<>();

	private final Set<String> protectedPhrases = new HashSet<>();

	private int maxPhraseWords = 1;

	@PostConstruct
	public void init() {
		fieldIds = Arrays.stream(translationFields.split(",")).map(String::trim).filter(id -> !id.isEmpty())
				.collect(Collectors.toSet());
		loadTranslations();
	}

	/**
	 * @return true when the field is configured for translation and the
	 *         language pair is English / Portuguese
	 */
	public boolean canTranslate(String fieldId, String fromLanguage, String toLanguage) {
		return fieldIds.contains(fieldId) && isSupportedPair(fromLanguage, toLanguage);
	}

	public String translate(String fromLanguage, String toLanguage, String text) {
		if (text == null || text.isEmpty() || !isSupportedPair(fromLanguage, toLanguage)) {
			return text;
		}
		boolean toEnglish = ENGLISH.equalsIgnoreCase(toLanguage);
		Map<String, String> translations = toEnglish ? portugueseToEnglish : englishToPortuguese;

		List<String> words = new ArrayList<>();
		List<String> separators = new ArrayList<>(); // separators.get(i) is the text before words.get(i)
		Matcher matcher = WORD.matcher(text);
		int end = 0;
		while (matcher.find()) {
			separators.add(text.substring(end, matcher.start()));
			words.add(matcher.group());
			end = matcher.end();
		}
		String trailing = text.substring(end);

		StringBuilder result = new StringBuilder();
		int index = 0;
		boolean skipNextSpace = false;
		while (index < words.size()) {
			String separator = separators.get(index);
			if (skipNextSpace && separator.trim().isEmpty() && result.length() > 0) {
				separator = "";
			}
			result.append(separator);
			skipNextSpace = false;

			int matchedWords = 0;
			for (int length = Math.min(maxPhraseWords, words.size() - index); length > 0; length--) {
				if (!isPhrase(separators, index, length)) {
					continue;
				}
				String key = normalize(String.join(" ", words.subList(index, index + length)));
				if (protectedPhrases.contains(key)) {
					appendUntranslated(result, words, separators, index, length, toEnglish);
					matchedWords = length;
					break;
				}
				if (translations.containsKey(key)) {
					String translation = translations.get(key);
					if (translation.isEmpty()) {
						skipNextSpace = true;
					} else {
						result.append(applyCase(String.join(" ", words.subList(index, index + length)), translation));
					}
					matchedWords = length;
					break;
				}
			}
			if (matchedWords == 0) {
				appendUntranslated(result, words, separators, index, 1, toEnglish);
				matchedWords = 1;
			}
			index += matchedWords;
		}
		result.append(trailing);
		return result.toString();
	}

	private boolean isSupportedPair(String fromLanguage, String toLanguage) {
		return (ENGLISH.equalsIgnoreCase(fromLanguage) && PORTUGUESE.equalsIgnoreCase(toLanguage))
				|| (PORTUGUESE.equalsIgnoreCase(fromLanguage) && ENGLISH.equalsIgnoreCase(toLanguage));
	}

	/** Words of a phrase must be separated only by white space ("near, the" is not a phrase) */
	private boolean isPhrase(List<String> separators, int index, int length) {
		for (int i = index + 1; i < index + length; i++) {
			if (!separators.get(i).trim().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private void appendUntranslated(StringBuilder result, List<String> words, List<String> separators, int index,
			int length, boolean toEnglish) {
		for (int i = index; i < index + length; i++) {
			if (i > index) {
				result.append(separators.get(i));
			}
			String word = words.get(i);
			String ordinalSuffix = toEnglish && (word.equals("º") || word.equals("ª"))
					? englishOrdinalSuffix(result)
					: null;
			if (ordinalSuffix != null) {
				result.append(ordinalSuffix);
			} else {
				result.append(toEnglish ? LatinTransliterationImpl.stripAccents(word) : word);
			}
		}
	}

	/** Keeps the case style of the typed text: UPPER, Each Word Capitalized, First capitalized or as in the file */
	private String applyCase(String source, String translation) {
		boolean hasLowerCase = !source.equals(source.toUpperCase(Locale.ROOT));
		if (!hasLowerCase && source.length() > 1) {
			return translation.toUpperCase(Locale.ROOT);
		}
		String[] sourceWords = source.split("\\s+");
		boolean allWordsCapitalized = sourceWords.length > 1
				&& Arrays.stream(sourceWords).allMatch(word -> Character.isUpperCase(word.charAt(0)));
		if (allWordsCapitalized) {
			String[] translationWords = translation.split(" ");
			for (int i = 0; i < translationWords.length; i++) {
				if (i == 0 || !CONNECTING_WORDS.contains(translationWords[i].toLowerCase(Locale.ROOT))) {
					translationWords[i] = capitalize(translationWords[i]);
				}
			}
			return String.join(" ", translationWords);
		}
		if (Character.isUpperCase(source.charAt(0))) {
			return capitalize(translation);
		}
		return translation;
	}

	private String capitalize(String word) {
		return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}

	/** English ordinal suffix for a number typed with º or ª: 1º -> 1st, 2ª -> 2nd, 11º -> 11th */
	private String englishOrdinalSuffix(StringBuilder result) {
		int end = result.length();
		int start = end;
		while (start > 0 && Character.isDigit(result.charAt(start - 1))) {
			start--;
		}
		if (start == end || end - start > 9) {
			return null;
		}
		int number = Integer.parseInt(result.substring(start, end));
		if (number % 100 >= 11 && number % 100 <= 13) {
			return "th";
		}
		switch (number % 10) {
		case 1:
			return "st";
		case 2:
			return "nd";
		case 3:
			return "rd";
		default:
			return "th";
		}
	}

	private String normalize(String phrase) {
		return LatinTransliterationImpl.removeDiacritics(phrase).toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
	}

	private void loadTranslations() {
		try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(TRANSLATIONS_FILE)) {
			if (inputStream == null) {
				LOGGER.warn("Portuguese translations file {} not found in classpath (expected from reg-client resources.zip), "
						+ "fields will only be copied", TRANSLATIONS_FILE);
				return;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				addEntry(line.trim(), lineNumber);
			}
			LOGGER.info("Loaded Portuguese translations : {} English and {} Portuguese entries, {} protected phrases",
					englishToPortuguese.size(), portugueseToEnglish.size(), protectedPhrases.size());
		} catch (Exception exception) {
			LOGGER.error("Failed to load Portuguese translations file {}", TRANSLATIONS_FILE, exception);
		}
	}

	private void addEntry(String line, int lineNumber) {
		if (line.isEmpty() || line.startsWith("#")) {
			return;
		}
		if (line.startsWith("!")) {
			String phrase = normalize(line.substring(1));
			if (!phrase.isEmpty()) {
				protectedPhrases.add(phrase);
				updateMaxPhraseWords(phrase);
			}
			return;
		}
		int position = indexOfOperator(line);
		if (position < 0) {
			LOGGER.warn("Ignoring invalid Portuguese translations line {} : {}", lineNumber, line);
			return;
		}
		char operator = line.charAt(position);
		String english = line.substring(0, position).trim();
		String portuguese = line.substring(position + 1).trim();
		if (english.isEmpty()) {
			LOGGER.warn("Ignoring invalid Portuguese translations line {} : {}", lineNumber, line);
			return;
		}
		if (operator == '=' || operator == '>') {
			englishToPortuguese.putIfAbsent(normalize(english), portuguese);
			updateMaxPhraseWords(english);
		}
		if ((operator == '=' || operator == '<') && !portuguese.isEmpty()) {
			portugueseToEnglish.putIfAbsent(normalize(portuguese), english);
			updateMaxPhraseWords(portuguese);
		}
	}

	private int indexOfOperator(String line) {
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '=' || c == '>' || c == '<') {
				return i;
			}
		}
		return -1;
	}

	private void updateMaxPhraseWords(String phrase) {
		maxPhraseWords = Math.max(maxPhraseWords, phrase.trim().split("\\s+").length);
	}
}
