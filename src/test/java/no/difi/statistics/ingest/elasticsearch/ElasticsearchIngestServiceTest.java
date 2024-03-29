package no.difi.statistics.ingest.elasticsearch;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import no.difi.statistics.InndataAPI;
import no.difi.statistics.api.IngestResponse;
import no.difi.statistics.elasticsearch.Client;
import no.difi.statistics.elasticsearch.IdResolver;
import no.difi.statistics.elasticsearch.config.ElasticsearchConfig;
import no.difi.statistics.model.TimeSeriesDefinition;
import no.difi.statistics.model.TimeSeriesPoint;
import no.difi.statistics.test.utils.ElasticsearchHelper;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Collections.singletonList;
import static no.difi.statistics.api.IngestResponse.Status.Ok;
import static no.difi.statistics.elasticsearch.IndexNameResolver.resolveIndexName;
import static no.difi.statistics.model.TimeSeriesDefinition.builder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Komponent- og (delvis) integrasjonstest av inndata-tjenesten. Integrasjon mot <code>elasticsearch</code>-tjenesten
 * verifiseres, mens <code>authenticate</code>-tjenesten mockes.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = {InndataAPI.class, ElasticsearchConfig.class}, initializers = ElasticsearchIngestServiceTest.Initializer.class)
@TestPropertySource(properties = {"file.base.difi-statistikk=src/test/resources/apikey"})
@ActiveProfiles("test")
@WireMockTest(httpPort = 8888)
@Testcontainers
public class ElasticsearchIngestServiceTest {

