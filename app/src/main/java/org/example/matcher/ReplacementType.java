package org.example.matcher;


public enum ReplacementType {
    NAME,
    RECEIVER,
    VALUE,
    ARGUMENT,
    ARG_COUNT,
    SEMANTIC,
    TARGET,
    OPERATOR,
    TYPE,
    ELEMENT,
    SIZE,
    BODY,
    PARAMETER,
    PARAM_COUNT,
    ENTRY_ADD,
    ENTRY_REMOVE,

    WHOLE_EXPRESSION;


    public boolean isLeafFriendly() {
        return switch (this) {
            case NAME, RECEIVER, VALUE, ARGUMENT, SEMANTIC, TARGET, OPERATOR, TYPE, PARAMETER -> true;
            default -> false;
        };
    }


}

