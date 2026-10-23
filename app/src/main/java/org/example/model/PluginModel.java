package org.example.model;

import java.util.Objects;

public final class PluginModel implements ExpressionNode{

    public String id;
    public String version;
    public boolean appliedViaApply;
    public ExpressionNode applyTarget;
    public Boolean apply;

    @Override
    public void print(String indent) {
        System.out.print(toStructuredString(indent));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PluginModel other)) return false;

        return appliedViaApply == other.appliedViaApply
                && Objects.equals(id, other.id)
                && Objects.equals(version, other.version)
                && Objects.equals(apply, other.apply)
                && Objects.equals(applyTarget, other.applyTarget);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, version, appliedViaApply);
        result = 31 * result + (applyTarget != null ? applyTarget.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return toStructuredString("").trim();
    }

    @Override
    public String identityKey() {
        String mode;
        if (appliedViaApply) {
            if (id != null) {
                mode = "apply-plugin";
            } else if (applyTarget != null) {
                mode = "apply-from";
            } else {
                mode = "apply-unknown";
            }
        } else {
            mode = "plugins-block-id";
        }

        return "Plugin" +
                "|mode=" + mode +
                "|id=" + safe(id) +
                "|targetId=" + keyOfIdentity(applyTarget);
    }

    @Override
    public String contentKey() {
        return identityKey() +
                "|version=" + safe(version) +
                "|apply=" + safe(apply) +
                "|targetContent=" + keyOfContent(applyTarget);
    }


    public String toStructuredString(String indent) {
        StringBuilder sb = new StringBuilder();

        if (!appliedViaApply) {
            if (id != null) {
                sb.append(indent).append("id '").append(id).append("'");

                if (version != null) {
                    sb.append(" version '").append(version).append("'");
                }

                if (apply != null) {
                    sb.append(" apply ").append(apply);
                }

                sb.append("\n");
            }
        }
        // applied via apply
        if (appliedViaApply) {
            if (id != null) {
                sb.append(indent).append("apply plugin: ").append(id).append("\n");
            }
            if (applyTarget != null) {
                sb.append(indent).append("apply from: ").append(applyTarget).append("\n");
            }
        }
        return sb.toString();
    }

    private String safe(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static String keyOfIdentity(ExpressionNode node) {
        return node == null ? "null" : node.identityKey();
    }

    private static String keyOfContent(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }

}
