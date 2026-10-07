package com.specodyssey.util;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.el.PropertyNotWritableException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSP의 ${record.prop}를 Tomcat 10.1과 11에서 똑같이 읽게 하는 EL 해석기 (2026-10-07).
 *
 * 두 버전이 record를 읽는 규칙이 달라서, 한쪽에만 맞춘 record는 다른 버전에서 그 화면 전체가 500이었다.
 *   - 10.1 (EL 5.0): record를 모른다 — BeanELResolver가 getX()/isX()만 찾는다. 구성요소의 x()는 못 읽는다.
 *   - 11   (EL 6.0): RecordELResolver가 x()만 찾는다. 계산 값으로 둔 getX()/isX()는 못 읽는다.
 * 그래서 record마다 getX()와 x()를 둘 다 두는 규칙(README)이 있었는데, 새 record가 생길 때마다 빠졌다.
 * 이 해석기는 record면 x() → getX() → isX() 순서로 찾아 버전과 상관없이 같은 값을 돌려준다.
 * 톰캣 기본 해석기들보다 앞에서 돌도록 RecordElResolverRegistrar가 앱 시작 때 등록한다.
 * record가 아니거나 맞는 메서드가 없으면 손대지 않고 다음 해석기(Bean·Map 등)에 넘긴다 — 기존 동작은 그대로다.
 */
public class RecordElResolver extends ELResolver {

    /** record 클래스 + 속성 이름 → 읽을 메서드. 없으면 NONE */
    private static final Map<String, Method> CACHE = new ConcurrentHashMap<>();
    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Method accessor = accessor(base, property);
        if (accessor == null) {
            return null;
        }
        context.setPropertyResolved(base, property);
        try {
            return accessor.invoke(base);
        } catch (InvocationTargetException e) {
            throw new ELException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new ELException(e);
        }
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        if (accessor(base, property) == null) {
            return null;
        }
        context.setPropertyResolved(base, property);
        return null; // record는 읽기 전용 — 쓸 수 있는 타입이 없다
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        if (accessor(base, property) != null) {
            context.setPropertyResolved(base, property);
            throw new PropertyNotWritableException("record는 읽기 전용입니다: " + property);
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        if (accessor(base, property) == null) {
            return false;
        }
        context.setPropertyResolved(base, property);
        return true;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return base instanceof Record ? Object.class : null;
    }

    // EL 5.0(Tomcat 10.1)에는 있고 6.0(11)에서 빠진 메서드 — @Override 없이 둬서 두 버전 모두에서 링크된다
    public Iterator<java.beans.FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    /** record면 x() → getX() → isX()(boolean) 순서로 인자 없는 공개 메서드를 찾는다. 아니면 null */
    static Method accessor(Object base, Object property) {
        if (!(base instanceof Record) || property == null) {
            return null;
        }
        String name = property.toString();
        if (name.isEmpty()) {
            return null;
        }
        Method found = CACHE.computeIfAbsent(base.getClass().getName() + "#" + name,
                key -> find(base.getClass(), name));
        return found == NONE ? null : found;
    }

    private static Method find(Class<?> type, String name) {
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String candidate : new String[] {name, "get" + capitalized, "is" + capitalized}) {
            Method method = noArgMethod(type, candidate);
            if (method != null && (!candidate.startsWith("is") || candidate.equals(name)
                    || method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                return method;
            }
        }
        return NONE;
    }

    private static Method noArgMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            if (method.getReturnType() == void.class || Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            // 중첩 record가 public이 아니면(package-private 등) 공개 메서드라도 바로 부를 수 없다 — 우리 코드라 열어 둔다
            if (!Modifier.isPublic(type.getModifiers()) || type.getEnclosingClass() != null) {
                method.trySetAccessible();
            }
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
