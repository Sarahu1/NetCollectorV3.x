/**
 * 
 */
package com.cattsoft.collect.manage.ui;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import com.cattsoft.collect.io.net.ssh.SSHShell;
import com.cattsoft.collect.manage.data.ConfigUtils;
import com.cattsoft.collect.manage.data.EncryptUtil;
import com.cattsoft.collect.manage.logging.LogManager;

/** SSH 连接信息设置对话框
 * @author Xiaohong
 *
 */
public class ShellInfoDialog extends JDialog {
	private Logger logger = LogManager.getLogger(getName());
	private static final long serialVersionUID = 1L;
	/*** 主机地址 */
	private String host;
	/*** 程序类型 */
	private Object[] type;
	/*** 工作目录 */
	private String folder;
	/*** 配置信息 */
	private Properties prop;
	// 界面输入组件
	private JTextField txtHost = new JTextField();
	private JTextField txtUser = new JTextField();
	private JPasswordField txtPwd = new JPasswordField();
	private JPasswordField txtRoot = new JPasswordField();
	private JComboBox cmbType = null;
	private JTextField txtFolder = new JTextField();
	
	private JButton btnRestart ;
	private JButton btnStop;
	/*** 操作类型 重启(restart)/停止(stop)*/
	private String otype;
	/*** SSH 远程管理工具 */
	private SSHShell shell;
	/*** 操作命令模板 */
	private String operate_templet = null;
	
	private Queue<String> commands;
	
	private String hostname = "";
	
	private boolean complete = true;
	
	private JPanel panel;
	
	private JTextPane infoPanel;
	private JScrollPane scrollPanel;
	
	private JLabel lbl_result;
	
	private Window parent;
	
	/**
	 * @param host
	 * @param hostname
	 * @param type
	 * @param folder
	 * @param otype 操作类型 - 重启(restart), 停止(stop)
	 */
	public ShellInfoDialog(Window parent, String host, String hostname, Object[] type, String folder, String otype) {
		setTitle(("操作确认" + ((null != hostname) ? " - " + hostname : "")));
		this.parent = parent;
		this.host = host;
		this.hostname = hostname;
		this.type = type;
		if(null == this.type) {
			// 默认项
			this.type = new OperateType[]{
					new OperateType("常规", "permanent"),
					new OperateType("忙时", "daily"),
					new OperateType("统计", "report")
			};
		} else {
			this.type = new OperateType[type.length];
			for (int i = 0; i < type.length; i++) {
				if(type[i] instanceof String) {
					this.type[i] = new OperateType(
							"permanent".equalsIgnoreCase((String) type[i]) ? "常规"
									: ("daily".equalsIgnoreCase((String) type[i]) ? "忙时"
											: "report".equalsIgnoreCase((String) type[i]) ? "统计"
													: "未知"), (String) type[i]);
				} else {
					this.type[i] = type[i];
				}
			}
		}
		this.folder = folder;
		this.otype = otype;
		this.prop = ConfigUtils.getProp();
		if(null == this.folder)
			this.folder = prop.getProperty("catalog", "");
		operate_templet = "sh shell.sh $exec $type";
		init();
	}
	
