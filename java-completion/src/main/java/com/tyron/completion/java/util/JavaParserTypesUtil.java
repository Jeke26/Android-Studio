package com.tyron.completion.java.util;

import static com.github.javaparser.utils.PositionUtils.sortByBeginPosition;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.IntersectionType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import org.openjdk.javax.lang.model.type.DeclaredType;
import org.openjdk.javax.lang.model.type.NoType;
import org.openjdk.javax.lang.model.type.TypeKind;
import org.openjdk.javax.lang.model.type.TypeMirror;
import org.openjdk.javax.lang.model.type.TypeVariable;
import org.openjdk.source.tree.IdentifierTree;
import org.openjdk.source.tree.ParameterizedTypeTree;
import org.openjdk.source.tree.PrimitiveTypeTree;
import org.openjdk.source.tree.Tree;
import org.openjdk.source.tree.WildcardTree;
import org.openjdk.tools.javac.code.BoundKind;
import org.openjdk.tools.javac.tree.JCTree;

import java.util.Objects;

public class JavaParserTypesUtil {

    public interface NeedFqnDelegate {
        boolean needsFqn(String name);
    }

    // trees
    public static ClassOrInterfaceType toClassOrInterfaceType(Tree tree) {
        ClassOrInterfaceType type = new ClassOrInterfaceType();
        if (tree instanceof IdentifierTree) {
            type.setName(((IdentifierTree) tree).getName().toString());
        }
        if (tree instanceof ParameterizedTypeTree) {
            ParameterizedTypeTree parameterizedTypeTree = (ParameterizedTypeTree) tree;
            Type t = toType(parameterizedTypeTree.getType());

            NodeList<Type> typeArguments = new NodeList<>();
            for (Tree typeArgument : parameterizedTypeTree.getTypeArguments()) {
                Type typ = toType(typeArgument);
                typeArguments.add(typ);
            }
            if (t.isClassOrInterfaceType()) {
                type.setName(t.asClassOrInterfaceType().getName());
            }
            type.setTypeArguments(typeArguments);
        }
        return type;
    }

    public static Type toType(Tree tree) {
        Type type;
        if (tree instanceof PrimitiveTypeTree) {
            type = getPrimitiveType((PrimitiveTypeTree) tree);
        } else if (tree instanceof IdentifierTree) {
            type = toClassOrInterfaceType(tree);
        } else if (tree instanceof WildcardTree) {
            JCTree.JCWildcard wildcardTree = (JCTree.JCWildcard) tree;
            WildcardType wildcardType = new WildcardType();
            Tree bound = wildcardTree.getBound();
            Type boundType = toType(bound);
            if (wildcardTree.kind.kind == BoundKind.EXTENDS) {
                wildcardType.setExtendedType((ReferenceType) boundType);
            } else {
                wildcardType.setSuperType((ReferenceType) boundType);
            }
            type = wildcardType;
        } else if (tree instanceof ParameterizedTypeTree) {
            type = toClassOrInterfaceType(tree);
        }
        else {
            type = StaticJavaParser.parseType(tree.toString());
        }
        return type;
    }

    public static Type getPrimitiveType(PrimitiveTypeTree tree) {
        Type type;
        switch (tree.getPrimitiveTypeKind()) {
            case INT:
                type = PrimitiveType.intType();
                break;
            case BOOLEAN:
                type = PrimitiveType.booleanType();
                break;
            case LONG:
                type = PrimitiveType.longType();
                break;
            case SHORT:
                type = PrimitiveType.shortType();
                break;
            case CHAR:
                type = PrimitiveType.charType();
                break;
            case FLOAT:
                type = PrimitiveType.floatType();
                break;
            case VOID:
                type = new VoidType();
                break;
            default:
                type = new UnknownType();
        }
        return type;
    }



    // type mirrors

