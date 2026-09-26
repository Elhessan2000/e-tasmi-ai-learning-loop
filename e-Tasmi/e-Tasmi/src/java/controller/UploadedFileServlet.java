package controller;

import util.LocalFileUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves locally stored upload files from /uploads/*
 */
@WebServlet(name = "UploadedFileServlet", urlPatterns = {"/uploads/*"})
public class UploadedFileServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String safePath = pathInfo.replace("..", "").replace("\\", "/");
        if (safePath.startsWith("/")) safePath = safePath.substring(1);

        Path file = Paths.get(LocalFileUtil.getUploadsDir()).resolve(safePath);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (!file.toAbsolutePath().startsWith(Paths.get(LocalFileUtil.getUploadsDir()).toAbsolutePath())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String contentType = Files.probeContentType(file);
        if (contentType == null) contentType = "application/octet-stream";
        response.setContentType(contentType);
        response.setContentLengthLong(Files.size(file));
        response.setHeader("Cache-Control", "public, max-age=86400");

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file, out);
        }
    }
}
