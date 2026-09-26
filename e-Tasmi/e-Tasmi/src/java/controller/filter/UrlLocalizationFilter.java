package controller.filter;

import util.LocaleSupport;
import util.LocaleSupport.LocaleParseResult;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * SEO URL localization filter for e-Tasmi.
 *
 * <ul>
 *   <li>{@code /en/home}, {@code /ar/auth/login}, {@code /ms/student/dashboard} forward to existing servlets.</li>
 *   <li>Unprefixed GET requests receive a 302 redirect to the resolved locale prefix.</li>
 *   <li>Sets {@code currentLocale}, {@code currentDir}, and legacy {@code locale} request attributes
 *       so JSP can render {@code lang}/{@code dir} before client scripts run (no layout flash).</li>
 * </ul>
 *
 * Registered in {@code web.xml} (must run before {@link AuthFilter}).
 */
public class UrlLocalizationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (req.getDispatcherType() == DispatcherType.FORWARD) {
            chain.doFilter(request, response);
            return;
        }

        String contextPath = req.getContextPath();
        String path = req.getRequestURI().substring(contextPath.length());
        if (path.isEmpty()) {
            path = "/";
        }

        if (LocaleSupport.shouldBypassLocaleRouting(path)) {
            chain.doFilter(request, response);
            return;
        }

        LocaleParseResult parsed = LocaleSupport.parsePrefixedPath(path);
        if (parsed != null) {
            LocaleSupport.applyRequestLocale(req, resp, parsed.locale());
            req.getRequestDispatcher(parsed.strippedPath()).forward(req, resp);
            return;
        }

        String locale = LocaleSupport.resolvePreferredLocale(req);
        LocaleSupport.applyRequestLocale(req, resp, locale);

        if ("GET".equalsIgnoreCase(req.getMethod())) {
            String query = req.getQueryString();
            String target = contextPath + LocaleSupport.localizedPath(locale, path);
            if (query != null && !query.isEmpty()) {
                target += "?" + query;
            }
            resp.sendRedirect(target);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // no-op
    }
}
