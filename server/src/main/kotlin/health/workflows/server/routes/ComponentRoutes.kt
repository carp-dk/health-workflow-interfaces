package health.workflows.server.routes

import health.workflows.interfaces.api.ComponentIndex
import health.workflows.interfaces.api.ConsumptionInterface
import health.workflows.interfaces.api.PlatformProfile
import health.workflows.interfaces.api.SearchQuery
import health.workflows.interfaces.graph.RelationType
import health.workflows.interfaces.model.WorkflowArtifactPackage
import health.workflows.server.ErrorResponse
import health.workflows.server.auth.UserIdentity
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route


/**
 * Registers all components routes under the caller's route context.
 *
 * Assumes the caller has wrapped this in `authenticate("api-key")` — a resolved
 * [UserIdentity] is guaranteed to be present on every call.
 *
 * All workflow packages are treated as openly accessible to any valid keyholder.
 */
fun Route.componentRoutes(service: ConsumptionInterface, index: ComponentIndex) {

    // -- Publish ---------------------------------------------------------------
    post("/components") {
        val identity = call.principal<UserIdentity>()!!
        val pkg = call.receive<WorkflowArtifactPackage>()
        // Inject the authenticated user's identity into authors at publish time.
        val pkgWithAuthor = pkg.copy(
            metadata = pkg.metadata.copy(
                authors = ((pkg.metadata.authors ?: emptyList()) + identity.userId).distinct(),
            ),
        )
        call.respond(service.publish(pkgWithAuthor))
    }

    // -- Search ----------------------------------------------------------------
    post("/components/search") {
        val query = call.receive<SearchQuery>()
        call.respond(service.search(query))
    }

    // -- Per-component routes --------------------------------------------------
    route("/components/{id}/{version}") {

        // GET /components/{id}/{version}
        get {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            call.respond(service.getComponent(id, version))
        }

        // GET /components/{id}/{version}/doi
        get("/doi") {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            val doi = service.getDOI(id, version)
            call.respond(mapOf("doi" to doi))
        }

        // GET /components/{id}/{version}/dependencies
        get("/dependencies") {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            call.respond(service.resolveDependencies(id, version))
        }

        // POST /components/{id}/{version}/compatibility
        post("/compatibility") {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            val profile = call.receive<PlatformProfile>()
            call.respond(service.checkCompatibility(id, version, profile))
        }

        // GET /components/{id}/{version}/lineage
        get("/lineage") {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            call.respond(service.getLineage(id, version))
        }

        // GET /components/{id}/{version}/related?relation=CONTAINS
        get("/related") {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            val relationParam = call.request.queryParameters["relation"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("MISSING_PARAM", "Query parameter 'relation' is required"),
                )
            val relation = runCatching { RelationType.valueOf(relationParam) }.getOrElse {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_PARAM", "Unknown relation type: $relationParam"),
                )
            }
            call.respond(index.getRelated(id, version, relation))
        }
    }
}
