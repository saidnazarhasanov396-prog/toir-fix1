package com.toir.dto.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.regex.Pattern;

public class MoneyDecimalStringDeserializer extends JsonDeserializer<BigDecimal> {

    private static final Pattern CANONICAL_DECIMAL =
            Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d{1,4})?");

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw JsonMappingException.from(parser, "Money value must be a canonical JSON decimal string");
        }
        String value = parser.getText();
        if (!CANONICAL_DECIMAL.matcher(value).matches()) {
            throw JsonMappingException.from(parser,
                    "Money value must be a canonical decimal string with at most four fractional digits");
        }
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.scale() > 4) {
            throw JsonMappingException.from(parser, "Money value scale must not exceed four");
        }
        return decimal;
    }

    @Override
    public BigDecimal getNullValue(DeserializationContext context) throws JsonMappingException {
        throw JsonMappingException.from(context.getParser(), "Money value must not be null");
    }

    @Override
    public BigDecimal getAbsentValue(DeserializationContext context) {
        return BigDecimal.ZERO;
    }
}
