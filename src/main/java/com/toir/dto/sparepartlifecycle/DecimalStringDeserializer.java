package com.toir.dto.sparepartlifecycle;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Service-life limit qiymatlari faqat JSON string ko'rinishida qabul qilinadi.
 * Qiymat {@code double} orqali o'tkazilmaydi: matn kanonik decimal shakliga
 * (eksponentasiz, guruh ajratgichsiz) tekshiriladi va {@link BigDecimal} ga
 * aylantiriladi. Ruxsat etilgan maksimal scale — 6. Noto'g'ri kiritish 400 bilan
 * rad etiladi.
 */
public class DecimalStringDeserializer extends JsonDeserializer<BigDecimal> {

    private static final Pattern CANONICAL = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");
    private static final int MAX_SCALE = 6;

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() != JsonToken.VALUE_STRING) {
            throw MismatchedInputException.from(p, BigDecimal.class,
                    "Decimal value must be sent as a JSON string, e.g. \"12.5\"");
        }
        String text = p.getText();
        if (text == null || !CANONICAL.matcher(text).matches()) {
            throw ctxt.weirdStringException(text, BigDecimal.class,
                    "must be a decimal string like \"12.5\" without exponent or grouping separators");
        }
        BigDecimal value = new BigDecimal(text);
        if (value.scale() > MAX_SCALE) {
            throw ctxt.weirdStringException(text, BigDecimal.class,
                    "must not have more than " + MAX_SCALE + " decimal places");
        }
        return value;
    }
}
