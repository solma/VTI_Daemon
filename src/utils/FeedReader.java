package utils;

/**
 * It Reads and prints any RSS/Atom feed type.
 * 
 * @author Sol Ma
 *
 */
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

public class FeedReader {
	public final static HashMap<String, String> route_id;
	static{
		route_id=new HashMap<String, String>();
		//route_id.put("vti_redline", "307");
		//route_id.put("vti_purpleline", "308");
		//route_id.put("vti_yellowline", "309");
		//route_id.put("vti_blueline", "310");
		//route_id.put("vti_pinkline", "311");
		//route_id.put("vti_greenline", "312");
		route_id.put("vti_brownline", "313");
		//route_id.put("vti_orangeline", "314");
		//route_id.put("vti_purpleexpressline", "323");
	}
	protected static Connection conn;
	static{
		//only access the credential table in the local database once 
		try{
	//	existing_credentials = new HashMap<String, String>();
		Class.forName("org.postgresql.Driver").newInstance();
		conn = DriverManager.getConnection(
				"jdbc:postgresql://localhost:5433/VTI", "postgres",
				"postgresql");
		Statement stat = conn.createStatement();
		ResultSet rs = stat.executeQuery("select * from credentials;");
		while (rs.next()) {
			existing_credentials.put(
					rs.getString("username"),
					rs.getString("accessToken") + " "
							+ rs.getString("accessTokenSecret"));
			System.out.println("username = " + rs.getString("username"));
			System.out.println("accessToken = "
					+ rs.getString("accessToken"));
			System.out.println("accessTokenSecret = "
					+ rs.getString("accessTokenSecret"));
		}
		rs.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/*
	 * @ this function writes a collection of lines to a file
	 */
	public static void writeFile(String file, Collection<String> lines) {
		Calendar cal = Calendar.getInstance();
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(file,true));
			out.write(DateFormat.getDateTimeInstance(
					DateFormat.FULL, DateFormat.MEDIUM).format(
					cal.getTime()));
			out.newLine();
			for (String line : lines) {
				out.write(line);
				out.newLine();
			}
			out.newLine();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * @ this function writes a collection of lines to a file
	 */
	public static HashSet<String> readFile(String file) {
		HashSet<String> ret = new HashSet<String>();
		try {
			Scanner s = new Scanner(new File(file));
			while (s.hasNextLine())
				ret.add(s.nextLine());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return ret;
	}

	public static List<String> retrieveFeeds(String url) {
		ArrayList<String> alerts = new ArrayList<String>();
		try {
			URL feedUrl = new URL(url);
			String routeId = url.substring(url.lastIndexOf('=') + 1);
			//System.out.println(routeId);
			String idFile = "cta_rss/alert_ids/" + routeId;

			SyndFeedInput input = new SyndFeedInput();
			SyndFeed feed = input.build(new XmlReader(feedUrl));

			// System.out.println(feed);
			HashSet<String> old_alertIds = readFile(idFile);
			HashSet<String> alertIds = new HashSet<String>();

			@SuppressWarnings("unchecked")
			List<SyndEntryImpl> entries = feed.getEntries();
			for (SyndEntryImpl entry : entries) {
				String link = entry.getLink();
				String id = link.substring(link.lastIndexOf('=') + 1);
				if (!old_alertIds.contains(id)) { // is a new alert
					// System.out.println(entry.getDescription().getValue());
					alertIds.add(id);
					alerts.add(entry.getDescription().getValue());
				}
			}
			if(alertIds.size()>0)
				writeFile(idFile, alertIds);

		} catch (Exception ex) {
			ex.printStackTrace();
			System.out.println("ERROR: " + ex.getMessage());
		}

		return alerts;
	}

}
