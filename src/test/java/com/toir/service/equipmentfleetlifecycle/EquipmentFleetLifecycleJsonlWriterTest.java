package com.toir.service.equipmentfleetlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.dto.equipmentfleetlifecycle.EquipmentFleetLifecycleV1;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleBatchLoader.Batch;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleStreamService.PreparedFleetStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

class EquipmentFleetLifecycleJsonlWriterTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-03T08:15:30Z");
    private static final String CORRELATION_ID = "fleet-request-42";

    @Test
    void writesEachFleetLineAsAnIndependentlyParseableLfTerminatedJsonRecord() throws Exception {
        EquipmentFleetLifecycleStreamService streamService = mock(EquipmentFleetLifecycleStreamService.class);
        PreparedFleetStream prepared = prepared();
        EquipmentFleetLifecycleV1.Line first = line("10000000-0000-0000-0000-000000000001", "EQ-1");
        EquipmentFleetLifecycleV1.Line second = line("20000000-0000-0000-0000-000000000002", "EQ-2");
        doAnswer(invocation -> {
            EquipmentFleetLifecycleStreamService.ItemSink sink = invocation.getArgument(1);
            sink.accept(first);
            sink.accept(second);
            return 2L;
        }).when(streamService).stream(eq(prepared), any());
        EquipmentFleetLifecycleJsonlWriter writer = new EquipmentFleetLifecycleJsonlWriter(mapper(), streamService);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writer.write(output, prepared);

        String text = output.toString(StandardCharsets.UTF_8);
        String[] records = text.split("\\n", -1);
        assertThat(records).hasSize(3);
        assertThat(records[0]).isNotBlank().startsWith("{").endsWith("}");
        assertThat(records[1]).isNotBlank().startsWith("{").endsWith("}");
        assertThat(records[2]).isEmpty();
        assertThat(mapper().readTree(records[0]).path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(mapper().readTree(records[1]).path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(mapper().readTree(records[0]).path("generatedAt").asText()).isEqualTo(GENERATED_AT.toString());
        assertThat(mapper().readTree(records[1]).path("generatedAt").asText()).isEqualTo(GENERATED_AT.toString());
        assertThat(mapper().readTree(records[0]).path("consistency").asText())
                .isEqualTo("FIXED_AS_OF_READ_COMMITTED_BATCHES");
        assertThat(mapper().readTree(records[1]).path("consistency").asText())
                .isEqualTo("FIXED_AS_OF_READ_COMMITTED_BATCHES");
        assertThat(text).doesNotStartWith("[").doesNotContain("equipmentCount", "manifest", "summary", "\\n\\n");
    }

    @Test
    void writesNoBytesForAnEmptyFleet() throws Exception {
        EquipmentFleetLifecycleStreamService streamService = mock(EquipmentFleetLifecycleStreamService.class);
        PreparedFleetStream prepared = prepared();
        when(streamService.stream(eq(prepared), any())).thenReturn(0L);
        EquipmentFleetLifecycleJsonlWriter writer = new EquipmentFleetLifecycleJsonlWriter(mapper(), streamService);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writer.write(output, prepared);

        assertThat(output.toByteArray()).isEmpty();
    }

    @Test
    void writesOnePhysicalJsonLineWhenTheSharedMapperHasIndentOutputEnabled() throws Exception {
        EquipmentFleetLifecycleStreamService streamService = mock(EquipmentFleetLifecycleStreamService.class);
        PreparedFleetStream prepared = prepared();
        EquipmentFleetLifecycleV1.Line line = line("10000000-0000-0000-0000-000000000001", "EQ-1");
        doAnswer(invocation -> {
            EquipmentFleetLifecycleStreamService.ItemSink sink = invocation.getArgument(1);
            sink.accept(line);
            return 1L;
        }).when(streamService).stream(eq(prepared), any());
        ObjectMapper indentingMapper = mapper().enable(SerializationFeature.INDENT_OUTPUT);
        EquipmentFleetLifecycleJsonlWriter writer = new EquipmentFleetLifecycleJsonlWriter(indentingMapper, streamService);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writer.write(output, prepared);

        String text = output.toString(StandardCharsets.UTF_8);
        String[] records = text.split("\\n", -1);
        assertThat(records).hasSize(2);
        assertThat(records[0]).startsWith("{").endsWith("}");
        assertThat(records[1]).isEmpty();
        assertThat(mapper().readTree(records[0]).path("equipment").path("code").asText()).isEqualTo("EQ-1");
    }

    @Test
    void serializationFailureLogsCorrelationAndLeavesOnlyPriorCompleteRecords() throws Exception {
        EquipmentFleetLifecycleStreamService streamService = mock(EquipmentFleetLifecycleStreamService.class);
        PreparedFleetStream prepared = prepared();
        EquipmentFleetLifecycleV1.Line first = line("10000000-0000-0000-0000-000000000001", "EQ-1");
        EquipmentFleetLifecycleV1.Line second = line("20000000-0000-0000-0000-000000000002", "EQ-2");
        doAnswer(invocation -> {
            EquipmentFleetLifecycleStreamService.ItemSink sink = invocation.getArgument(1);
            sink.accept(first);
            sink.accept(second);
            return 2L;
        }).when(streamService).stream(eq(prepared), any());
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        ObjectWriter failingWriter = mock(ObjectWriter.class);
        byte[] firstRecord = mapper().writeValueAsBytes(first);
        when(failingMapper.writer()).thenReturn(failingWriter);
        when(failingWriter.without(SerializationFeature.INDENT_OUTPUT)).thenReturn(failingWriter);
        when(failingWriter.writeValueAsBytes(first)).thenReturn(firstRecord);
        when(failingWriter.writeValueAsBytes(second))
                .thenThrow(new JsonProcessingException("secret serialization detail") { });
        EquipmentFleetLifecycleJsonlWriter writer = new EquipmentFleetLifecycleJsonlWriter(failingMapper, streamService);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (LogCapture logs = captureLogs()) {
            assertThatThrownBy(() -> writer.write(output, prepared))
                    .isInstanceOf(JsonProcessingException.class)
                    .hasMessage("secret serialization detail");

            assertSingleAbortLog(logs, "SERIALIZATION");
        }

        assertOnlyFirstRecord(output);
    }

    @Test
    void databaseFailureAfterACompleteRecordLogsOnceAndPropagatesWithoutAnErrorRecord() throws Exception {
        EquipmentFleetLifecycleStreamService streamService = mock(EquipmentFleetLifecycleStreamService.class);
        PreparedFleetStream prepared = prepared();
        EquipmentFleetLifecycleV1.Line first = line("10000000-0000-0000-0000-000000000001", "EQ-1");
        doAnswer(invocation -> {
            EquipmentFleetLifecycleStreamService.ItemSink sink = invocation.getArgument(1);
            sink.accept(first);
            throw new DataAccessResourceFailureException("secret database detail");
        }).when(streamService).stream(eq(prepared), any());
        EquipmentFleetLifecycleJsonlWriter writer = new EquipmentFleetLifecycleJsonlWriter(mapper(), streamService);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (LogCapture logs = captureLogs()) {
            assertThatThrownBy(() -> writer.write(output, prepared))
                    .isInstanceOf(DataAccessResourceFailureException.class)
                    .hasMessage("secret database detail");

            assertSingleAbortLog(logs, "DATABASE");
        }

        assertOnlyFirstRecord(output);
    }

    @Test
    void clientIoFailureAfterACompleteRecordLogsOnceAndPropagatesWithoutAnErrorRecord() throws Exception {
        EquipmentFleetLifecycleStreamService streamService = mock(EquipmentFleetLifecycleStreamService.class);
        PreparedFleetStream prepared = prepared();
        EquipmentFleetLifecycleV1.Line first = line("10000000-0000-0000-0000-000000000001", "EQ-1");
        EquipmentFleetLifecycleV1.Line second = line("20000000-0000-0000-0000-000000000002", "EQ-2");
        doAnswer(invocation -> {
            EquipmentFleetLifecycleStreamService.ItemSink sink = invocation.getArgument(1);
            sink.accept(first);
            sink.accept(second);
            return 2L;
        }).when(streamService).stream(eq(prepared), any());
        EquipmentFleetLifecycleJsonlWriter writer = new EquipmentFleetLifecycleJsonlWriter(mapper(), streamService);
        FailAfterFirstLineOutputStream output = new FailAfterFirstLineOutputStream();

        try (LogCapture logs = captureLogs()) {
            assertThatThrownBy(() -> writer.write(output, prepared))
                    .isInstanceOf(IOException.class)
                    .hasMessage("secret client detail");

            assertSingleAbortLog(logs, "CLIENT_IO");
        }

        assertOnlyFirstRecord(output.delegate());
    }

    private static void assertOnlyFirstRecord(ByteArrayOutputStream output) throws Exception {
        String text = output.toString(StandardCharsets.UTF_8);
        String[] records = text.split("\\n", -1);
        assertThat(records).hasSize(2);
        assertThat(records[0]).isNotBlank();
        assertThat(records[1]).isEmpty();
        assertThat(mapper().readTree(records[0]).path("equipment").path("code").asText()).isEqualTo("EQ-1");
        assertThat(text).doesNotContain("EQ-2", "error", "summary", "secret");
    }

    private static void assertSingleAbortLog(LogCapture logs, String failureType) {
        assertThat(logs.events()).hasSize(1);
        ILoggingEvent event = logs.events().get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).isEqualTo(
                "Equipment fleet lifecycle JSONL stream aborted correlationId=" + CORRELATION_ID
                        + " emittedLines=1 failureType=" + failureType);
        assertThat(event.getFormattedMessage()).doesNotContain("secret");
    }

    private static LogCapture captureLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(EquipmentFleetLifecycleJsonlWriter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new LogCapture(logger, appender);
    }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger;
        private final ListAppender<ILoggingEvent> appender;

        private LogCapture(Logger logger, ListAppender<ILoggingEvent> appender) {
            this.logger = logger;
            this.appender = appender;
        }

        private List<ILoggingEvent> events() {
            return appender.list;
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static final class FailAfterFirstLineOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private int completeLines;

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
            if (value == (byte) 0x0A) {
                completeLines++;
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (completeLines > 0) {
                throw new IOException("secret client detail");
            }
            delegate.write(bytes, offset, length);
        }

        private ByteArrayOutputStream delegate() {
            return delegate;
        }
    }

    private static PreparedFleetStream prepared() {
        return new PreparedFleetStream(
                null, false, GENERATED_AT, CORRELATION_ID, new Batch(List.of(), null, false));
    }

    private static EquipmentFleetLifecycleV1.Line line(String id, String code) {
        return new EquipmentFleetLifecycleV1.Line(
                EquipmentFleetLifecycleV1.SCHEMA_VERSION,
                GENERATED_AT,
                EquipmentFleetLifecycleV1.CONSISTENCY,
                new EquipmentFleetLifecycleV1.EquipmentCore(
                        UUID.fromString(id), code, "Pump", null, null, null, null, null, null, null,
                        null, null, null, "ACTIVE", null, null, null, null, null, null, null, null, null, null),
                List.of(),
                null,
                List.of(),
                new EquipmentFleetLifecycleV1.DataQuality(true, List.of()));
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.INDENT_OUTPUT);
    }
}
