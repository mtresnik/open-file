package org.open.file.snapshot.store

import java.util.*

object SnapshotDaoProvider {

    fun getDao(): SnapshotDao {
        return requireNotNull(ServiceLoader.load(SnapshotDao::class.java).firstOrNull()) {
            "SnapshotDao was null!"
        }
    }

}