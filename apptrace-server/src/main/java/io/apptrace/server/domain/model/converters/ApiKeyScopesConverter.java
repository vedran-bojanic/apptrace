package io.apptrace.server.domain.model.converters;

import io.apptrace.server.domain.enums.ApiKeyScope;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class ApiKeyScopesConverter implements AttributeConverter<Set<ApiKeyScope>, String> {

    @Override
    public String convertToDatabaseColumn(Set<ApiKeyScope> scopes) {
        if (scopes == null || scopes.isEmpty()) return "";
        return scopes.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    @Override
    public Set<ApiKeyScope> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return EnumSet.noneOf(ApiKeyScope.class);
        return Arrays.stream(dbData.split(","))
                .map(String::trim)
                .map(ApiKeyScope::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApiKeyScope.class)));
    }
}
