package mains.example;

import java.io.IOException;
import java.net.URI;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.imap.IMAPClient;

import constants.Configurations;
import utils.IMAPUtils;

/**
 * IMAP接続テスト.
 * @see https://github.com/apache/commons-net/blob/master/src/main/java/org/apache/commons/net/examples/mail/IMAPMail.java
 *
 * @author cyrus
 */
public class IMAPExample {

	/**
	 * main.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			final URI uri = Configurations.getImapUri();

			// Connect and login
			final IMAPClient imap = IMAPUtils.imapLogin(uri, 10000, null);

			// suppress login details
			imap.addProtocolCommandListener(new PrintCommandListener(System.out, true));

			try {
				imap.setSoTimeout(6000);

				imap.capability();

				imap.select("inbox");

				imap.examine("inbox");

				imap.status("inbox", new String[] { "MESSAGES" });

				imap.list("", "*"); // Show the folders

			} catch (final IOException e) {
				System.out.println(imap.getReplyString());
				e.printStackTrace();
				System.exit(10);
			} finally {
				imap.logout();
				imap.disconnect();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}