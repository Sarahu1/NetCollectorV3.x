/**
 * 
 */
package com.cattsoft.collect.io.file.ftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.net.ProtocolCommandEvent;
import org.apache.commons.net.ProtocolCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 上传文件到Ftp服务器.
 * 远程工作目录分隔符请使用 "/"(不包括引号)进行分隔,或使用\\,避免出现目录混乱.
 * @author ChenXiaohong
 */
public class CommonFtpClient {
	private Logger logger = LoggerFactory.getLogger(getClass());
	/*** FTP连接客户端.*/
	private FTPClient client = null;
	/*** 默认编码.*/
	private String encode = "UTF-8";
	/*** FTP主机地址*/
	private String hostname;
	/*** FTP首选主机地址,出现可供选择的多个地址时,连接备用地址后重置为首先地址*/
	private final String preferencehost;
	/*** 备用主机地址*/
	private List<String> sparehosts = new ArrayList<String>();
	/*** 连接端口*/
	private int port;
	/*** 认证状态*/
	private boolean authenticated = false;
	/*** 默认工作目录*/
	private String workdir = null;
	/*** 登录用户名.*/
	private String username = null;
	/*** 用户密码*/
	private String password = null;
	/*** 连接超时时长*/
	private int connectTimeout = 30 * 1000;
	
	/** 文件备份日期详细类型.
	 * 文件上传过程服务器已存在该文件备份时的选项.
	 * @author ChenXiaohong
	 */
	public enum DatePattern {
		/*** 年份*/
		YEAR("yyyy"),
		/*** 月*/
		MONTH("yyyyMM"),
		/*** 天*/
		DAILY("yyyyMMdd"),
		/*** 小时*/
		HOUR("yyyyMMddHH"),
		MINUTE("yyyyMMddHHmm");
		
		public final String pattern;
		
		private DatePattern(String pattern) {
			this.pattern = pattern;
		}
	}
	
	/**
	 * @param hostname 主机地址
	 * @param port 端口,小于1时将使用默认端口
	 */
	public CommonFtpClient(String hostname, int port) {
		String[] hosts = hostname.split(",");
		// 添加备用主机列表
		Collections.addAll(sparehosts, hosts);
		sparehosts.remove(hosts[0]);
		//:~
		this.hostname = hosts[0];
		this.preferencehost = this.hostname;
		this.port = port;
		client = new FTPClient();
		FTPClientConfig config = new FTPClientConfig(
				FTPClientConfig.SYST_UNIX);
		config.setServerTimeZoneId(TimeZone.getDefault().getID());
		// 自动检测编码
		client.setAutodetectUTF8(true);
		// 连接超时设置
		client.setConnectTimeout(connectTimeout);
		// 设置编码
		client.setControlEncoding(encode);
		client.configure(config);
		// 打印通道命令日志
//		client.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out))); 
		client.addProtocolCommandListener(new ProtocolCommandListener() {
			public void protocolReplyReceived(ProtocolCommandEvent event) {
				System.out.println("< " + event.getMessage());
			}
			
			public void protocolCommandSent(ProtocolCommandEvent event) {
				System.out.println("> " + event.getMessage());
			}
		});
	}
	
	/** 注销登录.
	 * @return 是否成功
	 */
	public boolean logout() {
		boolean flag = false;
		try {
			// 默认重置
			authenticated = false;
			if(client.isConnected()) {
				flag = client.logout();
			} else {
				flag = true;
			}
		} catch (IOException e) {
			// 注销登录出现错误
			logger.error("Log out error.{}", e.getMessage());
		}
		return flag;
	}
	
	/**
	 * @return 登录是否成功
	 */
	public boolean login() {
		return login(username, password);
	}
	
	/** 登录FTP.
	 * @param username 用户名
	 * @param password 密码
	 * @return 是否成功
	 */
	public boolean login(String username, String password) {
		boolean flag = authenticated;
		try {
			// 匿名登录设置
			if(null == username || "".equals(username.trim())) {
				username = "anonymous";
				password = "anonymous";
			}
			if(!flag) {
				flag = client.login(username, password);
			}
			if(flag) {
				// 切换工作目录
				boolean changed = changeWorkdir(workdir);
				if(!changed) {
					// 尝试创建该目录并切换
					changed = createDirecroty(workdir);
				}
			} else {
				// 登录失败,请检查用户名或密码是否正确
				logger.info("Login failed, please check your user name or password is correct.");
			}
		} catch (IOException e) {
			logger.error("Log on to the server when an error occurs!error:{}", e.getMessage());
		}
		authenticated = flag;
		return authenticated;
	}
	
