package constants;

import java.net.URI;

/**
 * 環境設定.
 *
 * @author cyrus
 */
public interface Configurations {

	/**
	 * ユーザー名.
	 */
	String USERNAME = "CHANGEME";

	/**
	 * パスワード.
	 */
	String PASSWORD = "CHANGEME";

	/**
	 * サーバー.
	 */
	String IMAP_SERVER = "CHANGEME";

	/**
	 * ポート.
	 */
	int IMAP_PORT = 993;

	/**
	 * SSL.
	 */
	boolean IMAP_USE_SSL = true;

	/**
	 * IMAPの接続URIを取得.
	 * 
	 * @return
	 */
	public static URI getImapUri() {
		if (IMAP_USE_SSL) {
			return URI.create(String.format("imaps://%s:%s@/%s", USERNAME, PASSWORD, IMAP_SERVER));
		} else {
			return URI.create(String.format("imap://%s:%s@/%s", USERNAME, PASSWORD, IMAP_SERVER));
		}
	}
}