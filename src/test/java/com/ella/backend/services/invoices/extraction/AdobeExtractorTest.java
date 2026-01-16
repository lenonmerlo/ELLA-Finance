package com.ella.backend.services.invoices.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ella.backend.config.AdobeOAuthProperties;

class AdobeExtractorTest {

    @Test
    void getStatus_disabled() {
        AdobeOAuthProperties props = new AdobeOAuthProperties();
        props.setEnabled(false);

        AdobeExtractor extractor = new AdobeExtractor(props, (RestTemplate) null);
        assertEquals("DISABLED", extractor.getStatus());
    }

    @Test
    void getStatus_notConfigured_whenMissingCreds() {
        AdobeOAuthProperties props = new AdobeOAuthProperties();
        props.setEnabled(true);
        props.setClientId("");
        props.setAccessToken("");

        AdobeExtractor extractor = new AdobeExtractor(props, (RestTemplate) null);
        assertEquals("NOT_CONFIGURED", extractor.getStatus());
    }

    @Test
    void getStatus_ready_whenConfigured() {
        AdobeOAuthProperties props = new AdobeOAuthProperties();
        props.setEnabled(true);
        props.setClientId("client");
        props.setAccessToken("token");

        AdobeExtractor extractor = new AdobeExtractor(props, (RestTemplate) null);
        assertEquals("READY", extractor.getStatus());
    }

    @Test
    void extract_returnsNull_whenDisabled() {
        AdobeOAuthProperties props = new AdobeOAuthProperties();
        props.setEnabled(false);

        AdobeExtractor extractor = new AdobeExtractor(props, new RestTemplate());
        assertNull(extractor.extract("%PDF-1.4".getBytes()));
    }

    @Test
    void extract_returnsNull_whenNotConfigured() {
        AdobeOAuthProperties props = new AdobeOAuthProperties();
        props.setEnabled(true);
        props.setClientId("");
        props.setAccessToken("");

        AdobeExtractor extractor = new AdobeExtractor(props, new RestTemplate());
        assertNull(extractor.extract("%PDF-1.4".getBytes()));
    }

    @Test
    void extract_returnsNull_whenRestTemplateMissing() {
        AdobeOAuthProperties props = new AdobeOAuthProperties();
        props.setEnabled(true);
        props.setClientId("client");
        props.setAccessToken("token");

        AdobeExtractor extractor = new AdobeExtractor(props, (RestTemplate) null);
        assertNull(extractor.extract("%PDF-1.4".getBytes()));
    }

    @Test
    void extract_callsAdobeApi_andReturnsRawJson() {
        AdobeOAuthProperties props = new AdobeOAuthProperties();
        props.setEnabled(true);
        props.setClientId("client-123");
        props.setAccessToken("token-abc");
        props.setApiEndpoint("https://pdf-services.adobe.io");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        String expectedJson = "{\"elements\":[{\"Text\":\"hello\"}]}";

        server.expect(requestTo("https://pdf-services.adobe.io/extract"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-abc"))
                .andExpect(header("x-api-key", "client-123"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(expectedJson, MediaType.APPLICATION_JSON));

        AdobeExtractor extractor = new AdobeExtractor(props, restTemplate);

        String result = extractor.extract("%PDF-1.4".getBytes());
        assertNotNull(result);
        assertEquals(expectedJson, result);

        server.verify();
    }
}
