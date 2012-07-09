/**
 * 
 */
package com.cattsoft.collect.io.net.ftp.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Properties;

import com.cattsoft.collect.io.net.ftp.exception.FTPException;
import com.cattsoft.collect.io.net.ssh.jsch.Channel;
import com.cattsoft.collect.io.net.ssh.jsch.ChannelExec;
import com.cattsoft.collect.io.net.ssh.jsch.ChannelSftp;
import com.cattsoft.collect.io.net.ssh.jsch.JSch;
import com.cattsoft.collect.io.net.ssh.jsch.JSchException;
import com.cattsoft.collect.io.net.ssh.jsch.Logger;
import com.cattsoft.collect.io.net.ssh.jsch.Session;
import com.cattsoft.collect.io.net.ssh.jsch.SftpATTRS;
import com.cattsoft.collect.io.net.ssh.jsch.SftpException;
import com.cattsoft.collect.io.net.ssh.jsch.SftpProgressMonitor;
import com.cattsoft.collect.io.utils.DigestUtils;

/** SSH 文件传输(SFTP)
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class SFTPClient extends FTPClient {
	private Logger logger = getLogger(SFTPClient.class);
	/**
	 * Session 获取
	 */
	private JSch jsch = null;
	/**
	 * SFTP 传输通道
	 */
	private ChannelSftp sftp = null;
	/**
	 * 会话
	 */
	private Session session = null;
	
	/**
	 * @param hostname 主机地址
	 * @param port 端口
	 * @param workdir 工作目录
	 */
	public SFTPClient(String hostname, int port, String workdir) {
		this(hostname, port, null, null, workdir);
	}
	
	/**
	 * @param hostname 主机地址
	 * @param port 端口
	 * @param username 用户名
	 * @param password 密码
	 * @param workdir 工作目录
	 */
	public SFTPClient(String hostname, int port, String username, String password, String workdir) {
		String[] hosts = hostname.split(",");
		// 设置备用主机地址
		setStandbyHost(Arrays.copyOfRange(hosts, 1, hosts.length));
		// 设置首先地址
		setPriorityHost(hosts[0].trim());
		this.hostname = getPriorityHost();
		// SFTP连接默认端口为 22
		this.port = (port > 0 ? port : 22);
		this.username = username;
		this.password = password;
		this.pwd = workdir;
		
		jsch = new JSch();
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#ls(java.lang.String)
	 */
	@Override
	public Object[] ls(String pathname) {
		try {
			return sftp.ls(pathname).toArray();
		} catch (Exception e) {
		}
		return new Object[]{};
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#getHost()
	 */
	@Override
	public String getHost() {
		try {
			return session.getHost();
		} catch (Exception e) {
		}
		return hostname;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#connect()
	 */
	@Override
	public boolean connect() throws FTPException {
		try {
			session = jsch.getSession(getUsername(), getHostname(), getPort());
		} catch (JSchException e) {
			throw new FTPException("获取主机会话失败!地址:" + getHostname() + ", 端口:" + getPort(), e);
		}
		try {
			// 配置
			session.setConfig(getSshConfig());
			// 设置密码
			session.setPassword(password);
			// 连接
			session.connect((int)getTimeout());
			// 是否连接成功
			if(session.isConnected()) {
				// 打开SFTP
				Channel channel = session.openChannel("sftp");
				// 连接到SFTP
				channel.connect((int)getTimeout());

				sftp = (ChannelSftp) channel;
				try {
					cwd(pwd);
				} catch (Exception e) {
					// 工作目录切换失败
				}
			}
		} catch (JSchException e) {
			throw new FTPException("连接到主机时出现异常!地址:" + getHostname() + ", 端口:" + getPort(), e);
		}
		return session.isConnected();
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#isConnected()
	 */
	@Override
	public boolean isConnected() {
		try {
			if(null != session) {
				return session.isConnected();
			}
		} catch (Exception e) {
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#loging(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean login(String username, String password) throws FTPException {
		this.username = username;
		this.password = password;
		
		// 重新连接
		connect();
		
		return isConnected();
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#isLogged()
	 */
	@Override
	public boolean isLogged() {
		return isConnected();
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#upload(java.lang.String, java.io.File, com.cattsoft.collect.transfer.ftp.FtpTransferProcessListener)
	 */
	@Override
	public boolean upload(String directory, File file, boolean backup, 
			FtpTransferProcessListener listener) throws FTPException {
		directory = directory.replaceAll("\\\\", "/");
		String cpwd = printWorkingDirectory();
		if(null == cpwd || !directory.equals(cpwd)) {
			// 切换至目录
			try {
				// 确定目录是否存在
				sftp.realpath(directory);
			} catch (Exception e) {
				// 创建目录
				logger.log(Logger.INFO, "上传目录不存在,正在创建目录(" + directory + ")");
				mkdir(directory);
			}
			logger.log(Logger.INFO, "正在切换至目录" + directory);
			cwd(directory);
		}
		// 传输方式,默认为覆盖文件
		int mode = ChannelSftp.OVERWRITE;
		
		// 准备上传
		// 是否上传成功
		boolean uploaded = false;
		//断点位置
		long offset = 0;
		// 上传临时文件名称
		String tmpFilename = file.getName() + "._tmp";
		// 查找服务器文件是否已存在
		SftpATTRS file_attrs = null;
		try {
			file_attrs = sftp.lstat(file.getName());
		} catch (Exception e) {
			rm(file.getName());
		}
		// 文件存在时进行验证
		if(file_attrs != null) {
			boolean compared = false;
			// 服务器存在文件,验证指纹信息
			if(file_attrs.getSize() == file.length()) {
				logger.log(Logger.INFO, "服务器已存在该文件,正在较验文件..");
				String md5sum = md5Sum(directory + File.separator + file.getName());
				FileInputStream fs = null;
				try {
					// 本地文件指纹信息
					fs = new FileInputStream(file);
					String file_md5 = DigestUtils.md5Hex(fs);
					logger.log(Logger.INFO, "远程文件指纹信息:" + md5sum);
					logger.log(Logger.INFO, "本地文件指纹信息:" + file_md5);
					// 比对
					compared = md5sum.equals(file_md5);
				} catch (Exception e) {
				} finally {
					try {
						if(null != fs)
							fs.close();
					} catch (Exception e2) {
					}
				}
			}
			if(compared) {
				// 文件验证成功
				uploaded = true;
			} else if(backup) {
				// 验证不通过,备份, 删除
				// 备份
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
					logger.log(Logger.INFO, "服务器文件("+ directory + File.separator + file.getName() +")备份成功,备份路径:" + backupPath);
				}
			} else {
				// 删除
				rm(file.getName());
			}
		} else {
			// 服务器不存在该文件,查找临时文件
			try {
				SftpATTRS tmpAttrs = sftp.lstat(tmpFilename);
				if(null != tmpAttrs) {
					// 存在临时文件
					if(tmpAttrs.getSize() > file.length()) {
						// 服务器文件大于本地文件,删除服务器文件
						logger.log(Logger.INFO, "服务器文件大于本地文件,正在删除服务器文件..");
						rm(tmpFilename);
					} else {
						// 文件存在,设置断点位置
						offset = tmpAttrs.getSize();
						// 传输模式为追加到文件
						mode = ChannelSftp.APPEND;
						logger.log(Logger.INFO, "服务器已存在该文件,续传断点位置:" + offset);
					}
				}
			} catch (Exception e) {
				// 临时文件查找失败,尝试删除
				rm(tmpFilename);
			}
		}
		// 文件未上传完成,上传文件
		if(!uploaded) {
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(file);
				// 跳过的长度,断点
				fis.skip(offset);
				// 传输至临时文件
				sftp.put(fis, tmpFilename, new SftpProcessListener(offset,
						file.length(), listener), mode);
				uploaded = true;
				// 重命令临时文件
				rename(tmpFilename, file.getName());
				
				logger.log(Logger.INFO, "文件上传完成,正在较验文件..");
				// 验证文件正确性
				// 获取远程文件MD5
				String md5sum = md5Sum(directory + File.separator + file.getName());
				boolean checked = uploaded;
				if(null != md5sum) {
					try {
						FileInputStream fs = new FileInputStream(file);
						String file_md5 = DigestUtils.md5Hex(fs);
						logger.log(Logger.INFO, "远程文件指纹信息:" + md5sum);
						logger.log(Logger.INFO, "本地文件指纹信息:" + file_md5);
						checked = md5sum.equals(file_md5);
						fs.close();
					} catch (Exception e) {
						// 比对文件长度
						try {
							logger.log(Logger.INFO, "无法对文件指纹进行比对,比对文件长度..");
							SftpATTRS attrs = sftp.lstat(file.getName());
							checked = (file.length() == attrs.getSize());
						} catch (SftpException e1) {
						}
					}
				}
				if(checked) {
					logger.log(Logger.INFO, "文件较验成功!");
				} else {
					// 验证失败,删除远程文件
					logger.log(Logger.INFO, "文件较验失败!数据可能已损坏,正在删除文件..");
					rm(file.getName());
					logger.log(Logger.INFO, "已删除服务器文件!上传失败");
					throw new FTPException("文件较验失败!数据可能已损坏,已删除文件");
				}
			} catch(SocketException e) {
				throw new FTPException("网络连接已断开或重置,上传失败!", e);
			} catch (Exception e) {
				throw new FTPException("文件上传失败!", e);
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
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#download(java.lang.String, java.lang.String, com.cattsoft.collect.transfer.ftp.FtpTransferProcessListener)
	 */
	@Override
	public boolean download(String filepath, String dest,
			FtpTransferProcessListener listener) throws FTPException {
		int mode = ChannelSftp.OVERWRITE;
		File dest_file = new File(dest);
		File tmp_file = new File(dest_file.getName() + "._tmp");
		if(null != dest_file.getParentFile()) {
			tmp_file = new File(dest_file.getParentFile(), tmp_file.getName());
		}
		// 断点位置
		long offset = 0;
		// 服务器文件长度
		long count = 0;
		if(tmp_file.exists()) {
			// 文件已存在,判断是否可以进行断点续传
			try {
				// 获取服务器文件信息
				SftpATTRS attrs = sftp.lstat(filepath);
				if(null != attrs) {
					count = attrs.getSize();
					if(tmp_file.length() > attrs.getSize()) {
						// 删除本地文件
						tmp_file.delete();
					} else {
						// 服务器文件小于等于本地文件长度
						// 断点位置
						offset = tmp_file.length();
						// 追加
						mode = ChannelSftp.RESUME;
						logger.log(Logger.INFO, "本地文件已存在, 续传断点位置:" + offset);
					}
				}
			} catch (Exception e) {
			}
		}
		boolean downloaded = (count > 0 && count == tmp_file.length());
		if(downloaded) {
			try {
				logger.log(Logger.INFO, "正在验证文件..");
				// 检查文件指纹信息
				String md5sum = md5Sum(filepath);
				FileInputStream fis = new FileInputStream(tmp_file);
				String destmd5 = DigestUtils.md5Hex(fis);
				fis.close();
				if(null != md5sum) {
					logger.log(Logger.INFO, "远程文件指纹信息:" + md5sum);
					logger.log(Logger.INFO, "本地文件指纹信息:" + destmd5);
					// 验证是否通过
					downloaded = md5sum.equals(destmd5);
				} else {
					// 无法获取md5,比对文件长度
					// 获取服务器文件信息
					SftpATTRS attrs = sftp.lstat(filepath);
					// 长度是否相同
					downloaded = (attrs.getSize() == tmp_file.length());
				}
			} catch (Exception e) {
				// 出现异常时删除文件
				downloaded = false;
				throw new FTPException("无法验证文件信息,服务文件已移除或不存在!");
			}
		}
		// 准备下载文件
		if(!downloaded) {
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(tmp_file, true);
				// 下载文件
				sftp.get(filepath, fos, new SftpProcessListener(listener), mode, offset);
				try {
					logger.log(Logger.INFO, "正在验证文件..");
					// 检查文件指纹信息
					String md5sum = md5Sum(filepath);
					FileInputStream fis = new FileInputStream(tmp_file);
					String destmd5 = DigestUtils.md5Hex(fis);
					fis.close();
					if(null != md5sum) {
						logger.log(Logger.INFO, "远程文件指纹信息:" + md5sum);
						logger.log(Logger.INFO, "本地文件指纹信息:" + destmd5);
						// 验证是否通过
						downloaded = md5sum.equals(destmd5);
					} else {
						// 无法获取md5,比对文件长度
						// 获取服务器文件信息
						SftpATTRS attrs = sftp.lstat(filepath);
						// 长度是否相同
						downloaded = (attrs.getSize() == tmp_file.length());
					}
				} catch (Exception e) {
					downloaded = false;
					tmp_file.delete();
				}
			} catch (SftpException e) {
				throw new FTPException("文件下载时出现异常!文件下载失败!", e);
			} catch (IOException e) {
				throw new FTPException("无法下载文件!", e);
			} finally {
				try {
					if (null != fos) {
						fos.close();
					}
				} catch (Exception e2) {
				}
			}
		}
		if(downloaded) {
			// 下载已完成
			// 重命名临时文件
			tmp_file.renameTo(dest_file);
			logger.log(Logger.INFO, "文件下载成功!");
		} else {
			logger.log(Logger.ERROR, "文件下载失败!");
		}
		return downloaded;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#disconnect(boolean)
	 */
	@Override
	public void disconnect(boolean abort) {
		try {
			if(abort) {
				sftp.sendSignal("KILL");
			}
		} catch (Exception e) {
		}
		try {
			sftp.exit();
		} catch (Exception e) {
			// 
		}
		try {
			// 断开连接
			session.disconnect();
		} catch (Exception e) {
			// 断开连接时出现异常
		}
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTP#cwd(java.lang.String)
	 */
	public boolean cwd(String directory) throws FTPException {
		try {
			sftp.cd(directory.replaceAll("\\\\", "/"));
			return true;
		} catch (SftpException e) {
			throw new FTPException("切换目录("+ directory +")时出现异常!", e);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#mkdir(java.lang.String)
	 */
	@Override
	public void mkdir(String directory) throws FTPException {
		super.mkdir(directory);
		try {
			// 循环创建各级目录
			directory = !directory.endsWith("/") ? (directory + "/") : directory;
			int index = 0;
			while(index > -1 && index < directory.length()) {
				index = directory.indexOf("/", index + 1);
				String subdir = directory.substring(0, index + 1);
				if(subdir.endsWith("/")) {
					subdir = subdir.substring(0, subdir.length() - 1);
				}
				if("".equals(subdir))
					break;
				try {
					// 确定目录是否存在
					sftp.realpath(subdir);
				} catch (Exception e) {
					// 出现异常,目录不存在,创建目录
					sftp.mkdir(subdir);
				}
			}
		} catch (Exception e) {
			throw new FTPException("创建目录("+ directory +")失败!", e);
		}
	}
	
	/** 获取远程文件MD5值.
	 * @param filepath 文件路径
	 * @return 文件MD5,获取失败时为 <code>null</code>
	 */
	public String md5Sum(String filepath) {
		String md5 = null;
		// 命令执行通道
		Channel md5Channel = null;
		try {
			// 获取EXEC通道
			md5Channel = session.openChannel("exec");
			// 命令,无法识别Windows下的路径分隔符,替换
			String command = "md5sum " + filepath.replace("\\", "/") + "\n";
			// 计算文件MD5值
			((ChannelExec) md5Channel).setCommand(command);
			((ChannelExec)md5Channel).setPty(true);
			// md5Channel.setOutputStream(System.out);
			md5Channel.setInputStream(null);
			// ((ChannelExec)md5Channel).setErrStream(System.err);
			// 输入流,获取主机输出信息
			InputStream in = md5Channel.getInputStream();

			// 连接并执行命令
			md5Channel.connect();

			// 读取返回数据
			byte[] tmp = new byte[1024];
			String out = null;
			while (true) {
				while (in.available() > 0) {
					int i = in.read(tmp, 0, 1024);
					if (i < 0)
						break;
					out = new String(tmp, 0, i);
				}
				if (md5Channel.isClosed()) {
					md5Channel.getExitStatus();
					break;
				}
				// 等待
				try{Thread.sleep(1000);}catch(Exception ee){}
			}
			if(null != out && !out.contains("No such file")) {
				String[] outs = out.trim().split("  ");
				if(outs.length == 2) {
					md5 = outs[0];
				}
			}
			in.close();
		} catch (Exception e) {
			// 获取失败
			logger.log(Logger.ERROR, "获取服务器文件MD5值时出现异常!无法验证该文件." + e.getMessage());
		} finally {
			if (null != md5Channel) {
				md5Channel.disconnect();
			}
		}
		return md5;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#printWorkingDirectory()
	 */
	@Override
	public String printWorkingDirectory() {
		try {
			return sftp.pwd();
		} catch (SftpException e) {
		}
		try {
			return sftp.getHome();
		} catch (SftpException e) {
		}
		return null;
	}
	
	/**
	 * 获取服务配置
	 * 
	 * @return
	 */
	private Properties getSshConfig() {
		Properties sshConfig = new Properties();
		try {
			sshConfig.put("StrictHostKeyChecking", "no");
		} catch (Exception e) {
		}
		return sshConfig;
	}

	/** 文件传输进度.
	 * @author 陈小鸿
	 * @author chenxiaohong@mail.com
	 *
	 */
	class SftpProcessListener implements SftpProgressMonitor {
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
		 * 传输类型
		 */
		private int op;
		/**
		 * 进度通知
		 */
		private FtpTransferProcessListener listener = null;
		
		public SftpProcessListener() {
		}
		
		/**
		 * @param listener 进度通知
		 */
		public SftpProcessListener(FtpTransferProcessListener listener) {
			this(0, 0, listener);
		}
		
		/**
		 * @param length 已传输的数据长度
		 * @param total 数据总长度
		 * @param listener 监听
		 */
		public SftpProcessListener(long length, long total, FtpTransferProcessListener listener) {
			this.length = length;
			this.total = total;
			this.listener = listener;
		}
		
		public void init(int op, String src, String dest, long total) {
			if(null != listener) {
				listener.setType(op);
			}
			// 使用文件流传输时length值为 -1
			if(total > -1)
				this.total = total;
			percent = -1;
			this.op = op;
		}
		public boolean count(long count) {
			length += count;
			if (percent >= this.length * 100 / total) {
				return true;
			}
			percent = this.length * 100 / total;
			try {
				if(null != listener) {
					listener.process(percent);
				} else {
					if(op == SftpProgressMonitor.PUT) {
						logger.log(Logger.DEBUG, "文件上传进度:" + percent + "%");
					} else if (op == SftpProgressMonitor.GET) {
						logger.log(Logger.DEBUG, "文件下载进度:" + percent + "%");
					}
				}
			} catch (Exception e) {
			}
			return !(percent == 100);
		}
		public void end() {
			// 完成
			if(null != listener) {
				listener.complete();
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#rm(java.lang.String)
	 */
	@Override
	public boolean rm(String filepath) {
		super.rm(filepath);
		try {
			sftp.rm(filepath);
			return true;
		} catch (SftpException e) {
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.io.net.ftp.client.FTPClient#rename(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean rename(String from, String to) {
		try {
			sftp.rename(from, to);
			return true;
		} catch (SftpException e) {
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#getHome()
	 */
	@Override
	public String getHome() {
		try {
			if(null == pwd)
				return printWorkingDirectory();
		} catch (Exception e) {
		}
		return pwd;
	}
	
	/* (non-Javadoc)
	 * @see com.cattsoft.collect.transfer.ftp.FTPClient#backupFile(java.lang.String, java.lang.String)
	 */
	@Override
	public String backupFile(String src, String dest) throws FTPException {
		File file = new File(src.replaceAll("\\\\", "/"));
		// 备份路径
		File backupFile = null;
		try {
			// 查找服务器是否存在该文件,存在进行下标递增
			// 新文件名称
			String newname = file.getName();
			// 文件备份下标起始值
			int index = 1;
			while(true) {
				SftpATTRS attrs = null;
				backupFile = new File(newname);
				if(null != file.getParentFile()) {
					backupFile = new File(file.getParentFile(), newname);
				}
				try {
					// 替换路径分隔符
					attrs = sftp.lstat(backupFile.getPath().replace("\\", "/"));
				} catch (Exception e) {
				}
				if(null != attrs) {
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
			try {
				// 删除
				sftp.rm(backupFile.getName());
			} catch (Exception e) {
				// 删除的文件不存在时将抛出异常
			}
			sftp.rename(file.getName(), backupFile.getName());
		} catch (Exception e) {
			throw new FTPException("文件备份失败!", e);
		}
		return backupFile.getPath();
	}
}
