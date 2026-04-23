package health.workflows.server

class ComponentNotFoundException(id: String, version: String) :
    Exception("Component not found: $id@$version")

class ContentHashMismatchException(id: String, version: String, expected: String, actual: String) :
    Exception("Content hash mismatch for $id@$version — expected $expected, actual $actual")
