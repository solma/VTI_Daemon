package account;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

import main.VTI;
import twitter4j.GeoLocation;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;
import utils.Log;

/**
 * 
 * @author Sol Ma
 * 
 */
public class VTIAccount implements Runnable {
	// caches to reduce number of database accesses format is <username,
	// accessToken+" "+accessTokenSecret>
	protected static HashMap<String, String> existing_credentials;
	static {
		// only access the credential table in the local database once
		try {
			existing_credentials = new HashMap<String, String>();
			Statement stat = VTI.conn.createStatement();
			ResultSet rs = stat.executeQuery("select * from credentials;");
			while (rs.next()) {
				existing_credentials.put(
						rs.getString("username"),
						rs.getString("accessToken") + " "
								+ rs.getString("accessTokenSecret"));
				// Log.println("username = " + rs.getString("username"));
				// Log.println("accessToken = "+
				// rs.getString("accessToken"));
				// Log.println("accessTokenSecret = "+
				// rs.getString("accessTokenSecret"));
			}
			rs.close();
			stat.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	protected Twitter twitter;
	protected User user;

	protected LinkedHashSet<String> seen_statuses;

	public static Twitter authorize(String screen_name) {
		Twitter twitter = null;
		AccessToken accessToken = null;
		try {
			// screen name is case-insensitive
			// the user has already authorized VTI
			if (existing_credentials.containsKey(screen_name.toLowerCase())) {
				// values
				String[] values = existing_credentials.get(
						screen_name.toLowerCase()).split(" ");
				TwitterFactory tf = new TwitterFactory(
						new ConfigurationBuilder()
								.setDebugEnabled(true)
								.setOAuthConsumerKey(VTI.VTI_CONSUMER_KEY)
								.setOAuthConsumerSecret(VTI.VTI_CONSUMER_SECRET)
								.setOAuthAccessToken(values[0])
								.setOAuthAccessTokenSecret(values[1]).build());
				twitter = tf.getInstance();
				accessToken = twitter.getOAuthAccessToken();
				Log
						.println(screen_name
								+ " has alreay authorized VTI, retrieve access token from local database");
			} else { // the user has not authorized VTI yet,add it to the
						// database
				try {
					twitter = new TwitterFactory().getInstance();
					twitter.setOAuthConsumer(VTI.VTI_CONSUMER_KEY,
							VTI.VTI_CONSUMER_SECRET);

					RequestToken requestToken = twitter.getOAuthRequestToken();
					Log.println("Got request token.");
					Log.println("Request token: "
							+ requestToken.getToken());
					Log.println("Request token secret: "
							+ requestToken.getTokenSecret());

					accessToken = null;
					BufferedReader br = new BufferedReader(
							new InputStreamReader(System.in));

					while (null == accessToken) {
						Log
								.println("Open the following URL and grant access to your account:");
						Log.println(requestToken.getAuthorizationURL());
						try {
							Desktop.getDesktop()
									.browse(new URI(requestToken
											.getAuthorizationURL()));
						} catch (IOException e) {
							e.printStackTrace();
						} catch (URISyntaxException e) {
							throw new AssertionError(e);
						}
						Log
								.print("Enter the PIN(if available) and hit enter after you granted access.[PIN]:");
						String pin = br.readLine();
						try {
							if (pin.length() > 0) {
								accessToken = twitter.getOAuthAccessToken(
										requestToken, pin);
							} else {
								accessToken = twitter
										.getOAuthAccessToken(requestToken);
							}
						} catch (TwitterException te) {
							if (401 == te.getStatusCode()) {
								Log
										.println("Unable to get the access token.");
							} else {
								te.printStackTrace();
							}
						}
					}

					// insert the credential of the new user into local database
					PreparedStatement prep = VTI.conn
							.prepareStatement("INSERT INTO credentials VALUES(?,?,?, now())");
					prep.setString(1, screen_name.toLowerCase());
					prep.setString(2, accessToken.getToken());
					prep.setString(3, accessToken.getTokenSecret());
					prep.executeUpdate();
					// insert the credential of the new user into cache
					existing_credentials.put(
							screen_name.toLowerCase(),
							accessToken.getToken() + " "
									+ accessToken.getTokenSecret());

					Log
							.println("Successfully stored access token to local database.");
					// System.exit(0);
					prep.close();
				} catch (TwitterException te) {
					te.printStackTrace();
					Log.println("Failed to get accessToken: "
							+ te.getMessage());
					System.exit(-1);
				}
			}
			//Log.println("Got access token.");
			//Log.println("Access token: " + accessToken.getToken());
			//Log.println("Access token secret: "+ accessToken.getTokenSecret());
			Log.println();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return twitter;
	}

	public VTIAccount(String screen_name) throws IOException {
		try {
			twitter = authorize(screen_name);
			user = twitter.verifyCredentials();
			seen_statuses = new LinkedHashSet<String>();
		} catch (TwitterException e) {
			e.printStackTrace();
		}
	}

	public Twitter getTwitter() {
		return twitter;
	}

	public void run() {
		/*
		 * PrintWriter logOut = null; try { logOut = new PrintWriter(new
		 * FileWriter("logs/" + twitter.getScreenName() + ".txt")); } catch
		 * (IllegalStateException e1) { e1.printStackTrace(); } catch
		 * (IOException e1) { e1.printStackTrace(); } catch (TwitterException
		 * e1) { e1.printStackTrace(); }
		 */
		while (true) {
			List<Status> statuses;
			// List<DirectMessage> dms;

			try {
				statuses = twitter.getMentions();
				// dms = twitter.getDirectMessages();

				for (Status status : statuses)
					if (!seen_statuses.contains(String.valueOf(status.getId()))) {
						seen_statuses.add(String.valueOf(status.getId()));
						// remove @screen_name (regardless letter cases) within
						// the status

						String new_status = status.getText().replaceAll(
								"@" + user.getScreenName(), "");
						new_status = new_status.replaceAll("@"
								+ user.getScreenName().toLowerCase(), "");

						twitter.updateStatus(new_status);

					}

			} catch (TwitterException e) {
				e.printStackTrace();
			}

			try {
				Thread.sleep(1000); // check the received tweets every 1 sec
			} catch (InterruptedException e) {
				// e.printStackTrace();
				break;
			}

		}
	}

}