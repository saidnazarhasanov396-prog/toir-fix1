package com.toir.service.equipmentfleetlifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class EquipmentFleetLifecycleJsonlWriter {

    private static final byte LF = (byte) 0x0A;
    private static final Logger log =
            LoggerFactory.getLogger(EquipmentFleetLifecycleJsonlWriter.class);

    private final ObjectWriter writer;
    private final EquipmentFleetLifecycleStreamService streamService;

    public EquipmentFleetLifecycleJsonlWriter(
            ObjectMapper objectMapper,
            EquipmentFleetLifecycleStreamService streamService) {
        this.writer = Objects.requireNonNull(objectMapper, "objectMapper")
                .writer()
                .without(SerializationFeature.INDENT_OUTPUT);
        this.streamService = Objects.requireNonNull(streamService, "streamService");
    }

    public void write(
            OutputStream output,
            EquipmentFleetLifecycleStreamService.PreparedFleetStream prepared) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(prepared, "prepared");
        long[] emittedLines = {0L};
        try {
            streamService.stream(prepared, line -> {
                byte[] bytes = writer.writeValueAsBytes(line);
                output.write(bytes);
                output.write(LF);
                emittedLines[0]++;
            });
            output.flush();
        } catch (IOException | RuntimeException failure) {
            if (emittedLines[0] > 0L) {
                log.error(
                        "Equipment fleet lifecycle JSONL stream aborted correlationId={} emittedLines={} failureType={}",
                        prepared.correlationId(),
                        emittedLines[0],
                        failureType(failure));
            }
            throw failure;
        }
    }

    private static String failureType(Exception failure) {
        if (failure instanceof JsonProcessingException) {
            return "SERIALIZATION";
        }
        if (failure instanceof DataAccessException) {
            return "DATABASE";
        }
        if (failure instanceof IOException) {
            return "CLIENT_IO";
        }
        return "INTERNAL";
    }
}
