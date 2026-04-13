package com.noom.interview.fullstack.sleep.repository

import com.noom.interview.fullstack.sleep.model.User

/**
 * Repository for querying [User] records.
 */
interface UserRepository {

    /**
     * Finds a user by their unique identifier.
     *
     * @param id the user's ID
     * @return the [User] if found, or `null` if no user exists with the given ID
     */
    fun findUserById(id: Long): User?
}
