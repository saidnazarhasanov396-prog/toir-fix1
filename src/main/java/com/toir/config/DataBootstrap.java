package com.toir.config;
import com.toir.entity.Contractor;

import com.toir.entity.Role;
import com.toir.repository.RoleRepository;
import com.toir.entity.User;
import com.toir.repository.UserRepository;
import com.toir.entity.UserStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Order(10)
public class DataBootstrap implements CommandLineRunner {

    private static final String ADMIN_ROLE_CODE = "SYSTEM_ADMIN";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "P@ssw0rd123";

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

    public DataBootstrap(RoleRepository roleRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Role adminRole = roleRepository.findByCode(ADMIN_ROLE_CODE).orElseGet(() -> {
            Role r = new Role();
            r.setCode(ADMIN_ROLE_CODE);
            r.setName("Системный администратор");
            r.setNameEn("System Administrator");
            r.setNameUz("Tizim administratori");
            r.setDescription("Полный доступ ко всем модулям");
            r.setSystem(true);
            r.setPermissions(List.of("*"));
            return roleRepository.save(r);
        });

        for (String[] row : BASE_ROLES) {
            String code = row[0];
            if (!roleRepository.existsByCode(code)) {
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

        User admin = userRepository.findByUsername(ADMIN_USERNAME).orElseGet(() -> {
            User u = new User();
            u.setUsername(ADMIN_USERNAME);
            u.setEmail("admin@toir.local");
            u.setFullName("System Administrator");
            u.setStatus(UserStatus.ACTIVE);
            return u;
        });
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setPrimaryRole(adminRole);
        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        admin.setRoles(roles);
        userRepository.save(admin);
    }
}
