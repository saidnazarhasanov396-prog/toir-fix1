package com.toir.config;

import com.toir.entity.Role;
import com.toir.entity.User;
import com.toir.enums.UserStatus;
import com.toir.exception.RestException;
import com.toir.repository.UserRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.JwtService;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";
    private final UserRepository userRepository;
    private final JwtService jwtService;
    @Value("${app.openapi.dev-url:http://localhost:8080}")
    private String devUrl;

    @Value("${app.openapi.prod-url:https://api-toir.tenzorsoft.uz}")
    private String prodUrl;

    @Bean
    public OpenAPI toirOpenAPI() {
        Server devServer = new Server();
        devServer.setUrl(devUrl);
        devServer.setDescription("Locale");

        Server prodServer = new Server();
        prodServer.setUrl(prodUrl);
        prodServer.setDescription("Production");
        String jwtToken = getAdminToken();

        return new OpenAPI()
                .info(new Info()
                        .title("TOIR Backend API")
                        .description("### Admin Token:\n `" + jwtToken + "`")
                        .version("1.0.0"))
                .servers(List.of(devServer, prodServer))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .name(SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    private String getAdminToken() {
        Optional<User> admin = userRepository.findByUsername("admin");
        if (admin.isPresent()) {
            User user = admin.get();
            Hibernate.initialize(user.getRoles());
            Hibernate.initialize(user.getPrimaryRole());

            Set<String> permissions = new LinkedHashSet<>();
            Set<Role> roles = user.getRoles();
            Set<String> authorityCodes = new LinkedHashSet<>(roles.stream().map(Role::getCode).toList());
            for (Role role : roles) {
                if (role.getPermissions() != null) permissions.addAll(role.getPermissions());
            }
            if (user.getPrimaryRole() != null && user.getPrimaryRole().getPermissions() != null) {
                permissions.addAll(user.getPrimaryRole().getPermissions());
            }

            String primaryRoleCode = user.getPrimaryRole() != null ? user.getPrimaryRole().getCode() : null;
            if (primaryRoleCode != null) authorityCodes.add(primaryRoleCode);
            AuthenticatedUser principal = new AuthenticatedUser(
                    user.getId().toString(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getDepartmentId() != null ? user.getDepartmentId().toString() : null,
                    primaryRoleCode,
                    List.copyOf(permissions)
            );

            Map<String, Object> extra = new HashMap<>();
            extra.put("email", user.getEmail());
            extra.put("fullName", user.getFullName());
            extra.put("departmentId", principal.departmentId());
            extra.put("primaryRoleCode", primaryRoleCode);
            extra.put("permissions", principal.permissions());

            return jwtService.generateToken(principal.id(), principal.username(), List.copyOf(authorityCodes), extra);
        }
        return "Token op kelolmadim";
    }
}
