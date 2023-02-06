package com.tyron.completion.java.provider;

import static org.jetbrains.kotlin.com.intellij.patterns.PlatformPatterns.elementType;
import static org.jetbrains.kotlin.com.intellij.patterns.PsiJavaPatterns.psiElement;

import androidx.annotation.NonNull;

import com.tyron.completion.java.util.JavaCompletionUtil;
import com.tyron.completion.lookup.LookupElement;
import com.tyron.completion.lookup.impl.LookupItemUtil;
import com.tyron.completion.model.CompletionList;
import com.tyron.completion.psi.codeInsight.completion.CompletionUtil;
import com.tyron.completion.psi.codeInsight.completion.JavaConstructorCallElement;
import com.tyron.completion.psi.codeInsight.completion.JavaMethodCallElement;
import com.tyron.completion.psi.completion.JavaClassNameCompletionContributor;
import com.tyron.completion.psi.completion.JavaClassNameInsertHandler;
import com.tyron.completion.psi.completion.JavaKeywordCompletion;
import com.tyron.completion.psi.completion.item.JavaLookupElementBuilder;
import com.tyron.completion.psi.completion.item.JavaPsiClassReferenceElement;
import com.tyron.completion.psi.completion.item.VariableLookupItem;
import com.tyron.completion.psi.scope.CompletionElement;
import com.tyron.completion.psi.scope.JavaCompletionProcessor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.com.intellij.lang.java.JavaLanguage;
import org.jetbrains.kotlin.com.intellij.openapi.util.Condition;
import org.jetbrains.kotlin.com.intellij.openapi.util.Conditions;
import org.jetbrains.kotlin.com.intellij.patterns.ElementPattern;
import org.jetbrains.kotlin.com.intellij.patterns.PatternCondition;
import org.jetbrains.kotlin.com.intellij.psi.JavaTokenType;
import org.jetbrains.kotlin.com.intellij.psi.LambdaUtil;
import org.jetbrains.kotlin.com.intellij.psi.PsiClass;
import org.jetbrains.kotlin.com.intellij.psi.PsiCodeBlock;
import org.jetbrains.kotlin.com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.com.intellij.psi.PsiEnumConstant;
import org.jetbrains.kotlin.com.intellij.psi.PsiExpression;
import org.jetbrains.kotlin.com.intellij.psi.PsiIdentifier;
import org.jetbrains.kotlin.com.intellij.psi.PsiImportStatementBase;
import org.jetbrains.kotlin.com.intellij.psi.PsiImportStaticStatement;
import org.jetbrains.kotlin.com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.kotlin.com.intellij.psi.PsiJavaReference;
import org.jetbrains.kotlin.com.intellij.psi.PsiKeyword;
import org.jetbrains.kotlin.com.intellij.psi.PsiLambdaExpression;
import org.jetbrains.kotlin.com.intellij.psi.PsiLambdaParameterType;
import org.jetbrains.kotlin.com.intellij.psi.PsiMember;
import org.jetbrains.kotlin.com.intellij.psi.PsiMethod;
import org.jetbrains.kotlin.com.intellij.psi.PsiParameter;
import org.jetbrains.kotlin.com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.kotlin.com.intellij.psi.PsiSubstitutor;
import org.jetbrains.kotlin.com.intellij.psi.PsiType;
import org.jetbrains.kotlin.com.intellij.psi.PsiVariable;
import org.jetbrains.kotlin.com.intellij.psi.PsiWildcardType;
import org.jetbrains.kotlin.com.intellij.psi.filters.ElementFilter;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiUtil;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.kotlin.com.intellij.util.ProcessingContext;
import org.jetbrains.kotlin.com.intellij.util.containers.JBIterable;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes completion candidates using PSI from the kotlin compiler.
 */
public class JavaKotlincCompletionProvider {

