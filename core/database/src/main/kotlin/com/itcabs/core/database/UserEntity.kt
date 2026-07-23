package com.itcabs.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.itcabs.domain.model.User
import com.itcabs.domain.model.UserRole
import com.itcabs.domain.model.UserStatus

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long,
    val phone: String,
    val role: String,
    val name: String,
    val status: String,
    val isAdmin: Boolean = false,
)

fun UserEntity.toDomain() = User(
    id = id,
    phone = phone,
    role = UserRole.valueOf(role),
    name = name,
    status = UserStatus.valueOf(status),
    isAdmin = isAdmin
)

fun User.toEntity() = UserEntity(
    id = id,
    phone = phone,
    role = role.name,
    name = name,
    status = status.name,
    isAdmin = isAdmin
)
