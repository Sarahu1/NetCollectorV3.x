/**
 * 
 */
package com.cattsoft.collect.io.net.ftp.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import com.cattsoft.collect.io.net.ftp.exception.FTPException;
import com.cattsoft.collect.io.net.ftp.ftp4j.FTPAbortedException;
import com.cattsoft.collect.io.net.ftp.ftp4j.FTPDataTransferException;
import com.cattsoft.collect.io.net.ftp.ftp4j.FTPDataTransferListener;
import com.cattsoft.collect.io.net.ftp.ftp4j.FTPFile;
import com.cattsoft.collect.io.net.ssh.jsch.Logger;

/** FTP 文件传输(FTP4J)
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class JFTPClient extends FTPClient {
	private Logger logger = getLogger(NFTPClient.class);
	/**
	 * FTP4J FTP传输通道
	 */
	private com.cattsoft.collect.io.net.ftp.ftp4j.FTPClient ftp = null;


	/**
	 * @param hostname 主机地址
	 * @param port 端口
	 * @param workdir 工作目录
	 */
	public JFTPClient(String hostname, int port, String workdir) {
		this(hostname, port, workdir, null, workdir);
	}

	/**
	 * @param hostname 主机地址
	 * @param port 端口
	 * @param username 用户名
	 * @param password 密码
	 * @param workdir 工作目录
	 */
	public JFTPClient(String hostname, int port, String username, String password, String workdir) {
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

		ftp = new com.cattsoft.collect.io.net.ftp.ftp4j.FTPClient();
		ftp.setCharset("UTF-8");
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#isLogged()
	 */
	@Override
	public boolean isLogged() {
		return ftp.isAuthenticated();
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#isConnected()
	 */
	@Override
	public boolean isConnected() {
		return ftp.isConnected();
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.FTP#login(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean login(String username, String password) throws FTPException {
		try {
			ftp.login(username, password);
			// 登录成功, 切换工作目录
			if(ftp.isAuthenticated()) {
				try {
					// 切换至工作目录
					cwd(pwd);
				} catch (Exception e) {
					// 切换出现异常, 尝试创建
					try {
						mkdir(pwd);
					} catch (Exception e2) {
						try {
							// 重试切换
							cwd(pwd);
						} catch (Exception e3) {
							throw new FTPException("登录服务器成功,但切换工作目录时出现异常!", e);
						}
					}
				}
			}
		} catch (Exception e) {
			throw new FTPException("登录服务器时出现异常!", e);
		}
		return ftp.isAuthenticated();
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.FTP#connect()
	 */
	@Override
	public boolean connect() throws FTPException {
		try {
			ftp.connect(hostname, port);
		} catch (Exception e) {
			throw new FTPException("连接到主机(" +hostname+ ")时出现错误,请检查连接信息或主机状态." + e.getMessage(), e);
		}
		return ftp.isConnected();
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.FTP#disconnect(boolean)
	 */
	@Override
	public void disconnect(boolean abort) {
		if(abort) {
			try {
				ftp.abortCurrentConnectionAttempt();
				ftp.abortCurrentDataTransfer(!abort);
			} catch (Exception e) {
			}
		}
		try {
			ftp.logout();
		} catch (Exception e) {
		}
		try {
			ftp.disconnect(!abort);
		} catch (Exception e) {
		}
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.FTP#cwd(java.lang.String)
	 */
	@Override
	public boolean cwd(String directory) throws FTPException {
		try {
			ftp.changeDirectory(directory.replaceAll("\\\\", "/"));
			return directory.equals(printWorkingDirectory());
		} catch (Exception e) {
			throw new FTPException("切换工作目录("+directory+")时出现错误!", e);
		}
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#mkdir(java.lang.String)
	 */
	@Override
	public void mkdir(String directory) throws FTPException {
		super.mkdir(directory);
		// 原工作目录
		String oriWorkDir = null;
		try {
			oriWorkDir = printWorkingDirectory();
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
					try {
						// 尝试切换, 不存在时将出现异常
						cwd(subdir);
					} catch (Exception e) {
						ftp.createDirectory(subdir);
						// 切换
						cwd(subdir);
					}
				}
			}
		} catch (Exception e) {
			throw new FTPException("创建目录("+ directory +")失败!", e);
		} finally {
			try {
				if(null != oriWorkDir) {
					// 切换为原工作目录
					cwd(oriWorkDir);
				}
			} catch (Exception e2) {
				logger.log(Logger.ERROR, "切换至工作目录(" +oriWorkDir+ ")时出现异常!");
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#printWorkingDirectory()
	 */
	@Override
	public String printWorkingDirectory() {
		try {
			return ftp.currentDirectory();
		} catch (Exception e) {
			logger.log(Logger.ERROR, "当前工作目录获取失败!" + e.getMessage());
		}
		return pwd;
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#getHost()
	 */
	@Override
	public String getHost() {
		return ftp.getHost();
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#upload(java.lang.String, java.io.File, com.cattsoft.collect.io.net.ftp.client.FtpTransferProcessListener)
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
			cwded = cwd(directory);
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

		// 续传断点位置
		long offset = 0;
		// 临时文件
		File tmpFile = new File(file.getName() + "._tmp");
		if(null != file.getParentFile()) {
			tmpFile = new File(file.getParentFile(), tmpFile.getName());
		}
		// 查找文件是否存在
		FTPFile[] ftpfiles = Arrays.asList(ls(file.getName())).toArray(new FTPFile[]{});
		if(ftpfiles.length == 0) {
			// 查找临时文件,获取临时文件长度
			try {
				long tmpFileLength = ftp.fileSize(tmpFile.getName());
				// 比对长度
				if(tmpFileLength > file.length()) {
					// 临时文件大于本地文件,删除临时文件
					logger.log(Logger.INFO, "服务器文件大于本地文件,正在删除服务器文件..");
					rm(tmpFile.getName());
				} else {
					// 设置续传断点
					offset = tmpFileLength;
					if(null != listener)
						listener.setCompleteType(FtpTransferProcessListener.COMPLETE_HTTP);
					logger.log(Logger.INFO, "服务器已存在该文件,续传断点位置:" + offset);
				}
			} catch (Exception e) {
			}
		} else if(backup) {
			// 存在同名文件, 备份
			String backupPath = null;
			uploaded = ftpfiles[0].getSize() == file.length();
			if(!uploaded) {
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
			} else {
				// 设置监听器传输完成类型为 文件已存在
				if(null != listener) {
					listener.setCompleteType(FtpTransferProcessListener.COMPLETE_EXISTS);
					listener.complete();
				}
				logger.log(Logger.INFO, "在服务器中找到同名且长度相同文件(" +file.getName()+ "),完成上传");
			}
		} else {
			// 删除原文件
			rm(file.getName());
		}
		if(!uploaded) {
			InputStream fis = null;
			try {
				fis = new FileInputStream(file);
				ftp.setType(com.cattsoft.collect.io.net.ftp.ftp4j.FTPClient.TYPE_BINARY); 
				ftp.setPassive(true);
				// 上传
				ftp.append(tmpFile.getName(), fis, offset, new FTPDataTransferAdapter(offset, file.length(), listener));
				if(null != listener)
					uploaded = listener.isComplete();
				// 验证文件传输是否成功
				FTPFile[] files = Arrays.asList(ls(tmpFile.getName())).toArray(new FTPFile[]{});
				if(files.length > 0) {
					uploaded = (files[0].getSize() == file.length());
					if(!uploaded) {
						logger.log(Logger.INFO, "文件较验失败!数据可能已损坏,正在删除文件..");
						rm(tmpFile.getName());
						logger.log(Logger.INFO, "已删除服务器文件!上传失败");
					}
				}
				if(uploaded) {
					// 重命名临时文件
					ftp.rename(tmpFile.getName(), file.getName());
				} else {
					throw new FTPException("文件较验失败!数据可能已损坏,已删除文件");
				}
			} catch (FTPDataTransferException e) {
				throw new FTPException("传输文件数据出现异常!", e);
			} catch (FTPAbortedException e) {
				throw new FTPException("文件传输被中断,上传失败!", e);
			} catch (Exception e) {
				throw new FTPException("文件上传出现异常,上传失败!", e);
			} finally {
				try {
					if(null != fis) {
						fis.close();
					}
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
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#download(java.lang.String, java.lang.String, com.cattsoft.collect.io.net.ftp.client.FtpTransferProcessListener)
	 */
	@Override
	public boolean download(String filepath, String dest,
			FtpTransferProcessListener listener) throws FTPException {
		boolean downloaded = false;
		File file = new File(filepath);
		File local = new File(dest);
		// 临时文件
		File tmp_file = new File(file.getName() + "._tmp");
		if(null != file.getParentFile()) {
			// 切换工作目录
			cwd(file.getParentFile().getPath());
		}
		if(null != local.getParentFile()) {
			tmp_file = new File(local.getParentFile(), tmp_file.getName());
		}
		// 查找文件,确定文件是否存在
		FTPFile[] ftpFiles = Arrays.asList(ls(file.getName())).toArray(new FTPFile[]{});
		if(null == ftpFiles || ftpFiles.length == 0) {
			throw new FTPException("未在服务器找到该文件("+ filepath +")");
		}
		long offset = 0;

		if(tmp_file.exists()) {
			// 临时文件已存在
			offset = tmp_file.length();
			logger.log(Logger.INFO, "本地文件已存在, 续传断点位置:" + offset);
		} else {
			try {
				tmp_file.createNewFile();
			} catch (Exception e) {
			}
		}
		OutputStream fos = null;
		try {
			// 文件输出流
			fos = new FileOutputStream(tmp_file, true);
			// 下载
			ftp.setType(com.cattsoft.collect.io.net.ftp.ftp4j.FTPClient.TYPE_BINARY); 
			ftp.setPassive(true);
			ftp.download(file.getName(), fos, offset, new FTPDataTransferAdapter(offset, ftpFiles[0].getSize(), listener));
			if(null != listener) {
				downloaded = listener.isComplete();
			}
			downloaded = tmp_file.length() == ftpFiles[0].getSize();
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
		// 验证是否成功
		if(downloaded) {
			tmp_file.renameTo(local);
			logger.log(Logger.INFO, "文件下载成功!");
		} else {
			// 删除
			tmp_file.delete();
			logger.log(Logger.ERROR, "文件下载失败!");
		}
		return downloaded;
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#backupFile(java.lang.String, java.lang.String)
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
					ftpfiles = Arrays.asList(ls(newname)).toArray(new FTPFile[]{});
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
			// 删除
			rm(backupFile.getName());
			// 重命名
			rename(file.getName(), backupFile.getName());
		} catch (Exception e) {
			throw new FTPException("文件备份失败!", e);
		}
		return backupFile.getPath();
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#ls(java.lang.String)
	 */
	@Override
	public Object[] ls(String pathname) {
		try {
			return ftp.list(pathname);
		} catch (Exception e) {
			logger.log(Logger.ERROR, "列举目录("+ pathname +")时出现异常!" + e.getMessage());
		}
		return new Object[0];
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#rename(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean rename(String from, String to) {
		try {
			ftp.rename(from, to);
			return true;
		} catch (Exception e) {
			logger.log(Logger.ERROR, "文件(" + from + ")重命名失败!" + e.getMessage());
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#rm(java.lang.String)
	 */
	@Override
	public boolean rm(String filepath) {
		try {
			ftp.deleteFile(filepath);
			return true;
		} catch (Exception e) {
			logger.log(Logger.ERROR, "删除文件时出现异常!" + e.getMessage());
		}
		return false;
	}

	/** 数据传输进度
	 * @author 陈小鸿
	 * @author chenxiaohong@mail.com
	 *
	 */
	class FTPDataTransferAdapter implements FTPDataTransferListener {
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

		public FTPDataTransferAdapter(long length, long total, FtpTransferProcessListener listener) {
			this.total = total;
			this.length = length;
			percent = -1;
			this.listener = listener;
		}

		public void started() {
		}

		public void transferred(int length) {
			this.length += length;

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

		public void completed() {
			if(null != listener) {
				listener.complete();
			}
		}

		public void aborted() {
		}

		public void failed() {
		}
	}
}