    private static final String ELASTICSEARCH_VERSION = "7.17.2";
    @Container
    public static ElasticsearchContainer container = new ElasticsearchContainer(
            DockerImageName
                    .parse("docker.elastic.co/elasticsearch/elasticsearch")
                    .withTag(ELASTICSEARCH_VERSION));

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of(
                    "no.difi.statistics.elasticsearch.host=" + container.getHost(),
                    "no.difi.statistics.elasticsearch.port=" + container.getFirstMappedPort()
            ).applyTo(applicationContext.getEnvironment());
        }

    }

    @BeforeAll
    static void setUp() {
        container.start();
        assertTrue(container.isRunning());
        jwk = createKey();
    }

    @AfterAll
    static void tearDown() {
        container.stop();
    }

    private final ZonedDateTime now = ZonedDateTime.of(2021, 3, 3, 0, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private Client client;
    private ElasticsearchHelper elasticsearchHelper;
    private final String owner = "123456789"; // Not a valid orgno

    private static final String wellKnown = "{\"issuer\":\"http://localhost:8888/idporten-oidc-provider/\",\"authorization_endpoint\":\"http://localhost:8888/idporten-oidc-provider/authorize\",\"pushed_authorization_request_endpoint\":\"http://localhost:8888/idporten-oidc-provider/par\",\"token_endpoint\":\"http://localhost:8888/idporten-oidc-provider/token\",\"end_session_endpoint\":\"http://localhost:8888/idporten-oidc-provider/endsession\",\"revocation_endpoint\":\"http://localhost:8888/idporten-oidc-provider/revoke\",\"jwks_uri\":\"http://localhost:8888/idporten-oidc-provider/jwk\",\"response_types_supported\":[\"code\",\"id_token\",\"id_token token\",\"token\"],\"response_modes_supported\":[\"query\",\"form_post\",\"fragment\"],\"subject_types_supported\":[\"pairwise\"],\"id_token_signing_alg_values_supported\":[\"RS256\"],\"code_challenge_methods_supported\":[\"S256\"],\"userinfo_endpoint\":\"http://localhost:8888/idporten-oidc-provider/userinfo\",\"scopes_supported\":[\"openid\",\"profile\"],\"ui_locales_supported\":[\"nb\",\"nn\",\"en\",\"se\"],\"acr_values_supported\":[\"Level3\",\"Level4\"],\"frontchannel_logout_supported\":true,\"frontchannel_logout_session_supported\":true,\"introspection_endpoint\":\"http://localhost:8888/idporten-oidc-provider/tokeninfo\",\"token_endpoint_auth_methods_supported\":[\"client_secret_post\",\"client_secret_basic\",\"private_key_jwt\",\"none\"],\"request_parameter_supported\":true,\"request_uri_parameter_supported\":false,\"request_object_signing_alg_values_supported\":[\"RS256\",\"RS384\",\"RS512\"]}";

    private static final String kid = "mykey10";
    private static RSAKey jwk;

    @BeforeEach
    public void setupMaskinporten() {
        elasticsearchHelper = new ElasticsearchHelper(client);
        elasticsearchHelper.waitForGreenStatus();

        stubFor(any(urlMatching(".*well-known.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(wellKnown)));
        stubFor(any(urlMatching(".*jwk.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[" + jwk.toJSONString() + "]}")));
    }

    private static RSAKey createKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(kid)
                    .generate();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to create RSAKey", e);
        }
    }

    private String signJwt(JWTClaimsSet claims) {
        try {
            JWSSigner signer = new RSASSASigner(jwk.toPrivateKey());
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(kid)
                    .build();
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }


    private JWTClaimsSet createJWTClaimsSet() {
        final HashMap<String, String> consumer = new HashMap<>();
        consumer.put("authority", "iso6523-actorid-upis");
        consumer.put("ID", "0192:" + owner);

        return new JWTClaimsSet.Builder()
                .issuer("http://localhost:8888/idporten-oidc-provider/") // must match issuer in .well-known/openid-configuration in stub
                .claim("consumer", consumer)
                .claim("scope", "digdir:statistikk.skriv")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plusMillis(120000).toEpochMilli()))
                .build();

    }

    @AfterEach
    public void cleanup() {
        elasticsearchHelper.clear();
    }

    @Test
    public void whenBulkIngestingPointsThenAllPointsAreIngested() {
        List<TimeSeriesPoint> points = newArrayList(
                point().timestamp(now).measurement("aMeasurement", 10546L).build(),
                point().timestamp(now.plusMinutes(1)).measurement("aMeasurement", 346346L).build(),
                point().timestamp(now.plusMinutes(2)).measurement("aMeasurement", 786543L).build()
        );
        TimeSeriesDefinition seriesDefinition = seriesDefinition().name("series").minutes().owner(owner);
        ResponseEntity<IngestResponse> response = ingest(seriesDefinition, points.get(0), points.get(1), points.get(2));
        Assertions.assertEquals(3, Objects.requireNonNull(response.getBody()).getStatuses().size());
        for (IngestResponse.Status status : response.getBody().getStatuses())
            Assertions.assertEquals(Ok, status);
        assertIngested(seriesDefinition, points, response.getBody());
    }

    @Test
    public void whenBulkIngestingUpdateOfPointsThenAllPointsAreIngestedAndLastUpdatedStored() {
        TimeSeriesPoint point1 = point().timestamp(now).measurement("aMeasurement", 103L).build();
        TimeSeriesPoint updateOfPoint1 = point().timestamp(now).measurement("aMeasurement", 2354L).build();
        TimeSeriesPoint point2 = point().timestamp(now.plusMinutes(1)).measurement("aMeasurement", 567543L).build();
        TimeSeriesDefinition seriesDefinition = seriesDefinition().name("series").minutes().owner(owner);
        ResponseEntity<IngestResponse> response = ingest(seriesDefinition, point1, updateOfPoint1, point2);
        Assertions.assertEquals(3, Objects.requireNonNull(response.getBody()).getStatuses().size());
        assertIngested(seriesDefinition, 0, updateOfPoint1, response.getBody());
        assertIngested(seriesDefinition, 1, point2, response.getBody());
    }

    @Test
    public void whenIngestingUpdatePointThenIngestedLastUpdated() {
        TimeSeriesPoint point1 = point().timestamp(now).measurement("aMeasurement", 103L).build();
        TimeSeriesPoint updateOfPoint1 = point().timestamp(now).measurement("aMeasurement", 2354L).build();
        TimeSeriesPoint point2 = point().timestamp(now.plusMinutes(1)).measurement("aMeasurement", 567543L).build();
        TimeSeriesDefinition seriesDefinition = seriesDefinition().name("series").minutes().owner(owner);
        ResponseEntity<IngestResponse> response1 = ingest(seriesDefinition, point1);
        ResponseEntity<IngestResponse> response2 = ingest(seriesDefinition, updateOfPoint1);
        ResponseEntity<IngestResponse> response3 = ingest(seriesDefinition, point2);
        Assertions.assertEquals(Ok, Objects.requireNonNull(response1.getBody()).getStatuses().get(0));
        Assertions.assertEquals(Ok, Objects.requireNonNull(response2.getBody()).getStatuses().get(0));
        Assertions.assertEquals(Ok, Objects.requireNonNull(response3.getBody()).getStatuses().get(0));
        assertIngested(seriesDefinition, updateOfPoint1);
        assertIngested(seriesDefinition, point2);
    }

    @Test
    public void whenIngestingTwoPointsWithSameTimestampAndDifferentCategoriesThenBothAreIngested() {
        TimeSeriesPoint point = point().timestamp(now).category("category1", "abc").category("category2", "def").measurement("aMeasurement", 103L).build();
        TimeSeriesPoint pointWithDifferentCategory = point().timestamp(now).category("category1", "abc").measurement("aMeasurement", 2354L).build();
        TimeSeriesDefinition seriesDefinition = seriesDefinition().name("series").minutes().owner(owner);
        ResponseEntity<IngestResponse> response1 = ingest(seriesDefinition, point);
        ResponseEntity<IngestResponse> response2 = ingest(seriesDefinition, pointWithDifferentCategory);
        assertIngested(seriesDefinition, 0, point, Objects.requireNonNull(response1.getBody()));
        assertIngested(seriesDefinition, 0, pointWithDifferentCategory, Objects.requireNonNull(response2.getBody()));
    }

    @Test
    public void whenIngestingTwoPointsWithSameTimestampAndSameCategoriesThenLastPointIsIngested() {
        TimeSeriesPoint point1 = point().timestamp(now).category("category", "abc").measurement("aMeasurement", 103L).build();
        TimeSeriesPoint updateOfPoint1 = point().timestamp(now).category("category", "abc").measurement("aMeasurement", 2354L).build();
        TimeSeriesDefinition seriesDefinition = seriesDefinition().name("series").minutes().owner(owner);
        ResponseEntity<IngestResponse> response1 = ingest(seriesDefinition, point1);
        assertIngested(seriesDefinition, 0, point1, Objects.requireNonNull(response1.getBody()));
        ResponseEntity<IngestResponse> response2 = ingest(seriesDefinition, updateOfPoint1);
        assertIngested(seriesDefinition, 0, updateOfPoint1, Objects.requireNonNull(response2.getBody()));
    }

    @Test
    public void whenIngestingAPointThenProperlyNamedIndexIsCreated() {
        TimeSeriesDefinition seriesDefinition = seriesDefinition().name("series").minutes().owner(owner);
        ResponseEntity<IngestResponse> response =
                ingest(seriesDefinition, point().timestamp(now).measurement("aMeasurement", 103L).build());
        Assertions.assertEquals(Ok, Objects.requireNonNull(response.getBody()).getStatuses().get(0));
        Assertions.assertEquals(format("%s@%s@minute%d", owner, seriesDefinition.getName(), now.getYear()), returnFirstNonGeoIpIndex(elasticsearchHelper.indices()));
    }

    private String returnFirstNonGeoIpIndex(String[] indices) {
        for (String index : indices) {
            if (!index.contains("geoip")) {
                return index;
            }
        }

        throw new IllegalArgumentException("No indices without geoip exists.");
    }

    @Test
    public void whenIngestingUpdateOnTheHourPointThenPointIsIngested() {
        int addMinute = now.getMinute() == 59 ? -1 : 1;
        TimeSeriesPoint point1 = point().timestamp(now).measurement("aMeasurement", 105L).build();
        TimeSeriesPoint pointSameHourAsFirst = point().timestamp(now.plusMinutes(addMinute)).measurement("aMeasurement", 108L).build();
        TimeSeriesPoint pointNextHour = point().timestamp(now.plusHours(1)).measurement("aMeasurement", 115L).build();
        TimeSeriesDefinition seriesDefinition = seriesDefinition().name("series").hours().owner(owner);
        ResponseEntity<IngestResponse> response1 = ingest(seriesDefinition, point1);
        ResponseEntity<IngestResponse> response3 = ingest(seriesDefinition, pointSameHourAsFirst);
        ResponseEntity<IngestResponse> response4 = ingest(seriesDefinition, pointNextHour);
        Assertions.assertEquals(Ok, Objects.requireNonNull(response1.getBody()).getStatuses().get(0));
        Assertions.assertEquals(Ok, Objects.requireNonNull(response3.getBody()).getStatuses().get(0));
        Assertions.assertEquals(Ok, Objects.requireNonNull(response4.getBody()).getStatuses().get(0));
        assertIngestedHour(seriesDefinition, pointSameHourAsFirst);
        assertIngestedHour(seriesDefinition, pointNextHour);
    }

    @Test
    public void givenASeriesWhenRequestingLastPointThenLastPointIsReturned() throws JSONException {
        List<TimeSeriesPoint> points = newArrayList(
                point().timestamp(now).measurement("aMeasurement", 10546L).build(),
                point().timestamp(now.plusMinutes(1)).measurement("aMeasurement", 346346L).build(),
                point().timestamp(now.plusMinutes(2)).measurement("aMeasurement", 786543L).build()
        );
        TimeSeriesDefinition seriesDefinition = seriesDefinition().name("series").minutes().owner(owner);
        final ResponseEntity<IngestResponse> ingest = ingest(seriesDefinition, points.get(0), points.get(1), points.get(2));
        Assertions.assertEquals(HttpStatus.OK, ingest.getStatusCode());
        elasticsearchHelper.refresh();
        JSONObject lastPoint = new JSONObject(last("series").getBody());
        Assertions.assertEquals(now.plusMinutes(2).format(ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")), lastPoint.get("timestamp"));
    }

    @Test
    public void givenNoSeriesWhenRequestingLastPointThenNothingIsReturned() {
        Assertions.assertNull(last("series").getBody());
    }

    private void assertIngested(TimeSeriesDefinition seriesDefinition, List<TimeSeriesPoint> points, IngestResponse response) {
        elasticsearchHelper.refresh();
        for (int i = 0; i < points.size(); i++)
            assertIngested(seriesDefinition, i, points.get(i), response);
        Assertions.assertEquals(points.size(), elasticsearchHelper.search(singletonList("*"), now.minusDays(1), now.plusDays(1)).getHits().getTotalHits().value);
    }

    private void assertIngested(TimeSeriesDefinition seriesDefinition, int index, TimeSeriesPoint point, IngestResponse response) {
        Assertions.assertEquals(Ok, response.getStatuses().get(index));
        assertIngested(seriesDefinition, point);
    }

    private void assertIngested(TimeSeriesDefinition seriesDefinition, TimeSeriesPoint point) {
        String id = IdResolver.id(point, seriesDefinition);
        Assertions.assertEquals(point.getMeasurement("aMeasurement").get(), elasticsearchHelper.get(
                resolveIndexName()
                        .seriesDefinition(builder().name("series").minutes().owner(owner))
                        .at(point.getTimestamp())
                        .single(),
                id,
                "aMeasurement"
        ));
    }

    private void assertIngestedHour(TimeSeriesDefinition seriesDefinition, TimeSeriesPoint point) {
        String id = IdResolver.id(point, seriesDefinition);
        Assertions.assertEquals(point.getMeasurement("aMeasurement").get(), elasticsearchHelper.get(
                resolveIndexName()
                        .seriesDefinition(builder().name("series").hours().owner(owner))
                        .at(point.getTimestamp())
                        .single(),
                id,
                "aMeasurement"
        ));
    }

    private TimeSeriesPoint.Builder point() {
        return TimeSeriesPoint.builder();
    }

    private TimeSeriesDefinition.NameEntry seriesDefinition() {
        return TimeSeriesDefinition.builder();
    }

    private ResponseEntity<IngestResponse> ingest(TimeSeriesDefinition seriesDefinition, TimeSeriesPoint... points) {
        return restTemplate.postForEntity(
                "/{owner}/{seriesName}/{distance}",
                request(points),
                IngestResponse.class,
                seriesDefinition.getOwner(),
                seriesDefinition.getName(),
                seriesDefinition.getDistance()
        );
    }

    private ResponseEntity<String> last(String series) {
        return restTemplate.getForEntity(
                "/{owner}/{seriesName}/minutes/last",
                String.class,
                owner,
                series
        );
    }

    private <T> HttpEntity<T> request(T entity) {
        final String token = signJwt(createJWTClaimsSet());
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        return new HttpEntity<>(
                entity,
                headers
        );
    }

}
