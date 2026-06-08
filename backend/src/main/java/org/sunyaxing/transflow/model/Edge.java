package org.sunyaxing.transflow.model;

public class Edge {
    private String id;
    private String source;
    private String target;
    private String sourceHandle;
    private String targetHandle;

    public Edge() {}

    public Edge(String id, String source, String target, String sourceHandle, String targetHandle) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.sourceHandle = sourceHandle;
        this.targetHandle = targetHandle;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getSourceHandle() { return sourceHandle; }
    public void setSourceHandle(String sourceHandle) { this.sourceHandle = sourceHandle; }
    public String getTargetHandle() { return targetHandle; }
    public void setTargetHandle(String targetHandle) { this.targetHandle = targetHandle; }
}
