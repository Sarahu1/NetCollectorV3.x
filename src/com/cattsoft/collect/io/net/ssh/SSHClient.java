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

/**
 * 可切换 root 的 SSH 客戶端
 * 
 * 已测试的系统:
 * 1. Ubuntu 8.04
 * 2. RedHat EL4
 * @author ChenXiaohong
 */
public class SSHClient {
	// 预设的提示字串
	private static final String DEFAULT_CHARSET = "UTF-8";
	private static final String DEFAULT_PASSPS  = "(Password|密码)[:：] ";
	private static final String ANSI_CONTROL    = "\\[[0-9]{0,2};?[0-9]{0,2}m";
	private static final String REGEX_KEYCHARS  = "[]{}()^$?+*.&|";
	
	private boolean isroot;     // 检查目前是否为 root
	private boolean connected;  // 检查目前是否连接中
	private String userps;       // user 的 shell 提示字串
	private String rootps;       // root 的 shell 提示字串
	private Session session;     // SSH 连接
	private Scanner sshin;       // SSH 输入端
	private PrintStream sshout;  // SSH 输出端
	private StringBuffer conbuf; // 最后一個指令的执行結果
	
	/**
	 * 建立 SSH 连接
	 * @param host 主机
	 * @param user 用户名
	 * @param password 密码
	 * @throws IOException 
	 * @throws JSchException 
	 */
	public SSHClient(String host, String user, String password) throws JSchException, IOException {
		this(host,user,password,DEFAULT_CHARSET);
	}
	
	/**
	 * 建立 SSH 连接, 采用指定的编码方式输出
	 * @param host 主机
	 * @param user 用户名
	 * @param password 密码
	 * @param charset 编码
	 * @throws JSchException 
	 * @throws IOException 
	 */
	public SSHClient(String host, String user, String password, String charset) throws JSchException, IOException {
		PipedInputStream ppis;
		PipedOutputStream ppos;
		try {
			// 设定连接方式
			JSch jsch = new JSch();
			session = jsch.getSession(user,host);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();
			ChannelShell ch = (ChannelShell)session.openChannel("shell");
			
			// 建立输入端
			ppis = new PipedInputStream();
			ppos = new PipedOutputStream();
			ppis.connect(ppos);
			ch.setInputStream(ppis);
			sshout = new PrintStream(ppos);
			
			// 建立输出端
			ppis = new PipedInputStream();
			ppos = new PipedOutputStream();
			ppis.connect(ppos);
			ch.setOutputStream(ppos);
			sshin = new Scanner(ppis,charset);
						
			// 连接到主机 (会 block)
			ch.connect();
			while(!ch.isConnected()) {
				if(ch.isClosed()) break;
				try {
					Thread.sleep(100);
				} catch(InterruptedException e) {}
			}
			
			// 寻找相同的两列视为 user 提示字串
			conbuf = new StringBuffer();
			sshout.print("\n\n");
			sshout.flush();
			String prev = "";
			String line = sshin.nextLine();
			while(line.trim().isEmpty() || !line.equals(prev)) {
				conbuf.append(prev);
				conbuf.append('\n');
				prev = line;
				line = sshin.nextLine();
			}
			conbuf.delete(0,1);
			
			// 拿掉多出來的两个空白行, 因 print("\n\n") 造成, 纯粹美观
			try {
				if(conbuf.substring(conbuf.length()-2).equals("\n\n")) {
					conbuf.delete(conbuf.length()-2,conbuf.length());
				}
				// 记录 user 提示字串
				int home = line.indexOf('$');
				home = (home != -1) ? home : line.length();
				userps = escapeRegex(line.substring(0,home));
			} catch (Exception e) {
			}
			connected = true;
		} catch(JSchException e) {
			throw e;
		}
	}

	/**
	 * 切换到 root 並且移动到 root 主目录
	 * @param password root 密码
	 */
	public boolean switchRoot(String password) {
		return switchRoot(password,DEFAULT_PASSPS);
	}
	
	/**
	 * 切换到 root 並且移动到 root 根目录, 检查提示字符串
	 * @param password root 密码
	 * @param passps 密码输入提示字串
	 */
	public boolean switchRoot(String password, String passps) {
		String line;
		
		// 查找登入成功的提示字串
		sshout.print("su -\n");
		sshout.flush();
		
		// 查找密码输入的提示字串
		sshin.findWithinHorizon(passps,0);
		sshout.print(password);
		sshout.print('\n');
		sshout.flush();
		
		// 检查是否登入成功
		sshout.print("echo $?\n");
		sshout.flush();
		do {
			line = sshin.nextLine();
		} while(!line.matches("^[0-9]+$"));
		isroot = (Integer.parseInt(line)==0);
		
		// 记录 root 提示字串
		try {
			if(isroot) {
				sshout.print("\n");
				line = sshin.nextLine();
				int home = line.indexOf('~');
				rootps = escapeRegex(line.substring(0,home));
			}
		} catch (Exception e) {
		}
		return isroot;
	}
	
	/**
	 * 执行一个指令, 返回結束代码 (只能执行不需要输入的指令)
	 * @param command 指令字串
	 * @return 结束代码
	 */
	public int execute(String command) {
		String currps = isroot ? rootps : userps;
		
		// 发送命令
		sshout.print(command);
		sshout.print("\n\n");
		sshout.flush();
		
		// 跳到指令之後
		sshin.findWithinHorizon(currps,0);
		sshin.nextLine();
		
		// 接收输出, 注意 currps 因为有设计 Regex 所以不可以用 indexOf 判断
		currps = "^"+currps+".+";
		conbuf.delete(0,conbuf.length());
		String line = sshin.nextLine();
		line = line.replaceAll(ANSI_CONTROL,"");
		while(!line.matches(currps)) {
			conbuf.append(line);
			conbuf.append('\n');
			line = sshin.nextLine();
			line = line.replaceAll(ANSI_CONTROL,"");
		}	
			
		// 送出取得回传值的指令
		sshout.print("echo $?\n");
		sshout.flush();
		
		// 跳到指令之后
		do {
			line = sshin.nextLine();
		} while(!line.matches("^[0-9]+$"));
		
		return Integer.parseInt(line);
	}

	/**
	 * 返回最后一个命令的输出
	 * @return 字符
	 */
	public String getLastOutput() {
		return conbuf.toString();
	}
	
	/**
	 * 检查连接状态
	 * @return 连接成功或失败
	 */
	public boolean isConnected() {
		return connected;
	}
	
	/**
	 * 结束 ssh 连接
	 */
	public void close() {
		if(isroot) sshout.print("exit");
		session.disconnect();
		connected = false;
	}
	
	//  Regex 的特殊字元
	private String escapeRegex(String s) {
		char ch;
		String result = "";
		
		for(int i=0;i<s.length();i++) {
			ch = s.charAt(i);
			if(REGEX_KEYCHARS.indexOf(ch)>-1) result += "\\";
			result += ch;
		}
		
		return result;
	}
	
}
