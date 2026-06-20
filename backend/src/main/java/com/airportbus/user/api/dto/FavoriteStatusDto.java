package com.airportbus.user.api.dto;

/** 收藏 / 取消端点的返回体:favorited=true 已收藏,false 已取消。 */
public record FavoriteStatusDto(boolean favorited) {}
