/**
 * 
 */
package com.cattsoft.collect.io.net.ftp.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Arrays;
import java.util.TimeZone;

import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;

import com.cattsoft.collect.io.net.ftp.exception.FTPException;
import com.cattsoft.collect.io.net.ssh.jsch.Logger;

/** FTP 文件传输(Basic)
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class NFTPClient extends FTPClient {
	private Logger logger = getLogger(NFTPClient.class);
	/**
	 * FTP 传输通道
	 */
	private org.apache.commons.net.ftp.FTPClient ftp = null;
	/**
	 * 是否已登录
	 */
	private boolean logged = false;
	
	/**
	 * @param hostname 主机地址
	 * @param port 端口
	 * @param workdir 工作目录
	 */
	public NFTPClient(String hostname, int port, String workdir) {
		this(hostname, port, workdir, null, workdir);
	}
	
	/**
	 * @param hostname 主机地址
	 * @param port 端口
	 * @param username 用户名
	 * @param password 密码
	 * @param workdir 工作目录
	 */
	public NFTPClient(String hostname, int port, String username, String password, String workdir) {
		String[] hosts = hostname.split(",");
		// 设置备用主机地址
		setStandbyHost(Arrays.copyOfRange(hosts, 1, hosts.length));
		// 设置首先地址
		setPriorityHost(hosts[0].trim());
		this.hostname = getPriorityHost();
		// SFTP连接默认端口为 21
		this.port = (port > 0 ? port : this.port);
		this.username = username;
		this.password = password;
		this.pwd = workdir;
		ftp = new org.apache.commons.net.ftp.FTPClient();
		FTPClientConfig config = new FTPClientConfig(
				FTPClientConfig.SYST_UNIX);
		config.setServerTimeZoneId(TimeZone.getDefault().getID());
		// 自动检测编码
		ftp.setAutodetectUTF8(true);
		// 连接超时设置
		ftp.setConnectTimeout((int) getTimeout());
		// 设置编码
		ftp.setControlEncoding("UTF-8");
		ftp.configure(config);
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#loging(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean login(String username, String password) throws FTPException {
		try {
			// 登录
			logged = ftp.login(username, password);
			if(logged) {
				// 切换工作目录
				try {
					cwd(pwd);
				} catch (Exception e) {
					// 尝试创建该目录
					try {
						mkdir(pwd);
						// 重试切换
						cwd(pwd);
					} catch (Exception e2) {
						throw new FTPException("登录服务器成功,但切换工作目录时出现异常!", e);
					}
				}
			} else {
				logger.log(Logger.ERROR, "登录失败,请检查用户名或密码是否正确!");
			}
		} catch(FTPException e) {
			throw e;
		} catch (Exception e) {
			throw new FTPException("登录服务器时出现异常!", e);
		}
		return isLogged();
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#isConnected()
	 */
	@Override
	public boolean isConnected() {
		boolean connected = ftp.isConnected();
		if(!connected)
			logged = false;
		return connected;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#isLogged()
	 */
	@Override
	public boolean isLogged() {
		return isConnected() ? logged : false;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#logout()
	 */
	@Override
	public void logout() {
		super.logout();
		try {
			logged = false;
			ftp.logout();
		} catch (Exception e) {
		}
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#getHost()
	 */
	@Override
	public String getHost() {
		try {
			return ftp.getRemoteAddress().getHostAddress();
		} catch (Exception e) {
		}
		return hostname;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#connect()
	 */
	@Override
	public boolean connect() throws FTPException {
		boolean connected = false;
		try {
			ftp.connect(hostname, port);
			connected = chkReply();
		} catch (SocketException e) {
			throw new FTPException("连接错误!请检查主机地址(" +hostname+ ")是否正确." + e.getMessage(), e);
		} catch (IOException e) {
			throw new FTPException("连接到主机时出现错误,请检查主机("+hostname+")状态." + e.getMessage(), e);
		} catch (Exception e) {
			throw new FTPException("连接到主机(" +hostname+ ")时出现未知错误,请检查连接信息或主机状态." + e.getMessage(), e);
		}
		return connected;
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#upload(java.lang.String, java.io.File, com.cattsoft.collect.transfer.ftp.FtpTransferProcessListener)
	 */
	@Override
	public boolean upload(String directory, File file, boolean backup,
			FtpTransferProcessListener listener) throws FTPException {
		boolean uploaded = false;
		// 检查目录
		boolean cwded = false;
		try {
			directory = directory.replaceAll("\\\\", "/");
			if(directory.endsWith("/") && directory.length() > 1)
				directory = directory.substring(0, directory.length() - 1);
			cwded = ftp.changeWorkingDirectory(directory);
		} catch (Exception e) {
		}
		if(null != directory && !cwded) {
			// 创建并切换目录
			logger.log(Logger.INFO, "上传目录不存在,正在创建目录(" + directory + ")");
			mkdir(directory);
		}
		if(null != directory && !directory.equals(printWorkingDirectory())) {
			logger.log(Logger.INFO, "正在切换至目录" + directory);
			cwd(directory);
		}
		// 临时文件名称
		String tmp_filename = file.getName() + "._tmp";
		// 查找断点位置
		long offset = 0;
		// 查找同名文件是否存在
		FTPFile[] ftp_files = listFiles(file.getName(), true);
		if(null != ftp_files && ftp_files.length > 0) {
			// 比对文件长度
			uploaded = ftp_files[0].getSize() == file.length();
			if(!uploaded && backup) {
				// 长度不同, 备份服务器文件
				String backupPath = null;
				try {
					logger.log(Logger.INFO, "存在同名文件,正在备份服务器文件..");
					backupPath = backupFile(directory + File.separator + file.getName(), null);
				} catch (Exception e) {
				}
				if(null == backupPath) {
					logger.log(Logger.INFO, "服务器文件与本地文件比对失败,删除服务器文件..");
					rm(file.getName());
				} else {
					logger.log(Logger.INFO, "文件("+ directory + File.separator + file.getName() +")备份成功,备份路径:" + backupPath);
				}
			} else if(!backup) {
				rm(file.getName());
			} else {
				// 设置监听器传输完成类型为 文件已存在
				if(null != listener) {
					listener.setCompleteType(FtpTransferProcessListener.COMPLETE_EXISTS);
					listener.complete();
				}
				logger.log(Logger.INFO, "在服务器中找到同名且长度相同文件(" +file.getName()+ "),完成上传");
			}
		} else if(!uploaded) {
			// 查找服务器临时文件
			FTPFile[] tmp_files = listFiles(tmp_filename, true);
			if(null != tmp_files && tmp_files.length > 0) {
				if(tmp_files[0].getSize() > file.length()) {
					// 临时文件大于本地文件,删除临时文件
					logger.log(Logger.INFO, "服务器文件大于本地文件,正在删除服务器文件..");
					rm(tmp_filename);
				} else {
					// 设置文件断点上传位置
					offset = tmp_files[0].getSize();
					ftp.setRestartOffset(offset);
					if(null != listener)
						listener.setCompleteType(FtpTransferProcessListener.COMPLETE_HTTP);
					logger.log(Logger.INFO, "服务器已存在该文件,续传断点位置:" + offset);
				}
			}
		}
		if(!uploaded) {
			// 删除原文件
			rm(file.getName());
			// 上传文件
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(file);
				fis.skip(offset);
				// 上传进度打印
				ftp.setCopyStreamListener(new CopyStreamAdapter(offset, file.length(), listener));
				// 二进制传输
				ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
				// ftp.setFileType(FTP.ASCII_FILE_TYPE);
				// 使用被动模式
				ftp.enterLocalPassiveMode();
				// 设置流传输模式
				ftp.setFileTransferMode(org.apache.commons.net.ftp.FTP.STREAM_TRANSFER_MODE);  
				uploaded = ftp.storeFile(tmp_filename, fis);
				if(uploaded) {
					// 重命名临时文件
					ftp.rename(tmp_filename, file.getName());
				}
				logger.log(Logger.INFO, "文件上传完成,正在较验文件..");
				// 验证文件
				FTPFile[] ftpfiles = listFiles(file.getName(), true);
				if(null != ftpfiles && ftpfiles.length > 0) {
					uploaded = ftpfiles[0].getSize() == file.length();
					if(!uploaded) {
						logger.log(Logger.INFO, "文件较验失败!数据可能已损坏,正在删除文件..");
						rm(file.getName());
						logger.log(Logger.INFO, "已删除服务器文件!上传失败");
					}
				} else {
					uploaded = false;
				}
				if(!uploaded)
					throw new FTPException("文件较验失败!数据可能已损坏,已删除文件");
			} catch (Exception e) {
				throw new FTPException("文件上传出现异常,上传失败!", e);
			} finally {
				try {
					if(null != fis)
						fis.close();
				} catch (Exception e2) {
				}
			}
		}
		if(uploaded) {
			logger.log(Logger.INFO, "文件上传成功,文件路径:" + directory + File.separator + file.getName());
		}
		return uploaded;
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#download(java.lang.String, java.lang.String, com.cattsoft.collect.transfer.ftp.FtpTransferProcessListener)
	 */
	@Override
	public boolean download(String filepath, String dest,
			FtpTransferProcessListener listener) throws FTPException {
		boolean downloaded = false;
		File file = new File(filepath);
		File local = new File(dest);
		// 临时文件
		File tmp_file = new File(file.getName() + "._tmp");
		OutputStream fos = null;
		try {
			if(null != file.getParentFile()) {
				// 切换工作目录
				cwd(file.getParentFile().getPath());
			}
			if(null != local.getParentFile()) {
				tmp_file = new File(local.getParentFile(), tmp_file.getName());
			}
			// 查找文件,确定文件是否存在
			FTPFile[] ftpFiles = listFiles(file.getName(), true);
			if(null == ftpFiles || ftpFiles.length == 0) {
				throw new FTPException("未在服务器找到该文件("+ filepath +")");
			}
			if(tmp_file.exists()) {
				// 临时文件已存在
				ftp.setRestartOffset(tmp_file.length());
				logger.log(Logger.INFO, "本地文件已存在, 续传断点位置:" + ftp.getRestartOffset());
			} else {
				tmp_file.createNewFile();
			}
			// 文件输出流
			fos = new FileOutputStream(tmp_file, true);
			// 下载
			ftp.enterLocalPassiveMode(); 
			ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE); 
			ftp.setCopyStreamListener(new CopyStreamAdapter(tmp_file.length(), ftpFiles[0].getSize(), listener));
			downloaded = ftp.retrieveFile(ftpFiles[0].getName(), fos);
		} catch (Exception e) {
			throw new FTPException("文件下载出现异常!下载失败!", e);
		} finally {
			try {
				if(null != fos) {
					fos.flush();
					fos.close();
				}
			} catch (Exception e2) {
			}
		}
		if(downloaded) {
			// 下载已完成
			// 重命名临时文件
			tmp_file.renameTo(local);
			logger.log(Logger.INFO, "文件下载成功!");
		} else {
			logger.log(Logger.ERROR, "文件下载失败!");
		}
		return downloaded;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#rm(java.lang.String)
	 */
	@Override
	public boolean rm(String filepath) {
		super.rm(filepath);
		try {
			return ftp.deleteFile(filepath);
		} catch (Exception e) {
			logger.log(Logger.ERROR, "删除文件时出现异常!" + e.getMessage());
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#rename(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean rename(String from, String to) {
		try {
			return ftp.rename(from, to);
		} catch (IOException e) {
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#printWorkingDirectory()
	 */
	@Override
	public String printWorkingDirectory() {
		try {
			return ftp.printWorkingDirectory();
		} catch (Exception e) {
		}
		return pwd;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#ls(java.lang.String)
	 */
	@Override
	public Object[] ls(String pathname) {
		try {
			return ftp.listFiles(pathname);
		} catch (IOException e) {
		}
		return new Object[]{};
	}
	
	/** 遍历服务器文件.
	 * @param filename 文件名称
	 * @return 文件列表
	 */
	private FTPFile[] listFiles(String filename, boolean lenient) {
		if(lenient) {
			FTPClientConfig config = new FTPClientConfig();
            config.setLenientFutureDates(true);
            ftp.configure(config);
		}
		try {
			return ftp.listFiles(filename, new FTPFileFilter() {
				public boolean accept(FTPFile ftpfile) {
					return ftpfile.isFile();
				}
			});
		} catch (Exception e) {
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#disconnect(boolean)
	 */
	@Override
	public void disconnect(boolean abort) {
		// 注销登录
		logout();
		// 断开连接
		try {
			try {
				if(abort) {
					ftp.abort();
				}
			} catch (Exception e) {
			}
			logged = false;
			ftp.disconnect();
		} catch (Exception e) {
		}
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#cwd(java.lang.String)
	 */
	@Override
	public boolean cwd(String directory) throws FTPException {
		if(null == directory)
			return false;
		try {
			if(!ftp.changeWorkingDirectory(new String(directory.replaceAll("\\\\", "/").getBytes(), ftp.getControlEncoding())))
				throw new FTPException("目录不存在!");
		} catch (Exception e) {
			throw new FTPException("切换工作目录("+directory+")时出现错误!", e);
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#mkdir(java.lang.String)
	 */
	@Override
	public void mkdir(String directory) throws FTPException {
		super.mkdir(directory);
		// 原工作目录
		String oriWorkDir = null;
		try {
			oriWorkDir = ftp.printWorkingDirectory();
		} catch (Exception e) {
		}
		boolean changed = false;
		try {
			cwd(directory);
		} catch (Exception e) {
			changed = false;
		}
		try {
			if(!changed) {
				int nextindex = 0;
				if (directory.startsWith("/")) {
					// 切换至根目录
					cwd("/");
					nextindex = 1;
				}
				// 循环逐级创建目录
				int index = 0;
				while(index > -1 && index < directory.length()) {
					index = directory.indexOf("/", index + 1);
					if(index < 0) {
						if(nextindex - 1 == directory.length())
							break;
						else
							index = directory.length() - 1;
					}
					String subdir = directory.substring(nextindex, index + 1);
					nextindex = index + 1;
					if(subdir.endsWith("/")) {
						subdir = subdir.substring(0, subdir.length() - 1);
					}
					if("".equals(subdir))
						break;
					if(!ftp.changeWorkingDirectory(subdir)) {
						if(ftp.makeDirectory(subdir)) {
							// 切换
							cwd(subdir);
						}
					}
				}
			}
		} catch (Exception e) {
			throw new FTPException("创建目录("+ directory +")失败!", e);
		} finally {
			try {
				if(null != oriWorkDir) {
					// 切换为原工作目录
					ftp.changeWorkingDirectory(oriWorkDir);
				}
			} catch (Exception e2) {
				logger.log(Logger.ERROR, "切换至工作目录(" +oriWorkDir+ ")时出现异常!");
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#backupFile(java.lang.String, java.lang.String)
	 */
	@Override
	public String backupFile(String src, String dest) throws FTPException {
		File file = new File(src.replaceAll("\\\\", "/"));
		// 备份文件
		File backupFile = null;
		try {
			// 查找服务器是否存在该文件,存在进行下标递增
			// 新文件名称
			String newname = file.getName();
			// 文件备份下标起始值
			int index = 1;
			while(true) {
				FTPFile[] ftpfiles = null;
				try {
					// 替换路径分隔符
					ftpfiles = listFiles(newname, true);
				} catch (Exception e) {
				}
				if(null != ftpfiles && ftpfiles.length > 0) {
					// 备份下标递增
					index++;
					// 后缀名
					int extIndex = newname.lastIndexOf(".");
					String ext = newname.substring(extIndex > -1 ? extIndex : newname.length() - 1);
					String regex = ".*\\s\\(\\d+\\)" + ext.replaceAll("\\.", "\\\\.");
					if(!newname.matches(regex)) {
						newname = newname.substring(0, newname.lastIndexOf(".")) + 
								" ("+ index +")" + newname.substring(newname.lastIndexOf("."));
					}
					// 是否到达最大备份数量
					if(index > getMaxbackup()) {
						// 重命名文件, 下标由1开始
						for (int i = 2; i <= getMaxbackup(); i++) {
							String back_name = newname.replaceFirst(" \\(\\d+\\)", String.format(" (%s)", i));
							String back_newname = back_name.replaceFirst(" \\(\\d+\\)", String.format(" (%s)", i - 1));
							if(i == 2) {
								// 删除基础下标文件
								rm(file.getName());
								back_newname = file.getName();
							} 
							if(i <= getMaxbackup()) {
								// 重命名
								rename(back_name, back_newname);
							}
						}
						break;
					}
					newname = newname.replaceFirst(" \\(\\d+\\)", String.format(" (%s)", index));
				} else {
					if(index == 2) {
						index = 1;
					}
					break;
				}
			}
			logger.log(Logger.INFO, "正在备份文件..");
			backupFile = new File(newname);
			// 存在指定文件名
			if(null != dest && !"".equals(dest)) {
				newname = dest;
			}
			if(null != file.getParentFile()) {
				// 存在父级目录
				backupFile = new File(file.getParentFile(), newname);
			}
			try {
				// 删除
				ftp.deleteFile(backupFile.getName());
			} catch (Exception e) {
				// 删除的文件不存在时将抛出异常
			}
			ftp.rename(file.getName(), backupFile.getName());
		} catch (Exception e) {
			throw new FTPException("文件备份失败!", e);
		}
		return backupFile.getPath();
	}
	
	/**
	 * @return 响应是否成功
	 */
	private boolean chkReply() {
		return checkReply(ftp.getReplyCode());
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
			logger.log(Logger.ERROR, "检查状态信息时出现异常!" + e.getMessage());
		}
		return flag;
	}
	
	/** 数据传输进度监听器.
	 * @author ChenXiaohong
	 */
	class CopyStreamAdapter implements CopyStreamListener {
		/**
		 * 已传输长度
		 */
		private long length = 0;
		/**
		 * 待传数据总长度
		 */
		private long total = 0;
		/**
		 * 传输进度百分比
		 */
		private long percent = -1;
		/**
		 * 进度通知
		 */
		private FtpTransferProcessListener listener = null;
		
		public CopyStreamAdapter(long length, long total, FtpTransferProcessListener listener) {
			// 使用文件流传输时length值为 -1
			this.total = total;
			this.length = length;
			percent = -1;
			this.listener = listener;
		}
		
		public void bytesTransferred(CopyStreamEvent event) {
			//
		}

		public void bytesTransferred(long totalBytesTransferred,
				int bytesTransferred, long streamSize) {
			length += bytesTransferred;
			if (percent >= this.length * 100 / total) {
				
			}
			percent = this.length * 100 / total;
			try {
				if(null != listener) {
					listener.transferred(length);
					listener.process(percent);
				} else {
					logger.log(Logger.DEBUG, "文件传输进度:" + percent + "%");
				}
			} catch (Exception e) {
			}
		}
	}
}
