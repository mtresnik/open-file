package org.open.file.snapshot.store

import java.util.ServiceLoader

object NodeDaoProvider {

    fun getDao(): NodeDao {
        return requireNotNull(ServiceLoader.load(NodeDao::class.java).firstOrNull()) {
            "NodeDao was null!"
        }
    }

}