// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version "8.2.2" apply false
    kotlin("android") version "1.9.22" apply false
}