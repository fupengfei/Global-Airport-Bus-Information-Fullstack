package com.airportbus.user.security;

public record JwtPrincipal(long userId, String role) {}
