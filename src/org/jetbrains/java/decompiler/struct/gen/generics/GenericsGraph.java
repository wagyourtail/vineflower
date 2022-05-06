package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.DotExporter;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.ArrayList;
import java.util.List;

public final class GenericsGraph {
  private final VBStyleCollection<GenNode, VarType> nodes = new VBStyleCollection<>();
  private int curId = 0;

  private void addNode(GenericType type) {
    // Already seen
    if (this.nodes.containsKey(type)) {
      return;
    }

    GenNode genNode = new GenNode(++curId, type);
    nodes.addWithKey(genNode, type);

    for (VarType arg : type.getArguments()) {
      if (arg instanceof GenericType) {
        // TODO: use wildcard type to set successor
        addNode((GenericType) arg);
      }
    }
  }

  private GenNode getOrCreate(VarType type) {
    if (this.nodes.containsKey(type)) {
      return this.nodes.getWithKey(type);
    }

    GenNode genNode = new GenNode(++curId, type);
    nodes.addWithKey(genNode, type);

    return genNode;
  }

  public static GenericsGraph buildForClass(ClassesProcessor.ClassNode node, GenericsGraph graph) {
    GenericClassDescriptor signature = node.classStruct.getSignature();

    if (signature == null) {
      return graph;
    }

    GenericType genType = signature.genericType;

    graph.addNode(genType);

    for (int i = 0; i < genType.getArguments().size(); i++) {
      VarType arg = genType.getArguments().get(i);

      if (signature.fbounds.size() > i) {
        List<VarType> bounds = signature.fbounds.get(i);

        for (VarType bound : bounds) {
          graph.nodes.getWithKey(arg)
            .addSuccessor(graph.getOrCreate(bound));
        }
      }
    }

    node.classStruct.getSignature().graph = graph;

    for (StructMethod mt : node.classStruct.getMethods()) {
      // Can't inherit for static methods
      buildForMethod((mt.getAccessFlags() & CodeConstants.ACC_STATIC) == 0 ? graph.copy() : new GenericsGraph(), mt);
    }

    DotExporter.toDotFile(graph, node.classStruct, "genGraph");

    return graph;
  }

  public static GenericsGraph buildForMethod(GenericsGraph graph, StructMethod method) {
    if (method.getSignature() != null) {
      List<VarType> parameterTypes = method.getSignature().parameterTypes;

      for (int i = 0; i < parameterTypes.size(); i++) {
        VarType type = parameterTypes.get(i);
        if (type instanceof GenericType) {
          graph.addNode((GenericType) type);

          if (method.getSignature().typeParameterBounds.size() > i) {
            for (VarType bound : method.getSignature().typeParameterBounds.get(i)) {
              graph.nodes.getWithKey(type)
                .addSuccessor(graph.getOrCreate(bound));
            }
          }
        }
      }

      method.getSignature().graph = graph;
    }

    DotExporter.toDotFile(graph, method, "genGraph");

    return graph;
  }

  // to: left
  // from: right
  public boolean isAssignable(VarType to, VarType from) {
    GenNode fromNode = nodes.getWithKey(from);
    GenNode toNode = nodes.getWithKey(to);

    if (fromNode == null || toNode == null) {
      return false;
    }

    return iterateGens(fromNode, to);
  }

  private static boolean iterateGens(GenNode from, VarType to) {
    if (from.getType().equals(to)) {
      return true;
    }

    for (GenNode node : from.getSuccessors()) {
      if (iterateGens(node, to)) {
        return true;
      }
    }

    return false;
  }

  public VBStyleCollection<GenNode, VarType> getNodes() {
    return nodes;
  }

  public GenericsGraph copy() {
    GenericsGraph graph = new GenericsGraph();

    for (GenNode node : this.nodes) {
      graph.nodes.addWithKey(node.shallowCopy(), node.getType());
    }

    for (GenNode node : this.nodes) {
      for (GenNode succ : node.getSuccessors()) {
        graph.nodes.getWithKey(node.getType())
          .addSuccessor(graph.nodes.getWithKey(succ.getType()));
      }
    }

    graph.curId = this.curId;

    return graph;
  }

  public static final class GenNode {
    private final int id;
    private final VarType type;

    // Graph
    private final List<GenNode> predecessors = new ArrayList<>();
    private final List<GenNode> successors = new ArrayList<>();

    private GenNode(int id, VarType type) {
      this.id = id;
      this.type = type;
    }

    private GenNode shallowCopy() {
      return new GenNode(id, type);
    }

    public int getId() {
      return id;
    }

    public VarType getType() {
      return type;
    }

    private void addSuccessor(GenNode node) {
      successors.add(node);

      node.predecessors.add(this);
    }

    public List<GenNode> getSuccessors() {
      return successors;
    }

    public List<GenNode> getPredecessors() {
      return predecessors;
    }
  }
}
