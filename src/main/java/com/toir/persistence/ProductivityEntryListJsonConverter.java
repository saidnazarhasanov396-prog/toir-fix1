package com.toir.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.dto.equipmentpassport.ProductivityEntryDto;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

@Converter
public class ProductivityEntryListJsonConverter
        implements AttributeConverter<List<ProductivityEntryDto>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String convertToDatabaseColumn(List<ProductivityEntryDto> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public List<ProductivityEntryDto> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Collections.emptyList();
        try {
            return mapper.readValue(dbData, new TypeReference<List<ProductivityEntryDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
