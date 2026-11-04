package com.sanosysalvos.config_server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/* Servidor real en un puerto aleatorio, sin autenticación configurada (desarrollo local) */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTests {

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> get(String ruta) throws IOException, InterruptedException {
        return cliente.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void sirveLaConfiguracionDeLosCincoServicios() throws Exception {
        for (String servicio : new String[] { "bff", "discovery-server", "ms-alertas", "ms-chat", "ms-mascotas" }) {
            HttpResponse<String> respuesta = get("/" + servicio + "/default");

            assertThat(respuesta.statusCode()).as(servicio).isEqualTo(200);
            assertThat(respuesta.body()).as(servicio).contains("config/" + servicio + ".yml");
        }
    }

    @Test
    void cadaServicioListaLoQueNecesita() throws Exception {
        String mascotas = get("/ms-mascotas/default").body();
        assertThat(mascotas).contains("aws.s3.bucket-name", "aws.sns.topic-arn", "internal.api-token", "cognito.client-id");

        String alertas = get("/ms-alertas/default").body();
        assertThat(alertas).contains("aws.sns.topic-arn", "sqs.cola-contacto-url", "sqs.cola-mascota-reportada-url",
                "sqs.cola-mascota-reunificada-url", "sqs.cola-chat-aviso-url", "frontend.url", "internal.api-token",
                "ms-mascotas.base-url");

        String chat = get("/ms-chat/default").body();
        assertThat(chat).contains("chat.ticket-secret", "chat.allowed-origins", "internal.api-token", "aws.sns.topic-arn");
    }

    @Test
    void losSecretosYLasUrlsPorEntornoNoTienenValorPorDefecto() throws Exception {
        String alertas = get("/ms-alertas/default").body();
        /* si falta el secreto el servicio debe fallar al arrancar, no entrar como root sin contraseña ni apuntar a localhost */
        assertThat(alertas).contains("${DB_USER}", "${DB_PASSWORD}", "${DB_URL}", "${MS_MASCOTAS_BASE_URL}");
        assertThat(alertas).doesNotContain("${DB_USER:", "${DB_PASSWORD:", "${MS_MASCOTAS_BASE_URL:");

        for (String servicio : new String[] { "bff", "ms-alertas", "ms-chat", "ms-mascotas" }) {
            assertThat(get("/" + servicio + "/default").body()).as(servicio)
                    .contains("${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE}")
                    .doesNotContain("EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:");
        }
        assertThat(get("/ms-chat/default").body()).contains("${CHAT_ALLOWED_ORIGINS}").doesNotContain("CHAT_ALLOWED_ORIGINS:");
    }

    @Test
    void elBffYaNoTieneRutasObsoletas() throws Exception {
        String bff = get("/bff/default").body();

        assertThat(bff).doesNotContain("ms-usuarios").doesNotContain("bff.retry-after");
        assertThat(bff).contains("servicios.rutas.mascotas", "servicios.rutas.alertas", "servicios.rutas.contactos",
                "servicios.rutas.chat");
    }

    @Test
    void laConfiguracionServidaNoTraeSecretosLiterales() throws Exception {
        for (String servicio : new String[] { "bff", "discovery-server", "ms-alertas", "ms-chat", "ms-mascotas" }) {
            String cuerpo = get("/" + servicio + "/default").body();

            assertThat(cuerpo).as(servicio).doesNotContain("AKIA", "mongodb+srv://", "-----BEGIN");
        }
    }

    @Test
    void elHealthNoMuestraDetallesSinAutenticar() throws Exception {
        HttpResponse<String> respuesta = get("/actuator/health");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"status\":\"UP\"").doesNotContain("diskSpace");
    }

    @Test
    void soloSeExponenHealthEInfo() throws Exception {
        /* ojo: /actuator/env también encaja con /{aplicación}/{perfil} del Config Server, por eso se mira el índice */
        String indice = get("/actuator").body();

        assertThat(indice).contains("\"health\"", "\"info\"");
        assertThat(indice).doesNotContain("\"env\"", "\"beans\"", "\"heapdump\"", "\"configprops\"");
    }

    @Test
    void sinCredencialesConfiguradasElServidorAtiendeSinAutenticar() throws Exception {
        /* modo desarrollo local: no pide credenciales; el modo protegido se prueba en ConfigServerConAutenticacionTests */
        assertThat(get("/ms-mascotas/default").statusCode()).isEqualTo(200);
    }
}
