/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.modules

import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.idea.framework.KotlinLibraryKind
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.resolve.TargetPlatform

object NativeLibraryKind : PersistentLibraryKind<DummyLibraryProperties>("kotlin.native"), KotlinLibraryKind {
    override val compilerPlatform: TargetPlatform
        get() = NativeIdePlatformKind.compilerPlatform

    override fun createDefaultProperties() = DummyLibraryProperties.INSTANCE!!
}
