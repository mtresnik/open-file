package org.open.file.backup.store

import java.util.ServiceLoader

/**
 * Resolves the active [BackupDao] off the classpath at runtime.
 *
 * Mirrors [org.open.file.snapshot.store.SnapshotDaoProvider] — the concrete
 * DAO is picked up from `META-INF/services/org.open.file.backup.store.BackupDao`
 * on whichever backup module (`:apps:backup:sql`, …) is on the classpath.
 */
object BackupDaoProvider {

    fun getDao(): BackupDao {
        return requireNotNull(ServiceLoader.load(BackupDao::class.java).firstOrNull()) {
            "BackupDao was null! Is apps:backup:sql (or another impl) on the runtime classpath?"
        }
    }

}
