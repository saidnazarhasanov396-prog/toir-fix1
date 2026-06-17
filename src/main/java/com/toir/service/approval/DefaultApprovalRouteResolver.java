package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.UserStatus;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DefaultApprovalRouteResolver implements ApprovalRouteResolver {

    private final ApprovalTemplateRepository templateRepository;
    private final UserRepository userRepository;

    @Override
    public List<CreateApprovalRequest.StepInput> resolveRoute(ApprovalRequest request) {
        if (request == null || request.getTargetType() == null) {
            return List.of();
        }
        return templateRepository
                .findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(request.getTargetType())
                .map(this::stepsFromTemplate)
                .orElse(List.of());
    }

    private List<CreateApprovalRequest.StepInput> stepsFromTemplate(ApprovalTemplate template) {
        ApprovalRoutePolicy policy = template.getRoutePolicy();
        if (policy == ApprovalRoutePolicy.USER_BASED && template.getApproverId() != null) {
            return List.of(new CreateApprovalRequest.StepInput(template.getApproverId(), null));
        }
        if (template.getApproverId() != null) {
            return List.of(new CreateApprovalRequest.StepInput(template.getApproverId(), template.getApproverRole()));
        }
        String routeRole = switch (policy) {
            case SYSTEM_ADMIN -> "SYSTEM_ADMIN";
            case DEPARTMENT_HEAD -> "DEPARTMENT_HEAD";
            case ROLE_BASED, USER_BASED -> template.getApproverRole();
        };
        if (!StringUtils.hasText(routeRole)) {
            return List.of();
        }
        return resolveRole(routeRole)
                .map(List::of)
                .orElse(List.of());
    }

    @Override
    public Optional<CreateApprovalRequest.StepInput> resolveRole(String approverRole) {
        if (!StringUtils.hasText(approverRole)) {
            return Optional.empty();
        }
        String routeRole = approverRole.trim();
        List<User> activeUsers = userRepository.findAllWithRolesAndIsDeletedFalse().stream()
                .filter(user -> user.getStatus() == null || user.getStatus() == UserStatus.ACTIVE)
                .toList();
        return activeUsers.stream()
                .filter(user -> hasRoleOrPermission(user, routeRole))
                .findFirst()
                .or(() -> activeUsers.stream()
                        .filter(user -> hasRoleOrPermission(user, "SYSTEM_ADMIN") || hasRoleOrPermission(user, "*"))
                        .findFirst())
                .map(user -> new CreateApprovalRequest.StepInput(user.getId(), routeRole));
    }

    private boolean hasRoleOrPermission(User user, String code) {
        return roleStream(user)
                .flatMap(role -> Stream.concat(
                        Stream.of(role.getCode()),
                        role.getPermissions() == null ? Stream.empty() : role.getPermissions().stream()
                ))
                .filter(Objects::nonNull)
                .anyMatch(code::equals);
    }

    private Stream<Role> roleStream(User user) {
        if (user == null) {
            return Stream.empty();
        }
        Stream<Role> primary = user.getPrimaryRole() == null ? Stream.empty() : Stream.of(user.getPrimaryRole());
        Stream<Role> additional = user.getRoles() == null ? Stream.empty() : user.getRoles().stream();
        return Stream.concat(primary, additional).filter(Objects::nonNull);
    }
}
