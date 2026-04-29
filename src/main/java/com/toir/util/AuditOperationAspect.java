package com.toir.util;

import com.toir.enums.AuditAction;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditOperationAspect {

    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService serializationService;

    @Around("""
            execution(public * com.toir.service..*Service.*(..))
            && !within(com.toir.service.AuditLogService)
            && !within(com.toir.service.AuthService)
            """)
    public Object auditServiceOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        AuditAction action = actionFor(method.getName());
        if (action == null) {
            return joinPoint.proceed();
        }

        Object oldState = oldState(joinPoint, action);
        String oldSnapshot = oldState != null ? serializationService.toJson(oldState) : null;
        Object result = joinPoint.proceed();
        String newSnapshot = action == AuditAction.DELETE || result == null ? null : serializationService.toJson(result);
        Object resourceId = resourceId(result, joinPoint.getArgs());
        String entityType = entityType(joinPoint.getTarget(), oldState, result);
        String module = entityType.toUpperCase();

        auditBuilderService.log(
                entityType,
                resourceId != null ? String.valueOf(resourceId) : null,
                action,
                module,
                description(entityType, action, method.getName()),
                oldSnapshot,
                newSnapshot
        );

        return result;
    }

    private AuditAction actionFor(String methodName) {
        if (methodName.startsWith("create")) {
            return AuditAction.CREATE;
        }
        if (methodName.startsWith("update")) {
            return AuditAction.UPDATE;
        }
        if (methodName.startsWith("delete")) {
            return AuditAction.DELETE;
        }
        return null;
    }

    private Object oldState(ProceedingJoinPoint joinPoint, AuditAction action) {
        if (action == AuditAction.CREATE) {
            return null;
        }
        Object id = firstIdArg(joinPoint.getArgs());
        if (id == null) {
            return null;
        }
        return findExistingEntity(joinPoint.getTarget(), id);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object findExistingEntity(Object target, Object id) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!CrudRepository.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                ReflectionUtils.makeAccessible(field);
                Object candidate = ReflectionUtils.getField(field, target);
                if (!(candidate instanceof CrudRepository repository)) {
                    continue;
                }
                try {
                    Optional<?> found = repository.findById(id);
                    if (found.isPresent()) {
                        return found.get();
                    }
                } catch (RuntimeException ignored) {
                    // Repository ID type did not match this method argument.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private Object firstIdArg(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UUID || arg instanceof String || arg instanceof Number) {
                return arg;
            }
        }
        return null;
    }

    private Object resourceId(Object result, Object[] args) {
        Object id = extractId(result);
        return id != null ? id : firstIdArg(args);
    }

    private Object extractId(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Method recordAccessor = value.getClass().getMethod("id");
            return recordAccessor.invoke(value);
        } catch (Exception ignored) {
            // fall through to JavaBean getter
        }
        try {
            Method getter = value.getClass().getMethod("getId");
            return getter.invoke(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String entityType(Object target, Object oldState, Object result) {
        if (oldState != null) {
            return normalizeType(oldState.getClass().getSimpleName());
        }
        if (result != null) {
            return normalizeType(result.getClass().getSimpleName());
        }
        String serviceName = target.getClass().getSimpleName();
        int proxyMarker = serviceName.indexOf("$$");
        if (proxyMarker >= 0) {
            serviceName = serviceName.substring(0, proxyMarker);
        }
        return normalizeType(serviceName.replaceFirst("Service$", ""));
    }

    private String normalizeType(String raw) {
        String cleaned = raw
                .replaceFirst("Dto$", "")
                .replaceFirst("Response$", "")
                .replaceFirst("Request$", "");
        return cleaned.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private String description(String entityType, AuditAction action, String methodName) {
        return "%s %s via %s".formatted(entityType, action.name().toLowerCase(), methodName);
    }
}
