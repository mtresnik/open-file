package org.open.file.snapshot.models

import java.io.File
import java.util.Date
import java.util.UUID

data class Snapshot(val id: UUID,
                    var created: Date,
                    var updated: Date,
                    var deleted: Boolean,
                    var properties: MutableMap<String, Any> = mutableMapOf(),
                    var target: File) {}