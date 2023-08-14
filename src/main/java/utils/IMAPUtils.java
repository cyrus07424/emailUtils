package utils;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Locale;

import org.apache.commons.net.ProtocolCommandListener;
import org.apache.commons.net.imap.IMAPClient;
import org.apache.commons.net.imap.IMAPSClient;

/**
 * IMAPUtils.
 * @see https://github.com/apache/commons-net/blob/master/src/main/java/org/apache/commons/net/examples/mail/IMAPUtils.java
 * @see https://github.com/apache/commons-net/blob/master/src/main/java/org/apache/commons/net/examples/mail/Utils.java
 *
 * @author cyrus
 */
public class IMAPUtils {

	/**
	 * If the initial password is: '*' - replace it with a line read from the system console '-' - replace it with next line from STDIN 'ABCD' - if the input is
	 * all upper case, use the field as an environment variable name
	 *
	 * Note: there are no guarantees that the password cannot be snooped.
	 *
	 * Even using the console may be subject to memory snooping, however it should be safer than the other methods.
	 *
	 * STDIN may require creating a temporary file which could be read by others Environment variables may be visible by using PS
	 */
	public static String getPassword(final String username, String password) throws IOException {
		if ("-".equals(password)) { // stdin
			final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			password = in.readLine();
		} else if ("*".equals(password)) { // console
			final Console con = System.console(); // Java 1.6
			if (con == null) {
				throw new IOException("Cannot access Console");
			}
			final char[] pwd = con.readPassword("Password for " + username + ": ");
			password = new String(pwd);
		} else if (password.equals(password.toUpperCase(Locale.ROOT))) { // environment variable name
			final String tmp = System.getenv(password);
			if (tmp != null) { // don't overwrite if variable does not exist (just in case password is all uppers)
				password = tmp;
			}
		}
		return password;
	}

	/**
	 * Parses the URI and use the details to connect to the IMAP(S) server and login.
	 *
	 * @param uri            the URI to use, e.g. imaps://user:pass@imap.mail.yahoo.com/folder or imaps://user:pass@imap.googlemail.com/folder
	 * @param defaultTimeout initial timeout (in milliseconds)
	 * @param listener       for tracing protocol IO (may be null)
	 * @return the IMAP client - connected and logged in
	 * @throws IOException if any problems occur
	 */
	public static IMAPClient imapLogin(final URI uri, final int defaultTimeout, final ProtocolCommandListener listener)
			throws IOException {
		final String userInfo = uri.getUserInfo();
		if (userInfo == null) {
			throw new IllegalArgumentException("Missing userInfo details");
		}

		final String[] userpass = userInfo.split(":");
		if (userpass.length != 2) {
			throw new IllegalArgumentException("Invalid userInfo details: '" + userInfo + "'");
		}

		final String username = userpass[0];
		String password = userpass[1];
		// prompt for the password if necessary
		password = getPassword(username, password);

		final IMAPClient imap;

		final String scheme = uri.getScheme();
		if ("imaps".equalsIgnoreCase(scheme)) {
			System.out.println("Using secure protocol");
			imap = new IMAPSClient(true); // implicit
		} else if ("imap".equalsIgnoreCase(scheme)) {
			imap = new IMAPClient();
		} else {
			throw new IllegalArgumentException("Invalid protocol: " + scheme);
		}
		final int port = uri.getPort();
		if (port != -1) {
			imap.setDefaultPort(port);
		}

		imap.setDefaultTimeout(defaultTimeout);

		if (listener != null) {
			imap.addProtocolCommandListener(listener);
		}

		final String server = uri.getHost();
		System.out.println("Connecting to server " + server + " on " + imap.getDefaultPort());

		try {
			imap.connect(server);
			System.out.println("Successfully connected");
		} catch (final IOException e) {
			throw new IOException("Could not connect to server.", e);
		}

		if (!imap.login(username, password)) {
			imap.disconnect();
			throw new IOException("Could not login to server. Check login details.");
		}

		return imap;
	}
}