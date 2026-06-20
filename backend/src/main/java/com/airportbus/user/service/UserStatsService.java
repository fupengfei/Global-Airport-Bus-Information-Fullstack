package com.airportbus.user.service;

import com.airportbus.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 用户统计(只读)。供 admin 概览编排。 */
@Service
public class UserStatsService {

    private final UserMapper users;

    public UserStatsService(UserMapper users) { this.users = users; }

    public long totalUsers() { return users.countUsers(); }

    public long newUsersInLastDays(int days) {
        return users.countUsersSince(LocalDate.now().minusDays(clamp(days) - 1L));
    }

    /** 近 days 天每天注册数,空天补 0,日期连续升序,date 为 ISO 串。 */
    public List<DailyRegistration> registrations(int days) {
        int n = clamp(days);
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(n - 1L);
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (UserMapper.DayCount r : users.countRegistrationsByDay(since)) byDay.put(r.day(), r.count());
        List<DailyRegistration> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LocalDate d = since.plusDays(i);
            out.add(new DailyRegistration(d.toString(), byDay.getOrDefault(d, 0L)));
        }
        return out;
    }

    private static int clamp(int days) { return days < 1 ? 7 : Math.min(days, 90); }

    public record DailyRegistration(String date, long count) {}
}