    public static Type toType(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.ARRAY) {
            return toArrayType((org.openjdk.javax.lang.model.type.ArrayType) typeMirror);
        }
        if (typeMirror.getKind().isPrimitive()) {
            return toPrimitiveType((org.openjdk.javax.lang.model.type.PrimitiveType) typeMirror);
        }
        if (typeMirror instanceof org.openjdk.javax.lang.model.type.IntersectionType) {
            return toIntersectionType((org.openjdk.javax.lang.model.type.IntersectionType) typeMirror);
        }
        if (typeMirror instanceof org.openjdk.javax.lang.model.type.WildcardType) {
            return toWildcardType((org.openjdk.javax.lang.model.type.WildcardType) typeMirror);
        }
        if (typeMirror instanceof org.openjdk.javax.lang.model.type.DeclaredType) {
            return toClassOrInterfaceType((DeclaredType) typeMirror);
        }
        if (typeMirror instanceof org.openjdk.javax.lang.model.type.TypeVariable) {
            return toType(((TypeVariable) typeMirror));
        }
        if (typeMirror instanceof NoType) {
            return new VoidType();
        }
        return null;
    }

    public static IntersectionType toIntersectionType(org.openjdk.javax.lang.model.type.IntersectionType type) {
        NodeList<ReferenceType> collect =
                type.getBounds().stream().map(JavaParserTypesUtil::toType)
                        .map(it -> ((ReferenceType) it))
                        .collect(NodeList.toNodeList());
        return new IntersectionType(collect);
    }

    public static Type toType(TypeVariable typeVariable) {
        TypeParameter typeParameter = new TypeParameter();
        TypeMirror upperBound = typeVariable.getUpperBound();
        Type type = toType(upperBound);
        if (type != null && type.isIntersectionType()) {
            typeParameter.setTypeBound(type.asIntersectionType().getElements().stream()
                    .filter(Type::isClassOrInterfaceType)
                    .map(Type::asClassOrInterfaceType)
                    .collect(NodeList.toNodeList()));
        }
        typeParameter.setName(typeVariable.toString());
        return typeParameter;
    }

    public static WildcardType toWildcardType(org.openjdk.javax.lang.model.type.WildcardType type) {
        WildcardType wildcardType = new WildcardType();
        if (type.getSuperBound() != null) {
            wildcardType.setSuperType((ReferenceType) toType(type.getSuperBound()));
        }
        if (type.getExtendsBound() != null) {
            wildcardType.setExtendedType((ReferenceType) toType(type.getExtendsBound()));
        }
        return wildcardType;
    }

    public static PrimitiveType toPrimitiveType(org.openjdk.javax.lang.model.type.PrimitiveType type) {
        PrimitiveType.Primitive primitive = PrimitiveType.Primitive.valueOf(type.getKind().name());
        return new PrimitiveType(primitive);
    }

    public static ArrayType toArrayType(org.openjdk.javax.lang.model.type.ArrayType type) {
        Type componentType = toType(type.getComponentType());
        return new ArrayType(componentType);
    }

    public static ClassOrInterfaceType toClassOrInterfaceType(DeclaredType type) {
        ClassOrInterfaceType classOrInterfaceType = new ClassOrInterfaceType();
        if (!type.getTypeArguments().isEmpty()) {
            classOrInterfaceType.setTypeArguments(type.getTypeArguments().stream().map(JavaParserTypesUtil::toType).filter(Objects::nonNull).collect(NodeList.toNodeList()));
        }
        if (!type.asElement().toString().isEmpty()) {
            classOrInterfaceType.setName(type.asElement().toString());
        }
        return classOrInterfaceType;
    }

    public static String getName(Type type, NeedFqnDelegate needFqnDelegate) {
        PrinterConfiguration configuration = new DefaultPrinterConfiguration();
        JavaPrettyPrinterVisitor visitor = new JavaPrettyPrinterVisitor(configuration) {
            @Override
            public void visit(SimpleName n, Void arg) {
                printOrphanCommentsBeforeThisChildNode(n);
                printComment(n.getComment(), arg);

                String identifier = n.getIdentifier();
                if (needFqnDelegate.needsFqn(identifier)) {
                    printer.print(identifier);
                } else {
                    printer.print(ActionUtil.getSimpleName(identifier));
                }
            }
        };
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter(t -> visitor, configuration);
        return prettyPrinter.print(type);
    }

    public static String getSimpleName(Type type) {
        PrinterConfiguration configuration = new DefaultPrinterConfiguration();
        JavaPrettyPrinterVisitor visitor = new JavaPrettyPrinterVisitor(configuration);
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter(t -> visitor, configuration);
        return prettyPrinter.print(type);
    }
}