package org.open.file.template.org.open.file.template.api

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.open.file.template.TemplateService
import org.open.file.template.models.Template

fun Route.templateRoutes(service: TemplateService) {
    route("/templates") {
        get { call.respond(service.getAll()) }
        get("/{id}") { call.respond(requireNotNull(service[call.parameters["id"]!!])) }
        put("/{id}") { call.respond(requireNotNull(service.set(call.parameters["id"]!!, call.receive<Template>()))) }
        post { call.respond(requireNotNull(service.create(call.receive()))) }
        delete("/id") { call.respond(requireNotNull(service.delete(call.parameters["id"]!!))) }
    }
}