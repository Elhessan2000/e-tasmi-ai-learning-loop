package util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Cloudinary uploader (no external dependencies).
 *
 * Configure via environment variables:
 * - CLOUDINARY_CLOUD_NAME
 * - CLOUDINARY_API_KEY
 * - CLOUDINARY_API_SECRET
 */
public final class CloudinaryUtil {
	private static final Pattern SECURE_URL_PATTERN = Pattern.compile("\\\"secure_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

	private CloudinaryUtil() {}

	public static boolean isConfigured() {
		return notBlank(env("CLOUDINARY_CLOUD_NAME"))
				&& notBlank(env("CLOUDINARY_API_KEY"))
				&& notBlank(env("CLOUDINARY_API_SECRET"));
	}

	public static String uploadImageAsJpg(InputStream file, String publicId, String fileName, String contentType) throws IOException {
		return upload("image", file, fileName, contentType, Map.of(
				"public_id", publicId,
				"overwrite", "true",
				"invalidate", "true",
				"format", "jpg"
		));
	}

	public static String uploadVideo(InputStream file, String publicId, String fileName, String contentType) throws IOException {
		return upload("video", file, fileName, contentType, Map.of(
				"public_id", publicId,
				"overwrite", "true",
				"invalidate", "true"
		));
	}

	public static String uploadRaw(InputStream file, String publicId, String fileName, String contentType) throws IOException {
		return upload("raw", file, fileName, contentType, Map.of(
				"public_id", publicId,
				"overwrite", "true",
				"invalidate", "true"
		));
	}

	public static String buildDeliveryUrl(String resourceType, String publicId, String format) {
		String cloud = env("CLOUDINARY_CLOUD_NAME");
		if (!notBlank(cloud)) {
			return null;
		}

		String encodedPublicId = encodePublicId(publicId);
		String base = "https://res.cloudinary.com/" + cloud + "/" + resourceType + "/upload/" + encodedPublicId;
		if (notBlank(format)) {
			return base + "." + format;
		}
		return base;
	}

	public static boolean isCloudinaryUrl(String url) {
		if (!notBlank(url)) {
			return false;
		}
		try {
			URI uri = URI.create(url.trim());
			String host = uri.getHost();
			return host != null && host.toLowerCase().contains("res.cloudinary.com");
		} catch (Exception ex) {
			return false;
		}
	}

	public static String toAttachmentUrl(String url) {
		if (!isCloudinaryUrl(url)) {
			return url;
		}
		String trimmed = url.trim();
		if (trimmed.contains("/upload/") && !trimmed.contains("/upload/fl_attachment/")) {
			return trimmed.replaceFirst("/upload/", "/upload/fl_attachment/");
		}
		return trimmed;
	}

	public static String buildSignedDownloadUrl(String url, boolean attachment) {
		if (!isConfigured() || !isCloudinaryUrl(url)) {
			return null;
		}

		ParsedAsset asset = parseDeliveryUrl(url);
		if (asset == null || !notBlank(asset.resourceType) || !notBlank(asset.type) || !notBlank(asset.publicId)) {
			return null;
		}

		String cloudName = env("CLOUDINARY_CLOUD_NAME");
		String apiKey = env("CLOUDINARY_API_KEY");
		String apiSecret = env("CLOUDINARY_API_SECRET");
		long timestamp = Instant.now().getEpochSecond();

		TreeMap<String, String> params = new TreeMap<>();
		params.put("expires_at", String.valueOf(timestamp + 300));
		params.put("public_id", asset.publicId);
		params.put("timestamp", String.valueOf(timestamp));
		params.put("type", asset.type);
		if (attachment) {
			params.put("attachment", "true");
		}

		String signature;
		try {
			signature = sign(params, apiSecret);
		} catch (IOException ex) {
			return null;
		}

		StringBuilder query = new StringBuilder();
		query.append("api_key=").append(urlEncodeForm(apiKey));
		for (var e : params.entrySet()) {
			query.append('&').append(urlEncodeForm(e.getKey())).append('=').append(urlEncodeForm(e.getValue()));
		}
		query.append("&signature=").append(urlEncodeForm(signature));

		return "https://api.cloudinary.com/v1_1/" + cloudName + "/" + asset.resourceType + "/download?" + query;
	}

	public static boolean deleteByUrl(String url) throws IOException {
		if (!isConfigured() || !isCloudinaryUrl(url)) {
			return false;
		}

		ParsedAsset asset = parseDeliveryUrl(url);
		if (asset == null || !notBlank(asset.publicId) || !notBlank(asset.resourceType)) {
			return false;
		}
		return destroy(asset.resourceType, asset.publicId);
	}

	private static String upload(String resourceType,
						 InputStream file,
						 String fileName,
						 String contentType,
						 Map<String, String> extraParams) throws IOException {
		if (!isConfigured()) {
			throw new IOException("Cloudinary is not configured");
		}

		String cloudName = env("CLOUDINARY_CLOUD_NAME");
		String apiKey = env("CLOUDINARY_API_KEY");
		String apiSecret = env("CLOUDINARY_API_SECRET");

		long timestamp = Instant.now().getEpochSecond();

		TreeMap<String, String> params = new TreeMap<>();
		params.put("timestamp", String.valueOf(timestamp));
		if (extraParams != null) {
			for (var e : extraParams.entrySet()) {
				if (e.getKey() != null && e.getValue() != null && !e.getValue().isBlank()) {
					params.put(e.getKey(), e.getValue());
				}
			}
		}

		String signature = sign(params, apiSecret);

		String endpoint = "https://api.cloudinary.com/v1_1/" + cloudName + "/" + resourceType + "/upload";
		HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setConnectTimeout(15000);
		conn.setReadTimeout(30000);

		String boundary = "----etasmiBoundary" + System.currentTimeMillis();
		conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

		try (OutputStream out = conn.getOutputStream()) {
			// api_key
			writeFormField(out, boundary, "api_key", apiKey);
			// signed params
			for (var e : params.entrySet()) {
				writeFormField(out, boundary, e.getKey(), e.getValue());
			}
			writeFormField(out, boundary, "signature", signature);

			// file
			writeFileField(out, boundary, "file", fileName == null ? "upload" : fileName,
					notBlank(contentType) ? contentType : "application/octet-stream", file);

			// end
			out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		}

		int code = conn.getResponseCode();
		String body;
		try (InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream()) {
			body = readAll(in);
		}

		if (code < 200 || code >= 300) {
			throw new IOException("Cloudinary upload failed (" + code + "): " + truncate(body, 500));
		}

		String secureUrl = extractSecureUrl(body);
		if (!notBlank(secureUrl)) {
			throw new IOException("Cloudinary upload succeeded but response had no secure_url");
		}
		return secureUrl;
	}

	private static boolean destroy(String resourceType, String publicId) throws IOException {
		String cloudName = env("CLOUDINARY_CLOUD_NAME");
		String apiKey = env("CLOUDINARY_API_KEY");
		String apiSecret = env("CLOUDINARY_API_SECRET");

		long timestamp = Instant.now().getEpochSecond();
		TreeMap<String, String> params = new TreeMap<>();
		params.put("invalidate", "true");
		params.put("public_id", publicId);
		params.put("timestamp", String.valueOf(timestamp));

		String signature = sign(params, apiSecret);
		String body = "api_key=" + urlEncodeForm(apiKey)
				+ "&invalidate=" + urlEncodeForm("true")
				+ "&public_id=" + urlEncodeForm(publicId)
				+ "&signature=" + urlEncodeForm(signature)
				+ "&timestamp=" + urlEncodeForm(String.valueOf(timestamp));

		String endpoint = "https://api.cloudinary.com/v1_1/" + cloudName + "/" + resourceType + "/destroy";
		HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setConnectTimeout(15000);
		conn.setReadTimeout(30000);
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

		try (OutputStream out = conn.getOutputStream()) {
			out.write(body.getBytes(StandardCharsets.UTF_8));
		}

		int code = conn.getResponseCode();
		String responseBody;
		try (InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream()) {
			responseBody = readAll(in);
		}

		if (code < 200 || code >= 300) {
			throw new IOException("Cloudinary delete failed (" + code + "): " + truncate(responseBody, 500));
		}
		return responseBody == null || !responseBody.toLowerCase().contains("not found");
	}

	private static String sign(TreeMap<String, String> params, String apiSecret) throws IOException {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (var e : params.entrySet()) {
			String k = e.getKey();
			String v = e.getValue();
			if (!notBlank(k) || !notBlank(v)) {
				continue;
			}
			if (!first) {
				sb.append('&');
			}
			first = false;
			sb.append(k).append('=').append(v);
		}
		sb.append(apiSecret);

		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			return toHex(digest);
		} catch (Exception ex) {
			throw new IOException("Failed to compute Cloudinary signature", ex);
		}
	}