	/** 连接服务器.
	 * @return 连接是否成功
	 * @throws UnknownHostException 
	 */
	public boolean connect() throws UnknownHostException {
		InetAddress host = InetAddress.getByName(hostname);
		logger.info(String.format("Connecting to %s(%s) -> IP=%s PORT=%d", hostname, 
				host.getHostAddress(), host.getHostAddress(), (port>0 ? port : client.getDefaultPort())));
		boolean flag = false;
		try {
			// 连接
			logger.info("Connected to "+hostname+"("+host.getHostAddress()+")");
			if(port > 0) {
				client.connect(host, port);
			} else {
				// 使用默认端口
				client.connect(host);
			}
			flag = chkReply();
		} catch (SocketException e) {
			// 连接错误!请检查主机地址是否正确
			logger.error("Connection error! Check the host address({}) is correct.{}", host, e.getMessage());
		} catch (IOException e) {
			// 连接到主机时出现错误,请检查主机状态
			logger.error("Connected to the host when an error occurs, check the host({}) state.{}", host, e.getMessage());
		} catch (Exception e) {
			// 连接到主机时出现未知错误,请检查连接信息或主机状态
			logger.error("An unknown error occurred when connecting to the host({}), check the connection information or the host state.{}", host, e.getMessage());
		}
		if(!flag) {
			disconnect(true);
		}
		return flag;
	}

	/** 断开连接.
	 * @param force 是否强制断开
	 * @return 是否断开成功
	 */
	public boolean disconnect(boolean force) {
		try {
			if(force && client.isConnected()) {
				try {
					// 放弃当前操作
					client.abort();
				} catch (Exception e) {
					logger.error("Forced to suspend operation failed!{}", e.getMessage());
				}
			}
			// 检查认证状态,自动注销登录
			if(authenticated) {
				boolean logouted = logout();
				logger.info("automatic logout:{}", logouted);
			}
			client.disconnect();
			authenticated = false;
			logger.info("Disconnected with the server connections.");
		} catch (IOException e) {
			logger.error("Error when disconnected from the server!error:{}", e.getMessage());
		}
		return chkReply();
	}
	
	/** 检查响应状态.
	 * @param reply 响应状态码{@link FTPReply}
	 * @return 是否成功
	 */
	private boolean checkReply(int reply) {
		boolean flag = false;
		try {
			flag = FTPReply.isPositiveCompletion(reply);
		} catch (Exception e) {
			// 检查状态信息时出现异常
			logger.error("Check the status information when abnormal!{}",e.getMessage());
		}
		return flag;
	}
	
	/** 切换工作目录.
	 * @param pathname 目录
	 * @return 是否成功
	 */
	public boolean changeWorkdir(String pathname) {
		boolean flag = false;
		try {
			// 当前工作目录
			String curworkdir = client.printWorkingDirectory();
			flag = client.changeWorkingDirectory(new String(pathname.getBytes(), client.getControlEncoding()));  
			if(!flag) {
				logger.error("Working directory({}) failed to switch!", pathname);
				// 切换失败时返回初始目录
				client.changeWorkingDirectory(curworkdir);
			}
			logger.info("WorkDirectory changed:" + client.printWorkingDirectory());
		} catch (IOException e) {
			// 切换工作目录时出现错误
			logger.error("Switch the working directory error!{}", e.getMessage());
		}
		return flag;
	}
	
	/** 创建远程目录.
	 * 远程目录分隔符请使用 "/"(不包括引号)
	 * @param remote 远程目录
	 * @return 是否创建成功
	 * @throws IOException
	 */
	public boolean createDirecroty(String remote)
			throws IOException {
		boolean changed = false;
		String directory = remote.substring(0, remote.lastIndexOf("/") + 1);
		if (!directory.equalsIgnoreCase("/")
				&& !client.changeWorkingDirectory(new String(directory
						.getBytes(encode), "iso-8859-1"))) {
			// 如果远程目录不存在，则递归创建远程服务器目录
			int start = 0;
			int end = 0;
			if (directory.startsWith("/")) {
				client.changeWorkingDirectory("/");
				start = 1;
			} else {
				start = 0;
			}
			end = directory.indexOf("/", start);
			// 创建所有目录
			while (true) {
				String subDirectory = new String(remote.substring(start, end)
						.getBytes(encode), "iso-8859-1");
				if (!client.changeWorkingDirectory(subDirectory)) {
					if (client.makeDirectory(subDirectory)) {
						changed = client
								.changeWorkingDirectory(subDirectory);
					} else {
						changed = false;
						logger.error("Remote directory({}) creation failed", remote);
						// 上传目录无法创建
						// 文件无法进行上传,服务器连接将断开
						logger.error("Create upload directory({}) failed! File could not be uploaded will be disconnected! ", remote);
						disconnect(true);
					}
				}
				start = end + 1;
				end = directory.indexOf("/", start);
				// 检查所有目录是否创建完毕
				if (end <= start) {
					break;
				}
			}
		}
		return changed;
	}

	
	
