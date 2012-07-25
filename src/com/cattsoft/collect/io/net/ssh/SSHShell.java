/**
 * 
 */
package com.cattsoft.collect.io.net.ssh;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.Scanner;

import com.cattsoft.collect.io.net.ssh.jsch.ChannelShell;
import com.cattsoft.collect.io.net.ssh.jsch.JSch;
import com.cattsoft.collect.io.net.ssh.jsch.JSchException;
import com.cattsoft.collect.io.net.ssh.jsch.Session;

/** SSH SHELL 命令执行客户端.
 * 通过SSH连接服务器执行命令
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class SSHShell {
	/*** 默认编码 */
	public static final String DEFAULT_CHARSET = "UTF-8";
	/*** 控制符,用于替换控制符号 */
	public static final String ANSI_CONTROL = "\\[[0-9]{0,2};?[0-9]{0,2}m";
	/*** 回车符 */
	public static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");
	/*** 当前编码 */
	private String charset;
	/*** 是否已为root用户 */
	private boolean root;
	/*** 主机地址 */
	private String host;
	/*** 用户名称 */
	private String user;
	/*** 用户密码 */
	private String pswd;
	/*** 连接端口 */
	private int port;
	/*** SSH 连接会话 */
	private Session session;
	/*** 命令执行通道 */
	private ChannelShell shell;
	/*** SSH 输入端,接收服务器响应 */
	private Scanner sshin;
	/*** SSH 输出端,向主机输出命令 */
	private PrintStream sshout;
	/*** 最后一個指令的执行結果 */
	private StringBuffer response;
	/*** 是否获取简洁的响应结果,为<code>true</code>时没有输入提示 */
	private boolean clean;

	/**
	 * 建立 SSH 连接
	 * @param host 主机
	 * @param user 用户名
	 * @param password 密码
	 */
	public SSHShell(String host, String user, String password) {
		this(host, user, password, DEFAULT_CHARSET);
	}

	/** 建立 SSH 连接, 采用指定的编码方式输出
	 * @param host 主机
	 * @param user 用户名
	 * @param password 密码
	 * @param charset 编码
	 */
	public SSHShell(String host, String user, String password, String charset) {
		this(host, user, password, -1, charset);
	}
	
	/**
	 * 建立 SSH 连接, 采用指定的编码方式输出
	 * @param host 主机
	 * @param user 用户名
	 * @param password 密码
	 * @param port 端口(<=0时将使用默认值)
	 * @param charset 编码
	 */
	public SSHShell(String host, String user, String password,
			int port, String charset) {
		this.host = host;
		this.user = user;
		this.pswd = password;
		this.charset = charset;
		this.response = new StringBuffer();
	}

	/** 连接到主机
	 * @return 连接是否成功
	 * @throws JSchException 
	 */
	public boolean connect() throws JSchException {
		PipedInputStream ppis;
		PipedOutputStream ppos;
		
		try {
			// 设定连接方式
			JSch jsch = new JSch();
			session = jsch.getSession(user, host);
			if(port > 0)
				session.setPort(port);
			session.setPassword(pswd);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();
			shell = (ChannelShell) session.openChannel("shell");

			// 建立输入端
			ppis = new PipedInputStream();
			ppos = new PipedOutputStream();
			ppis.connect(ppos);
			shell.setInputStream(ppis);
			sshout = new PrintStream(ppos, true, charset);

			// 建立输出端
			ppis = new PipedInputStream();
			ppos = new PipedOutputStream();
			ppis.connect(ppos);
			shell.setOutputStream(ppos);
			sshin = new Scanner(ppis, charset);

			// 连接到主机 (会 block)
			shell.connect();

			// 读取输出
			readResponse();
			// 返回连接是否成功
			return session.isConnected() && shell.isConnected();
		} catch (IOException e) {
			throw new JSchException("无法建立数据输入输出连接", e);
		} catch (JSchException e) {
			if(e.getMessage().toLowerCase().contains("auth")) {
				throw new JSchException("服务器认证失败!请检查用户名密码是否正确", e);
			}
			if(e.getMessage().toLowerCase().contains("connect")) {
				throw new JSchException("无法连接到服务器,请检查主机地址是否正确", e.getCause());
			}
			throw e;
		}
	}
	
	/** 切换ROOT账户
	 * @param password root 密码
	 */
	public boolean su(String password) {
		// 输出 su
		sshout.println("su -");
		sshout.flush();
		
		// 跳到密码输入提示位置
		sshin.findWithinHorizon("((?i)Password)[:：] ", 0);
		// 输出密码
		sshout.println(password);
		sshout.flush();
		
		// 读取输出
		readResponse();
		
		// 检查是否成功
		return (root = (0 == readExitCode()));
	}
	
	/** 执行命令.
	 * @param command 命令行
	 * @return 命令结束代码
	 */
	public int execute(String command) {
		// 发送命令
		sshout.println(command);
		sshout.flush();
		
		// 跳到指令之後
		sshin.findWithinHorizon(".+[\\$\\#]\\s*", 0);
		sshin.nextLine();
		
		// 读取输出
		readResponse();
		
		System.out.println(shell.getExitStatus());
		
		// 返回结束代码
		return readExitCode();
	}
	
	/** 执行批量命令.
	 * @param commands 命令列表
	 * @param abort 执行命令出现异常时是否中断
	 * @return 命令结束代码
	 */
	public int execute(String[] commands, boolean abort) {
		int code = 0;
		for (String command : commands) {
			code = execute(command);
			if(0 != code && abort) {
				break;
			}
		}
		return code;
	}
	
	/**
	 * 读取响应输出
	 */
	private void readResponse() {
		sshout.println();
		sshout.flush();
		
		// 接收输出
		response.setLength(0);
		String prev = "";
		// 读取输出
		String line = sshin.nextLine().replaceAll(ANSI_CONTROL, "").replaceAll("\r", "");
		while(line.trim().isEmpty() || !line.equals(prev)) {
			if(!line.isEmpty())
				response.append(line).append(LINE_SEPARATOR);
			prev = line;
			line = sshin.nextLine().replaceAll(ANSI_CONTROL, "").replaceAll("\r", "");
		}
		// 删除末尾回车符
		response.delete(response.length() - 1, response.length());
		
		if(isClean()) {
			// 清除内容末尾的用户提示
			response.delete(response.length() - (line.length() + 1), response.length());
		}
	}
	
	/**
	 * 读取命令结束代码
	 */
	private int readExitCode() {
		// 读取命令结束代码
		sshout.println("echo $?");
		sshout.flush();
		// 读取
		String line = "";
		do {
			line = sshin.nextLine();
		} while(!line.matches("^[0-9]+$"));
		// shell.getExitStatus();
		return Integer.parseInt(line);
	}
	
	/** 是否已登录为ROOT用户
	 * @return the root
	 */
	public boolean isRoot() {
		return root;
	}
	
	/**
	 * @return the host
	 */
	public String getHost() {
		return host;
	}
	
	/**
	 * @return 最后一个命令的响应输出
	 */
	public String getLastResponse() {
		return response.toString();
	}
	
	/**
	 * @param clean the clean to set
	 */
	public void setClean(boolean clean) {
		this.clean = clean;
	}
	
	/**
	 * @return the clean
	 */
	public boolean isClean() {
		return clean;
	}
	
	/**
	 * 断开服务器连接
	 */
	public void disconnect() {
		try {
			// 打印退出
			sshout.println("exit");
			sshout.flush();
		} catch (Exception e) {
		}
		try {
			shell.disconnect();
		} catch (Exception e) {
		}
		try {
			session.disconnect();
		} catch (Exception e) {
		}
	}
	
	public static void main(String[] args) throws JSchException {
		SSHShell shell = new SSHShell("202.108.49.59", "collect", "Bb$4.b");
		shell.connect();
		
		shell.su("DTTngoss9ol.0p;|");
		
		System.out.println(shell.execute("cd /home/collect/NetCollectorV3.0/"));;
		
		System.out.println(shell.getLastResponse());
	}
}