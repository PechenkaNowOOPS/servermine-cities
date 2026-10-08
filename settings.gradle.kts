rootProject.name = "servermine-cities"

include(":economy:economy-api")
listOf(
    "api", "core", "storage", "creation", "territory", "protection", "members", "roles",
    "treasury", "progression", "upgrades", "market", "diplomacy", "gui", "admin", "plugin"
).forEach { include(":cities:cities-$it") }
