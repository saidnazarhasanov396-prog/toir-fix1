package com.toir.config;

import com.toir.entity.users.Role;
import com.toir.repository.users.RoleRepository;
import com.toir.entity.users.User;
import com.toir.repository.users.UserRepository;
import com.toir.enums.UserStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Order(10)
@EnableConfigurationProperties(BootstrapProperties.class)
public class DataBootstrap implements CommandLineRunner {

    private static final String ADMIN_ROLE_CODE = "SYSTEM_ADMIN";

    /** {ru, en, uz} translations for seeded role names. */
    private static final String[][] BASE_ROLES = {
            {"TECHNICAL_DIRECTOR",        "Технический директор",       "Technical Director",          "Texnik direktor"},
            {"CHIEF_MECHANIC",            "Главный механик",            "Chief Mechanic",              "Bosh mexanik"},
            {"CHIEF_POWER_ENGINEER",      "Главный энергетик",          "Chief Power Engineer",        "Bosh energetik"},
            {"CHIEF_INSTRUMENT_ENGINEER", "Главный приборист",          "Chief Instrument Engineer",   "Bosh asbobchi"},
            {"WORKSHOP_HEAD",             "Начальник цеха",             "Workshop Head",               "Sex boshlig'i"},
            {"SECTION_HEAD",              "Начальник участка",          "Section Head",                "Uchastka boshlig'i"},
            {"FOREMAN",                   "Мастер",                     "Foreman",                     "Usta"},
            {"RELIABILITY_ENGINEER",      "Инженер по надёжности",      "Reliability Engineer",        "Ishonchlilik muhandisi"},
            {"PPR_ENGINEER",              "Инженер по ППР",             "PPR Engineer",                "PPR muhandisi"},
            {"STOREKEEPER",               "Кладовщик",                  "Storekeeper",                 "Omborchi"},
            {"SUPPLY_SPECIALIST",         "Сотрудник снабжения",        "Supply Specialist",           "Ta'minotchi"},
            {"ECONOMIST",                 "Экономист",                  "Economist",                   "Iqtisodchi"},
            {"CONTRACTOR",                "Подрядчик",                  "Contractor",                  "Pudratchi"},
            {"VIEWER",                    "Наблюдатель",                "Viewer",                      "Kuzatuvchi"}
    };

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties bootstrapProperties;

    public DataBootstrap(RoleRepository roleRepository, UserRepository userRepository,
                         PasswordEncoder passwordEncoder, BootstrapProperties bootstrapProperties) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapProperties = bootstrapProperties;
    }

    @Override
    public void run(String... args) {
        Role adminRole = roleRepository.findByCodeAndIsDeletedFalse(ADMIN_ROLE_CODE).orElseGet(() -> {
            Role r1 = new Role();
            r1.setCode(ADMIN_ROLE_CODE);
            r1.setName("Системный администратор");
            r1.setNameEn("System Administrator");
            r1.setNameUz("Tizim administratori");
            r1.setDescription("Полный доступ ко всем модулям");
            r1.setSystem(true);
            r1.setPermissions(List.of("*"));
            return roleRepository.save(r1);
        });

        for (String[] row : BASE_ROLES) {
            String code = row[0];
            if (!roleRepository.existsByCodeAndIsDeletedFalse(code)) {
                Role r = new Role();
                r.setCode(code);
                r.setName(row[1]);
                r.setNameEn(row[2]);
                r.setNameUz(row[3]);
                r.setSystem(true);
                r.setPermissions(List.of("read"));
                roleRepository.save(r);
            }
        }

        if (!bootstrapProperties.isCreateDefaultAdmin()) {
            return;
        }

        userRepository.findByUsernameAndIsDeletedFalse(bootstrapProperties.getAdminUsername()).orElseGet(() -> {
            User u = new User();
            u.setUsername(bootstrapProperties.getAdminUsername());
            u.setEmail(bootstrapProperties.getAdminEmail());
            u.setFullName(bootstrapProperties.getAdminFullName());
            u.setStatus(UserStatus.ACTIVE);
            u.setPasswordHash(passwordEncoder.encode(bootstrapProperties.getAdminPassword()));
            u.setPrimaryRole(adminRole);
            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            u.setRoles(roles);
            return userRepository.save(u);
        });
    }
}
