package com.itcabs.data

import com.itcabs.core.network.dto.TokensDto
import com.itcabs.core.network.dto.UserDto
import com.itcabs.domain.model.AuthTokens
import com.itcabs.domain.model.Session
import com.itcabs.domain.model.User
import com.itcabs.domain.model.UserRole
import com.itcabs.domain.model.UserStatus

fun UserDto.toDomain(): User = User(
    id = id,
    phone = phone,
    role = UserRole.valueOf(role),
    name = name,
    status = UserStatus.valueOf(status),
)

fun TokensDto.toSession(): Session = Session(
    tokens = AuthTokens(accessToken, refreshToken),
    userId = userId,
    role = UserRole.valueOf(role),
)
