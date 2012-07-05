package com.cattsoft.collect.io.net.ssh;

import java.net.ConnectException;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import com.cattsoft.collect.manage.logging.LogManager;

/**
 * 可进入 SSH 执行命令的客户端
 * 
 * @author ChenXiaohong
 */
public class SSHShell {
	private Logger logger = LogManager.getLogger(getClass().getSimpleName());
	private boolean abort;          // 命令有错误时是否要中止动作
	private boolean silent;         // 是否开启靜音模式
	private String host;             // 主机
	private String user;             // 用户名
	private String userpwd;          // 密码
	private String rootpwd;          // root密码
	private Queue<String> commands; // 命令集
	private SSHClient sshc ;
	
	private final static String LINE_SEPARATOR = System.getProperty("line.separator", "\n");
	
	private StringBuffer lasOut = new StringBuffer();

	public SSHShell(String host, String username, String password) {
		this.host = host;
		this.user = username;
		this.userpwd = password;
		abort = true;
		silent = false;
		commands = new LinkedBlockingQueue<String>();
	}
	
	/**
	 * @param command 命令行
	 */
	public void addCommand(String command) {
		commands.add(command);
	}
	
	public void setCommands(Queue<String> commands) {
		this.commands = commands;
	}
	
	public Queue<String> getCommands() {
		return commands;
	}
	
	/**
	 * 设置主机 (必要)
	 * @param host
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * 用户名 (必要)
	 * @param user 用户名
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * 设定密码 (必要)
	 * @param userpwd 密码
	 */
	public void setUserpwd(String userpwd) {
		this.userpwd = userpwd;
	}

	/**
	 * 设定root密码 (选用, 沒填则不切换 root)
	 * @param rootpwd 密码
	 */
	public void setRootpwd(String rootpwd) {
		this.rootpwd = rootpwd;
	}

	/**
	 * 设定命令执行错误时是否需要中止
	 * @param abort 是否中止
	 */
	public void setAbort(boolean abort) {
		this.abort = abort;
	}

	/**
	 * 设定是否要静音, 不显示执行狀況 (默认值: false)
	 * @param silent 是否静音
	 */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}
	
	public boolean connect() {
		// 实例化后将自动连接
		try {
			sshc = new SSHClient(host,user,userpwd);
		} catch (Exception e) {
			if(null != e.getCause() && e.getCause() instanceof ConnectException) {
				lasOut.append("无法连接,请检查地址是否正确.\n" + e.getMessage());
			} else {
				lasOut.append(e.getMessage()).append(LINE_SEPARATOR);
			}
		}
		return sshc.isConnected();
	}
	
	/**
	 * 执行
	 */
	public void execute() {
		int exit;
		boolean isroot = false;
		lasOut = new StringBuffer();
		connect();
		
		if(sshc.isConnected()) {
			logger.info(sshc.getLastOutput());
			if (rootpwd != null) {
				isroot = sshc.switchRoot(rootpwd);
				if (!silent) {
					if (isroot) {
						logger.info(">> 切换 root 身份");
					} else {
						logger.severe(">> 切换 root 失败");
						rootpwd = null;
						lasOut.append("请求Root权限失败!").append(LINE_SEPARATOR).append("授权失败!");
						throw new IllegalStateException("切换至Root权限失败!");
					}
				}
			}
			if (rootpwd == null || isroot) {
				// 执行命令列表
				boolean founderr = false;
				
				String command = null;
				while(null != (command = commands.poll())) {
					// 执行并返回命令执行状态
					exit = sshc.execute(command);
					if (!silent) {
						lasOut.append(command).append(LINE_SEPARATOR).append(sshc.getLastOutput()).append(LINE_SEPARATOR);
					}
					// 是否有出现异常
					if (exit != 0 && abort) {
						founderr = true;
						break;
					}
				}
				if(founderr) {
					lasOut.append(command).append(LINE_SEPARATOR).append("命令执行失败!").append(LINE_SEPARATOR);
					throw new IllegalStateException("命令执行失败!");
				}
			}
			// 关闭
			sshc.close();
		} else {
			throw new RuntimeException("连接失败");
		}
	}
	
	public String getRootpwd() {
		return rootpwd;
	}
	
	public SSHClient getSshc() {
		return sshc;
	}
	
	public String getHost() {
		return host;
	}

	public String getUser() {
		return user;
	}

	public String getUserpwd() {
		return userpwd;
	}

	public void setSshc(SSHClient sshc) {
		this.sshc = sshc;
	}
	
	public String getLastOut() {
		return lasOut.toString();
	}
	
	public void close() {
		try {
			sshc.close();
		} catch (Exception e) {
		}
	}
}