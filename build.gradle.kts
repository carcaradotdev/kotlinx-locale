// Everything the root project does lives in the convention plugin, so this file
// stays a declaration of what the root is rather than a place logic accumulates.
// Shared configuration reaches the modules through the convention plugins they
// apply, never through allprojects or subprojects.
plugins {
    id("kotlinx-locale-verification")
}
