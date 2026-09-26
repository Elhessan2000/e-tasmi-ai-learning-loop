package controller.admin;

import model.entity.Instructor;
import model.entity.InstructorVerificationStatus;
import model.service.InstructorVerificationItem;
import model.service.InstructorVerificationService;
import model.service.InstructorVerificationUpdateResult;
import util.Db;
import util.QualificationFileUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@WebServlet(
        name = "InstructorVerificationServlet",
        urlPatterns = {
                "/admin/instructors/verification",
                "/admin/instructors/pending",
                "/admin/instructors/view",
                "/admin/instructors/approve",
                "/admin/instructors/reject"
        }
)
public class InstructorVerificationServlet extends HttpServlet {
    private final InstructorVerificationService verificationService = new InstructorVerificationService();
    private final model.dao.InstructorDao instructorDao = new model.dao.impl.InstructorDaoJdbc();
    private final model.dao.UserDao userDao = new model.dao.impl.UserDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String servletPath = request.getServletPath();

        if ("/admin/instructors/pending".equals(servletPath)) {
            response.sendRedirect(request.getContextPath() + "/admin/instructors/verification?status=PENDING");
            return;
        }

        if ("/admin/instructors/view".equals(servletPath)) {
            Optional<InstructorVerificationItem> selected = loadItem(request.getParameter("id"));
            if (selected.isEmpty() || selected.get().getInstructor().getVerificationStatus() != InstructorVerificationStatus.PENDING) {
                response.sendRedirect(request.getContextPath() + "/admin/instructors/verification?status=PENDING&selected=0");
                return;
            }

            request.setAttribute("status", InstructorVerificationStatus.PENDING.name());
            request.setAttribute("items", verificationService.listPending());
            request.setAttribute("selectedItem", selected.get());
            request.getRequestDispatcher("/jsp/admin/instructor_verification.jsp").forward(request, response);
            return;
        }

        request.setAttribute("status", InstructorVerificationStatus.PENDING.name());
        request.setAttribute("items", verificationService.listPending());
        request.getRequestDispatcher("/jsp/admin/instructor_verification.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String servletPath = request.getServletPath();

        if ("/admin/instructors/approve".equals(servletPath) || "/admin/instructors/reject".equals(servletPath)) {
            String action = "/admin/instructors/approve".equals(servletPath) ? "approve" : "reject";
            handleStatusUpdate(request, response, action);
            return;
        }

        if ("/admin/instructors/verification".equals(servletPath)) {
            handleStatusUpdate(request, response, request.getParameter("action"));
            return;
        }

        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    private void handleStatusUpdate(HttpServletRequest request, HttpServletResponse response, String action) throws IOException {
        long instructorId = parseLong(firstNonBlank(request.getParameter("instructorId"), request.getParameter("id")));
        long actorUserId = readUserId(request.getSession(false));
        InstructorVerificationUpdateResult result = InstructorVerificationUpdateResult.failure("Invalid verification request.");
        if (instructorId > 0) {
            if ("approve".equalsIgnoreCase(action)) {
                result = verificationService.approve(actorUserId, instructorId);
            } else if ("reject".equalsIgnoreCase(action)) {
                result = verificationService.reject(actorUserId, instructorId);
            }
        }

        response.sendRedirect(request.getContextPath() + "/admin/instructors/verification?status=PENDING"
                + "&updated=" + (result.isSuccess() ? "1" : "0")
                + "&message=" + encodeMessage(result.getMessage()));
    }

    private Optional<InstructorVerificationItem> loadItem(String idParam) {
        long instructorId = parseLong(idParam);
        if (instructorId <= 0) {
            return Optional.empty();
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findById(connection, instructorId);
            if (instructorOpt.isEmpty()) {
                return Optional.empty();
            }

            Instructor instructor = instructorOpt.get();
            return userDao.findById(connection, instructor.getUserId())
                    .map(user -> new InstructorVerificationItem(
                            instructor,
                            user,
                            QualificationFileUtil.isAvailable(instructor.getQualificationFile())
                    ));
        } catch (SQLException ex) {
            return Optional.empty();
        }
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String encodeMessage(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace(" ", "+");
    }

    private long readUserId(HttpSession session) {
        if (session == null) {
            return 0L;
        }
        Object userIdObj = session.getAttribute("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }
        if (userIdObj != null) {
            try {
                return Long.parseLong(String.valueOf(userIdObj));
            } catch (NumberFormatException ex) {
                return 0L;
            }
        }
        return 0L;
    }
}
