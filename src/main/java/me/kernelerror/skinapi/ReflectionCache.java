package me.kernelerror.skinapi;

import java.lang.reflect.Method;

public class ReflectionCache {
    private Method updateAbilitiesMethod;
    private Method triggerHealthUpdateMethod;
    private Class<?> handleClass;
    private Object handle;

    public void setUpdateAbilitiesMethod(Method value) {
        updateAbilitiesMethod = value;
    }

    public Method getUpdateAbilitiesMethod() {
        return updateAbilitiesMethod;
    }

    public void setTriggerHealthUpdateMethod(Method value) {
        triggerHealthUpdateMethod = value;
    }

    public Method getTriggerHealthUpdateMethod() {
        return triggerHealthUpdateMethod;
    }

    public void setHandleClass(Class<?> value) {
        handleClass = value;
    }

    public Class<?> getHandleClass() {
        return handleClass;
    }

    public void setHandle(Object value) {
        handle = value;
    }

    public Object getHandle() {
        return handle;
    }
}