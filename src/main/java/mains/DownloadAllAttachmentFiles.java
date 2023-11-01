package mains;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;

import com.fasterxml.jackson.databind.JsonNode;

import constants.Configurations;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeUtility;
import utils.JacksonHelper;

/**
 * 全ての添付ファイルをダウンロード.
 * @see https://logicalerror.seesaa.net/article/462358077.html
 *
 * @author cyrus
 */
public class DownloadAllAttachmentFiles {

	/**
	 * デバッグモード.
	 */
	private static final boolean DEBUG_MODE = false;

	/**
	 * ダウンロード対象のフォルダ名.
	 */
	private static final String TARGET_FOLDER_NAME = "CHANGEME";

	/**
	 * 処理済みUID一覧のファイル.
	 */
	private static final File PROCESSED_UID_LIST_FILE = new File("data/processed.json");

	/**
	 * main.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("start.");
		try {
			// 処理済みUID一覧を取得
			Set<Long> uidSet = new HashSet<>();
			if (PROCESSED_UID_LIST_FILE.exists()) {
				JsonNode jsonNode = JacksonHelper.getObjectMapper().readTree(PROCESSED_UID_LIST_FILE);
				uidSet = StreamSupport.stream(jsonNode.spliterator(), false)
						.map(v -> v.asLong())
						.collect(Collectors.toSet());
			}

			// http://connector.sourceforge.net/doc-files/Properties.html
			Properties properties = System.getProperties();

			// セッションを取得
			Session session = Session.getInstance(properties, null);

			// IMAPでSSLを使用
			try (Store imap4 = session.getStore("imaps")) {
				// 接続
				imap4.connect(Configurations.IMAP_SERVER, Configurations.IMAP_PORT, Configurations.USERNAME,
						Configurations.PASSWORD);
				System.out.println(imap4.getURLName().toString());

				// https://stackoverflow.com/questions/11435947/how-do-i-uniquely-identify-a-java-mail-message-using-imap
				try (Folder folder = imap4.getFolder(TARGET_FOLDER_NAME)) {
					UIDFolder uidFolder = (UIDFolder) folder;
					folder.open(Folder.READ_ONLY);

					// 全てのメッセージの件数を取得
					int totalMessages = folder.getMessageCount();
					System.out.println("Total messages: " + totalMessages);

					// 新しいメッセージの件数を取得
					int newMessages = folder.getNewMessageCount();
					System.out.println("New messages: " + newMessages);

					// メッセージの一覧を取得
					Message[] messageArray = folder.getMessages();

					if (DEBUG_MODE) {
						// FIXME メッセージの一覧をシャッフル
						ArrayUtils.shuffle(messageArray);
					}

					// 全てのメッセージに対して実行
					for (Message message : messageArray) {
						try {
							// UID を取得
							Long uid = uidFolder.getUID(message);
							System.out.println("UID: " + uid);

							// 処理済みUID一覧に存在しない場合
							if (!uidSet.contains(uid)) {
								// From
								Address[] address = message.getFrom();
								String xFromEmail = "";
								String fromEmail = "";
								if (address != null) {
									InternetAddress internetAddress = (InternetAddress) address[0];
									xFromEmail = MimeUtility.decodeText(internetAddress.toString());
									fromEmail = internetAddress.getAddress();
								}
								System.out.println("xFromEmail: " + xFromEmail);
								System.out.println("fromEmail: " + fromEmail);

								// Subject
								String subject = message.getSubject();
								if (StringUtils.isNotBlank(subject)) {
									String decodedSubject = MimeUtility.decodeText(subject);
									System.out.println("subject: " + decodedSubject);
								}

								// 受信日時
								Date date = message.getSentDate();
								SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
								String dateText = sdf.format(date);
								System.out.println("date: " + dateText);

								// コンテンツタイプによって分岐
								if (message.isMimeType("text/plain")) {
									// 本文を取得
									String content = message.getContent().toString();
									System.out.println("content: " + content);
								} else if (message.isMimeType("multipart/*")) {
									// マルチパートを取得
									Multipart multipart = (Multipart) message.getContent();

									// マルチパートの件数を取得
									int multipartCount = multipart.getCount();

									// 全てのマルチパートに対して実行
									for (int i = 0; i < multipartCount; i++) {
										// ボディパートを取得
										MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
										System.out.println("contentType[" + i + "]: " + bodyPart.getContentType());

										// コンテンツタイプがtext/plainの場合はスキップ
										if (bodyPart.isMimeType("text/plain")) {
											// 本文を取得
											String content = bodyPart.getContent().toString();
											System.out.println("content: " + content);
											continue;
										}

										// コンテンツタイプがtext/htmlの場合はスキップ
										if (bodyPart.isMimeType("text/html")) {
											// HTML本文を取得
											String html = bodyPart.getContent().toString();
											System.out.println("html: " + html);
											continue;
										}

										// FIXME コンテンツタイプがmultipart/alternativeの場合はスキップ
										if (bodyPart.isMimeType("multipart/alternative")) {
											continue;
										}

										// ボディパートをファイルに保存
										saveBodyPart(fromEmail, bodyPart, uid, i);
									}
								}

								// 処理済みUID一覧に追加
								uidSet.add(uid);

								// デバッグモードの場合は終了
								if (DEBUG_MODE) {
									break;
								}
							} else {
								System.out.println("処理済みです: " + uid);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}

			// 処理済みUID一覧を出力
			JacksonHelper.getObjectMapper().writeValue(PROCESSED_UID_LIST_FILE,
					JacksonHelper.getObjectMapper().valueToTree(uidSet));
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			System.out.println("done.");
		}
	}

	/**
	 * ボディパートをファイルに保存.
	 *
	 * @param from
	 * @param bodyPart
	 * @param messageId
	 * @param attachmentIndex
	 * @throws Exception 
	 */
	private static void saveBodyPart(String from, MimeBodyPart bodyPart, Long messageId, int attachmentIndex)
			throws Exception {
		// ファイル名を取得
		String fileName = bodyPart.getFileName();
		if (StringUtils.isNotBlank(fileName)) {
			// ファイル名をデコード
			fileName = MimeUtility.decodeText(bodyPart.getFileName());
		} else {
			// メッセージIDからファイル名を作成
			fileName = String.format("%d%d", messageId, attachmentIndex);
		}
		System.out.println("fileName: " + fileName);

		// ファイルを作成
		File file = new File(String.format("data/%s/%s", from, fileName));

		// ファイルが存在する場合はファイル名を修正
		int i = 1;
		while (file.exists()) {
			i++;
			if (0 <= FilenameUtils.indexOfExtension(fileName)) {
				String newFileName = String.format("%s_%d.%s", FilenameUtils.getBaseName(fileName), i,
						FilenameUtils.getExtension(fileName));
				file = new File(file.getParentFile(), newFileName);
			} else {
				String newFileName = String.format("%s_%d", FilenameUtils.getBaseName(fileName), i);
				file = new File(file.getParentFile(), newFileName);
			}
		}
		FileUtils.createParentDirectories(file);

		// ファイルを保存
		bodyPart.saveFile(file);

		// ファイルの拡張子が存在しない場合
		if (StringUtils.isBlank(FilenameUtils.getExtension(file.getName()))) {
			// ファイル内容から適切な拡張子を取得
			String speculatedExtension = getSpeculatedExtension(file);

			// 新しいファイル名を作成
			String newFileName = String.format("%s.%s", file.getName(), speculatedExtension);
			File newFile = new File(file.getParentFile(), newFileName);

			// ファイル名を変更
			if (!file.renameTo(newFile)) {
				System.err.println("ファイル名の変更に失敗: " + newFile.getAbsolutePath());
			}
		}
	}

	/**
	 * ファイル内容から適切な拡張子を取得.
	 *
	 * @param file
	 * @return
	 * @throws Exception
	 */
	private static String getSpeculatedExtension(File file) throws Exception {
		return TikaConfig.getDefaultConfig().getMimeRepository().forName(new Tika().detect(file)).getExtension();
	}
}