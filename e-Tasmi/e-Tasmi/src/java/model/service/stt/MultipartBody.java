package model.service.stt;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class MultipartBody {

    private final String boundary;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    MultipartBody() {
        this.boundary = "----eTasmiStt" + System.nanoTime();
    }

    String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    void field(String name, String value) {
        write("--" + boundary + "\r\n");
        write("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(value == null ? "" : value);
        write("\r\n");
    }

    void file(String name, String filename, byte[] bytes) {
        write("--" + boundary + "\r\n");
        write("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n");
        write("Content-Type: application/octet-stream\r\n\r\n");
        out.write(bytes, 0, bytes.length);
        write("\r\n");
    }

    byte[] finish() {
        write("--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void write(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
    }
}
