/**
 * 
 */
package com.cattsoft.collect.io.net.ftp;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import com.cattsoft.collect.io.net.ftp.client.FtpTransferProcessListener;
import com.cattsoft.collect.io.net.ftp.exception.FTPException;
import com.cattsoft.collect.io.net.ssh.jsch.Logger;

/** FTP 客户端接口定义.
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 * @since JDK 1.6
 */
public interface FTP {
	/**
	 * 匿名登录.
	 * @throws FTPException 连接过程出现错误时将抛出该异常
	 */
	public boolean login() throws FTPException;
	
	/**
	 * 注销登录.
	 */
	public void logout();
	
	/** 使用指定用户名密码登录
	 * @param username 登录用户名
	 * @param password 登录密码
	 * @return 登录是否成功
	 * @throws FTPException 登录过程出现错误时将抛出该异常
	 */
	public boolean login(String username, String password) throws FTPException;
	
	/**
	 * @return 用户是否已登录
	 */
	public boolean isLogged();
	
	/**
	 * 连接到主机
	 * @return 连接是否成功
	 * @throws FTPException 连接过程出现错误时将抛出该异常
	 */
	public boolean connect() throws FTPException;
	
	/** 连接到主机
	 * @param host 主机地址
	 * @param port 连接端口
	 * @return 连接是否成功
	 * @throws FTPException 连接过程出现错误时将抛出该异常
	 */
	public boolean connect(String host, int port) throws FTPException;
	
	/**
	 * @return 是否已连接至主机
	 */
	public boolean isConnected();
	
	/**
	 * 文件上传.
	 * @param file 待上传文件
	 * @return 是否上传成功
	 * @throws FTPException 上传过程出现错误时将抛出该异常
	 */
	public boolean upload(File file) throws FTPException;
	
	/** 文件上传.
	 * @param directory 文件存放目录
	 * @param file 待上传文件
	 * @return 是否上传成功
	 * @throws FTPException 上传过程出现错误时将抛出该异常
	 */
	public boolean upload(String directory, File file) throws FTPException;
	
	/** 文件上传.
	 * @param directory 文件存放目录
	 * @param file 待上传文件
	 * @param listener 文件传输进度监听
	 * @return 是否上传成功
	 * @throws FTPException 上传过程出现错误时将抛出该异常
	 */
	public boolean upload(String directory, File file, FtpTransferProcessListener listener) throws FTPException;
	
	/** 文件上传.
	 * @param directory 文件存放目录
	 * @param file 待上传文件
	 * @param backup 存在同名文件时是否进行备份原文件(默认:true)
	 * @param listener 文件传输进度监听
	 * @return 是否上传成功
	 * @throws FTPException 上传过程出现错误时将抛出该异常
	 */
	public boolean upload(String directory, File file, boolean backup, FtpTransferProcessListener listener) throws FTPException;
	
	/** 文件上传.
	 * @param filename 文件名称
	 * @param input 输入流
	 * @return 上传是否成功
	 * @throws FTPException 上传过程出现错误时将抛出该异常
	 */
	public boolean upload(String filename, InputStream input) throws FTPException;
	
	/** 文件下载.
	 * @param filepath 远程文件路径
	 * @param dest 文件保存路径
	 * @return 文件下载是否成功
	 * @throws FTPException 文件下载出现错误时将抛出该异常
	 */
	public boolean download(String filepath, String dest) throws FTPException;
	
	/** 文件下载.
	 * @param filepath 远程文件路径
	 * @param dest 文件保存路径
	 * @param listener 文件传输进度监听
	 * @return 文件下载是否成功
	 * @throws FTPException 文件下载出现错误时将抛出该异常
	 */
	public boolean download(String filepath, String dest, FtpTransferProcessListener listener) throws FTPException;
	
	/** 文件下载.
	 * @param filepath 远程文件路径
	 * @param output 输出流
	 * @return 文件下载是否成功
	 * @throws FTPException 下载出现错误时将抛出该异常
	 */
	public boolean download(String filepath, OutputStream output) throws FTPException;
	
	
	/** 服务器文件备份.
	 * @param src 文件路径
	 * @param dest 备份路径
	 * @return 备份后文件路径
	 * @throws FTPException 备份过程出现错误时将抛出该异常
	 */
	public String backupFile(String src, String dest) throws FTPException;
	
	/** 本地文件备份.
	 * 须与服务器文件备份区分
	 * @param src 文件路径
	 * @param folder 备份目录
	 * @return 备份后的文件路径
	 */
	public String backup(File src, String folder);
	
	/** 断开连接.
	 * @param abort 是否强制中断
	 */
	public void disconnect(boolean abort);
	
	/**
	 * 断开连接.
	 */
	public void disconnect();
	
	/**
	 * 切换工作目录
	 * @return 是否成功
	 * @throws FTPException 切换出现错误时将抛出该异常
	 */
	public boolean cwd() throws FTPException;
	
	/** 切换工作目录
	 * @param directory 目录
	 * @return 是否成功
	 * @throws FTPException 切换出现错误时将抛出该异常
	 */
	public boolean cwd(String directory) throws FTPException;
	
	/** 删除服务器文件
	 * @param filepath 文件路径
	 * @return 是否成功
	 */
	public boolean rm(String filepath);
	
	/** 重命名文件
	 * @param from 文件名称
	 * @param to 新名称
	 * @return 是否成功
	 */
	public boolean rename(String from, String to);
	
	/**
	 * 打印当前工作目录
	 */
	public String printWorkingDirectory();
	
	/** 创建目录.
	 * @param directory 目录
	 * @throws FTPException 创建目录出现错误时将抛出该异常
	 */
	public void mkdir(String directory) throws FTPException;
	
	/** 遍历服务器目录或文件
	 * @param pathname 路径或文件名称
	 * @return 列表
	 * @throws FTPException
	 */
	public Object[] ls(String pathname) throws FTPException;
	
	/**
	 * 从备用主机地址列表中随机选择主机地址.
	 * 无备用地址时将返回首先地址
	 * @return 主机地址
	 */
	public String randomlyHost();
	
	/**
	 * FTP 默认工作目录
	 */
	public String getHome();
	
	/**
	 * @return 当前连接主机地址
	 */
	public String getHost();
	
	/**
	 * 获取日志输出
	 * @return 日志输出
	 */
	public Logger getLogger(Class<?> cls);
}
