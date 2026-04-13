package com.noom.interview.fullstack.sleep.repository

import com.noom.interview.fullstack.sleep.model.User

/**
 * Repository for querying [User] records.
 */
interface UserRepository {

    /**
     * Persists a new user.
     *
     * @param user the user to save (the [User.id] field is ignored; the database generates the ID)
     * @return the saved [User] with its generated ID
     */
    fun saveUser(user: User): User

    /**
     * Finds a user by their unique identifier.
     *
     * @param id the user's ID
     * @return the [User] if found, or `null` if no user exists with the given ID
     */
    fun findUserById(id: Long): User?
}
