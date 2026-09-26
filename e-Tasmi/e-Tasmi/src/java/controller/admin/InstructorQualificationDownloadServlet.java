package controller.admin;

import model.dao.InstructorDao;
import model.dao.impl.InstructorDaoJdbc;
import model.entity.Instructor;
import util.CloudinaryUtil;
import util.Db;
import util.QualificationFileUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.Optional;

@WebServlet(name = "InstructorQualificationDownloadServlet", urlPatterns = {"/admin/instructors/qualification/download"})
public class InstructorQualificationDownloadServlet extends HttpServlet {
    private final InstructorDao instructorDao = new InstructorDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long instructorId;
        try {
            instructorId = Long.parseLong(request.getParameter("instructorId"));
        } catch (Exception ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid instructorId");
            return;
        }

        String qualificationFile;
        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findById(connection, instructorId);
            if (instructorOpt.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Instructor not found");
                return;
            }
            qualificationFile = instructorOpt.get().getQualificationFile();
        } catch (Exception ex) {
            throw new ServletException("Failed to load instructor qualification file", ex);
        }

        if (qualificationFile == null || qualificationFile.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No qualification file submitted");
            return;
        }
        boolean inline = "1".equals(request.getParameter("inline")) || "true".equalsIgnoreCase(request.getParameter("inline"));

        String q = qualificationFile.trim();
        if (QualificationFileUtil.isHttpUrl(q)) {
            if (!QualificationFileUtil.remoteFileAvailable(q)) {
                String signedDownloadUrl = CloudinaryUtil.buildSignedDownloadUrl(q, !inline);
                if (signedDownloadUrl == null || signedDownloadUrl.isBlank()) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Qualification file is not accessible from remote storage");
                    return;
                }
                response.sendRedirect(signedDownloadUrl);
                return;
            }
            response.sendRedirect(inline ? q : CloudinaryUtil.toAttachmentUrl(q));
            return;
        }

        Path filePath;
        try {
            filePath = Paths.get(q).toAbsolutePath().normalize();
        } catch (Exception ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid file path");
            return;
        }

        if (!QualificationFileUtil.isAllowedQualificationPath(filePath)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "File path is not allowed");
            return;
        }

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found on server");
            return;
        }

        String fileName = QualificationFileUtil.headerSafeFileName(filePath.getFileName() == null ? "qualification.pdf" : filePath.getFileName().toString());

        String contentType = null;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (Exception ignored) {
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = fileName.toLowerCase().endsWith(".pdf") ? "application/pdf" : "application/octet-stream";
        }

        response.setContentType(contentType);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", (inline ? "inline" : "attachment") + "; filename=\"" + fileName + "\"");

        long size;
        try {
            size = Files.size(filePath);
            if (size >= 0) {
                response.setContentLengthLong(size);
            }
        } catch (Exception ignored) {
        }

        try (InputStream in = Files.newInputStream(filePath); OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

}
