package org.open.file.snapshot.api

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.open.file.snapshot.SnapshotService

fun Route.snapshotRoutes(service: SnapshotService) {
    route("/snapshots") {
        get { call.respond(service.getAll()) }
        get("/{id}") { call.respond(requireNotNull(service.getById(call.parameters["id"]!!))) }
        post { call.respond(requireNotNull(service.create(call.receive()))) }
    }
}