	/**
	 * 初始化界面
	 */
	private void init() {
		UIUtils.addEscapeListener(this);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setResizable(false);
		setLayout(null);
		pack();
		setModal(true);
		setSize(322, 258);
		
		infoPanel = new JTextPane();
		
		// 设置HTML显示
		HTMLEditorKit htmlKit = new HTMLEditorKit(); 
		HTMLDocument htmlDocument = (HTMLDocument) htmlKit.createDefaultDocument(); 
		infoPanel.setEditorKit(htmlKit); 
		infoPanel.setDocument(htmlDocument); 
		infoPanel.setEditable(false);
		
		scrollPanel = new JScrollPane(infoPanel);
		scrollPanel.setBounds(0, 0, getWidth(), getHeight());
		scrollPanel.setVisible(false);
		add(scrollPanel);
		
		panel = new JPanel();
		panel.setLayout(null);
		panel.setBounds(0, 0, getWidth(), getHeight());
		add(panel);
		
		// 添加显示组件
		addComponent(new JLabel("主机地址:"), new Rectangle(10, 10, 80, 22));
		
		addJTextComponent(txtHost, host, new Rectangle(80, 10, 220, 22));

		addComponent(new JLabel("用户名称:"), new Rectangle(10, 40, 80, 22));
		addJTextComponent(txtUser, prop.getProperty("sshuser"), new Rectangle(80, 40, 220, 22));

		txtPwd.setBorder(null);
		txtPwd.setEchoChar('*');
		addComponent(new JLabel("用户密码:"), new Rectangle(10, 70, 80, 22));
		
		addJTextComponent(txtPwd, EncryptUtil.desedeDecoder(
				prop.getProperty("sshpwd", ""), EncryptUtil.DEFAULT_DESKEY),
				new Rectangle(80, 70, 220, 22));

		addComponent(new JLabel("Root密码:"), new Rectangle(10, 100, 80, 22));
		txtRoot.setEchoChar('*');
		addJTextComponent(txtRoot, EncryptUtil.desedeDecoder(prop.getProperty("rootpwd", ""), EncryptUtil.DEFAULT_DESKEY), new Rectangle(80, 100, 220, 22));

		addComponent(new JLabel("程序目录:"), new Rectangle(10, 130, 80, 22));
		addJTextComponent(
				txtFolder,
				(folder.replaceAll(" ", "").isEmpty()) ? prop
						.getProperty("catalog") : folder.replaceAll(" ", ""),
				new Rectangle(80, 130, 220, 22));

		addComponent(new JLabel("应用类型:"), new Rectangle(10, 160, 80, 22));
		cmbType = new JComboBox(type);
		cmbType.setSelectedIndex(0);
		if(cmbType.getItemCount() <= 1) {
			cmbType.setEnabled(false);
		}
		cmbType.setBounds(new Rectangle(80, 160, 220, 22));
		panel.add(cmbType);
		
		lbl_result = new JLabel();
		lbl_result.setBounds(10, 190, 160, 30);
		panel.add(lbl_result);
		
		Rectangle bounds = new Rectangle(getWidth() - (62 +40), 194, 82, 26);
		// 重启按钮
		btnRestart = new JButton("重启");
		btnRestart.setBounds(bounds);
		// 停止按钮
		btnStop = new JButton("停止");
		btnStop.setBounds(bounds);
		if(OperateType.RESTART.equalsIgnoreCase(otype)) {
			panel.add(btnRestart);
		} else if (OperateType.STOP.equalsIgnoreCase(otype)){
			panel.add(btnStop);
		} else {
			// 供选择
			btnRestart.setBounds(new Rectangle(170, 194, 62, 24));
			btnStop.setBounds(new Rectangle(240, 194, 62, 24));
			panel.add(btnRestart);
			panel.add(btnStop);
		}
		// 事件监听
		btnRestart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				commands = getCommands(getOperateType(), "restart", txtFolder.getText());
				execute(txtHost.getText(), txtUser.getText(), new String(
						txtPwd.getPassword()),
						new String(txtRoot.getPassword()), txtFolder.getText());
			}
		});
		btnStop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				commands = getCommands(getOperateType(), "stop", txtFolder.getText());
				execute(txtHost.getText(), txtUser.getText(), new String(
						txtPwd.getPassword()),
						new String(txtRoot.getPassword()), txtFolder.getText());
			}
		});
		UIUtils.setCenter(this);
		// 判断数据是否已全部设置,自动关闭当前对话框
		if (!txtHost.getText().trim().isEmpty()
				&& !txtUser.getText().trim().isEmpty()
				&& !new String(txtPwd.getPassword()).isEmpty()
				&& !new String(txtRoot.getPassword()).isEmpty()
				&& !txtFolder.getText().trim().isEmpty() && null != otype
				&& this.type.length == 1) {
			commands = getCommands(getOperateType(), otype, txtFolder.getText());
			// 自动设置
			execute(txtHost.getText(), txtUser.getText(), new String(
					txtPwd.getPassword()),
					new String(txtRoot.getPassword()), txtFolder.getText());
		}
		
		if(null == host || "".equals(host)) {
			txtHost.setText(prop.getProperty("lastSsh", ""));
		}
		
		// 显示连接确认
		setVisible(true);
	}

	private String getOperateType() {
		String type = this.type[0].toString();
		if(null != cmbType.getSelectedItem()) {
			if(cmbType.getSelectedItem() instanceof OperateType) {
				type = ((OperateType)cmbType.getSelectedItem()).getType();
			}
		}
		return type;
	}
	
	/** 覆盖此方法取值
	 * @param host
	 * @param username
	 * @param password
	 * @param rootpwd
	 * @param folder
	 */
	public void execute(String host, String username, String password, String rootpwd, String folder) {
		try {
			if(host.isEmpty() || username.isEmpty() || password.isEmpty() || folder.isEmpty())
				return;
		} catch (Exception e) {
		}
		enabledControls(false);
		shell = new SSHShell(host, null , null);
		shell.setHost(host);
		shell.setUser(username);
		shell.setUserpwd(password);
		shell.setRootpwd(rootpwd);
		// 添加 cd 目录命令
		shell.setCommands(commands);
		new Thread(new Runnable() {
			public void run() {
				try {
					complete = false;
					setLblResult("正在执行..");
					// 执行
					shell.execute();
					setLblResult(shell.getLastOut());
					out("<b>命令已成功执行!</b></br>" + shell.getLastOut());
					logger.info("命令结果:\n" + shell.getLastOut());
					complete = true;
					if (null != type && type.length == 1) {
						JOptionPane.showMessageDialog(null, "当前命令已成功执行完成!");
						close();
					} else {
						int flag = JOptionPane.showConfirmDialog(null,
								"当前命令已成功执行完成!是否继续其它操作?", "执行成功",
								JOptionPane.YES_NO_OPTION,
								JOptionPane.INFORMATION_MESSAGE);
						if (flag == JOptionPane.YES_OPTION) {
							enabledControls(true);
						} else {
							close();
							if(null != parent) {
								UIUtils.dispatchCloseEvent(parent);
							}
						}
					}
				} catch (Exception e) {
					setLblResult(shell.getLastOut());
					JOptionPane.showMessageDialog(
							null,
							("主机" + (null != hostname ? hostname : "") + "("
									+ shell.getHost() + ")执行命令操作失败!以下为详细信息:\n" + shell
									.getLastOut()), ("操作失败 - " + (null != hostname ? hostname : txtHost.getText())),
							JOptionPane.ERROR_MESSAGE);
					Point center = new Point();
					center.x = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth() / 2 - getWidth() / 2;
					center.y = (int) Toolkit.getDefaultToolkit().getScreenSize().getHeight() / 2 - getHeight() / 2;
					setLocation(center);
					// 返回确认执行
					enabledControls(true);
				} finally {
					// 关闭
					shell.close();
					complete = true;
				}
			}
		}, "manage_execute").start();
//		dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
	}
	
	private void close() {
		if(!complete) {
			int flag = JOptionPane.showConfirmDialog(null, "操作尚未完成,是否中断进程?", "确认", JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
			if(flag == JOptionPane.YES_OPTION) {
				// 
				shell.close();
			} else {
				return;
			}
		}
		// 保存设置
		try {
			prop.put("sshuser", txtUser.getText());
			prop.put("sshpwd", EncryptUtil.desedeEncoder(new String(txtPwd.getPassword()), EncryptUtil.DEFAULT_DESKEY));
			prop.put("rootpwd", EncryptUtil.desedeEncoder(new String(txtRoot.getPassword()), EncryptUtil.DEFAULT_DESKEY));
			prop.put("catalog", txtFolder.getText());
			if(null == host || "".equals(host)) {
				// 自定义主机地址
				prop.put("lastSsh", txtHost.getText());
			}
			ConfigUtils.store(prop, "changes ssh setting");
		} catch (Exception e) {
			logger.severe("配置信息保存失败!" + e.toString());
		}
		UIUtils.dispatchCloseEvent(this);
	}
	
	protected void processWindowEvent(WindowEvent e) {
		if (e.getID() == WindowEvent.WINDOW_CLOSING) {
			if(null != shell) {
				shell.close();
			}
		}
		super.processWindowEvent(e);
	}
	

	private void addJTextComponent(JTextComponent component, String value, Rectangle rec) {
		component.setBorder(null);
		component.setText(value);
		component.setBounds(rec);
		UIUtils.addFocusSelectAll(component);
		panel.add(component);
	}
	
	private void enabledControls(boolean enabled) {
		txtHost.setEnabled(enabled);
		txtUser.setEnabled(enabled);
		txtPwd.setEnabled(enabled);
		txtRoot.setEnabled(enabled);
		txtFolder.setEnabled(enabled);
		btnRestart.setEnabled(enabled);
		btnStop.setEnabled(enabled);
		if(type.length > 1) {
			cmbType.setEnabled(enabled);
		}
	}
	
	private void addComponent(JComponent comp, Rectangle rec) {
		comp.setBounds(rec);
		panel.add(comp);
	}
	
	private void setLblResult(String result) {
		String[] results = result.replaceAll("\r", "").split("\n");
		if(results.length > 0) {
			lbl_result.setText("<html><p style=\"font-family:微软雅黑,宋体,verdana,arial,sans-serif;font-size:12pt\">"+results[results.length-1]+"</p></html>");
		}
	}
	
	private void out(String info) {
//		panel.setVisible(false);
//		scrollPanel.setVisible(true);
//		infoPanel.setVisible(true);
		infoPanel
		.setText("<p style=\"font-family:微软雅黑,宋体,verdana,arial,sans-serif;font-size:12pt\">"
				+ info + "</p>");
	}
	
	private Queue<String> getCommands(String type, String exec, String catalog) {
		Queue<String> command = new LinkedBlockingQueue<String>();
		// cd 切换至工作目录
		command.add("cd " + catalog);
		// 添加执行命令
		command.add(ConfigUtils.getShell(type, operate_templet.replace("$exec", exec).replace("$type", type)));
		return command;
	}
	
	public class OperateType {
		/*** 重启(restart) */
		public static final String RESTART = "restart";
		/*** 停止(stop) */
		public static final String STOP = "stop";
		
		private String type;
		private String name;
		
		public OperateType(String name, String type) {
			this.name = name;
			this.type = type;
		}
		
		public String getType() {
			return type;
		}
		
		public String toString() {
			return name;
		}
	}
}

