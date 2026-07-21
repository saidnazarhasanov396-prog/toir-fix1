package com.toir.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads the complete ERP-facing presentation payload owned by TOIR. */
@Component
public class ErpPresentationDatasetProvider {

    private final ObjectMapper objectMapper;
    private volatile JsonNode datasets;

    public ErpPresentationDatasetProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode get(String datasetType) {
        JsonNode root = datasets;
        if (root == null) {
            synchronized (this) {
                root = datasets;
                if (root == null) {
                    root = readDatasets();
                    datasets = root;
                }
            }
        }
        JsonNode dataset = root.path(datasetType);
        if (dataset.isMissingNode() || dataset.isNull()) {
            throw new IllegalStateException("TOIR ERP presentation dataset is missing: " + datasetType);
        }
        return dataset;
    }

    private JsonNode readDatasets() {
        try (var input = new ClassPathResource("erp-presentation/datasets.json").getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("TOIR ERP presentation datasets cannot be loaded", exception);
        }
    }
}
