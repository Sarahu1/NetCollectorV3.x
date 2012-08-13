/**
 * 
 */
package com.cattsoft.collect.io.net.mail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.mail.search.AndTerm;
import javax.mail.search.FromStringTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;

import com.sun.mail.smtp.SMTPTransport;

/**
 * 邮件收发.
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class EmailHelper {
	/*** 邮件发送设置 ***/
	/*** 端口类型*/
	String smtp_port = "smtp";
	String protocol = "ssl";
	/*** 发件人标识 */
	private String mailer = "smtpsend";
	/*** 用户名 */
	private String username;
	/*** 密码 */
	private String password;
	/*** 邮件发送主体 */
	private MailBodyPart part;
	/*** 是否需要认证 */
	private boolean auth;
	/*** 提供邮件发送服务器连接时长获取 */
	private long ctime = 0;
	
	private SMTPTransport transport;
	
	String smtp_host;

	/*** 邮件收取设置 ***/
	
	String pop_host;
	/*** 端口类型 */
	private String pop_port = "pop3";
	// 与邮件服务器连接后得到的邮箱  
	private Store store;  
	// 收件箱
	private Folder folder;  
	/*** 提供邮件收取服务器连接时长获取 */
	private long rtime;

	/**
	 * @param host 邮件发送SMTP地址
	 * @param port 发送端口
	 * @param username 用户名
	 * @param password 密码
	 * @param part 邮件主体 <code>EmailHelper.MailBodyPart part =  helper.new MailBodyPart("to", "cc", "title");</code>
	 * @return 是否发送成功
	 * @throws MessagingException 发送失败或出现错误时将抛出该异常
	 */
	public Message send(String host, int port, String username, String password, MailBodyPart part, String protocol) throws MessagingException{
		this.smtp_host = host;
		this.username = username;
		this.password = password;
		this.protocol = protocol;
		this.part = part;
		auth = (username != null);
		// 设置发件人, 便于查找邮件
		part.setFrom(null != part.getFrom() ? part.getFrom() : username);
		Properties props = System.getProperties();
		if (host != null)
			props.put("mail." + smtp_port + ".host", host);
		if (auth)
			props.put("mail." + smtp_port + ".auth", "true");
		props.put("mail.transport.protocol", smtp_port);
		
		if("ssl".equalsIgnoreCase(protocol)) {
			props.put("mail." + smtp_port + ".starttls.enable", "true");
//			props.put("mail." + smtp_port + ".socketFactory.class", "javax.net.ssl.SSLSocketFactory");
			props.put("mail." + smtp_port + ".port", port);
			props.put("mail." + smtp_port + ".socketFactory.port", port);
			props.put("mail." + smtp_port + ".socketFactory.fallback", "false");
		}
		// 获取邮件会话
		Session session = Session.getDefaultInstance(props);
		// 准备邮件
		Message msg = new MimeMessage(session);
		msg.setHeader("Content-Transfer-Encoding", "Base64");
		msg.setHeader("X-Mailer", mailer);
		// 邮件发送时间
		msg.setSentDate(new Date());
		msg.setFrom(new InternetAddress((null != part.getFrom() ? part.getFrom() : username)));
		//设置收件人
		msg.setRecipients(Message.RecipientType.TO,
				InternetAddress.parse(part.getTo(), false));
		//抄送
		if (part.getCc() != null)
			msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(part.getCc(), false));
		//标题
		msg.setSubject(part.getSubject());
		//附件
		if(part.getParts().getCount() > 0) {
			MimeBodyPart mbp1 = new MimeBodyPart();
			mbp1.setText(part.getContent());
			//添加文本
			part.getParts().addBodyPart(mbp1, 0);
			msg.setContent(part.getParts());
		} else {
			msg.setText(null != part.getContent() ? part.getContent() : "");
		}
		transport = (SMTPTransport)session.getTransport(smtp_port);
		try {
			long c_start = System.currentTimeMillis();
			if (auth)
				transport.connect(host, username, password);
			else
				transport.connect();
			// 连接时长
			ctime = System.currentTimeMillis() - c_start;
			transport.sendMessage(msg, msg.getAllRecipients());
			System.out.println("\nMail was sent successfully.");
			return msg;
		} finally {
			System.out.println("Response: " +
					transport.getLastServerResponse());
			transport.close();
		}
	}

	/** 收取指定发件人和标题邮件
	 * @param host POP3 主机地址
	 * @param port 端口
	 * @param username 用户名
	 * @param password 密码
	 * @param from 发件人
	 * @param subject 标题
	 */
	public Message[] receiver(String host, int port, String username, String password, String from, String subject) throws MessagingException {
		Message [] messages = new Message[0];
		this.pop_host = host;
		Properties props = System.getProperties();
		
		if("ssl".equalsIgnoreCase(protocol)) {
			pop_port = "pop3s";
			props.put("mail."+pop_port+".socketFactory.class", "javax.net.ssl.SSLSocketFactory");
			props.put("mail."+pop_port+".socketFactory.fallback", "false");
			props.put("mail."+pop_port+".socketFactory.port", port);
			props.put("mail."+ pop_port +".ssl.enable", "true");
		}
		
		props.put("mail." + pop_port + ".host", host);
		if (auth)
			props.put("mail." + pop_port + ".auth", "true");
		
		props.setProperty("mail.store.protocol", pop_port);
		props.put("mail."+pop_port+".port",  port);

		// 获取邮件会话
		Session session = Session.getDefaultInstance(props);

		// 利用Session对象获得Store对象，并连接pop3服务器    
		store = session.getStore(pop_port);
		
		long c_time = System.currentTimeMillis();
		store.connect(host, username, password);
		rtime = System.currentTimeMillis() - c_time;
		
		// 获得邮箱内的邮件夹Folder对象，以"读-写"方式打开
		folder = store.getFolder("inbox");
		folder.open(Folder.READ_WRITE);

		// 搜索指定发件人和标题的邮件  
		SearchTerm st = new AndTerm(
				new FromStringTerm(part.getFrom()),
				new SubjectTerm(part.getSubject()));
		try {
			if(folder.getMessageCount() > 0) {
				messages = folder.getMessages(folder.getMessageCount() - folder.getNewMessageCount(), folder.getMessageCount());
			}
			// 全搜索
			if(messages.length == 0) {
				messages = folder.getMessages();
			}
			// 搜索邮件
//			messages = folder.search(st);
			
			// 轻量
//			FetchProfile fp = new FetchProfile();
//			fp.add(FetchProfile.Item.ENVELOPE);
//			folder.fetch(messages, fp);
			
			List<Message> matchMessages = new LinkedList<Message>();
			for (Message message : messages) {
				// 匹配发件人与标题
				if(st.match(message)) {
					matchMessages.add(message);
					System.out.println("主题: " + message.getSubject());
					System.out.println("发件人: " + message.getFrom()[0]);
					System.out.println("日期:" + message.getSentDate());
				}
			}
			messages = matchMessages.toArray(new Message[matchMessages.size()]);
			
			System.out.println("搜索过滤到" + messages.length + " 封符合条件的邮件！");  
			for(int i = 0; i < messages.length; i++) {
				// 设置删除标记，调用saveChanges()方法保存修改
				messages[i].setFlag(Flags.Flag.DELETED, true);
				try {
					// messages[i].saveChanges();
					System.out.println("邮件已设置删除标记");
				} catch (Exception e) {
					// 没办法,删除不了
					System.err.println("无法删除邮件");
					try {
						// 设置已读
						messages[i].setFlag(Flags.Flag.SEEN, true);
					} catch (Exception e2) {
					}
				}
			}
		} finally {
			try {
				// 关闭连接时设置了删除标记的邮件才会被真正删除，相当于"QUIT"命令  
				if(null != folder)
					folder.close(true);  
				if(null != store)
				store.close(); 
			} catch (Exception e2) {
			}
		}
		return messages;
	}
	
	/**
	 * @param host
	 * @param port
	 * @return
	 * @throws MessagingException
	 */
	public Message[] receiver(String host, int port) throws MessagingException {
		return receiver(host, port, username, password, part.getFrom(), part.getSubject());
	}
	
	/**
	 * @return the ctime
	 */
	public long getCtime() {
		return ctime;
	}
	
	/**
	 * @return the rtime
	 */
	public long getRtime() {
		return rtime;
	}
	
	/**
	 * @return the smtp_host
	 */
	public String getSmtp_host() {
		return smtp_host;
	}
	
	/**
	 * @return the pop_host
	 */
	public String getPop_host() {
		return pop_host;
	}

	/** 邮件发送主体信息
	 * @author 陈小鸿
	 * @author chenxiaohong@mail.com
	 */
	public class MailBodyPart implements Serializable {
		private static final long serialVersionUID = 1L;
		/*** 发件人 */
		private String from;
		/*** 收件人 */
		private String to;
		/*** 抄送 */
		private String cc;
		/*** 标题 */
		private String subject;
		/*** 邮件内容 */
		private String content;
		/*** 邮件附件 */
		Multipart parts = new MimeMultipart();	//附件

		/**
		 * @param to 收件人
		 * @param cc 抄送
		 * @param subject 标题
		 */
		public MailBodyPart(String to, String cc, String subject) {
			this.to = to;
			this.cc = cc;
			this.subject = subject;
		}

		public String getFrom() {
			return from;
		}

		public void setFrom(String from) {
			this.from = from;
		}

		public String getTo() {
			return to;
		}

		public void setTo(String to) {
			this.to = to;
		}

		public String getCc() {
			return cc;
		}

		public void setCc(String cc) {
			this.cc = cc;
		}

		public String getSubject() {
			return subject;
		}

		public void setSubject(String subject) {
			this.subject = subject;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		public Multipart getParts() {
			return parts;
		}

		public void setParts(Multipart parts) {
			this.parts = parts;
		}

		/**
		 * 添加附件
		 * @param part 附件
		 * @return 是否添加成功
		 * @throws FileNotFoundException 文件不存在时将抛出该异常
		 * @throws MessagingException 添加附件出现问题时将抛出该异常
		 */
		public void addAttach(String path) throws FileNotFoundException, MessagingException {
			File file = new File(path);
			if(!file.exists()) {
				throw new FileNotFoundException("未找到文件:" + path);
			}
			MimeBodyPart mbp = new MimeBodyPart();
			try {
				mbp.attachFile(file);
				mbp.setFileName(MimeUtility.encodeText(mbp.getFileName()));
				parts.addBodyPart(mbp);
			} catch (Exception e) {
				throw new MessagingException("附件添加失败!");
			}
		}
	}
}