rootProject.name = "PassButlerAndroid"

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.squareup.sqldelight") {
                useModule("com.squareup.sqldelight:gradle-plugin:${requested.version}")
            }
        }
    }
}

include(":PassButler")
include(":PassButlerCommon")
