package com.toir.dto.sparepartlifecycle;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Service-life limit qiymatlari HTTP chegarasida aniq decimal string sifatida
 * uzatiladi. {@link BigDecimal} qiymatini binar floating-point orqali o'tkazmasdan,
 * {@code toPlainString()} bilan (eksponentasiz) yozadi.
 */
public class DecimalStringSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.toPlainString());
    }
}
