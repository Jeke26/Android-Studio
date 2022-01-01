package com.tyron.completion.java.util;

import static com.tyron.completion.java.util.JavaParserTypesUtil.toType;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.ReceiverParameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.UnparsableStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import com.tyron.completion.java.rewrite.EditHelper;

import org.openjdk.javax.lang.model.element.ExecutableElement;
import org.openjdk.javax.lang.model.element.TypeParameterElement;
import org.openjdk.javax.lang.model.element.VariableElement;
import org.openjdk.javax.lang.model.type.ExecutableType;
import org.openjdk.javax.lang.model.type.TypeMirror;
import org.openjdk.javax.lang.model.type.TypeVariable;
import org.openjdk.source.tree.AnnotationTree;
import org.openjdk.source.tree.BlockTree;
import org.openjdk.source.tree.ClassTree;
import org.openjdk.source.tree.CompilationUnitTree;
import org.openjdk.source.tree.ErroneousTree;
import org.openjdk.source.tree.ExpressionStatementTree;
import org.openjdk.source.tree.ExpressionTree;
import org.openjdk.source.tree.IdentifierTree;
import org.openjdk.source.tree.ImportTree;
import org.openjdk.source.tree.LiteralTree;
import org.openjdk.source.tree.MemberSelectTree;
import org.openjdk.source.tree.MethodInvocationTree;
import org.openjdk.source.tree.MethodTree;
import org.openjdk.source.tree.PackageTree;
import org.openjdk.source.tree.StatementTree;
import org.openjdk.source.tree.Tree;
import org.openjdk.source.tree.TypeParameterTree;
import org.openjdk.source.tree.VariableTree;
import org.openjdk.tools.javac.code.Type.ClassType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JavaParserUtil {

    public static CompilationUnit toCompilationUnit(CompilationUnitTree tree) {
        CompilationUnit compilationUnit = new CompilationUnit();
        compilationUnit.setPackageDeclaration(toPackageDeclaration(tree.getPackage()));
        tree.getImports().forEach(importTree -> compilationUnit.addImport(toImportDeclaration(importTree)));

        compilationUnit.setTypes(tree.getTypeDecls().stream()
                .map(JavaParserUtil::toClassOrInterfaceDeclaration)
                .collect(NodeList.toNodeList()));
        return compilationUnit;
    }

    public static PackageDeclaration toPackageDeclaration(PackageTree tree) {
        PackageDeclaration declaration = new PackageDeclaration();
        declaration.setName(tree.getPackageName().toString());
        return declaration;
    }

    public static ImportDeclaration toImportDeclaration(ImportTree tree) {
        String name = tree.getQualifiedIdentifier().toString();
        boolean isAsterisk = name.endsWith("*");
        return new ImportDeclaration(name, tree.isStatic(), isAsterisk);
    }

    public static ClassOrInterfaceDeclaration toClassOrInterfaceDeclaration(Tree tree) {
        if (tree instanceof ClassTree) {
            return toClassOrInterfaceDeclaration((ClassTree) tree);
        }
        return null;
    }

    public static BlockStmt toBlockStatement(BlockTree tree) {
        BlockStmt blockStmt = new BlockStmt();
        blockStmt.setStatements(tree.getStatements().stream()
                .map(JavaParserUtil::toStatement)
                .collect(NodeList.toNodeList()));
        return blockStmt;
    }

    public static Statement toStatement(StatementTree tree) {
        if (tree instanceof ExpressionStatementTree) {
            return toExpressionStatement((ExpressionStatementTree) tree);
        }
        if (tree instanceof VariableTree) {
            return toVariableDeclarationExpression(((VariableTree) tree));
        }
        return StaticJavaParser.parseStatement(tree.toString());
    }

    public static ExpressionStmt toExpressionStatement(ExpressionStatementTree tree) {
        ExpressionStmt expressionStmt = new ExpressionStmt();
        expressionStmt.setExpression(toExpression(tree.getExpression()));
        return expressionStmt;
    }

    public static Expression toExpression(ExpressionTree tree) {
        if (tree instanceof MethodInvocationTree) {
            return toMethodCallExpression((MethodInvocationTree) tree);
        }
        if (tree instanceof MemberSelectTree) {
            return toFieldAccessExpression((MemberSelectTree) tree);
        }
        if (tree instanceof IdentifierTree) {
            return toNameExpr(((IdentifierTree) tree));
        }
        if (tree instanceof LiteralTree) {
        }
        if (tree instanceof ErroneousTree) {
            ErroneousTree erroneousTree = (ErroneousTree) tree;
            if (!erroneousTree.getErrorTrees().isEmpty()) {
                Tree errorTree = erroneousTree.getErrorTrees().get(0);
                return toExpression((ExpressionTree) errorTree);
            }
        }
        return null;
    }

    public static ExpressionStmt toVariableDeclarationExpression(VariableTree tree) {
        VariableDeclarationExpr expr = new VariableDeclarationExpr();
        expr.setModifiers(tree.getModifiers().getFlags().stream()
                .map(JavaParserUtil::toModifier)
                .collect(NodeList.toNodeList()));

        VariableDeclarator declarator = new VariableDeclarator();
        declarator.setName(tree.getName().toString());
        declarator.setInitializer(toExpression(tree.getInitializer()));
        declarator.setType(toType(tree.getType()));
        expr.addVariable(declarator);

        ExpressionStmt stmt = new ExpressionStmt();
        stmt.setExpression(expr);
        return stmt;
    }

    public static NameExpr toNameExpr(IdentifierTree tree) {
        NameExpr nameExpr = new NameExpr();
        nameExpr.setName(tree.getName().toString());
        return nameExpr;
    }

    public static MethodCallExpr toMethodCallExpression(MethodInvocationTree tree) {
        MethodCallExpr expr = new MethodCallExpr();
        if (tree.getMethodSelect() instanceof MemberSelectTree) {
            MemberSelectTree methodSelect = (MemberSelectTree) tree.getMethodSelect();
            expr.setScope(toExpression(methodSelect.getExpression()));
            expr.setName(methodSelect.getIdentifier().toString());
        }
        expr.setArguments(tree.getArguments().stream()
                .map(JavaParserUtil::toExpression)
                .collect(NodeList.toNodeList()));
        expr.setTypeArguments(tree.getTypeArguments().stream()
                .map(JavaParserTypesUtil::toType)
                .collect(NodeList.toNodeList()));
        if (tree.getMethodSelect() instanceof IdentifierTree) {
            expr.setName(toNameExpr((IdentifierTree) tree.getMethodSelect()).getName());
        }
        return expr;
    }

    public static FieldAccessExpr toFieldAccessExpression(MemberSelectTree tree) {
        FieldAccessExpr fieldAccessExpr = new FieldAccessExpr();
        fieldAccessExpr.setName(tree.getIdentifier().toString());
        fieldAccessExpr.setScope(toExpression(tree.getExpression()));
        return fieldAccessExpr;
    }

    public static ClassOrInterfaceDeclaration toClassOrInterfaceDeclaration(ClassTree tree) {
        ClassOrInterfaceDeclaration declaration = new ClassOrInterfaceDeclaration();
        declaration.setName(tree.getSimpleName().toString());
        declaration.setExtendedTypes(NodeList.nodeList(JavaParserTypesUtil.toClassOrInterfaceType(tree.getExtendsClause())));
        declaration.setTypeParameters(tree.getTypeParameters().stream()
                .map(JavaParserUtil::toTypeParameter)
                .collect(NodeList.toNodeList()));
        declaration.setTypeParameters(tree.getTypeParameters().stream()
                .map(JavaParserUtil::toTypeParameter)
                .collect(NodeList.toNodeList()));
        declaration.setImplementedTypes(tree.getImplementsClause().stream()
                .map(JavaParserTypesUtil::toClassOrInterfaceType)
                .collect(NodeList.toNodeList()));
        declaration.setModifiers(tree.getModifiers().getFlags().stream()
                .map(JavaParserUtil::toModifier)
                .collect(NodeList.toNodeList()));
        declaration.setMembers(tree.getMembers().stream()
                .map(JavaParserUtil::toBodyDeclaration)
                .collect(NodeList.toNodeList()));
        return declaration;
    }

    public static BodyDeclaration<?> toBodyDeclaration(Tree tree) {
        if (tree instanceof MethodTree) {
            return toMethodDeclaration(((MethodTree) tree), null);
        }
        if (tree instanceof VariableTree) {
            return toFieldDeclration((VariableTree) tree);
        }
        return null;
    }

    public static FieldDeclaration toFieldDeclration(VariableTree tree) {
        FieldDeclaration declaration = new FieldDeclaration();
        declaration.setModifiers(tree.getModifiers().getFlags().stream()
                .map(JavaParserUtil::toModifier)
                .collect(NodeList.toNodeList()));

        VariableDeclarator declarator = new VariableDeclarator();
        declarator.setName(tree.getName().toString());

        Expression initializer = toExpression(tree.getInitializer());
        if (initializer != null) {
            declarator.setInitializer(initializer);
        }
        Type type = toType(tree.getType());
        if (type != null) {
            declarator.setType(type);
        }
        declaration.addVariable(declarator);

        return declaration;
    }

    public static MethodDeclaration toMethodDeclaration(MethodTree method, ExecutableType type) {
        MethodDeclaration methodDeclaration = new MethodDeclaration();
        methodDeclaration.setAnnotations(method.getModifiers().getAnnotations().stream()
                .map(JavaParserUtil::toAnnotation)
                .collect(NodeList.toNodeList()));
        methodDeclaration.setName(method.getName().toString());
        methodDeclaration.setType(toType(method.getReturnType()));
        methodDeclaration.setModifiers(method.getModifiers().getFlags().stream()
                .map(JavaParserUtil::toModifier)
                .collect(NodeList.toNodeList()));
        methodDeclaration.setParameters(method.getParameters().stream()
                .map(JavaParserUtil::toParameter)
                .collect(NodeList.toNodeList()));
        methodDeclaration.setTypeParameters(method.getTypeParameters().stream()
                .map(it -> toType(((TypeMirror) it)))
                .filter(Objects::nonNull)
                .map(type1 -> type1 != null ? type1.toTypeParameter() : Optional.<TypeParameter>empty())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(NodeList.toNodeList()));
        methodDeclaration.setBody(toBlockStatement(method.getBody()));
        if (method.getReceiverParameter() != null) {
            methodDeclaration.setReceiverParameter(toReceiverParameter(method.getReceiverParameter()));
        }
        return methodDeclaration;
    }

    public static AnnotationExpr toAnnotation(AnnotationTree tree) {
        if (tree.getArguments().isEmpty()) {
            MarkerAnnotationExpr expr = new MarkerAnnotationExpr();
            expr.setName(toType(tree.getAnnotationType()).toString());
            return expr;
        }
        if (tree.getArguments().size() == 1) {
            SingleMemberAnnotationExpr expr = new SingleMemberAnnotationExpr();
            expr.setName(toType(tree.getAnnotationType()).toString());
            expr.setMemberValue(toExpression(tree.getArguments().get(0)));
            return expr;
        }
        NormalAnnotationExpr expr = new NormalAnnotationExpr();
        expr.setName(toType(tree.getAnnotationType()).toString());
        expr.setPairs(tree.getArguments().stream()
                .map(arg -> {
                    MemberValuePair pair = new MemberValuePair();
                    pair.setValue(toExpression(arg));
                    return pair;
                })
                .collect(NodeList.toNodeList()));
        return expr;
    }


    public static Parameter toParameter(VariableTree tree) {
        Parameter parameter = new Parameter();
        parameter.setType(toType(tree.getType()));
        parameter.setModifiers(tree.getModifiers().getFlags().stream()
                .map(JavaParserUtil::toModifier)
                .collect(NodeList.toNodeList()));
        parameter.setName(tree.getName().toString());
        return parameter;
    }

    /**
     * Convert a parameter into {@link Parameter} object. This method is called from
     * source files, giving their accurate names.
     */
    public static Parameter toParameter(TypeMirror type, VariableTree name) {
        Parameter parameter = new Parameter();
        parameter.setType(EditHelper.printType(type));
        parameter.setName(name.getName().toString());
        parameter.setModifiers(name.getModifiers().getFlags().stream()
                .map(JavaParserUtil::toModifier)
                .collect(NodeList.toNodeList()));
        parameter.setName(name.getName().toString());
        return parameter;
    }

    public static TypeParameter toTypeParameter(TypeParameterTree type) {
        return StaticJavaParser.parseTypeParameter(type.toString());
    }

    public static ReceiverParameter toReceiverParameter(VariableTree parameter) {
        ReceiverParameter receiverParameter = new ReceiverParameter();
        receiverParameter.setName(parameter.getName().toString());
        receiverParameter.setType(toType(parameter.getType()));
        return receiverParameter;
    }










    public static MethodDeclaration toMethodDeclaration(ExecutableElement method, ExecutableType type) {
        MethodDeclaration methodDeclaration = new MethodDeclaration();
        methodDeclaration.setType(toType(type.getReturnType()));
        methodDeclaration.setDefault(method.isDefault());
        methodDeclaration.setName(method.getSimpleName().toString());
        methodDeclaration.setModifiers(method.getModifiers().stream()
                .map(modifier -> Modifier.Keyword.valueOf(modifier.name()))
                .toArray(Modifier.Keyword[]::new));
        methodDeclaration.setParameters(IntStream.range(0, method.getParameters().size())
                .mapToObj(i -> toParameter(type.getParameterTypes().get(i), method.getParameters().get(i)))
                .collect(NodeList.toNodeList()));
        methodDeclaration.setTypeParameters(type.getTypeVariables().stream()
                .map(it -> toType(((TypeMirror) it)))
                .filter(Objects::nonNull)
                .map(type1 -> type1 != null ? type1.toTypeParameter() : Optional.<TypeParameter>empty())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(NodeList.toNodeList()));
        return methodDeclaration;
    }


    public static Modifier toModifier(org.openjdk.javax.lang.model.element.Modifier modifier) {
        return new Modifier(Modifier.Keyword.valueOf(modifier.name()));
    }

    /**
     * Convert a parameter into {@link Parameter} object. This method is called from
     * compiled class files, giving inaccurate parameter names
     */
    public static Parameter toParameter(TypeMirror type, VariableElement name) {
        Parameter parameter = new Parameter();
        parameter.setType(EditHelper.printType(type));
        parameter.setName(name.getSimpleName().toString());
        parameter.setModifiers(name.getModifiers().stream()
                .map(JavaParserUtil::toModifier)
                .collect(NodeList.toNodeList()));
        parameter.setName(name.getSimpleName().toString());
        return parameter;
    }

    public static TypeParameter toTypeParameter(TypeParameterElement type) {
        return StaticJavaParser.parseTypeParameter(type.toString());
    }

    public static TypeParameter toTypeParameter(TypeVariable typeVariable) {
        return StaticJavaParser.parseTypeParameter(typeVariable.toString());
    }

    public static List<String> getClassNames(Type type) {
        List<String> classNames = new ArrayList<>();
        if (type.isClassOrInterfaceType()) {
            classNames.add(type.asClassOrInterfaceType().getName().asString());
        }
        if (type.isWildcardType()) {
            WildcardType wildcardType = type.asWildcardType();
            wildcardType.getExtendedType().ifPresent(t -> classNames.addAll(getClassNames(t)));
            wildcardType.getSuperType().ifPresent(t -> classNames.addAll(getClassNames(t)));
        }
        if (type.isArrayType()) {
            classNames.addAll(getClassNames(type.asArrayType().getComponentType()));
        }
        if (type.isIntersectionType()) {
            type.asIntersectionType().getElements().stream()
                    .map(JavaParserUtil::getClassNames)
                    .forEach(classNames::addAll);
        }
        return classNames;
    }


    /**
     * Print a node declaration into its string representation
     * @param node node to print
     * @param delegate callback to whether a class name should be printed as fully qualified names
     * @return String representation of the method declaration properly formatted
     */
    public static String prettyPrint(Node node, JavaParserTypesUtil.NeedFqnDelegate delegate) {
        PrinterConfiguration configuration = new DefaultPrinterConfiguration();
        JavaPrettyPrinterVisitor visitor =  new JavaPrettyPrinterVisitor(configuration) {
            @Override
            public void visit(SimpleName n, Void arg) {
                printOrphanCommentsBeforeThisChildNode(n);
                printComment(n.getComment(), arg);

                String identifier = n.getIdentifier();
                if (delegate.needsFqn(identifier)) {
                    printer.print(identifier);
                } else {
                    printer.print(ActionUtil.getSimpleName(identifier));
                }
            }
        };
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter(t -> visitor, configuration);
        return prettyPrinter.print(node);
    }
}
