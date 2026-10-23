package org.example.model;

import org.jspecify.annotations.NonNull;
import java.util.Objects;

public record PropertyNode(ExpressionNode object, ExpressionNode property) implements ExpressionNode {

    @Override
    public @NonNull String toString() {
        String propertyName = (property instanceof ConstantNode(Object value))
                ? value.toString()
                : property.toString();

        return object.toString() + "." + propertyName;
    }
    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PropertyNode(ExpressionNode object1, ExpressionNode property1))) return false;

        return Objects.equals(object, object1)
                && Objects.equals(property, property1);
    }
    @Override
    public int hashCode() {
        int result = (object != null ? object.hashCode() : 0);
        result = 31 * result + (property != null ? property.hashCode() : 0);
        return result;
    }
    @Override
    public String identityKey() {
        return "Property" +
                "|object=" + keyOfIdentity(object) +
                "|property=" + keyOfIdentity(property);
    }
    @Override
    public String contentKey() {
        return "Property" +
                "|object=" + keyOfContent(object) +
                "|property=" + keyOfContent(property);
    }

    private static String keyOfIdentity(ExpressionNode node) {
        return node == null ? "null" : node.identityKey();
    }
    private static String keyOfContent(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }

}