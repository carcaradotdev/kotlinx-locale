/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Everything the root project does lives in the convention plugin, so this file
// stays a declaration of what the root is rather than a place logic accumulates.
// Shared configuration reaches the modules through the convention plugins they
// apply, never through allprojects or subprojects.
plugins {
    id("kotlinx-locale-verification")
}
