package org.open.file.snapshot.store.sql

import org.open.file.snapshot.Snapshots
import org.open.file.snapshot.models.SavedSnapshot
import org.open.file.snapshot.models.Snapshot
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun Snapshots.toModel(): Snapshot {
    return SavedSnapshot(
        id = this.id,
        rootPath = this.rootPath,
        createdAt = Instant.fromEpochMilliseconds(this.createdAt)
    )
}

fun List<Snapshots>.toModelList(): List<Snapshot> = this.map(Snapshots::toModel)

fun SavedSnapshot.fromModel(): Snapshots {
    return Snapshots(
        id = this.id,
        rootPath = this.rootPath,
        createdAt = this.createdAt.toEpochMilliseconds()
    )
}

fun List<SavedSnapshot>.fromModelList(): List<Snapshots> = this.map {
    it.fromModel()
}