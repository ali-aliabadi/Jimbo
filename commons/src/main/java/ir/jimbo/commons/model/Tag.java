package ir.jimbo.commons.model;

import java.util.HashMap;
import java.util.Map;

public class Tag {
    private String name;
    private String content;
    private Map<String, String> props;

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public Map<String, String> getProps() {
        return props;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setProps(Map<String, String> props) {
        this.props = props;
    }

    public Tag(String name, String content) {
        this.name = name;
        this.content = content;
        this.props = new HashMap<>();
    }

    public Tag(String name) {
        this.name = name;
        this.content = "";
        this.props = new HashMap<>();
    }

    public Tag() {
        this.name = "";
        this.content = "";
        this.props = new HashMap<>();
    }
}
