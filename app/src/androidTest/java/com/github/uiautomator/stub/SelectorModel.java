package com.github.uiautomator.stub;

public class SelectorModel {
    private String _text;
    private String _className;
    private String _packageName;
    private String _description;
    private String _resourceId;

    public SelectorModel() {

    }
    public String getText() {
        return _text;
    }

    public void setText(String text) {
        this._text = text;
    }

    public String getClassName() {
        return _className;
    }

    public void setClassName(String className) {
        this._className = className;
    }

    public String getPackageName() {
        return _packageName;
    }

    public void setPackageName(String packageName) {
        this._packageName = packageName;
    }

    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        this._description = description;
    }

    public String getResourceId() {
        return _resourceId;
    }

    public void setResourceId(String resourceId) {
        this._resourceId = resourceId;
    }
}
