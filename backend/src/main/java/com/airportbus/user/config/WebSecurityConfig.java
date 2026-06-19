package com.airportbus.user.config;

import com.airportbus.user.security.JwtAuthFilter;
import com.airportbus.user.security.JwtService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSecurityConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwt) {
        return new JwtAuthFilter(jwt);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterReg(JwtAuthFilter f) {
        FilterRegistrationBean<JwtAuthFilter> b = new FilterRegistrationBean<>(f);
        b.addUrlPatterns("/*");
        b.setOrder(1);
        return b;
    }
}
