package com.fos.workspace.onlyoffice;

import com.fos.workspace.config.GlobalExceptionHandler;
import com.fos.workspace.onlyoffice.api.OnlyOfficeController;
import com.fos.workspace.onlyoffice.application.OnlyOfficeConfigService;
import com.fos.workspace.onlyoffice.application.OnlyOfficeDownloadService;
import com.fos.workspace.onlyoffice.application.OnlyOfficeSaveHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.HttpServletRequest;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnlyOfficeController.class)
@ContextConfiguration(classes = {
        OnlyOfficeController.class,
        GlobalExceptionHandler.class,
        OnlyOfficeControllerSecurityTest.TestSecurityConfig.class
})
@TestPropertySource(properties = "fos.security.enabled=true")
class OnlyOfficeControllerSecurityTest {

    private static final String DOCUMENT_KEY = "00000000-0000-0000-0000-000000000001_v1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OnlyOfficeConfigService configService;

    @MockBean
    private OnlyOfficeDownloadService downloadService;

    @MockBean
    private OnlyOfficeSaveHandler saveHandler;

    @MockBean
    private JwtDecoder jwtDecoder;

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable());
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/actuator/health",
                            "/actuator/info",
                            "/api/v1/onlyoffice/callback/**",
                            "/api/v1/onlyoffice/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/onlyoffice/download/**").permitAll()
                    .requestMatchers(HttpMethod.HEAD, "/api/v1/onlyoffice/download/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/api/v1/onlyoffice/download/**").permitAll()
                    .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .bearerTokenResolver(onlyOfficeAwareBearerTokenResolver())
                            .jwt(jwt -> {}));
            return http.build();
        }

        private BearerTokenResolver onlyOfficeAwareBearerTokenResolver() {
            DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
            return request -> shouldBypassResourceServer(request) ? null : delegate.resolve(request);
        }

        private boolean shouldBypassResourceServer(HttpServletRequest request) {
            String path = request.getRequestURI();
            String method = request.getMethod();
            if (path == null || method == null) {
                return false;
            }

            if (path.startsWith("/api/v1/onlyoffice/download/")) {
                return HttpMethod.GET.matches(method)
                        || HttpMethod.HEAD.matches(method)
                        || HttpMethod.OPTIONS.matches(method);
            }

            return path.startsWith("/api/v1/onlyoffice/callback/")
                    || "/api/v1/onlyoffice/health".equals(path);
        }
    }

    @Test
    void should_allow_onlyoffice_download_get_without_bearer_token() throws Exception {
        byte[] bytes = "docx".getBytes();
        when(downloadService.downloadDocument(eq(DOCUMENT_KEY), eq("signed-token")))
                .thenReturn(new OnlyOfficeDownloadService.DownloadedDocument(
                        "test.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        4L,
                        bytes));

        mockMvc.perform(get("/api/v1/onlyoffice/download/{documentKey}", DOCUMENT_KEY)
                        .param("token", "signed-token"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition", containsString("test.docx")));
    }

    @Test
    void should_ignore_non_keycloak_bearer_token_for_onlyoffice_download_get() throws Exception {
        byte[] bytes = "pdf".getBytes();
        when(downloadService.downloadDocument(eq(DOCUMENT_KEY), eq("signed-token")))
                .thenReturn(new OnlyOfficeDownloadService.DownloadedDocument(
                        "test.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        3L,
                        bytes));

        mockMvc.perform(get("/api/v1/onlyoffice/download/{documentKey}", DOCUMENT_KEY)
                        .param("token", "signed-token")
                        .header("Authorization", "Bearer onlyoffice-outbox-token"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    void should_allow_onlyoffice_download_head_without_bearer_token() throws Exception {
        when(downloadService.downloadDocument(eq(DOCUMENT_KEY), eq("signed-token")))
                .thenReturn(new OnlyOfficeDownloadService.DownloadedDocument(
                        "test.docx",
                        MediaType.APPLICATION_OCTET_STREAM_VALUE,
                        4L,
                        "docx".getBytes()));

        mockMvc.perform(head("/api/v1/onlyoffice/download/{documentKey}", DOCUMENT_KEY)
                        .param("token", "signed-token"))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", 4L));
    }

    @Test
    void should_require_normal_bearer_token_for_onlyoffice_download_post() throws Exception {
        mockMvc.perform(post("/api/v1/onlyoffice/download/{documentKey}", DOCUMENT_KEY)
                        .param("token", "signed-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(downloadService);
    }
}
