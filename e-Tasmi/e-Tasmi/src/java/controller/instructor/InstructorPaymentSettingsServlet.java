package controller.instructor;

import util.LocaleSupport;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Legacy route — payment configuration now lives on {@code /instructor/payments}. */
@WebServlet(name = "InstructorPaymentSettingsServlet", urlPatterns = {"/instructor/payment-settings"})
public class InstructorPaymentSettingsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.sendRedirect(LocaleSupport.localizedUrl(request, "/instructor/payments"));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.sendRedirect(LocaleSupport.localizedUrl(request, "/instructor/payments"));
    }
}
