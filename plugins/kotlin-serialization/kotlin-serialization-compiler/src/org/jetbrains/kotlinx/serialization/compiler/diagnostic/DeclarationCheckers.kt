/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.serialization.compiler.diagnostic

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.reportFromPlugin
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.util.slicedMap.Slices
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import org.jetbrains.kotlinx.serialization.compiler.backend.common.AbstractSerialGenerator
import org.jetbrains.kotlinx.serialization.compiler.backend.common.findTypeSerializerOrContext
import org.jetbrains.kotlinx.serialization.compiler.resolve.*

internal val SERIALIZABLE_PROPERTIES: WritableSlice<ClassDescriptor, SerializableProperties> = Slices.createSimpleSlice()
internal val SERIALIZER_FOR_PROPERTY: WritableSlice<PropertyDescriptor, ClassDescriptor> = Slices.createSimpleSlice()

class SerializationPluginDeclarationChecker : DeclarationChecker {
    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (descriptor !is ClassDescriptor) return

        checkCanBeSerializedInternally(descriptor, context.trace)
        val props = buildSerializableProperties(descriptor, context.trace) ?: return
        analyzePropertiesSerializers(context.trace, descriptor, props.serializableProperties)
    }

    private fun checkCanBeSerializedInternally(descriptor: ClassDescriptor, trace: BindingTrace) {
        if (!descriptor.hasSerializableAnnotationWithoutArgs) return

        if (!descriptor.isInternalSerializable && !descriptor.hasCompanionObjectAsSerializer) {
            trace.reportOnSerializableAnnotation(descriptor, SerializationErrors.SERIALIZABLE_ANNOTATION_IGNORED)
        }
    }

    // todo: also check on a side of external serializer
    private fun buildSerializableProperties(descriptor: ClassDescriptor, trace: BindingTrace): SerializableProperties? {
        if (!descriptor.annotations.hasAnnotation(SerializationAnnotations.serializableAnnotationFqName)) return null
        if (!descriptor.isInternalSerializable) return null
        if (descriptor.hasCompanionObjectAsSerializer) return null // customized by user

        val props = SerializableProperties(descriptor, trace.bindingContext)
        if (!props.isExternallySerializable) trace.reportOnSerializableAnnotation(
            descriptor,
            SerializationErrors.PRIMARY_CONSTRUCTOR_PARAMETER_IS_NOT_A_PROPERTY
        )

        // check that all names are unique
        val namesSet = mutableSetOf<String>()
        props.serializableProperties.forEach {
            if (!namesSet.add(it.name)) {
                descriptor.safeReport { a ->
                    trace.reportFromPlugin(
                        SerializationErrors.DUPLICATE_SERIAL_NAME.on(a, it.name),
                        SerializationPluginErrorsRendering
                    )
                }
            }
        }

        trace.record(SERIALIZABLE_PROPERTIES, descriptor, props)
        return props
    }

    private fun analyzePropertiesSerializers(trace: BindingTrace, serializableClass: ClassDescriptor, props: List<SerializableProperty>) {
        val generatorContextForAnalysis = object : AbstractSerialGenerator(trace.bindingContext, serializableClass) {}
        // todo: recursive check for prop type arguments (e.g. List<NonSerializableType>)
        props.forEach {
            val serializer = it.serializableWith?.toClassDescriptor ?: generatorContextForAnalysis.findTypeSerializerOrContext(
                it.module,
                it.type,
                it.descriptor.annotations,
                it.descriptor.findPsi()
            )
            if (serializer != null) {
                trace.record(SERIALIZER_FOR_PROPERTY, it.descriptor, serializer)
            } else {
                if (it.genericIndex != null) return@forEach // properties with `T` types do not have dedicated serializers
                // todo: report only on property type, not on a whole prop for better-looking?
                val psi = it.descriptor.findPsi() ?: return@forEach
                trace.reportFromPlugin(
                    SerializationErrors.SERIALIZER_NOT_FOUND.on(psi),
                    SerializationPluginErrorsRendering
                )
            }
        }
    }

    private inline fun ClassDescriptor.safeReport(report: (KtAnnotationEntry) -> Unit) {
        findSerializableAnnotationDeclaration()?.let(report)
    }

    private fun BindingTrace.reportOnSerializableAnnotation(descriptor: ClassDescriptor, error: DiagnosticFactory0<KtAnnotationEntry>) {
        descriptor.safeReport { e ->
            reportFromPlugin(
                error.on(e),
                SerializationPluginErrorsRendering
            )
        }
    }

}
