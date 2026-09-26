package controller.admin;

import model.service.AuditLogService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@WebServlet(name = "AdminAuditLogsServlet", urlPatterns = {"/admin/logs"})
public class AdminAuditLogsServlet extends HttpServlet {
    private final AuditLogService auditLogService = new AuditLogService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = pickFilterString(request, "filterAction", "action");
        String role = pickFilterString(request, "filterRole", "role");
        String dateFrom = normalizeDate(trimToNull(request.getParameter("dateFrom")));
        String dateTo = normalizeDate(trimToNull(request.getParameter("dateTo")));
        if (dateFrom != null && dateTo != null && dateFrom.compareTo(dateTo) > 0) {
            String t = dateFrom;
            dateFrom = dateTo;
            dateTo = t;
        }

        request.setAttribute("activeMenu", "logs");
        request.setAttribute("filterAction", action);
        request.setAttribute("filterRole", role);
        request.setAttribute("dateFrom", dateFrom);
        request.setAttribute("dateTo", dateTo);
        if (request.getParameter("dateFrom") != null && !request.getParameter("dateFrom").isBlank() && dateFrom == null) {
            request.setAttribute("error", "Date From filter was ignored because it was invalid.");
        } else if (request.getParameter("dateTo") != null && !request.getParameter("dateTo").isBlank() && dateTo == null) {
            request.setAttribute("error", "Date To filter was ignored because it was invalid.");
        }
        request.setAttribute("logs", auditLogService.listLogs(action, role, dateFrom, dateTo));
        request.getRequestDispatcher("/jsp/admin/logs.jsp").forward(request, response);
    }

    private String pickFilterString(HttpServletRequest request, String primary, String legacy) {
        String a = trimToNull(request.getParameter(primary));
        if (a != null) {
            return a;
        }
        return trimToNull(request.getParameter(legacy));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
