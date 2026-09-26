package controller.student;

import model.service.quran.QuranLibrarySearchService;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "StudentQuranLibrarySearchServlet", urlPatterns = {"/student/api/quran-library/search"})
public class StudentQuranLibrarySearchServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentQuranLibrarySearchServlet.class.getName());

    private final QuranLibrarySearchService searchService = new QuranLibrarySearchService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, max-age=60");

        String qRaw = request.getParameter("q");
        if (qRaw == null) {
            qRaw = request.getParameter("query");
        }

        try {
            HttpResponse<String> upstream = searchService.fetchQuickRaw(qRaw);
            int code = upstream.statusCode();
            response.setStatus(code);
            response.setContentType("application/json;charset=UTF-8");
            String body = QuranLibrarySearchService.cappedBody(upstream);
            response.getWriter().write(body == null ? "" : body);
        } catch (IOException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if ("query required".equals(msg)) {
                badRequest(response, "query_required");
                return;
            }
            if ("query too long".equals(msg)) {
                badRequest(response, "query_too_long");
                return;
            }
            if ("invalid query characters".equals(msg)) {
                badRequest(response, "invalid_query");
                return;
            }
            if (msg.startsWith("search api base unresolved")) {
                badRequest(response, "search_not_configured");
                return;
            }
            LOGGER.log(Level.WARNING, "Quran Library search failed", ex);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", "load_failed");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JsonUtil.obj(err));
        }
    }

    private static void badRequest(HttpServletResponse response, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", Boolean.FALSE);
        err.put("error", code);
        response.getWriter().write(JsonUtil.obj(err));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