	private static void writeFormField(OutputStream out, String boundary, String name, String value) throws IOException {
		out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
		out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
		out.write("\r\n".getBytes(StandardCharsets.UTF_8));
	}

	private static void writeFileField(OutputStream out,
							 String boundary,
							 String fieldName,
							 String fileName,
							 String contentType,
							 InputStream file) throws IOException {
		out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
		out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + sanitizeFileName(fileName) + "\"\r\n")
				.getBytes(StandardCharsets.UTF_8));
		out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));

		byte[] buf = new byte[8192];
		int read;
		while ((read = file.read(buf)) != -1) {
			out.write(buf, 0, read);
		}
		out.write("\r\n".getBytes(StandardCharsets.UTF_8));
	}

	private static String extractSecureUrl(String json) {
		if (json == null) {
			return null;
		}
		Matcher m = SECURE_URL_PATTERN.matcher(json);
		if (!m.find()) {
			return null;
		}
		String url = m.group(1);
		// Cloudinary sometimes escapes slashes
		return url.replace("\\/", "/");
	}

	private static String readAll(InputStream in) throws IOException {
		if (in == null) {
			return "";
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int read;
		while ((read = in.read(buf)) != -1) {
			baos.write(buf, 0, read);
		}
		return baos.toString(StandardCharsets.UTF_8);
	}

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16));
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}

	private static String encodePublicId(String publicId) {
		if (publicId == null) {
			return "";
		}
		String[] parts = publicId.split("/");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) sb.append('/');
			sb.append(urlEncodePathSegment(parts[i]));
		}
		return sb.toString();
	}

	private static String urlEncodePathSegment(String s) {
		try {
			return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
		} catch (Exception ex) {
			return s == null ? "" : s;
		}
	}

	private static ParsedAsset parseDeliveryUrl(String url) {
		try {
			URI uri = URI.create(url.trim());
			String path = uri.getPath();
			if (!notBlank(path)) {
				return null;
			}

			String[] rawSegments = path.split("/");
			int uploadIndex = -1;
			for (int i = 0; i < rawSegments.length; i++) {
				if ("upload".equals(rawSegments[i])) {
					uploadIndex = i;
					break;
				}
			}
			if (uploadIndex < 2) {
				return null;
			}

			String resourceType = rawSegments[uploadIndex - 1];
			String type = rawSegments[uploadIndex];
			List<String> assetSegments = new ArrayList<>();
			for (int i = uploadIndex + 1; i < rawSegments.length; i++) {
				String part = rawSegments[i];
				if (notBlank(part)) {
					assetSegments.add(part);
				}
			}
			while (!assetSegments.isEmpty() && "fl_attachment".equals(assetSegments.get(0))) {
				assetSegments.remove(0);
			}
			if (!assetSegments.isEmpty() && assetSegments.get(0).matches("v\\d+")) {
				assetSegments.remove(0);
			}
			if (assetSegments.isEmpty()) {
				return null;
			}

			String publicId = String.join("/", assetSegments);
			if ("image".equalsIgnoreCase(resourceType)) {
				publicId = stripTrailingExtension(publicId);
			}
			if (!notBlank(publicId)) {
				return null;
			}
			return new ParsedAsset(resourceType, type, publicId);
		} catch (Exception ex) {
			return null;
		}
	}

	private static String stripTrailingExtension(String publicId) {
		if (!notBlank(publicId)) {
			return publicId;
		}
		int slash = publicId.lastIndexOf('/');
		int dot = publicId.lastIndexOf('.');
		if (dot > slash) {
			return publicId.substring(0, dot);
		}
		return publicId;
	}

	private static String urlEncodeForm(String value) {
		try {
			return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
		} catch (Exception ex) {
			return value == null ? "" : value;
		}
	}

	private static String sanitizeFileName(String name) {
		if (name == null || name.isBlank()) {
			return "upload";
		}
		return name.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private static String env(String key) {
		String v = System.getenv(key);
		return v == null ? null : v.trim();
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		if (s.length() <= max) {
			return s;
		}
		return s.substring(0, max) + "...";
	}

	private static final class ParsedAsset {
		private final String resourceType;
		private final String type;
		private final String publicId;

		private ParsedAsset(String resourceType, String type, String publicId) {
			this.resourceType = resourceType;
			this.type = type;
			this.publicId = publicId;
		}
	}
}
