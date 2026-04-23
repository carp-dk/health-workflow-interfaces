package health.workflows.server

import health.workflows.interfaces.api.ComponentIndex
import health.workflows.interfaces.api.ConsumptionInterface
import health.workflows.server.auth.KeyStore
import health.workflows.server.routes.componentRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ErrorResponse(val code: String, val message: String)

private val appJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Application.configureApp(
    service: ConsumptionInterface,
    index: ComponentIndex,
    keyStore: KeyStore,
) {
    install(ContentNegotiation) { json(appJson) }

    install(CallLogging)

    install(Authentication) {
        bearer("api-key") {
            authenticate { credential -> keyStore.resolve(credential.token) }
        }
    }

    install(StatusPages) {
        exception<ComponentNotFoundException> { call, ex ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", ex.message ?: "Not found"))
        }
        exception<ContentHashMismatchException> { call, ex ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("HASH_MISMATCH", ex.message ?: "Content hash mismatch"))
        }
        exception<NotImplementedError> { call, ex ->
            call.respond(HttpStatusCode.NotImplemented, ErrorResponse("NOT_IMPLEMENTED", ex.message ?: "Not implemented in this deployment"))
        }
        exception<Throwable> { call, ex ->
            call.application.log.error("Unhandled exception in ${call.request.local.uri}", ex)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "Internal server error"))
        }
    }

    routing {
        route("/api/v1") {
            authenticate("api-key") {
                componentRoutes(service, index)
            }
        }
    }
}
