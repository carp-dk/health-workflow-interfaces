# Registry Server

The `server` module is the reference implementation of a workflow registry (R0). It provides a RESTful API for the `ConsumptionInterface`, backed by the `ComponentIndex`.

## Features

- **Platform Neutrality**: Any platform client can submit its own `PlatformProfile` for compatibility checking.
- **REST API**: Implements the full seven-operation `ConsumptionInterface`.
- **Atomic Persistence**: Graph state and packages are persisted to disk using an atomic temp-file + replace strategy, ensuring reliability on Windows and Unix-like systems.
- **API Key Auth**: Simple bearer token authentication for all protected endpoints.
- **External Integration**: Provisioned for WorkflowHub (DOI minting and lineage tracking).

## Architecture

The server is built with [Ktor](https://ktor.io/) and uses the following components:

- **[`RegistryService`](src/main/kotlin/health/workflows/server/service/RegistryService.kt)**: The core logic implementing `ConsumptionInterface`.
- **[`InMemoryComponentIndex`](../lib/src/main/kotlin/health/workflows/interfaces/graph/InMemoryComponentIndex.kt)**: Graph-based indexing and search.
- **[`PackageStore`](src/main/kotlin/health/workflows/server/store/PackageStore.kt)**: File-backed storage for workflow artifacts.
- **[`KeyStore`](src/main/kotlin/health/workflows/server/auth/KeyStore.kt)**: API key management.

## Getting Started

### Prerequisites

- Java 21+
- Gradle (provided wrapper or system installation)

### Configuration

The server requires a `keys.yaml` file in the configuration directory to authenticate requests.

1. Copy the example config:
   ```bash
   cp ../config/keys.example.yaml data/config/keys.yaml
   ```
2. Generate an API key:
   ```bash
   ./gradlew :server:generateKey -PuserId=dev@example.com -Pname="Developer"
   ```

### Running the Server

Start the server on the default port (8080):

```bash
./gradlew :server:run
```

To specify custom data or config directories:

```bash
./gradlew :server:run -Ddata.dir=my-data -Dconfig.dir=my-config -Dserver.port=9000
```

## Documentation Links

- **Main README**: [Overview of the project](../README.md)
- **API Specification**: [`openapi.yaml`](../openapi.yaml)
- **Shared Library**: [`lib` module](../lib/)
- **Workflow Models**: [`workflow-models.md`](../docs/workflow-models.md)
- **Consumption Interface**: [`consumption-interface.md`](../docs/consumption-interface.md)
- **Component Index**: [`component-index.md`](../docs/component-index.md)
- **Compatibility Evaluator**: [`compatibility-evaluator.md`](../docs/compatibility-evaluator.md)

