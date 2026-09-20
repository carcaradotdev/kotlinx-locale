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

package dev.carcara.kotlinx.locale

import android.content.Context
import androidx.startup.Initializer
import kotlin.concurrent.Volatile

internal object LocaleContext {

    @Volatile
    var applicationContext: Context? = null
}

/**
 * Keeps the application context so [Locale.current] can read the 12/24-hour
 * setting from it.
 *
 * Public because `androidx.startup` builds it by reflection from the `meta-data`
 * node this library adds to the manifest, not because an app has any reason to
 * name it.
 *
 * An app that would rather not have it removes that one `meta-data` node from
 * its own manifest, and [Locale.current] then reports no hour cycle:
 *
 * ```xml
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     android:exported="false"
 *     tools:node="merge">
 *     <meta-data
 *         android:name="dev.carcara.kotlinx.locale.LocaleContextInitializer"
 *         tools:node="remove" />
 * </provider>
 * ```
 *
 * The `tools:` prefix needs `xmlns:tools="http://schemas.android.com/tools"` on
 * the `<manifest>` root, which a manifest that has never used a `tools:`
 * attribute does not have.
 *
 * Do not put `tools:node="remove"` on the provider. Every library that uses
 * `androidx.startup` enters through that one provider, WorkManager, `emoji2`,
 * `profileinstaller` and `lifecycle-process` among them, so removing it stops
 * their initialisers too. The build stays green and the damage shows up at run
 * time as initialisation that never happened.
 */
public class LocaleContextInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        LocaleContext.applicationContext = context.applicationContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    public companion object
}
