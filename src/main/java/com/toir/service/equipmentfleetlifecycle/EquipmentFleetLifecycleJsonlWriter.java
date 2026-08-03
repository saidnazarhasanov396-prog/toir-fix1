package com.toir.service.equipmentfleetlifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class EquipmentFleetLifecycleJsonlWriter {

    private static final byte LF = (byte) '\n';

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
        streamService.stream(prepared, line -> {
            byte[] bytes = writer.writeValueAsBytes(line);
            output.write(bytes);
            output.write(LF);
        });
        output.flush();
    }
}
