package io.mosip.registration.util.transliteration;

import java.text.Normalizer;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.transliteration.spi.Transliteration;
import io.mosip.registration.config.AppConfig;

/**
 * Transliteration between English and Portuguese. Both languages use the Latin
 * script, so the text is copied as it is, except that accents are removed when
 * going from Portuguese to English (João -> Joao). Any other language pair is
 * delegated to the other available {@link Transliteration} implementation
 * (kernel ICU4J based one, if present in the lib folder).
 */
@Primary
@Component
public class LatinTransliterationImpl implements Transliteration<String> {

	private static final Logger LOGGER = AppConfig.getLogger(LatinTransliterationImpl.class);

	private static final String ENGLISH = "eng";

	private static final String PORTUGUESE = "por";

	@Autowired
	private ApplicationContext applicationContext;

	private Transliteration<String> delegate;

	@Override
	public String transliterate(String fromLanguage, String toLanguage, String text) {
		if (isLatinLanguage(fromLanguage) && isLatinLanguage(toLanguage)) {
			if (text == null || fromLanguage.equalsIgnoreCase(toLanguage) || !ENGLISH.equalsIgnoreCase(toLanguage)) {
				return text;
			}
			return stripAccents(text);
		}

		Transliteration<String> other = getDelegate();
		if (other == null) {
			throw new IllegalArgumentException(
					"Transliteration not supported from " + fromLanguage + " to " + toLanguage);
		}
		return other.transliterate(fromLanguage, toLanguage, text);
	}

	private boolean isLatinLanguage(String langCode) {
		return ENGLISH.equalsIgnoreCase(langCode) || PORTUGUESE.equalsIgnoreCase(langCode);
	}

	/** Removes accents and ordinal indicators: João -> Joao, nº -> no */
	public static String stripAccents(String text) {
		// ordinal indicators are not decomposed by NFD (nº -> no, 1ª -> 1a)
		return removeDiacritics(text).replace('º', 'o').replace('ª', 'a');
	}

	/** Removes only the accent marks: João -> Joao, nº stays nº */
	public static String removeDiacritics(String text) {
		String withoutMarks = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return Normalizer.normalize(withoutMarks, Normalizer.Form.NFC);
	}

	@SuppressWarnings("unchecked")
	private synchronized Transliteration<String> getDelegate() {
		if (delegate == null) {
			Optional<Transliteration> other = applicationContext.getBeansOfType(Transliteration.class).values()
					.stream().filter(bean -> bean != this).findFirst();
			if (other.isPresent()) {
				LOGGER.info("Delegating non Latin transliteration to {}", other.get().getClass().getName());
				delegate = other.get();
			}
		}
		return delegate;
	}
}
