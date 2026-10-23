package org.example.model;

public sealed interface ExpressionNode extends Printable
        permits BinaryExpressionNode, BlockNode, ClosureNode, ConstantNode, ConstructorCallNode, ConstructorDeclNode, DeclarationNode, DelegatedValueNode, DependencyModel, ElvisNode, GStringNode, IfNode, ImportNode, IndexAccessNode, ListNode, MapNode, MethodCallNode, PluginModel, PropertyNode, RepositoryModel, SafeCallNode, TaskActionModel, TaskModel, TaskRelationModel, TernaryExpressionNode, ThrowNode, TupleNode, VariableNode {

    String identityKey();

    String contentKey();


}
