/*
 */
package com.common.offload;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 *
 */
public class OffloadFilter implements Filter {

    private boolean dryRun;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        dryRun = Boolean.TRUE.toString().equals(filterConfig.getInitParameter("dry-run"));
        LOGGER.log(Level.INFO, "OffloadFilter init: dryRun={0}", new Object[]{dryRun});
    }

    @Override
    public void doFilter(ServletRequest requestParam, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        LOGGER.finer("OffloadFilter doFilter");
        try {
            if (!dryRun && requestParam instanceof HttpServletRequest) {
                MyRequest wrapper = new MyRequest((HttpServletRequest) requestParam);
                wrapper.addHeader("My-Custom-Header", "val1");
                wrapper.addHeader("My-Custom-Header", "val2");
                requestParam = wrapper;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        chain.doFilter(requestParam, response);
    }

    @Override
    public void destroy() {
        LOGGER.info("OffloadFilter destroy");
    }

    private static final Logger LOGGER = Logger.getLogger(OffloadFilter.class.getName());
}

class MyRequest extends HttpServletRequestWrapper {

    protected final LinkedHashMap<String, List<String>> headers = new LinkedHashMap<>();

    protected final HashSet<String> removedHeaders = new HashSet<>();

    protected final boolean supportsGetHeaderNames;

    public MyRequest(HttpServletRequest request) {
        super(request);

        Enumeration<String> names = request.getHeaderNames();
        supportsGetHeaderNames = names != null;
        if (supportsGetHeaderNames) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                Enumeration<String> vals = request.getHeaders(name);
                headers.put(name, vals == null ? null : Collections.list(vals));
            }
        }
    }

    public void removeHeaderValues(String name) {
        headers.remove(name);
        removedHeaders.add(name);
    }

    public void addHeader(String name, String value) {
        removedHeaders.remove(name);
        List<String> x = headers.get(name);
        if (x == null) {
            x = new ArrayList<>();
            headers.put(name, x);
        }
        x.add(value);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return supportsGetHeaderNames ? Collections.enumeration(headers.keySet()) : null;
    }

    @Override
    public String getHeader(String name) {
        Enumeration<String> x = getHeaders(name);
        return (x != null && x.hasMoreElements()) ? x.nextElement() : null;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> x = headers.get(name);
        if (x != null) {
            return Collections.enumeration(x);
        }
        if (removedHeaders.contains(name)) {
            return Collections.emptyEnumeration();
        }
        return super.getHeaders(name);
    }

    @Override
    public long getDateHeader(String name) {
        String value = getHeader(name);
        if (value == null) {
            return -1L;
        }

        long result = internalParseDate(value, asdasd());
        if (result != -1L) {
            return result;
        }
        throw new IllegalArgumentException(value);
    }

    @Override
    public int getIntHeader(String name) {
        String value = getHeader(name);
        if (value == null) {
            return -1;
        }
        return Integer.parseInt(value);
    }

    private static long internalParseDate(String value, DateFormat[] formats) {
        for (int i = 0; i < formats.length; i++) {
            try {
                return formats[i].parse(value).getTime();
            } catch (ParseException ignore) {
            }
        }
        return -1L;
    }

    protected static final ThreadLocal<SimpleDateFormat[]> DF_TLS = new ThreadLocal<>();

    protected static SimpleDateFormat[] asdasd() {
        SimpleDateFormat[] formats = DF_TLS.get();
        if (formats == null) {
            formats = new SimpleDateFormat[]{
                new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
                new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
                new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
            };
            TimeZone tz = TimeZone.getTimeZone("GMT");
            for (SimpleDateFormat f : formats) {
                f.setTimeZone(tz);
            }
        }
        return formats;
    }
}