    private static final ElementPattern<PsiElement> AFTER_NUMBER_LITERAL =
            psiElement().afterLeaf(psiElement().withElementType(
                    elementType().oneOf(JavaTokenType.DOUBLE_LITERAL, JavaTokenType.LONG_LITERAL, JavaTokenType.FLOAT_LITERAL, JavaTokenType.INTEGER_LITERAL)));
    private static final ElementPattern<PsiElement> IMPORT_REFERENCE =
            psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).withParent(
                    PsiImportStatementBase.class));
    private static final ElementPattern<PsiElement> CATCH_OR_FINALLY = psiElement().afterLeaf(
            psiElement().withText("}").withParent(
                    psiElement(PsiCodeBlock.class).afterLeaf(psiElement().withText(PsiKeyword.TRY))));
    private static final ElementPattern<PsiElement> INSIDE_CONSTRUCTOR = psiElement().inside(psiElement(
            PsiMethod.class).with(new PatternCondition<PsiMethod>("constructor") {
        @Override
        public boolean accepts(@NonNull PsiMethod psiMethod, ProcessingContext processingContext) {
            return psiMethod.isConstructor();
        }
    }));
//    private static final ElementPattern<PsiElement> AFTER_ENUM_CONSTANT =
//            psiElement().inside(psiElement(PsiTypeElement.class)).afterLeaf(
//                    psiElement().inside(true, psiElement(PsiEnumConstant.class), psiElement(PsiClass.class, PsiExpressionList.class)));
    public JavaKotlincCompletionProvider() {
    }

    public void fillCompletionVariants(@NonNull PsiElement position,
                                       @NonNull CompletionList.Builder builder) {
        if (!isInJavaContext(position)) {
            return;
        }

        if (AFTER_NUMBER_LITERAL.accepts(position) ) { //||
//            UNEXPECTED_REFERENCE_AFTER_DOT.accepts(position) ||
//            AFTER_ENUM_CONSTANT.accepts(position)) {
//            _result.stopHere();
            return;
        }

        PsiElement parent = position.getParent();

        if (position instanceof PsiIdentifier) {
            new JavaKeywordCompletion(position, builder);

            addIdentifierVariants(position, builder);

            addClassNames(position, builder);

            if (parent instanceof PsiJavaCodeReferenceElement) {
                PsiJavaCodeReferenceElement parentRef = (PsiJavaCodeReferenceElement) parent;
                Set<LookupElement> lookupElements =
                        completeReference(position, parentRef, new ElementFilter() {
                            @Override
                            public boolean isAcceptable(Object o, @Nullable PsiElement psiElement) {
                                return true;
                            }

                            @Override
                            public boolean isClassAcceptable(Class aClass) {
                                return true;
                            }
                        }, JavaCompletionProcessor.Options.DEFAULT_OPTIONS, s -> true);

                for (LookupElement lookupElement : lookupElements) {
                    builder.addItem(lookupElement);
                }
            }
        }
    }

    private void addClassNames(PsiElement elementAt, CompletionList.Builder builder) {

    }

    private Set<LookupElement> completeReference(@NonNull PsiElement element,
                                                 @NonNull PsiJavaCodeReferenceElement javaReference,
                                                 @NonNull ElementFilter elementFilter,
                                                 @NonNull JavaCompletionProcessor.Options options,
                                                 @NonNull Condition<? super String> nameCondition) {
        PsiElement elementParent = element.getContext();
        if (elementParent instanceof PsiReferenceExpression) {
            final PsiExpression qualifierExpression =
                    ((PsiReferenceExpression) elementParent).getQualifierExpression();
            if (qualifierExpression instanceof PsiReferenceExpression) {
                final PsiElement resolve = ((PsiReferenceExpression) qualifierExpression).resolve();
                if (resolve instanceof PsiParameter) {
                    final PsiElement declarationScope = ((PsiParameter)resolve).getDeclarationScope();
                    if (((PsiParameter)resolve).getType() instanceof PsiLambdaParameterType) {
                        final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression) declarationScope;
                        if (PsiTypesUtil.getExpectedTypeByParent(lambdaExpression) == null) {
                            final int parameterIndex = lambdaExpression.getParameterList().getParameterIndex((PsiParameter)resolve);
                            Set<LookupElement> set = new LinkedHashSet<>();
                            final boolean overloadsFound = LambdaUtil.processParentOverloads(lambdaExpression,  functionalInterfaceType -> {
                                PsiType qualifierType = LambdaUtil.getLambdaParameterFromType(functionalInterfaceType, parameterIndex);
                                if (qualifierType instanceof PsiWildcardType) {
                                    qualifierType = ((PsiWildcardType)qualifierType).getBound();
                                }
                                if (qualifierType == null) return;

                                PsiReferenceExpression fakeRef = JavaCompletionUtil
                                        .createReference("xxx.xxx", JavaCompletionUtil
                                                .createContextWithXxxVariable(element, qualifierType));
                                set.addAll(processJavaQualifiedReference(fakeRef.getReferenceNameElement(), fakeRef, elementFilter, options, nameCondition));
                            });

                            if (overloadsFound) {
                                return set;
                            }
                        }
                    }
                }
            }
        }


        return processJavaQualifiedReference(element, javaReference, elementFilter, options, nameCondition);
    }

    private static Set<LookupElement> processJavaQualifiedReference(@NonNull PsiElement element,
                                                                    @NonNull PsiJavaCodeReferenceElement javaReference,
                                                                    @NonNull ElementFilter elementFilter,
                                                                    @NonNull JavaCompletionProcessor.Options options,
                                                                    @NonNull Condition<? super String> nameCondition) {
        Set<LookupElement> set = new LinkedHashSet<>();

        JavaCompletionProcessor processor = new JavaCompletionProcessor(element, elementFilter, options, nameCondition);
        PsiType plainQualifier = processor.getQualifierType();

//        List<PsiType> runtimeQualifiers = getQualifierCastTypes(javaReference, parameters);

        javaReference.processVariants(processor);

//        List<PsiTypeLookupItem> castItems = ContainerUtil.map(runtimeQualifiers, q -> PsiTypeLookupItem.createLookupItem(q, element));

        boolean pkgContext = JavaCompletionUtil.inSomePackage(element);

        PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(plainQualifier);
//        boolean honorExcludes = qualifierClass == null || !isInExcludedPackage(qualifierClass, false);

//        Set<PsiType> expectedTypes = ObjectUtils.coalesce(getExpectedTypes(parameters), Collections.emptySet());

        Iterable<CompletionElement> results = processor.getResults();
        Set<PsiMember> mentioned = new HashSet<>();
        for (CompletionElement completionElement : results) {
            Iterable<? extends LookupElement> lookupElements =
                    createLookupElements(completionElement, javaReference);
            for (LookupElement item : lookupElements) {
//                item.putUserData(QUALIFIER_TYPE_ATTR, plainQualifier);
                Object o = item.getObject();
                if (o instanceof PsiClass) {
                    PsiClass specifiedQualifierClass = javaReference.isQualified() ? qualifierClass : ((PsiClass)o).getContainingClass();
                    if (!JavaCompletionUtil.isSourceLevelAccessible(element, (PsiClass)o, pkgContext, specifiedQualifierClass)) {
                        continue;
                    }
                }
                if (o instanceof PsiMember) {
//                    if (honorExcludes && isInExcludedPackage((PsiMember)o, true)) {
//                        continue;
//                    }
                    mentioned.add(CompletionUtil.getOriginalOrSelf((PsiMember)o));
                }
                set.add(item);
//                PsiTypeLookupItem qualifierCast = findQualifierCast(item, castItems, plainQualifier, processor, expectedTypes);
//                if (qualifierCast != null) item = castQualifier(item, qualifierCast);
//                set.add(highlighter.highlightIfNeeded(qualifierCast != null ? qualifierCast.getType() : plainQualifier, item, o));
            }
        }

        PsiElement refQualifier = javaReference.getQualifier();
//        if (refQualifier == null && PsiTreeUtil.getParentOfType(element, PsiPackageStatement.class, PsiImportStatementBase.class) == null) {
//            StaticMemberProcessor memberProcessor = new JavaStaticMemberProcessor(parameters);
//            memberProcessor.processMembersOfRegisteredClasses(nameCondition, (member, psiClass) -> {
//                if (!mentioned.contains(member) && processor.satisfies(member, ResolveState.initial())) {
//                    ContainerUtil.addIfNotNull(set, memberProcessor.createLookupElement(member, psiClass, true));
//                }
//            });
//        }
//        else if (refQualifier instanceof PsiSuperExpression && ((PsiSuperExpression)refQualifier).getQualifier() == null) {
//            set.addAll(SuperCalls.suggestQualifyingSuperCalls(element, javaReference, elementFilter, options, nameCondition));
//        }

        return set;
    }

    static Iterable<? extends LookupElement> createLookupElements(@NotNull CompletionElement completionElement, @NotNull PsiJavaReference reference) {
        Object completion = completionElement.getElement();
        assert !(completion instanceof LookupElement);

        if (reference instanceof PsiJavaCodeReferenceElement) {
            if (completion instanceof PsiMethod &&
                ((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiImportStaticStatement) {
                return Collections.singletonList(JavaLookupElementBuilder.forMethod((PsiMethod)completion, PsiSubstitutor.EMPTY));
            }

            if (completion instanceof PsiClass) {
                List<JavaPsiClassReferenceElement> classItems = JavaClassNameCompletionContributor.createClassLookupItems(
                        CompletionUtil.getOriginalOrSelf((PsiClass)completion),
                        JavaClassNameCompletionContributor.AFTER_NEW.accepts(reference),
                        JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER,
                        Conditions.alwaysTrue());
                return JBIterable.from(classItems).flatMap(i -> JavaConstructorCallElement.wrap(i, reference.getElement()));
            }
        }

        PsiSubstitutor substitutor = completionElement.getSubstitutor();
        if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
        if (completion instanceof PsiClass) {
            JavaPsiClassReferenceElement classItem =
                    JavaClassNameCompletionContributor.createClassLookupItem((PsiClass)completion, true).setSubstitutor(substitutor);
            return JavaConstructorCallElement.wrap(classItem, reference.getElement());
        }

        if (completion instanceof PsiMethod) {
//            if (reference instanceof PsiMethodReferenceExpression) {
//                return Collections.singleton((LookupElement)new JavaMethodReferenceElement(
//                        (PsiMethod)completion, (PsiMethodReferenceExpression)reference, completionElement.getMethodRefType()));
//            }
            JavaMethodCallElement item = new JavaMethodCallElement((PsiMethod)completion).setQualifierSubstitutor(substitutor);
            item.setForcedQualifier(completionElement.getQualifierText());
            return Collections.singletonList(item);
        }

        if (completion instanceof PsiVariable) {
            if (completion instanceof PsiEnumConstant &&
                PsiTreeUtil.isAncestor(((PsiEnumConstant) completion).getArgumentList(), reference.getElement(), true)) {
                return Collections.emptyList();
            }
            return Collections.singletonList(new VariableLookupItem((PsiVariable)completion).setSubstitutor(substitutor).qualifyIfNeeded(reference, null));
        }
//        if (completion instanceof PsiPackage) {
//            return Collections.singletonList(new PackageLookupItem((PsiPackage)completion, reference.getElement()));
//        }

        return Collections.singletonList(LookupItemUtil.objectToLookupItem(completion));
    }


    private static List<PsiType> getQualifierCastTypes(PsiJavaReference javaReference) {
        return Collections.emptyList();
    }
    private void addIdentifierVariants(PsiElement elementAt, CompletionList.Builder builder) {

    }

    public static boolean isInJavaContext(PsiElement position) {
        return PsiUtilCore.findLanguageFromElement(position).isKindOf(JavaLanguage.INSTANCE);
    }
}
