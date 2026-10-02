package com.specodyssey.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Part;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 서블릿 컨테이너 없이 필터·서블릿을 단위로 돌려 보기 위한 가짜 요청/응답/세션(java.lang.reflect.Proxy).
 * 지원하지 않는 메서드는 null/0/false를 돌려준다 — 테스트가 건드리는 메서드만 구현했다.
 */
public final class FakeWeb {

    private FakeWeb() {
    }

    public static final class Session {
        public final Map<String, Object> attributes = new HashMap<>();
        public boolean invalidated;
        public int changeSessionIdCalls;
        private final HttpSession proxy;

        public Session() {
            proxy = (HttpSession) Proxy.newProxyInstance(FakeWeb.class.getClassLoader(), new Class<?>[] {HttpSession.class},
                    handler("session", (name, args) -> {
                        switch (name) {
                            case "getAttribute":
                                return attributes.get(args[0]);
                            case "setAttribute":
                                attributes.put((String) args[0], args[1]);
                                return null;
                            case "removeAttribute":
                                attributes.remove(args[0]);
                                return null;
                            case "invalidate":
                                invalidated = true;
                                attributes.clear();
                                return null;
                            case "getId":
                                return "fake-session";
                            default:
                                return NOT_HANDLED;
                        }
                    }));
        }

        public HttpSession http() {
            return proxy;
        }
    }

    public static final class Request {
        public String method = "GET";
        public String servletPath = "/";
        public String pathInfo;
        public String contextPath = "";
        public String remoteAddr = "127.0.0.1";
        public String characterEncoding;
        public String contentType;
        public final Map<String, String> params = new HashMap<>();
        public final Map<String, String> headers = new HashMap<>();
        public final Map<String, Object> attributes = new HashMap<>();
        public Session session;
        public final List<String> forwards = new ArrayList<>();
        public final Map<String, List<Part>> parts = new HashMap<>();
        public int changeSessionIdCalls;
        private final HttpServletRequest proxy;

        Request() {
            proxy = (HttpServletRequest) Proxy.newProxyInstance(FakeWeb.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class}, handler("request", (name, args) -> {
                        switch (name) {
                            case "getMethod":
                                return method;
                            case "getServletPath":
                                return servletPath;
                            case "getPathInfo":
                                return pathInfo;
                            case "getContextPath":
                                return contextPath;
                            case "getRemoteAddr":
                                return remoteAddr;
                            case "getCharacterEncoding":
                                return characterEncoding;
                            case "setCharacterEncoding":
                                characterEncoding = (String) args[0];
                                return null;
                            case "getContentType":
                                return contentType;
                            case "getParameter":
                                return params.get(args[0]);
                            case "getHeader":
                                return headers.get(args[0]);
                            case "getAttribute":
                                return attributes.get(args[0]);
                            case "setAttribute":
                                attributes.put((String) args[0], args[1]);
                                return null;
                            case "getSession":
                                boolean create = args == null || args.length == 0 || (Boolean) args[0];
                                if (session == null && create) {
                                    session = new Session();
                                }
                                return session == null ? null : session.http();
                            case "getPart":
                                List<Part> byName = parts.get(args[0]);
                                return byName == null || byName.isEmpty() ? null : byName.get(0);
                            case "getParts":
                                List<Part> all = new ArrayList<>();
                                parts.values().forEach(all::addAll);
                                return all;
                            case "changeSessionId":
                                changeSessionIdCalls++;
                                return "fake-session-2";
                            case "getRequestDispatcher":
                                String path = (String) args[0];
                                return Proxy.newProxyInstance(FakeWeb.class.getClassLoader(),
                                        new Class<?>[] {RequestDispatcher.class}, handler("dispatcher", (n, a) -> {
                                            if ("forward".equals(n) || "include".equals(n)) {
                                                forwards.add(path);
                                                return null;
                                            }
                                            return NOT_HANDLED;
                                        }));
                            default:
                                return NOT_HANDLED;
                        }
                    }));
        }

        public HttpServletRequest http() {
            return proxy;
        }

        public Request post(String path) {
            method = "POST";
            servletPath = path;
            return this;
        }

        public Request param(String name, String value) {
            params.put(name, value);
            return this;
        }

        public Request header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Request file(String fieldName, String fileName, byte[] bytes) {
            parts.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(part(fieldName, fileName, bytes));
            return this;
        }

        public Request loggedIn(Object user) {
            if (session == null) {
                session = new Session();
            }
            session.attributes.put("loginUser", user);
            return this;
        }
    }

    public static final class Response {
        public final Map<String, String> headers = new HashMap<>();
        public String redirect;
        public int errorStatus;
        public String errorMessage;
        public int status = 200;
        public String contentType;
        public final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final HttpServletResponse proxy;

        Response() {
            proxy = (HttpServletResponse) Proxy.newProxyInstance(FakeWeb.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class}, handler("response", (name, args) -> {
                        switch (name) {
                            case "setHeader":
                            case "addHeader":
                                headers.put((String) args[0], (String) args[1]);
                                return null;
                            case "getHeader":
                                return headers.get(args[0]);
                            case "sendRedirect":
                                redirect = (String) args[0];
                                return null;
                            case "sendError":
                                errorStatus = (Integer) args[0];
                                errorMessage = args.length > 1 ? (String) args[1] : null;
                                return null;
                            case "setStatus":
                                status = (Integer) args[0];
                                return null;
                            case "getStatus":
                                return status;
                            case "setContentType":
                                contentType = (String) args[0];
                                return null;
                            case "getContentType":
                                return contentType;
                            case "getOutputStream":
                                return new ServletOutputStream() {
                                    @Override
                                    public void write(int b) {
                                        body.write(b);
                                    }

                                    @Override
                                    public boolean isReady() {
                                        return true;
                                    }

                                    @Override
                                    public void setWriteListener(WriteListener listener) {
                                    }
                                };
                            default:
                                return NOT_HANDLED;
                        }
                    }));
        }

        public HttpServletResponse http() {
            return proxy;
        }
    }

    /** 체인이 불렸는지 세는 가짜 FilterChain */
    public static final class Chain {
        public final AtomicInteger calls = new AtomicInteger();

        public FilterChain chain() {
            return (req, resp) -> calls.incrementAndGet();
        }

        public boolean called() {
            return calls.get() > 0;
        }
    }

    /** 업로드 파일 파트(Part) 가짜 */
    public static Part part(String fieldName, String fileName, byte[] bytes) {
        return (Part) Proxy.newProxyInstance(FakeWeb.class.getClassLoader(), new Class<?>[] {Part.class},
                handler("part", (name, args) -> {
                    switch (name) {
                        case "getName":
                            return fieldName;
                        case "getSubmittedFileName":
                            return fileName;
                        case "getSize":
                            return (long) bytes.length;
                        case "getContentType":
                            return "application/octet-stream";
                        case "getInputStream":
                            return new ByteArrayInputStream(bytes);
                        default:
                            return NOT_HANDLED;
                    }
                }));
    }

    public static Request request() {
        return new Request();
    }

    public static Response response() {
        return new Response();
    }

    private static final Object NOT_HANDLED = new Object();

    @FunctionalInterface
    private interface Impl {
        Object call(String name, Object[] args) throws Throwable;
    }

    private static InvocationHandler handler(String what, Impl impl) {
        return (proxy, method, args) -> {
            Object result = impl.call(method.getName(), args);
            if (result != NOT_HANDLED) {
                return result;
            }
            return defaultFor(method);
        };
    }

    private static Object defaultFor(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
