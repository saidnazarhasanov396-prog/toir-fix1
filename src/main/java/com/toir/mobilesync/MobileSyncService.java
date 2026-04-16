package com.toir.mobilesync;

import com.toir.common.security.AuthenticatedUser;
import com.toir.common.security.SecurityScope;
import com.toir.conditionreading.ConditionReadingService;
import com.toir.inspection.InspectionService;
import com.toir.inspection.dto.InspectionRoundResultRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MobileSyncService {

    private final ConditionReadingService conditionService;
    private final InspectionService inspectionService;
    private final SecurityScope securityScope;

    public MobileSyncService(ConditionReadingService conditionService,
                             InspectionService inspectionService,
                             SecurityScope securityScope) {
        this.conditionService = conditionService;
        this.inspectionService = inspectionService;
        this.securityScope = securityScope;
    }

    public MobileSyncResult sync(MobileSyncRequest request) {
        List<String> errors = new ArrayList<>();
        int readingsOk = 0;
        int resultsOk = 0;
        int roundsOk = 0;

        AuthenticatedUser u = securityScope.currentUser();
        UUID userId = u != null && u.id() != null ? UUID.fromString(u.id()) : null;

        if (request.readings() != null) {
            for (MobileSyncRequest.ReadingItem item : request.readings()) {
                try {
                    conditionService.record(item.equipmentId(), item.payload(), userId);
                    readingsOk++;
                } catch (Exception ex) {
                    errors.add("reading " + item.clientId() + ": " + ex.getMessage());
                }
            }
        }

        if (request.inspectionResults() != null) {
            for (MobileSyncRequest.InspectionResultItem item : request.inspectionResults()) {
                try {
                    inspectionService.recordResult(item.roundId(),
                            new InspectionRoundResultRequest(
                                    item.checkpointId(),
                                    item.status(),
                                    item.measuredValue(),
                                    item.measuredUnit(),
                                    item.comment(),
                                    null
                            ));
                    resultsOk++;
                } catch (Exception ex) {
                    errors.add("result " + item.clientId() + ": " + ex.getMessage());
                }
            }
        }

        if (request.roundCompletions() != null) {
            for (MobileSyncRequest.RoundCompletionItem item : request.roundCompletions()) {
                try {
                    inspectionService.completeRound(item.roundId(), item.notes());
                    roundsOk++;
                } catch (Exception ex) {
                    errors.add("round " + item.clientId() + ": " + ex.getMessage());
                }
            }
        }

        return new MobileSyncResult(readingsOk, resultsOk, roundsOk, errors);
    }
}
