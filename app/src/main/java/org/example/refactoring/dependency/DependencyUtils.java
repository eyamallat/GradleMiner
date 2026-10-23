package org.example.refactoring.dependency;

import org.example.model.*;

import java.util.List;
import java.util.Objects;

public final class DependencyUtils {
    private DependencyUtils() {
    }

    public static boolean isDependency(DSLNode n) {
        return n != null && n.semantic instanceof DependencyModel;
    }

    public static boolean sameDependencyIdentity(DSLNode a, DSLNode b) {
        if (!(a != null && b != null && a.semantic instanceof DependencyModel da && b.semantic instanceof DependencyModel db)) {
            return false;
        }
        boolean sameConfiguration = Objects.equals(
                dependencyConfigurationName(da.configuration),
                dependencyConfigurationName(db.configuration)
        );
        boolean sameKind = Objects.equals(da.kind, db.kind);

        if (!sameConfiguration || !sameKind) return false;

        if (!norm(da.projectPath).isBlank() || !norm(db.projectPath).isBlank()) {
            return Objects.equals(norm(da.projectPath), norm(db.projectPath));
        }

        if (da.notation != null || db.notation != null) {
            return sameExpression(da.notation, db.notation);
        }

        if (!norm(da.catalogAlias).isBlank() || !norm(db.catalogAlias).isBlank()) {
            return Objects.equals(norm(da.catalogAlias), norm(db.catalogAlias));
        }

        return Objects.equals(norm(da.group), norm(db.group))
                && Objects.equals(norm(da.name), norm(db.name))
                && Objects.equals(norm(da.version), norm(db.version));
    }

    public static ExpressionNode dependencyValueExpression(DependencyModel d) {
        if (d == null) return null;

        if (d.notation != null) {
            return d.notation;
        }

        if (!safe(d.catalogAlias).isBlank()) {
            return referenceExpression(d.catalogAlias);
        }

        if (!safe(d.projectPath).isBlank()) {
            return new MethodCallNode(
                    null,
                    "project",
                    List.of(new ConstantNode(d.projectPath)),
                    List.of()
            );
        }

        if (!safe(d.group).isBlank() && !safe(d.name).isBlank()) {
            StringBuilder coords = new StringBuilder();
            coords.append(d.group).append(":").append(d.name);

            if (!safe(d.version).isBlank()) {
                coords.append(":").append(d.version);
            }

            if (!safe(d.classifier).isBlank()) {
                coords.append(":").append(d.classifier);
            }

            if (!safe(d.extension).isBlank()) {
                coords.append("@").append(d.extension);
            }

            ExpressionNode value = new ConstantNode(coords.toString());

            if (d.isPlatform) {
                return new MethodCallNode(null, "platform", List.of(value), List.of());
            }

            if (d.isEnforcedPlatform) {
                return new MethodCallNode(null, "enforcedPlatform", List.of(value), List.of());
            }

            return value;
        }

        return null;
    }

    public static boolean dependencyArtifactRolePreserved(DSLNode before, DSLNode after) {
        boolean dependencyPair =
                before.type == DSLNode.Type.DEPENDENCY_DECL
                        || after.type == DSLNode.Type.DEPENDENCY_DECL;

        if (!dependencyPair) return true;

        if (!(before.semantic instanceof DependencyModel b)) return false;
        if (!(after.semantic instanceof DependencyModel a)) return false;

        if (!norm(b.projectPath).isBlank() || !norm(a.projectPath).isBlank()) {
            return Objects.equals(norm(b.projectPath), norm(a.projectPath));
        }

        if (!norm(b.catalogAlias).isBlank() || !norm(a.catalogAlias).isBlank()) {
            return Objects.equals(norm(b.catalogAlias), norm(a.catalogAlias));
        }

        if (norm(b.group).isBlank() || norm(b.name).isBlank()) return false;
        if (norm(a.group).isBlank() || norm(a.name).isBlank()) return false;

        return Objects.equals(norm(b.group), norm(a.group))
                && Objects.equals(norm(b.name), norm(a.name));
    }

    private static String dependencyConfigurationName(String configuration) {
        String name = norm(configuration);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static ExpressionNode referenceExpression(String ref) {
        if (ref == null || ref.isBlank()) return null;
        if (ref.contains(":") || ref.contains("$")) return null;

        String[] parts = ref.split("\\.");
        ExpressionNode expr = new VariableNode(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            expr = new PropertyNode(expr, new VariableNode(parts[i]));
        }

        return expr;
    }

    private static boolean sameExpression(ExpressionNode a, ExpressionNode b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.contentKey(), b.contentKey());
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String norm(String s) {
        return safe(s).replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }
}
