package org.open.file.snapshot.store.sql

import org.open.file.snapshot.Snapshots
import org.open.file.snapshot.models.Snapshot

fun Snapshots.toModel(): Snapshot {
    return Snapshot(
        id = this.id,
        created = this.created,
        updated = this.updated,
        deleted = this.deleted,
        properties = this.properties,
        target = this.target
    )
}

fun List<Snapshots>.toModel(): List<Snapshot> = this.map(Snapshots::toModel)

fun Snapshot.fromModel(): Snapshots {
    return Snapshots(
        id = this.id,
        created = this.created,
        updated = this.created,
        deleted = this.deleted,
        properties = this.properties,
        target = this.target
    )
}

fun List<Snapshot>.fromModel(): List<Snapshots> = this.map(Snapshot::fromModel)