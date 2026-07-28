package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.UserStatus;
import com.toir.exception.RestException;
import com.toir.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ParallelApprovalAssigneeResolver {

    private final UserRepository userRepository;

    public List<CreateApprovalRequest.StepInput> resolve(ApprovalTemplate template) {
        if (template == null || template.getFlowType() != ApprovalFlowType.PARALLEL_ALL) {
            throw invalidTemplateSteps();
        }

        List<ApprovalTemplateStep> steps = orderedActiveSteps(template);
        if (steps.isEmpty()) {
            throw invalidTemplateSteps();
        }

        List<User> activeUsers = activeUsersByStableId();
        LinkedHashSet<UUID> resolved = new LinkedHashSet<>();
        for (ApprovalTemplateStep step : steps) {
            if (step.getApproverId() != null) {
                requireActiveExplicitUser(step.getApproverId(), activeUsers);
                resolved.add(step.getApproverId());
                continue;
            }

            String roleCode = normalizeRole(step.getApproverRole());
            if (roleCode == null) {
                throw invalidTemplateSteps();
            }
            List<User> members = members(activeUsers, roleCode);
            if (members.isEmpty()) {
                throw RestException.conflict("PARALLEL_APPROVER_ROLE_HAS_NO_ACTIVE_USERS");
            }
            members.forEach(user -> resolved.add(user.getId()));
        }

        if (resolved.isEmpty()) {
            throw invalidTemplateSteps();
        }
        return resolved.stream()
                .map(id -> new CreateApprovalRequest.StepInput(id, null))
                .toList();
    }

    private List<ApprovalTemplateStep> orderedActiveSteps(ApprovalTemplate template) {
        if (template.getSteps() == null) {
            return List.of();
        }
        List<ApprovalTemplateStep> steps = new ArrayList<>();
        for (ApprovalTemplateStep step : template.getSteps()) {
            if (step == null) {
                throw invalidTemplateSteps();
            }
            if (!step.isDeleted()) {
                boolean hasExplicitApprover = step.getApproverId() != null;
                boolean hasRole = step.getApproverRole() != null;
                if (hasExplicitApprover == hasRole) {
                    throw invalidTemplateSteps();
                }
                steps.add(step);
            }
        }
        steps.sort(Comparator.comparingInt(ApprovalTemplateStep::getStepOrder));
        return List.copyOf(steps);
    }

    private List<User> activeUsersByStableId() {
        List<User> users = userRepository.findAllWithRolesAndIsDeletedFalse();
        if (users == null) {
            return List.of();
        }
        return users.stream()
                .filter(this::isActiveUser)
                .sorted(Comparator.comparing(user -> user.getId().toString()))
                .toList();
    }

    private void requireActiveExplicitUser(UUID userId, List<User> activeUsers) {
        if (activeUsers.stream().noneMatch(user -> user.getId().equals(userId))) {
            throw invalidTemplateSteps();
        }
    }

    private List<User> members(List<User> activeUsers, String roleCode) {
        return activeUsers.stream()
                .filter(user -> hasRole(user, roleCode))
                .toList();
    }

    private boolean isActiveUser(User user) {
        return user != null
                && user.getId() != null
                && !user.isDeleted()
                && user.getStatus() == UserStatus.ACTIVE;
    }

    private boolean hasRole(User user, String roleCode) {
        return roleStream(user)
                .map(Role::getCode)
                .map(this::normalizeRole)
                .filter(Objects::nonNull)
                .anyMatch(roleCode::equals);
    }

    private Stream<Role> roleStream(User user) {
        Stream<Role> primary = user.getPrimaryRole() == null
                ? Stream.empty()
                : Stream.of(user.getPrimaryRole());
        Stream<Role> additional = user.getRoles() == null
                ? Stream.empty()
                : user.getRoles().stream();
        return Stream.concat(primary, additional).filter(Objects::nonNull);
    }

    private String normalizeRole(String roleCode) {
        if (roleCode == null) {
            return null;
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.startsWith("ROLE_") ? normalized.substring("ROLE_".length()) : normalized;
    }

    private RestException invalidTemplateSteps() {
        return RestException.badRequest("APPROVAL_TEMPLATE_STEPS_INVALID");
    }
}
