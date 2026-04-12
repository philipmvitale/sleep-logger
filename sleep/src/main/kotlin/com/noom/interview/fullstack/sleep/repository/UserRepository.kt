package com.noom.interview.fullstack.sleep.repository

import com.noom.interview.fullstack.sleep.model.User

interface UserRepository {
    fun findUserById(id: Long): User?
}