	/** 上传文件.
	 * @param remote 远程文件名
	 * @param local 本地文件
	 * @throws IOException 
	 */
	public boolean upload(String remote, File local, FtpTransferProcessListener listen) throws IOException {
		boolean flag = false;
		if(null == remote || "".equals(remote)) {
			remote = local.getName();
		}
		InputStream uploadis = null;
		try {
			// 断点长度
			long skip = 0;
			uploadis = new FileInputStream(local);
			// 二进制传输
			client.setFileType(FTP.BINARY_FILE_TYPE);
			// ftp.setFileType(FTP.ASCII_FILE_TYPE);
			// 使用被动模式
			client.enterLocalPassiveMode();
			// 设置流传输模式
			client.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);  
			// 检查服务器文件
			FTPFile[] ftpfiles = listFile(remote, true);
			if(ftpfiles.length > 0) {
				// 判断已存在的文件是否需要断点续传
				// 在服务器找到该文件,正在检查文件
				logger.info("Find the file({}) on the server is checking the file..", (client.printWorkingDirectory()) + File.separator + remote);
				// 文件上传只比对第一个文件
				FTPFile remote_file = ftpfiles[0];
				if(remote_file.getSize() == local.length()) {
					// 长度相等
					// 不再上传,设置为已传输成功
					// 在服务器中找到同名且长度相同文件({}),取消上传
					logger.info("Find the same name and length of the same file ({}), cancel the upload in the server.", remote);
					flag = true;
					return flag;
				} else if(remote_file.getSize() > local.length()) {
					// 大于本地文件长度
					// 备份服务器文件,重新上传
					// 服务器已存在同名文件,长度大于本地文件,将备份服务器文件后上传
					logger.info("The server file with the same name already exists, the length is greater than the local file, the backup server file upload.");
					String prefix = remote_file.getName().substring(0, remote_file.getName().lastIndexOf("."));
					String suffix = remote_file.getName().substring(remote_file.getName().lastIndexOf("."));
					String remote_new_name = prefix + "(1)" + suffix;
					String backupname = backupFile(remote_file.getName(), remote_new_name);
					// 服务器文件已备份
					logger.info("Server files have been backed up:{} > {}", remote_file.getName(), backupname);
				} else {
					// 小于本地文件长度
					// 服务器文件长度小于本地长度,尝试断点续传
					logger.info("The server file length is less than the local length, try resuming uploading..");
					// 断点续传
					// 设置续传位置
					client.setRestartOffset(ftpfiles[0].getSize());
					// 跳过数据
					uploadis.skip(ftpfiles[0].getSize());
					skip = ftpfiles[0].getSize();
				}
			}
			// 上传进度打印
			client.setCopyStreamListener(new CopyStreamAdapter(skip, local.length(), listen));
			// 上传
			// 开始上传文件
			logger.info("start uploading the data file({}) to {}...", local.getPath(), (client.printWorkingDirectory()) + File.separator + remote);
			logger.info("File Length:{}{}", (local.length() - skip)/1024l/1024l, "MB");
			flag = client.storeFile(remote, uploadis);
			// 文件上传完成
			if(flag) {
				logger.info("File ({}) the upload is complete.", local.getPath());
			}
		} catch (IOException e) {
			throw e;
		} finally { 
			if(null != uploadis) {
				try {
					uploadis.close();
				} catch (IOException e) {
				}
			}
		}
		return flag;
	}
	
	/** 文件列表.
	 * @param pathname 过滤文件名
	 * @param lenient
	 * @return 文件列表
	 */
	public FTPFile[] listFile(String pathname, boolean lenient) {
		if(lenient) {
			FTPClientConfig config = new FTPClientConfig();
            config.setLenientFutureDates(true);
            client.configure(config);
		}
		FTPFile[] files = new FTPFile[]{};
		try {
			files = client.listFiles(pathname);
		} catch (IOException e) {
			// 获取服务器文件列表出现异常
			logger.error("Access to server file list to abnormal!{}", e.getMessage());
		}
		return files;
	}
	
	
	/** 备份Ftp服务器文件,根据文件日期进行备份.
	 * 由 {@link FTPClient#rnfr(String)} 和 {@link FTPClient#rnto(String)} 进行文件的移动
	 * @param from 源文件路径
	 * @param to 备份路径
	 */
	public void backupByDate(String from, String to) {
		//
	}
	
	/** 备份服务器文件,已存在的备份文件自动设置下标
	 * @param from
	 * @param to
	 * @throws IOException 
	 */
	private String backupFile(String from, String to) throws IOException {
		boolean flag = false;
		// 最大备份数量
		int max_backup = 5;
		String index = "";
		String newname = to;
		while(!flag) {
			if(!"".equals(index)) {
//				int indexof = to.lastIndexOf(".");
				newname = newname.replaceFirst("\\(\\d+\\)", String.format("(%s)", index));
//				newname = to.substring(0, indexof) + String.format("(%s)", index) + to.substring(indexof);
			}
			FTPFile[] files = listFile(newname, true);
			if(files.length > 0) {
				if("".equals(index)) {
					index = "0";
				}
				int next_index = Integer.parseInt(index) + 1;
				if(next_index > max_backup) {
					flag = true;
					// 下标大于5时将直接删除服务器文件
					client.deleteFile(from);
					newname = "DELETED";
				} else {
					index = String.valueOf(next_index);
				}
				continue;
			} else {
				flag = true;
				try {
					client.rename(from, newname);
				} catch (IOException e) {
					// 重命名失败,删除服务器文件
					client.deleteFile(from);
					newname = "DELETED";
				}
			}
		}
		return newname;
	}
	
	/**
	 * @return 响应是否成功
	 */
	private boolean chkReply() {
		boolean flag = false;
		try {
			flag = checkReply(client.getReplyCode());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return flag;
	}
	
	/**
	 * @return 是否已连接
	 */
	public boolean isConnected() {
		return client.isConnected();
	}
	
	/**
	 * @return 是否已认证
	 */
	public boolean isAuthenticated() {
		return authenticated;
	}
	
	/**
	 * @param workdir 默认工作目录
	 */
	public void setWorkdir(String workdir) {
		this.workdir = workdir.replace("\\", File.separator);
	}
	
	public String getWorkdir() {
		return workdir;
	}
	
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}
	
	public String getHostname() {
		return hostname;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public int getPort() {
		return port;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public String getUsername() {
		return username;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
	
	public String getPassword() {
		return password;
	}
	
	public FTPClient getClient() {
		return client;
	}
	
	/**
	 * @return 备用Ftp服务器
	 */
	public List<String> getSparehosts() {
		return sparehosts;
	}
	
	public String getPreferencehost() {
		return preferencehost;
	}
}

/** 数据传输进度监听器.
 * @author ChenXiaohong
 */
class CopyStreamAdapter implements CopyStreamListener {
	/*** 数据流总长度*/
	private long localsize = 0;
	private long remoteSize = 0;
	// 进度更新
	private FtpTransferProcessListener ftpl = null;
	
	private java.text.DecimalFormat df = new java.text.DecimalFormat("0.00");
	
	double step = 0;
	double process = 0;
	
	public CopyStreamAdapter(long remoteSize, long localsize, FtpTransferProcessListener listen) {
		this.localsize = localsize;
		this.remoteSize = remoteSize;
		this.ftpl = listen;
		step = (this.localsize / 100d);
		process = this.remoteSize / step;
	}

	public CopyStreamAdapter(long remoteSize, long localsize) {
		this(remoteSize, localsize, null);
	}
	
	public void bytesTransferred(CopyStreamEvent event) {
		//
	}

	public void bytesTransferred(long totalBytesTransferred,
			int bytesTransferred, long streamSize) {
		if (totalBytesTransferred / step != process) {
			process = (totalBytesTransferred + remoteSize) / step;
			// 更新上传进度
			update(Double.valueOf(df.format(process)));
		}
	}
	
	/**
	 * @param process 上传进度
	 */
	public void update(double process) {
		// 上报进度
		try {
			if(null != ftpl) {
				ftpl.process(process);
			}
		} catch (Exception e) {
		}
	}
}