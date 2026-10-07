package com.alertamujer.backend.notification.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** FCM HTTP v1 adapter with the required five-second timeout and no retries. */
@Component
class FirebaseFcmClient implements FcmClient {
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private final String credentialsPath;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    @Autowired
    FirebaseFcmClient(@Value("${FCM_CREDENTIALS_PATH:}") String credentialsPath, ObjectMapper objectMapper) {
        this(credentialsPath, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    FirebaseFcmClient(String credentialsPath, ObjectMapper objectMapper, HttpClient client) {
        this.credentialsPath = credentialsPath; this.objectMapper = objectMapper; this.client = client;
    }

    @Override
    public FcmResult send(FcmNotification notification) {
        Credentials credentials = credentials();
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("message", Map.of(
                    "token", notification.token(),
                    "notification", Map.of("title", "Alerta SOS", "body", notification.message()),
                    "data", Map.of("emergencyId", notification.emergencyId().toString(),
                            "latitude", notification.latitude().toPlainString(),
                            "longitude", notification.longitude().toPlainString()))));
        } catch (Exception exception) {
            throw new FcmUnavailableBeforeSendException();
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/"
                + credentials.projectId() + "/messages:send"))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + credentials.accessToken())
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return FcmResult.sent("FCM_ACCEPTED");
            if (response.body().contains("UNREGISTERED") || response.body().contains("INVALID_ARGUMENT")) {
                return FcmResult.invalidToken("FCM_" + response.statusCode());
            }
            return FcmResult.failed("FCM_" + response.statusCode());
        } catch (java.net.http.HttpTimeoutException exception) {
            return FcmResult.failed("TIMEOUT");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return FcmResult.failed("INTERRUPTED");
        } catch (IOException exception) {
            return FcmResult.failed("FCM_IO_ERROR");
        }
    }

    private Credentials credentials() {
        if (credentialsPath == null || credentialsPath.isBlank()) throw new FcmUnavailableBeforeSendException();
        try (InputStream source = Files.newInputStream(Path.of(credentialsPath))) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(source).createScoped(FCM_SCOPE);
            if (!(credentials instanceof ServiceAccountCredentials serviceAccount)) throw new FcmUnavailableBeforeSendException();
            credentials.refreshIfExpired();
            if (credentials.getAccessToken() == null || serviceAccount.getProjectId() == null) {
                throw new FcmUnavailableBeforeSendException();
            }
            return new Credentials(serviceAccount.getProjectId(), credentials.getAccessToken().getTokenValue());
        } catch (IOException | IllegalArgumentException exception) {
            throw new FcmUnavailableBeforeSendException();
        }
    }

    private record Credentials(String projectId, String accessToken) { }
}
