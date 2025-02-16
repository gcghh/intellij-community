// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.platform

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.utils.DescriptionAware

@Suppress("DEPRECATION_ERROR")
@Deprecated(
    message = "This interface is deprecated and will be removed soon, use API from 'org.jetbrains.kotlin.platform.*' packages instead",
    replaceWith = ReplaceWith("TargetPlatform", "org.jetbrains.kotlin.platform.TargetPlatform"),
    level = DeprecationLevel.ERROR
)
abstract class IdePlatform<Kind : IdePlatformKind, out Arguments : CommonCompilerArguments> : DescriptionAware {
    abstract val kind: Kind
    abstract val version: TargetPlatformVersion

    abstract fun createArguments(init: Arguments.() -> Unit = {}): Arguments

    override val description
        get() = kind.name + " " + version.description

    override fun toString() = description
}
