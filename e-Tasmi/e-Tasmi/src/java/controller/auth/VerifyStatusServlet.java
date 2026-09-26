package controller.auth;

import model.service.EmailVerificationService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@WebServlet(name = "VerifyStatusServlet", urlPatterns = {"/auth/verify-status"})
public class VerifyStatusServlet extends HttpServlet {
    private final EmailVerificationService emailVerificationService = new EmailVerificationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");

        String email = request.getParameter("email");
        Optional<Boolean> verifiedOpt = emailVerificationService.isEmailVerified(email);
        if (verifiedOpt.isEmpty()) {
            response.getWriter().write("{\"known\":false,\"verified\":false}");
            return;
        }
        boolean verified = verifiedOpt.get();
        response.getWriter().write("{\"known\":true,\"verified\":" + verified + "}");
    }
}
