// Top-level build file for the StreamCaster project.
// Plugin declarations here make them available to subprojects (like :app)
// without applying them at the root level.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
