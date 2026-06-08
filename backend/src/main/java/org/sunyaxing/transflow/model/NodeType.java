package org.sunyaxing.transflow.model;

import java.util.List;

public enum NodeType {
    INPUT("TXT-INPUT", "KAFKA-CONSUMER", "HTTP-SERVER", "SYSLOG-INPUT", "FILE", "DIR"),
    MID("GROOVY", "TO-JSON", "IF-ELSE"),
    OUTPUT("CONSOLE", "HTTP-CLIENT", "KAFKA-PRODUCER", "SYSLOG-OUTPUT", "TXT-OUT");

    private final List<String> types;

    NodeType(String... types) {
        this.types = List.of(types);
    }

    public List<String> getTypes() { return types; }

    public static NodeType fromType(String type) {
        for (NodeType nt : values()) {
            if (nt.types.contains(type)) return nt;
        }
        return MID;
    }

    public boolean isInput() { return this == INPUT; }
    public boolean isOutput() { return this == OUTPUT; }
    public boolean isMid() { return this == MID; }
}
