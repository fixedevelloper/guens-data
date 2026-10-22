package com.guens.fieldcollect.security;

import com.guens.fieldcollect.user.Role;

public record AuthUser(Long id, String username, Role role, String deviceId) {}
