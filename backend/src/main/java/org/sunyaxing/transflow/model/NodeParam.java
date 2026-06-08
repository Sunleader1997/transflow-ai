package org.sunyaxing.transflow.model;

import java.util.List;

public class NodeParam {
    private String field;
    private String label;
    private String type;
    private String defaultValue;
    private String placeholder;
    private String hint;
    private List<String> options;

    public NodeParam() {}

    public NodeParam(String field, String label, String type, String defaultValue,
                     String placeholder, String hint, List<String> options) {
        this.field = field;
        this.label = label;
        this.type = type;
        this.defaultValue = defaultValue;
        this.placeholder = placeholder;
        this.hint = hint;
        this.options = options;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
}
