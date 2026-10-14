package com.swingmate.auth.social;

/** 공급자가 확인해 준 사용자 정보. email은 공급자가 주지 않으면 null. */
public record SocialUserInfo(String providerUserId, String email) {
}
