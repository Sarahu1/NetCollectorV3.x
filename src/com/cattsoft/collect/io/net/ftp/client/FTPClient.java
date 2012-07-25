/**
 * 
 */
package com.cattsoft.collect.io.net.ftp.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;

import org.slf4j.LoggerFactory;

import com.cattsoft.collect.io.net.ftp.FTP;
import com.cattsoft.collect.io.net.ftp.exception.FTPException;
import com.cattsoft.collect.io.net.ssh.jsch.Logger;


/**
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public abstract class FTPClient implements FTP {
	/**
	 * 连接主机地址名称
	 */
	String hostname = null;
	
	/**
	 * 登录用户名称(默认: anonymous)
	 */
	String username = "anonymous";
	
	/**
	 * 登录用户密码
	 */
	String password = "";
	
	/**
	 * 当前工作目录
	 */
	String pwd = null;
	
	/**
	 * 连接端口(默认:21)
	 */
	int port = 21;
	
	/**
	 * 超时时长(毫秒),默认为(8秒)
	 */
	long timeout = 1000 * 8;
	
	/**
	 * 文件备份最大数量
	 */
	private int maxbackup = 5;
	
	/**
	 * 首先主机地址
	 */
	private String priorityHost = null;
	
	/**
	 * 备用主机地址
	 */
	private String[] standbyHost = new String[]{};
	

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#connect(java.lang.String, int)
	 */
	@Override
	public boolean connect(String host, int port) throws FTPException {
		this.hostname = host;
		this.port = port;
		
		// 连接
		connect();
		
		return isConnected();
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#backupFile(java.lang.String, java.lang.String)
	 */
	public String backupFile(String src, String dest) throws FTPException {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#backup(java.lang.String, java.lang.String)
	 */
	@Override
	public String backup(File src, String folder) {
		if(!src.exists())
			return null;
		SimpleDateFormat monthSdf = new SimpleDateFormat("yyyyMM");
		File backupfolder = new File(folder + File.separator + monthSdf.format(System.currentTimeMillis()));
		// 是否为ZIP文件,读取备注信息
		if(src.getName().matches(".*\\.(?i)zip")) {
			
		}
		if(!backupfolder.exists()) {
			backupfolder.mkdirs();
		}
		// 备份文件路径
		File backupFile = new File(backupfolder.getPath(), src.getName());
		// 已存在时删除
		if(backupFile.exists()) {
			backupFile.delete();
		}
		// 重命名
		boolean backuped = src.renameTo(backupFile);
		if(!backuped) {
			// 重命名方式不成功
			// 其它方法
			FileInputStream fis = null;
			FileOutputStream fos = null;
			try {
				fis = new FileInputStream(src);
				fos = new FileOutputStream(backupFile);
				byte[] buffer = new byte[1024 * 4];
				int n = 0;
				while (-1 != (n = fis.read(buffer))) {
					fos.write(buffer, 0, n);
				}
				backuped = true;
			} catch(Exception e) {
				// 备份失败
			} finally {
				try {
					if(null != fos)
						fos.close();
					if(null != fis)
						fis.close();
				} catch (Exception e2) {
				}
			}
			
		}
		return backuped ? backupFile.getPath() : null;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#login()
	 */
	@Override
	public boolean login() throws FTPException {
		return login(getUsername(), password);
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#logout()
	 */
	public void logout() {
		
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#cwd()
	 */
	public boolean cwd() throws FTPException {
		// 切换到默认目录
		return cwd(this.pwd);
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#upload(java.lang.String, java.io.InputStream)
	 */
	@Override
	public boolean upload(String filename, InputStream input)
			throws FTPException {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#upload(java.io.File)
	 */
	@Override
	public boolean upload(File file) throws FTPException {
		return upload(pwd, file, null);
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#upload(java.lang.String, java.io.File)
	 */
	@Override
	public boolean upload(String directory, File file) throws FTPException {
		return upload(directory, file, null);
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.FTP#upload(java.lang.String, java.io.File, boolean, com.cattsoft.collect.io.net.ftp.client.FtpTransferProcessListener)
	 */
	@Override
	public boolean upload(String directory, File file, boolean backup,
			FtpTransferProcessListener listener) throws FTPException {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#upload(java.lang.String, java.io.File, com.cattsoft.collect.transfer.ftp.FtpTransferProcessListener)
	 */
	@Override
	public boolean upload(String directory, File file,
			FtpTransferProcessListener listener) throws FTPException {
		return upload(directory, file, true, listener);
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#isLogged()
	 */
	@Override
	public abstract boolean isLogged();
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#isConnected()
	 */
	@Override
	public abstract boolean isConnected();
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#download(java.lang.String, java.io.OutputStream)
	 */
	@Override
	public boolean download(String filepath, OutputStream output)
			throws FTPException {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#download(java.lang.String)
	 */
	@Override
	public boolean download(String filepath, String dest) throws FTPException {
		return download(filepath, dest, null);
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#download(java.lang.String, java.lang.String, com.cattsoft.collect.transfer.ftp.FtpTransferProcessListener)
	 */
	@Override
	public boolean download(String filepath, String dest,
			FtpTransferProcessListener listener) throws FTPException {
		return false;
	}
	
	/**
	 * @return the hostname
	 */
	public String getHostname() {
		return hostname;
	}
	
	/**
	 * @return the port
	 */
	public int getPort() {
		return port;
	}
	
	/**
	 * @param username the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}
	
	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}
	
	/**
	 * @param password the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}
	
	/**
	 * @param timeout the timeout to set
	 */
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
	
	/**
	 * @return the timeout
	 */
	public long getTimeout() {
		return timeout;
	}
	
	/**
	 * @return the priorityHost
	 */
	public String getPriorityHost() {
		return priorityHost;
	}
	
	/**
	 * @param priorityHost the priorityHost to set
	 */
	public void setPriorityHost(String priorityHost) {
		this.priorityHost = priorityHost;
	}
	
	/**
	 * @param standbyHost the standbyHost to set
	 */
	public void setStandbyHost(String[] standbyHost) {
		this.standbyHost = standbyHost;
	}
	
	/**
	 * @return the standbyHost
	 */
	public String[] getStandbyHost() {
		return standbyHost;
	}
	
	/**
	 * @param maxbackup the maxbackup to set
	 */
	public void setMaxbackup(int maxbackup) {
		this.maxbackup = maxbackup;
	}
	
	/**
	 * @return the maxbackup
	 */
	public int getMaxbackup() {
		return maxbackup;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#mkdir(java.lang.String)
	 */
	@Override
	public void mkdir(String directory) throws FTPException {
		directory = directory.replaceAll("\\\\", "/");
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.FTP#ls()
	 */
	@Override
	public Object[] ls(String pathname) throws FTPException {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#rm(java.lang.String)
	 */
	@Override
	public boolean rm(String filepath) {
		return false;
	}
	
	/** 重命名文件
	 * @param from 文件名称
	 * @param to 新名称
	 * @return 是否成功
	 */
	public boolean rename(String from, String to) {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#printWorkingDirectory()
	 */
	@Override
	public String printWorkingDirectory() {
		return null;
	}
	
	/**
	 * @return the logger
	 */
	public Logger getLogger(final Class<?> cls) {
		// 检测
		boolean checked = true;
		try {
			// org.slf4j.Logger
			ClassLoader.getSystemClassLoader().loadClass("org.slf4j.Logger");
		} catch (Exception e) {
			checked = false;
		}
		try {
			// org.slf4j.Logger
			ClassLoader.getSystemClassLoader().loadClass("java.util.logging.Logger");
		} catch (Exception e) {
			checked = false;
		}
		return checked ? new FTPLogger(cls) : new Logger() {
			public void log(int level, String message) {
				System.out.println(message);
			}
			@Override
			public boolean isEnabled(int level) {
				return true;
			}
		};
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.FTP#getHost()
	 */
	@Override
	public abstract String getHost();
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#getHome()
	 */
	@Override
	public String getHome() {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#randomlyHost()
	 */
	@Override
	public String randomlyHost() {
		if(getStandbyHost().length > 0) {
			// 获取随机地址
			java.util.List<String> random_list = Arrays.asList(getStandbyHost());
			// 随机打乱列表顺序
			Collections.shuffle(random_list, new java.util.Random());
			return random_list.get(0).trim();
		}
		// 返回首先地址
		return getPriorityHost().trim();
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#disconnect()
	 */
	@Override
	public void disconnect() {
		disconnect(false);
	}
	
	class FTPLogger implements Logger {
		org.slf4j.Logger sl4j_logger = null;
		java.util.logging.Logger lang_logger = null;
		/**
		 * 
		 */
		public FTPLogger(Class<?> cls) {
			try {
				sl4j_logger = LoggerFactory.getLogger(cls);
			} catch (Error e) {
				try {
					lang_logger = java.util.logging.Logger.getLogger(cls.getName());
				} catch (Error e2) {
					// 无法记录日志
				}
			}
		}
		
		public boolean isEnabled(int level) {
			return true;
		}

		public void log(int level, String message) {
			switch (level) {
			case Logger.DEBUG:
				if(null != sl4j_logger)
					sl4j_logger.debug(message);
				else if(null != lang_logger)
					lang_logger.info(message);
				else
					System.out.println(message);
				break;
			case Logger.INFO:
				if(null != sl4j_logger)
					sl4j_logger.info(message);
				else if(null != lang_logger)
					lang_logger.info(message);
				else
					System.out.println(message);
				break;
			case Logger.ERROR:
				if(null != sl4j_logger)
					sl4j_logger.error(message);
				else if(null != lang_logger)
					lang_logger.severe(message);
				else
					System.out.println(message);
				break;
			default:
				break;
			}
		}
	}
}
