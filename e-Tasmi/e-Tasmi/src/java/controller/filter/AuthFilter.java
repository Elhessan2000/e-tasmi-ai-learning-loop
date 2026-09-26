package controller.filter;

import model.dao.InstructorDao;
import model.dao.impl.InstructorDaoJdbc;
import model.entity.Instructor;
import util.Db;

import util.LocaleSupport;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import util.JsonUtil;

@WebFilter(filterName = "AuthFilter", urlPatterns = {
        "/jsp/student/*",
        "/jsp/instructor/*",
        "/jsp/admin/*",
    "/jsp/common/*",
        "/student/*",
        "/instructor/*",
        "/admin/*"
    ,"/notifications",
        "/live/*"
}, dispatcherTypes = {DispatcherType.REQUEST, DispatcherType.FORWARD})
public class AuthFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            String pathEarly = LocaleSupport.stripLocalePrefix(
                    req.getRequestURI().substring(req.getContextPath().length()));
            if (isJsonApi(pathEarly)) {
                sendJsonUnauthenticated(resp);
                return;
            }
            resp.sendRedirect(LocaleSupport.localizedUrl(req, "/auth/login?expired=1"));
            return;
        }

        String role = (String) session.getAttribute("role");
        String path = LocaleSupport.stripLocalePrefix(
                req.getRequestURI().substring(req.getContextPath().length()));

        // Prevent direct URL access to JSPs; allow only server-side forwards.
        if (path.startsWith("/jsp/")) {
            Object forwardedFrom = req.getAttribute("javax.servlet.forward.request_uri");
            if (forwardedFrom == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }

        if (path.startsWith("/jsp/student") || path.startsWith("/student")) {
            if (!"STUDENT".equalsIgnoreCase(role)) {
                handleForbidden(req, resp);
                return;
            }
        } else if (path.startsWith("/jsp/instructor") || path.startsWith("/instructor")) {
            if (!"INSTRUCTOR".equalsIgnoreCase(role)) {
                handleForbidden(req, resp);
                return;
            }

            String verificationStatus = (String) session.getAttribute("instructorVerificationStatus");

            if (verificationStatus == null) {
                verificationStatus = tryLoadInstructorVerificationStatus(session);
                if (verificationStatus != null) {
                    session.setAttribute("instructorVerificationStatus", verificationStatus);
                }
            }

            if (verificationStatus == null || !"APPROVED".equalsIgnoreCase(verificationStatus)) {
                req.setAttribute("verificationStatus", verificationStatus == null ? "PENDING" : verificationStatus);
                req.getRequestDispatcher("/jsp/auth/pendingApproval.jsp").forward(req, resp);
                return;
            }
        } else if (path.startsWith("/jsp/admin") || path.startsWith("/admin")) {
            if (!"ADMIN".equalsIgnoreCase(role)) {
                handleForbidden(req, resp);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void handleForbidden(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = LocaleSupport.stripLocalePrefix(
                req.getRequestURI().substring(req.getContextPath().length()));
        if (isJsonApi(path) || isAjax(req)) {
            sendJsonForbidden(resp);
            return;
        }
        resp.sendRedirect(LocaleSupport.localizedUrl(req, "/dashboard?forbidden=1"));
    }

    private static boolean isJsonApi(String path) {
        return path.startsWith("/student/api/") || path.startsWith("/instructor/api/");
    }

    private static void sendJsonUnauthenticated(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", Boolean.FALSE);
        m.put("error", "unauthenticated");
        m.put("message", "Login required.");
        resp.getWriter().write(JsonUtil.obj(m));
    }

    private static void sendJsonForbidden(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", Boolean.FALSE);
        m.put("error", "forbidden");
        m.put("message", "You do not have access to this resource.");
        resp.getWriter().write(JsonUtil.obj(m));
    }

    private boolean isAjax(HttpServletRequest req) {
        String xrw = req.getHeader("X-Requested-With");
        String accept = req.getHeader("Accept");
        return "XMLHttpRequest".equalsIgnoreCase(xrw)
                || (accept != null && accept.toLowerCase().contains("application/json"));
    }

    private String tryLoadInstructorVerificationStatus(HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        if (userIdObj == null) {
            return null;
        }

        long userId;
        if (userIdObj instanceof Number) {
            userId = ((Number) userIdObj).longValue();
        } else {
            try {
                userId = Long.parseLong(String.valueOf(userIdObj));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        InstructorDao instructorDao = new InstructorDaoJdbc();
        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, userId);
            if (instructorOpt.isPresent() && instructorOpt.get().getVerificationStatus() != null) {
                return instructorOpt.get().getVerificationStatus().name();
            }
            return null;
        } catch (SQLException ex) {
            return null;
        }
    }

    @Override
    public void destroy() {
        // No-op
    }
}
