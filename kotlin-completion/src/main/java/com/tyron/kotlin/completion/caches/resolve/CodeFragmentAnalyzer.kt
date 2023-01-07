//package com.tyron.kotlin.completion.caches.resolve
//
//import com.tyron.kotlin.completion.resolve.lazy.BodyResolveMode
//import com.tyron.kotlin.completion.util.ExplicitImportsScope
//import com.tyron.kotlin.completion.util.getResolutionScope
//import org.jetbrains.kotlin.com.intellij.openapi.util.Key
//import org.jetbrains.kotlin.com.intellij.psi.PsiElement
//import org.jetbrains.kotlin.descriptors.ClassDescriptor
//import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
//import org.jetbrains.kotlin.descriptors.ModuleDescriptor
//import org.jetbrains.kotlin.incremental.components.NoLookupLocation
//import org.jetbrains.kotlin.javax.inject.Inject
//import org.jetbrains.kotlin.psi.*
//import org.jetbrains.kotlin.psi.codeFragmentUtil.suppressDiagnosticsInDebugMode
//import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
//import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes3
//import org.jetbrains.kotlin.resolve.*
//import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoAfter
//import org.jetbrains.kotlin.resolve.lazy.ResolveSession
//import org.jetbrains.kotlin.resolve.scopes.ImportingScope
//import org.jetbrains.kotlin.resolve.scopes.LexicalScope
//import org.jetbrains.kotlin.resolve.scopes.LocalRedeclarationChecker
//import org.jetbrains.kotlin.resolve.scopes.utils.addImportingScopes
//import org.jetbrains.kotlin.types.KotlinType
//import org.jetbrains.kotlin.types.TypeUtils
//
//class CodeFragmentAnalyzer(
//    private val resolveSession: ResolveSession,
//    private val qualifierResolver: QualifiedExpressionResolver,
//    private val typeResolver: TypeResolver
//) {
//    @set:Inject // component dependency cycle
//    lateinit var resolveElementCache: ResolveElementCache
//
//    fun analyzeCodeFragment(codeFragment: KtCodeFragment, bodyResolveMode: BodyResolveMode): BindingTrace {
//        val contextAnalysisResult = analyzeCodeFragmentContext(codeFragment, bodyResolveMode)
//        return doAnalyzeCodeFragment(codeFragment, contextAnalysisResult)
//    }
//
//    private fun doAnalyzeCodeFragment(codeFragment: KtCodeFragment, contextInfo: ContextInfo): BindingTrace {
//        val (bindingContext, scope, dataFlowInfo) = contextInfo
//        val bindingTrace = DelegatingBindingTrace(bindingContext, "For code fragment analysis")
//
//        when (val contentElement = codeFragment.getContentElement()) {
//            is KtExpression -> {
//                val expectedType = codeFragment.getUserData(EXPECTED_TYPE_KEY) ?: TypeUtils.NO_EXPECTED_TYPE
//                contentElement.analyzeInContext(scope, trace = bindingTrace, dataFlowInfo = dataFlowInfo, expectedType = expectedType)
//                analyzeControlFlow(resolveSession, contentElement, bindingTrace)
//            }
//
//            is KtTypeReference -> {
//                val context = TypeResolutionContext(
//                    scope, bindingTrace,
//                    true, true, codeFragment.suppressDiagnosticsInDebugMode()
//                ).noBareTypes()
//
//                typeResolver.resolvePossiblyBareType(context, contentElement)
//            }
//        }
//
//        return bindingTrace
//    }
//
//    private data class ContextInfo(val bindingContext: BindingContext, val scope: LexicalScope, val dataFlowInfo: DataFlowInfo)
//
//    private fun analyzeCodeFragmentContext(codeFragment: KtCodeFragment, bodyResolveMode: BodyResolveMode): ContextInfo {
//        fun resolutionFactory(element: KtElement): BindingContext {
//            return resolveElementCache.resolveToElements(listOf(element), bodyResolveMode)
//        }
//
//        val context = refineContextElement(codeFragment.context)
//        val info = getContextInfo(context, ::resolutionFactory)
//        return info.copy(scope = enrichScopeWithImports(info.scope, codeFragment))
//    }
//
//    private tailrec fun getContextInfo(context: PsiElement?, resolutionFactory: (KtElement) -> BindingContext): ContextInfo {
//        var bindingContext: BindingContext = BindingContext.EMPTY
//        var dataFlowInfo: org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo = org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo.EMPTY
//        var scope: LexicalScope? = null
//
//        when (context) {
//            is KtPrimaryConstructor -> {
//                val containingClass = context.getContainingClassOrObject()
//                val resolutionResult = getClassDescriptor(containingClass, resolutionFactory)
//                if (resolutionResult != null) {
//                    bindingContext = resolutionResult.bindingContext
//                    scope = resolutionResult.descriptor.scopeForInitializerResolution
//                }
//            }
//            is KtSecondaryConstructor -> {
//                val expression = (context.bodyExpression ?: context.getDelegationCall().calleeExpression) as? KtExpression
//                if (expression != null) {
//                    bindingContext = resolutionFactory(expression)
//                    scope = bindingContext[BindingContext.LEXICAL_SCOPE, expression]
//                }
//            }
//            is KtClassOrObject -> {
//                val resolutionResult = getClassDescriptor(context, resolutionFactory)
//                if (resolutionResult != null) {
//                    bindingContext = resolutionResult.bindingContext
//                    scope = resolutionResult.descriptor.scopeForMemberDeclarationResolution
//                }
//            }
//            is KtFunction -> {
//                val bindingContextForFunction = resolutionFactory(context)
//                val functionDescriptor = bindingContextForFunction[BindingContext.FUNCTION, context]
//                if (functionDescriptor != null) {
//                    bindingContext = bindingContextForFunction
//
//                    @Suppress("NON_TAIL_RECURSIVE_CALL")
//                    val outerScope = getContextInfo(context.getParentOfType<KtDeclaration>(true), resolutionFactory).scope
//
//                    val localRedeclarationChecker = LocalRedeclarationChecker.DO_NOTHING
//                    scope = FunctionDescriptorUtil.getFunctionInnerScope(outerScope, functionDescriptor, localRedeclarationChecker)
//                }
//            }
//            is KtFile -> {
//                bindingContext = resolveSession.bindingContext
//                scope = resolveSession.fileScopeProvider.getFileResolutionScope(context)
//            }
//            is KtElement -> {
//                bindingContext = resolutionFactory(context)
//                scope = context.getResolutionScope(bindingContext)
//                dataFlowInfo = bindingContext.getDataFlowInfoAfter(context)
//            }
//        }
//
//        if (scope == null) {
//            val parentDeclaration = context?.getParentOfTypes3<KtDeclaration, KtFile, KtExpression>()
//            if (parentDeclaration != null) {
//                return getContextInfo(parentDeclaration, resolutionFactory)
//            }
//        }
//
//        return ContextInfo(bindingContext, scope ?: createEmptyScope(resolveSession.moduleDescriptor), dataFlowInfo)
//    }
//
//    private data class ClassResolutionResult(val bindingContext: BindingContext, val descriptor: ClassDescriptorWithResolutionScopes)
//
//    private fun getClassDescriptor(
//        classOrObject: KtClassOrObject,
//        resolutionFactory: (KtElement) -> BindingContext
//    ): ClassResolutionResult? {
//        val bindingContext: BindingContext
//        val classDescriptor: ClassDescriptor?
//
//        if (!KtPsiUtil.isLocal(classOrObject)) {
//            bindingContext = resolveSession.bindingContext
//            classDescriptor = resolveSession.getClassDescriptor(classOrObject, NoLookupLocation.FROM_IDE)
//        } else {
//            bindingContext = resolutionFactory(classOrObject)
//            classDescriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, classOrObject] as ClassDescriptor?
//        }
//
//        return (classDescriptor as? ClassDescriptorWithResolutionScopes)?.let { ClassResolutionResult(bindingContext, it) }
//    }
//
//    private fun refineContextElement(context: PsiElement?): KtElement? {
//        return when (context) {
//            is KtParameter -> context.getParentOfType<KtFunction>(true)?.let { it }
//            is KtProperty -> context.delegateExpressionOrInitializer
//            is KtConstructor<*> -> context
//            is KtFunctionLiteral -> context.bodyExpression?.statements?.lastOrNull()
//            is KtDeclarationWithBody -> context.bodyExpression
//            is KtBlockExpression -> context.statements.lastOrNull()
//            else -> null
//        } ?: context as? KtElement
//    }
//
//    private fun enrichScopeWithImports(scope: LexicalScope, codeFragment: KtCodeFragment): LexicalScope {
//        val additionalImportingScopes = mutableListOf<ImportingScope>()
//
//        val externalDescriptors = codeFragment.externalDescriptors ?: emptyList()
//        if (externalDescriptors.isNotEmpty()) {
//            additionalImportingScopes += ExplicitImportsScope(externalDescriptors)
//        }
//
//        val importList = codeFragment.importsAsImportList()
//        if (importList != null && importList.imports.isNotEmpty()) {
//            additionalImportingScopes += createImportScopes(importList)
//        }
//
//        if (additionalImportingScopes.isNotEmpty()) {
//            return scope.addImportingScopes(additionalImportingScopes)
//        }
//
//        return scope
//    }
//
//    private fun createImportScopes(importList: KtImportList): List<ImportingScope> {
//        return importList.imports.mapNotNull {
//            qualifierResolver.processImportReference(
//                it, resolveSession.moduleDescriptor, resolveSession.trace,
//                excludedImportNames = emptyList(), packageFragmentForVisibilityCheck = null
//            )
//        }
//    }
//
//    private fun createEmptyScope(moduleDescriptor: ModuleDescriptor): LexicalScope {
//        return LexicalScope.Base(ImportingScope.Empty, moduleDescriptor)
//    }
//
//    companion object {
//        val EXPECTED_TYPE_KEY = Key<KotlinType>("EXPECTED_TYPE")
//    }
//}