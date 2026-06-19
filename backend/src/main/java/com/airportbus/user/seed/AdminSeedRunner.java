package com.airportbus.user.seed;

import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 启动时幂等建一个 SUPER_ADMIN 并把账号密码打印到控制台(D4),为 #7 admin 铺路。 */
@Component
public class AdminSeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);
    private final UserMapper users;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String adminUser;
    private final String adminPass;

    public AdminSeedRunner(UserMapper users, PasswordEncoder encoder,
                           @Value("${airportbus.seed.enabled:true}") boolean enabled,
                           @Value("${airportbus.seed.admin-username:admin}") String adminUser,
                           @Value("${airportbus.seed.admin-password:admin12345}") String adminPass) {
        this.users = users; this.encoder = encoder;
        this.enabled = enabled; this.adminUser = adminUser; this.adminPass = adminPass;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (users.findFirstByRole("SUPER_ADMIN") != null) return;
        AppUser a = new AppUser();
        a.username = adminUser; a.email = adminUser + "@airportbus.local";
        a.passwordHash = encoder.encode(adminPass);
        a.locale = "zh-CN"; a.role = "SUPER_ADMIN"; a.emailVerified = true;
        users.insertUser(a);
        log.info("\n==== SEED ADMIN ====\n用户名: {}\n密码: {}\n(可用 airportbus.seed.admin-* 覆盖)\n====================",
                adminUser, adminPass);
    }
}
