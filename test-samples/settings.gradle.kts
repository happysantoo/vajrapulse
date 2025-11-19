rootProject.name = "vajrapulse-test-samples"

include("simple-success")
include("mixed-results")
include("all-patterns")

project(":simple-success").projectDir = file("simple-success")
project(":mixed-results").projectDir = file("mixed-results")
project(":all-patterns").projectDir = file("all-patterns")

