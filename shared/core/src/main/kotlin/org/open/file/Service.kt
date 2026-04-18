package org.open.file

interface Service<T> {

    fun getAll(): List<T>

    fun create(entity: T): T?

    operator fun set(id: String, entity: T): T?

    fun delete(id: String): Boolean
}