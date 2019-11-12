package uk.gov.ida.stuboidcclient.resources;

import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.OIDCResponseTypeValue;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import io.dropwizard.views.View;
import uk.gov.ida.stuboidcclient.configuration.StubOidcClientConfiguration;
import uk.gov.ida.stuboidcclient.rest.Urls;
import uk.gov.ida.stuboidcclient.services.AuthnRequestService;
import uk.gov.ida.stuboidcclient.services.AuthnResponseService;
import uk.gov.ida.stuboidcclient.services.RedisService;
import uk.gov.ida.stuboidcclient.services.TokenService;
import uk.gov.ida.stuboidcclient.views.ResponseView;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Path("/formPost")
public class StubOidcClientFormPostResource {

    private final TokenService tokenService;
    private final AuthnRequestService authnRequestService;
    private final AuthnResponseService authnResponseService;
    private final StubOidcClientConfiguration configuration;
    private final RedisService redisService;
    private URI authorisationURI;
    private URI redirectUri;


    public StubOidcClientFormPostResource(
            StubOidcClientConfiguration configuration,
            TokenService tokenService,
            AuthnRequestService authnRequestService,
            AuthnResponseService authnResponseService,
            RedisService redisService) {
        this.configuration = configuration;
        this.tokenService = tokenService;
        this.authnRequestService = authnRequestService;
        this.authnResponseService = authnResponseService;
        this.redisService = redisService;
        authorisationURI = UriBuilder.fromUri(configuration.getStubOpURI()).path(Urls.StubOp.AUTHORISATION_ENDPOINT_FORM_URI).build();
        redirectUri = UriBuilder.fromUri(configuration.getStubClientURI()).path(Urls.StubClient.REDIRECT_FORM_URI).build();
    }

    @GET
    @Path("/serviceAuthenticationRequest")
    @Produces(MediaType.APPLICATION_JSON)
    public Response formPostAuthenticationRequest() {

        return Response
                .status(302)
                .location(authnRequestService.generateFormPostAuthenticationRequest(
                        authorisationURI,
                        getClientID(),
                        redirectUri,
                        new ResponseType(ResponseType.Value.CODE, OIDCResponseTypeValue.ID_TOKEN, ResponseType.Value.TOKEN))
                        .toURI())
                        .build();
    }

    @GET
    @Path("/serviceAuthenticationRequestCodeIDToken")
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceAuthenticationRequestCodeIDToken() {

        return Response
                .status(302)
                .location(authnRequestService.generateFormPostAuthenticationRequest(
                        authorisationURI,
                        getClientID(),
                        redirectUri,
                        new ResponseType(ResponseType.Value.CODE, OIDCResponseTypeValue.ID_TOKEN))
                        .toURI())
                        .build();
    }

    @POST
    @Path("/validateAuthenticationResponse")
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public View validateAuthenticationResponse(String postBody) throws IOException, java.text.ParseException, ParseException, URISyntaxException {
        if (postBody == null || postBody.isEmpty()) {
            return new ResponseView(new URI(configuration.getStubTrustframeworkRP()), "Post Body is empty");
        }

        Optional<String> errors = authnResponseService.checkResponseForErrors(postBody);

        if (errors.isPresent()) {
            return new ResponseView(new URI(configuration.getStubTrustframeworkRP()), "Errors in Response: " + errors.get());
        }

        AuthorizationCode authorizationCode = authnResponseService.handleAuthenticationResponse(postBody, getClientID());

        String userInfoInJson = retrieveTokenAndUserInfo(authorizationCode);

        return new ResponseView(new URI(configuration.getStubTrustframeworkRP()), userInfoInJson);
    }


    private String retrieveTokenAndUserInfo(AuthorizationCode authCode) {

            OIDCTokens tokens = tokenService.getTokens(authCode, getClientID());
            UserInfo userInfo = tokenService.getUserInfo(tokens.getBearerAccessToken());

            String userInfoToJson = userInfo.toJSONObject().toJSONString();
            return userInfoToJson;
    }

    private ClientID getClientID() {
        String client_id = redisService.get("CLIENT_ID");
        if (client_id != null) {
            return new ClientID(client_id);
        }
        return new ClientID();
    }
}
