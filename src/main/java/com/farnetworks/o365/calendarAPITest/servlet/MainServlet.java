package com.farnetworks.o365.calendarAPITest.servlet;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.ProxySelectorRoutePlanner;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientRequestFactory;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.core.executors.ApacheHttpClient4Executor;
import org.json.JSONArray;
import org.json.JSONObject;

@SuppressWarnings("serial")
public class MainServlet extends HttpServlet {

	private static String AUTHORITY = "https://login.microsoftonline.com";
	private static String AUTHORIZATION_URI = "/common/oauth2/v2.0/authorize";
	private static String TOKEN_URI = "/common/oauth2/v2.0/token";
	private static String RESPONSE_TYPE = "code";
	private static String RESPONSE_MODE = "query";
	private static String ENDPOINT_URI = "https://outlook.office.com/api/v1.0";
	private static String EVENTS_VIEW_URI = "/me/calendarview";
	
	// application specific
	private static String CLIENT_ID = "b1ec1752-1ea0-4b88-a99a-d2d8d171efc9";
	private static String CLIENT_SECRET = "tapgY9gxSeWEEHTqyvPL3gi";
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		//super.doGet(req, res);
		String autohrizationCode = req.getParameter("code"); 
		if (autohrizationCode == null || autohrizationCode.isEmpty()){
			if (req.getSession().getAttribute("code") == null){
				// intiate the auth flow
				res.sendRedirect(AUTHORITY + AUTHORIZATION_URI + "?" 
						+ "client_id=" + CLIENT_ID
						+ "&response_type=" +  RESPONSE_TYPE
						+ "&redirect_uri=http://localhost:8080/calendarAPITest"
						+ "&response_mode=" + RESPONSE_MODE
						+ "&scope=https://outlook.office.com/calendars.read");
			} else {
				// già salvato
				if (req.getParameter("getAccessToken") != null){
					autohrizationCode = (String)req.getSession().getAttribute("code");
					try {
						ClientRequestFactory requestFactory = getClientRequestFactory(AUTHORITY, null, null);
						ClientRequest request = requestFactory.createRelativeRequest(TOKEN_URI).
							formParameter("grant_type", "authorization_code").
							formParameter("client_id", CLIENT_ID).
							formParameter("scope", "https://outlook.office.com/calendars.read").
							formParameter("code", autohrizationCode).
							formParameter("client_secret", CLIENT_SECRET).
							formParameter("redirect_uri", "http://localhost:8080/calendarAPITest").
							header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED);
						
						request.setHttpMethod("POST");
						
						ClientResponse response = request.execute();
						if (response.getResponseStatus() == Status.OK){
							Object entity = response.getEntity(String.class);
							String jsonString = entity.toString();
							JSONObject json = new JSONObject(jsonString);
							res.getWriter().println("<html><body><h1>Access token received</h1>" 
									+ "<form action=\"/calendarAPITest\">"
									+ "<input type=\"hidden\" value=\"1\" name=\"getEvents\"/>"
									+ "<input type=\"submit\" value=\"Get this month events\"/>"
									+ "</form></body></html>");	
							String accessToken = json.getString("access_token");
							String tokenType = json.getString("token_type");
							req.getSession().setAttribute("accessToken", accessToken);
							req.getSession().setAttribute("tokenType", tokenType);
						} else {
							res.getWriter().println(response.getResponseStatus().getStatusCode() 
									+ " - " + response.getResponseStatus().getReasonPhrase());
						}
					} catch (Exception e){
						e.printStackTrace();
					}
				} else if (req.getParameter("getEvents") != null){
					String accessToken = (String)req.getSession().getAttribute("accessToken");
					String tokenType = (String)req.getSession().getAttribute("tokenType");
					try {
						ClientRequestFactory requestFactory = getClientRequestFactory(ENDPOINT_URI, null, null);
						ClientRequest request = requestFactory.createRelativeRequest(EVENTS_VIEW_URI).
							queryParameter("startDateTime", "2015-08-01T01:00:00Z").
							queryParameter("endDateTime", "2015-08-31T23:00:00Z").
							queryParameter("$select", "Subject").
							header("Authorization", tokenType + " " + accessToken);
						
						request.setHttpMethod("GET");
						ClientResponse response = request.execute();
						if (response.getResponseStatus() == Status.OK){
							res.getWriter().println("<html><body><h1>Events retrieved</h1><table>");
							Object entity = response.getEntity(String.class);
							String jsonString = entity.toString();
							JSONObject json = new JSONObject(jsonString);
							JSONArray jsonEvents = json.getJSONArray("value");
							for (int i = 0; i < jsonEvents.length(); ++i){
								JSONObject jsonEvent = new JSONObject(jsonEvents.get(i).toString());
								res.getWriter().println("<tr><td>" + jsonEvent.getString("Subject") + "</td></tr>");
							}
							res.getWriter().println("</table></body></html>");
						} else {
							res.getWriter().println(response.getResponseStatus().getStatusCode() 
									+ " - " + response.getResponseStatus().getReasonPhrase());
						}
					} catch (Exception e){
						
					}
				}
			}
		} else {
			// we have been authorized, so go on and retrieve the access token
			req.getSession().setAttribute("code", autohrizationCode);
			res.getWriter().println("<html><body><h1>Authcode received</h1>"
					+ "<form action=\"/calendarAPITest\">"
					+ "<input type=\"hidden\" value=\"1\" name=\"getAccessToken\"/>"
					+ "<input type=\"submit\" value=\"Get access token\"/>"
					+ "</form></body></html>");	
		}
	}
	
	private String getBaseURI(){
		return "";
	}
	
	private ClientRequestFactory getClientRequestFactory(URI baseURI){
		return getClientRequestFactory(baseURI, null, null);
	}
	
	private ClientRequestFactory getClientRequestFactory(String baseURI, String userName, 
			String password) throws URISyntaxException {
		return getClientRequestFactory(new URI(baseURI), userName, password);
	}
		
	private ClientRequestFactory getClientRequestFactory(URI baseURI, String userName, String password){
		Credentials credentials = null;
		if (userName != null && password != null)
			credentials = new UsernamePasswordCredentials(userName, password);
		return getClientRequestFactory(baseURI, credentials);
	}
		
	private ClientRequestFactory getClientRequestFactory(URI baseURI, Credentials credentials){
		DefaultHttpClient httpClient = new DefaultHttpClient();
		ProxySelectorRoutePlanner routePlanner = new ProxySelectorRoutePlanner(
				httpClient.getConnectionManager().getSchemeRegistry(),
				ProxySelector.getDefault());  
		httpClient.setRoutePlanner(routePlanner);
		if (credentials != null)
			httpClient.getCredentialsProvider().setCredentials(
	            org.apache.http.auth.AuthScope.ANY, credentials);

	    ClientExecutor clientExecutor = new ApacheHttpClient4Executor(httpClient);
	    ClientRequestFactory clientRequestFactory = new ClientRequestFactory(clientExecutor, baseURI);
	    return clientRequestFactory;
	}
}
