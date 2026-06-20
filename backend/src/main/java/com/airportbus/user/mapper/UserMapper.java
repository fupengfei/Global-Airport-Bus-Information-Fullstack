package com.airportbus.user.mapper;

import com.airportbus.user.model.AppUser;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {
    int insertUser(AppUser u);
    AppUser findById(@Param("id") long id);
    AppUser findByUsername(@Param("username") String username);
    AppUser findByEmail(@Param("email") String email);
    AppUser findByAccount(@Param("account") String account); // username 或 email
    boolean existsByUsername(@Param("username") String username);
    boolean existsByEmail(@Param("email") String email);
    int updatePassword(@Param("id") long id, @Param("hash") String hash);
    int updateLocale(@Param("id") long id, @Param("locale") String locale);
    AppUser findFirstByRole(@Param("role") String role);

    long countUsers();

    long countUsersSince(@Param("since") java.time.LocalDate since);

    java.util.List<DayCount> countRegistrationsByDay(@Param("since") java.time.LocalDate since);

    /** 某天的注册数(day 为 DATE(created_at))。 */
    record DayCount(java.time.LocalDate day, long count) {}
}
