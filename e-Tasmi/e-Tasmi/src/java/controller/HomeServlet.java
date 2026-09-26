package controller;

import model.dao.ReportDao;
import model.dao.impl.ReportDaoJdbc;
import model.entity.ReportSummary;
import util.Db;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.RoundingMode;
import java.sql.Connection;

@WebServlet(name = "HomeServlet", urlPatterns = {"/home"})
public class HomeServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        int statStudents = 0;
        long statSessions = 0;
        Integer statCompletionPct = null;

        try (Connection connection = Db.getConnection()) {
            ReportDao reportDao = new ReportDaoJdbc();
            ReportSummary s = reportDao.loadSummary(connection);
            statStudents = (int) Math.min(Integer.MAX_VALUE, Math.max(0, s.getTotalStudents()));
            statSessions = s.getSessionsScheduled() + s.getSessionsOngoing() + s.getSessionsCompleted();
            if (s.getTotalEvaluations() > 0 && s.getAvgEvaluationScore() != null) {
                statCompletionPct = s.getAvgEvaluationScore().setScale(0, RoundingMode.HALF_UP).intValue();
                statCompletionPct = Math.max(0, Math.min(100, statCompletionPct));
            }
        } catch (Exception ignored) {
        }

        request.setAttribute("statStudents", statStudents);
        request.setAttribute("statSessions", statSessions);
        request.setAttribute("statCompletionPct", statCompletionPct);
        request.getRequestDispatcher("/home.jsp").forward(request, response);
    }
}
