package org.example.model;

import java.util.Objects;

public final class DependencyModel implements ExpressionNode {
    public String configuration;
    public String group;
    public String name;
    public String version;
    public String classifier;
    public String extension;
    public String projectPath;
    public String catalogAlias;
    public boolean isPlatform;
    public boolean isProject;
    public boolean isEnforcedPlatform;
    public DependencyDeclarationType kind;
    public ExpressionNode notation;

    @Override
    public void print(String indent) {
        System.out.println(indent + toStructuredString());
    }

    public String toStructuredString() {
        StringBuilder sb = new StringBuilder();
        sb.append("configuration='").append(safe(configuration)).append("'");
        if (kind == DependencyDeclarationType.PROJECT
                || kind == DependencyDeclarationType.PLATFORM_PROJECT) {
            sb.append(", projectPath='").append(safe(projectPath)).append("'");
        }
        if (kind == DependencyDeclarationType.EXTERNAL_MODULE
                || kind == DependencyDeclarationType.PLATFORM_EXTERNAL
                || kind == DependencyDeclarationType.ENFORCED_PLATFORM_EXTERNAL) {
            sb.append(", group='").append(safe(group)).append("'")
                    .append(", name='").append(safe(name)).append("'")
                    .append(", version='").append(safe(version)).append("'");
        }
        if (catalogAlias != null) {
            sb.append(", catalogAlias='").append(catalogAlias).append("'");
        }
        if (classifier != null) {
            sb.append(", classifier='").append(classifier).append("'");
        }

        if (extension != null) {
            sb.append(", extension='").append(extension).append("'");
        }
        if (notation != null) {
            sb.append(", notation='").append(notation).append("'");
        }

        return (sb.toString());
    }

    private String safe(String v) {
        return v != null ? v : ""; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DependencyModel other)) return false;

        return isPlatform == other.isPlatform
                && isProject == other.isProject
                && isEnforcedPlatform == other.isEnforcedPlatform
                && Objects.equals(configuration, other.configuration)
                && Objects.equals(group, other.group)
                && Objects.equals(name, other.name)
                && Objects.equals(version, other.version)
                && Objects.equals(classifier, other.classifier)
                && Objects.equals(extension, other.extension)
                && Objects.equals(projectPath, other.projectPath)
                && Objects.equals(catalogAlias, other.catalogAlias)
                && Objects.equals(notation, other.notation)
                && kind == other.kind;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                configuration,
                group,
                name,
                version,
                classifier,
                extension,
                projectPath,
                notation,
                catalogAlias,
                kind
        );
        result = 31 * result + Boolean.hashCode(isPlatform);
        result = 31 * result + Boolean.hashCode(isProject);
        result = 31 * result + Boolean.hashCode(isEnforcedPlatform);
        return result;
    }
    @Override
    public String identityKey() {
        return "Dependency" +
                "|configuration=" + safe(configuration) +
                "|group=" + safe(group) +
                "|name=" + safe(name) +
                "|projectPath=" + safe(projectPath) +
                "|notation=" + keyOfIdentity(notation)+
                "|catalogAlias=" + safe(catalogAlias) ;
    }

    @Override
    public String contentKey() {
        return identityKey() +
                "|version=" + safe(version) +
                "|classifier=" + safe(classifier) +
                "|extension=" + safe(extension) +
                "|kind=" + safe(kind) +
                "|isPlatform=" + isPlatform +
                "|isProject=" + isProject +
                "|notationContent=" + keyOfContent(notation)+
                "|isEnforcedPlatform=" + isEnforcedPlatform;
    }

    private String safe(Object v) {
        return v != null ? v.toString().trim() : "";
    }

    @Override
    public String toString() {
        return toStructuredString();
    }
    private static String keyOfIdentity(ExpressionNode node) {
        return node == null ? "null" : node.identityKey();
    }

    private static String keyOfContent(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }


}
