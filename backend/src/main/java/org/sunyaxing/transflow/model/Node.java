package org.sunyaxing.transflow.model;

import java.util.Map;

public class Node {
    private String id;
    private String type;
    private NodeType nodeType;
    private String label;
    private Position position;
    private Map<String, Object> config;
    private NodeStatus status;

    public Node() {}

    public Node(String id, String type, String label, Position position, Map<String, Object> config) {
        this.id = id;
        this.type = type;
        this.nodeType = NodeType.fromType(type);
        this.label = label;
        this.position = position;
        this.config = config;
        this.status = new NodeStatus();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; this.nodeType = NodeType.fromType(type); }
    public NodeType getNodeType() { return nodeType; }
    public void setNodeType(NodeType nodeType) { this.nodeType = nodeType; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Position getPosition() { return position; }
    public void setPosition(Position position) { this.position = position; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }
}
