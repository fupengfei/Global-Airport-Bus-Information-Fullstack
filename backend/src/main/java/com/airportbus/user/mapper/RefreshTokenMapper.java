package com.airportbus.user.mapper;

import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

public interface RefreshTokenMapper {
    int insert(@Param("userId") long userId, @Param("hash") String hash,
               @Param("expiresAt") LocalDateTime expiresAt);
    Row findByHash(@Param("hash") String hash);
    int revokeByHash(@Param("hash") String hash);
    int revokeAllForUser(@Param("userId") long userId);

    record Row(Long id, Long userId, LocalDateTime expiresAt, boolean revoked) {}
}
