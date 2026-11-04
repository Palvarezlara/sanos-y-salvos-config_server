package com.sanosysalvos.config_server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/* Con CONFIG_SERVER_AUTH_PASSWORD definida, todo exige credenciales salvo el health */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "config-server.auth.user=cliente-config",
        "config-server.auth.password=clave-de-prueba-con-mas-de-treinta-caracteres" })
class ConfigServerConAutenticacionTests {

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> get(String ruta, String usuario, String clave) throws IOException, InterruptedException {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).GET();
        if (usuario != null) {
            String credencial = Base64.getEncoder().encodeToString((usuario + ":" + clave).getBytes(StandardCharsets.UTF_8));
            pedido.header("Authorization", "Basic " + credencial);
        }
        return cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void sinCredencialesLaConfiguracionSeRechaza() throws Exception {
        for (String ruta : new String[] { "/ms-mascotas/default", "/ms-alertas/default", "/bff/default", "/actuator/info" }) {
            assertThat(get(ruta, null, null).statusCode()).as(ruta).isEqualTo(401);
        }
    }

    @Test
    void conCredencialesCorrectasSeSirveLaConfiguracion() throws Exception {
        HttpResponse<String> respuesta = get("/ms-mascotas/default", "cliente-config",
                "clave-de-prueba-con-mas-de-treinta-caracteres");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("config/ms-mascotas.yml");
    }

    @Test
    void conCredencialesIncorrectasSeRechaza() throws Exception {
        assertThat(get("/ms-mascotas/default", "cliente-config", "otra-clave").statusCode()).isEqualTo(401);
        assertThat(get("/ms-mascotas/default", "otro-usuario", "clave-de-prueba-con-mas-de-treinta-caracteres")
                .statusCode()).isEqualTo(401);
    }

    @Test
    void elHealthSigueAbiertoParaElHealthcheckPeroSinDetalles() throws Exception {
        HttpResponse<String> respuesta = get("/actuator/health", null, null);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"status\":\"UP\"").doesNotContain("diskSpace");
    }
}
