/**
 * 
 */
package com.cattsoft.collect.manage.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Properties;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import com.cattsoft.collect.io.net.ftp.FTP;
import com.cattsoft.collect.io.net.ftp.client.NFTPClient;
import com.cattsoft.collect.io.net.ftp.client.SFTPClient;
import com.cattsoft.collect.manage.data.ConfigUtils;
import com.cattsoft.collect.manage.data.EncryptUtil;
import com.cattsoft.collect.manage.logging.LogManager;

/** Ftp连接管理确认界面.
 * @author Xiaohong
 *
 */
public class MonitorFtpConnect extends JDialog {
	private static final long serialVersionUID = 1L;
	private Logger logger = LogManager.getLogger(getName());
	// 组件
	private JTextField txtHost;
	private JTextField txtPort;
	private JTextField txtUserName;
	private JPasswordField txtPassword;
	private JTextField txtFolder;
	private JTextField txtUfolder;
	
	private JButton btnConn;
	private JButton btnClose;
	
	/*** 配置属性 */
	private Properties prop;
	/*** 主机地址 */
	private String host;
	/*** 主机名称 */
	private String hostname;
	/*** FTP客户端 */
	private FTP ftp;
	/*** FTP连接是否已完成 */
	private boolean complete = false;
	/*** 是否自动连接 */
	private boolean autoconn;
	/*** 是否自动关闭FTP窗口 */
	private boolean autoCloseFtp;
	
	private String catalog;
	
	public MonitorFtpConnect(Window parent, String host, String hostname, String catalog, boolean autoconn, boolean autoCloseFtp) {
		super(
				parent,
				("快速连接" + (null != hostname && !"".equals(hostname) ? (" - " + hostname)
						: (null != host && !"".equals(host) ? " - " + host : ""))));
		this.host = host;
		this.hostname = hostname;
		this.autoconn = autoconn;
		this.catalog = catalog;
		this.autoCloseFtp = autoCloseFtp;
		init();
	}
	
	public MonitorFtpConnect(Window parent, String host, String hostname, String catalog) {
		this(parent, host, hostname, catalog, true, false);
	}
	
	private void init() {
		prop = ConfigUtils.getProp();
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		UIUtils.addEscapeListener(this);
		setModal(true);
		setLayout(null);
		pack();
		setSize(350, 244);
		setResizable(false);
		JLabel lblHost = new JLabel("主机地址:");
		lblHost.setBounds(new Rectangle(20, 20, 60, 22));
		txtHost = new JTextField();
		txtHost.setBounds(new Rectangle(90, 20, 120, 22));
		String host_addr = host;
		if(null == host || "".equals(host)) {
			autoconn = false;
			host_addr = prop.getProperty("lastFtp", "");
		}
		txtHost.setText(host_addr);
		UIUtils.addFocusSelectAll(txtHost);
		add(lblHost);
		add(txtHost);
		JLabel lblPort = new JLabel("端口:");
		lblPort.setBounds(new Rectangle(224, 20, 60, 22));
		txtPort = new JTextField();
		UIUtils.addFocusSelectAll(txtPort);
		UIUtils.setNumberDocument(txtPort);
		txtPort.addFocusListener(new FocusAdapter() {
			public void focusLost(FocusEvent e) {
				JTextField source = (JTextField)e.getSource();
				if(source.getText().isEmpty())
					source.setText(prop.getProperty("ftp.port", "22"));
				super.focusLost(e);
			}
		});
		txtPort.setText(prop.getProperty("ftp.port", "22"));
		txtPort.setBounds(new Rectangle(265, 20, 45, 22));
		add(lblPort);
		add(txtPort);
		JLabel lblUserName = new JLabel("用户名称:");
		lblUserName.setBounds(new Rectangle(20, 50, 60, 22));
		txtUserName = new JTextField();
		txtUserName.setText(prop.getProperty("sshuser", ""));
		txtUserName.setBounds(new Rectangle(90, 50, 220, 22));
		UIUtils.addFocusSelectAll(txtUserName);
		add(lblUserName);
		add(txtUserName);
		// 密码
		JLabel lblPassword = new JLabel("用户密码:");
		lblPassword.setBounds(new Rectangle(20, 80, 60, 22));
		txtPassword = new JPasswordField();
		txtPassword.setBounds(new Rectangle(90, 80, 220, 22));
		txtPassword.setEchoChar('*');
		
		txtPassword.setText(EncryptUtil.desedeDecoder(prop.getProperty("sshpwd", ""), EncryptUtil.DEFAULT_DESKEY));
		UIUtils.addFocusSelectAll(txtPassword);
		add(lblPassword);
		add(txtPassword);
		JLabel lblFolder = new JLabel("远程路径:");
		lblFolder.setBounds(new Rectangle(20, 110, 60, 22));
		txtFolder = new JTextField();
		txtFolder.setBounds(new Rectangle(90, 110, 220, 22));
		if(null == catalog || "".equals(catalog)) {
			catalog = prop.getProperty("catalog", "");
		}
		txtFolder.setText(catalog);
		UIUtils.addFocusSelectAll(txtFolder);
		add(lblFolder);
		add(txtFolder);
		
		// 上传目录指定面板
		JPanel uploadFolderPanel = new JPanel(null);
		uploadFolderPanel.setBackground(null);
		uploadFolderPanel.setBorder(null);
		
		uploadFolderPanel.setBounds(new Rectangle(0, 140, getWidth(), 22));
		
		JLabel lbl_ufolder = new JLabel("上传目录:");
		lbl_ufolder.setBounds(new Rectangle(20, 0, 60, 22));
		uploadFolderPanel.add(lbl_ufolder);
		
		final File file = new File(prop.getProperty("uploadDirectory", ConfigUtils.getCatalog() + File.separator + "collect_upload"));
		txtUfolder = new JTextField();
		UIUtils.addFocusSelectAll(txtUfolder);
		txtUfolder.setToolTipText("双击选择上传目录");
		txtUfolder.setText(file.getAbsolutePath());
		txtUfolder.setEditable(false);
		txtUfolder.setBounds(new Rectangle(90, 0, 220, 22));
		txtUfolder.setBackground(Color.WHITE);
		txtUfolder.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				super.mousePressed(e);
				if(e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 && txtUfolder.isEnabled()) {
					selectUploadFolder();
				}
			}
		});
		txtUfolder.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selectUploadFolder();
			}
		});
		uploadFolderPanel.add(txtUfolder);
		add(uploadFolderPanel);
		
		// 按钮
		btnConn = new JButton("连接");
		btnConn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				connect();
			}
		});
		btnConn.setBounds(new Rectangle(160, 175, 74, 24));
		add(btnConn);
		btnClose = new JButton("关闭");
		btnClose.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				close();
				try {
					// 断开FTP连接
					ftp.disconnect(false);
				} catch (Exception e1) {
				}
			}
		});
		btnClose.setBounds(new Rectangle(245, 175, 64, 24));
		add(btnClose);
		UIUtils.setCenter(this);
		// 检查数据完整性
		if(!txtHost.getText().isEmpty() && !txtPort.getText().isEmpty() &&
				!txtUserName.getText().isEmpty() && !new String(txtPassword.getPassword()).isEmpty() &&
				!txtFolder.getText().isEmpty() && autoconn) {
			// 自动连接
			connect();
		}
		setVisible(true);
	}
	
	/**
	 * 选择上传目录
	 */
	private void selectUploadFolder() {
		// 使用文件对话框
		JFileChooser fc_upload = new JFileChooser(new File(txtUfolder.getText()));
		fc_upload.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if(fc_upload.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			txtUfolder.setText(fc_upload.getSelectedFile().getAbsolutePath());
		}
	}
	
	private void close() {
		// 保存最后连接的Host地址
		try {
			Properties props = ConfigUtils.getProp();
			if(complete && (null == host || "".equals(host))) {
				props.put("lastFtp", ftp.getHost());
			}
			// 上传目录
			props.put("uploadDirectory", txtUfolder.getText());
			ConfigUtils.store(props, "save last Ftp");
		} catch (Exception e) {
		}
		UIUtils.dispatchCloseEvent(this);
	}
	
	protected void processWindowEvent(WindowEvent e) {
		if (e.getID() == WindowEvent.WINDOW_CLOSING) {
			if(null != ftp) {
				if(!complete) {
					try {
						ftp.logout();
						// 关闭FTP连接
						ftp.disconnect(false);
					} catch (Exception e1) {
					}
				}
			}
		}
		super.processWindowEvent(e);
	}
	
	private void enabledControls(boolean enabled) {
		txtHost.requestFocus();
		txtHost.setEnabled(enabled);
		txtPort.setEnabled(enabled);
		txtUserName.setEnabled(enabled);
		txtPassword.setEnabled(enabled);
		txtFolder.setEnabled(enabled);
		btnConn.setEnabled(enabled);
		txtUfolder.setBackground(enabled ? Color.WHITE : null);
		txtUfolder.setEnabled(enabled);
	}
	
	/**
	 * 保存配置信息
	 */
	private void saveConfig() {
		try {
			Properties props = ConfigUtils.getProp();
			props.put("ftp.port", txtPort.getText());
			props.put("sshuser", txtUserName.getText());
			props.put("sshpwd", EncryptUtil.desedeEncoder(new String(txtPassword.getPassword()), EncryptUtil.DEFAULT_DESKEY));
			props.put("catalog", txtFolder.getText());
			ConfigUtils.store(props, "connections settings");
		} catch (Exception e) {
		}
	}
	
	private void connect() {
		connect(txtHost.getText(), Integer.valueOf(txtPort.getText()),
				txtUserName.getText(), new String(txtPassword.getPassword()),
				txtFolder.getText());
	}
	
	/** 连接
	 * @param host
	 * @param port
	 * @param user
	 * @param pwd
	 * @param folder
	 */
	public void connect(final String host, final int port, final String user,
			final String pwd, final String folder) {
		if(host.isEmpty() || user.isEmpty() || pwd.isEmpty() || folder.isEmpty() || txtUfolder.getText().isEmpty())
			return;
		// 判断上传目录是否存在
		if(!new File(txtUfolder.getText()).exists()) {
			txtUfolder.requestFocus();
			txtUfolder.setBorder(BorderFactory.createLineBorder(Color.RED));
			txtUfolder.setToolTipText("上传目录不存在!请双击以选择上传目录!");
			txtUfolder.addFocusListener(new FocusAdapter() {
				public void focusLost(FocusEvent e) {
					if(new File(txtUfolder.getText()).exists()) {
						txtUfolder.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
						txtUfolder.setToolTipText("双击选择上传目录");
						super.focusLost(e);
					} else {
						return;
					}
				}
			});
			return;
		}
		// 创建线程连接并登录FTP主机
		new Thread(new Runnable() {
			public void run() {
				// 端口22使用SFTP上传
				if(port == 22) {
					ftp = new SFTPClient(host, port, user, pwd, folder);
				} else {
					try {
						ftp = new NFTPClient(host, port, user, pwd, folder);
					} catch (Exception e) {
						JOptionPane.showMessageDialog(null, "初始化FTP客户端时出现异常!请重启程序后重试", "错误", JOptionPane.ERROR_MESSAGE);
					} catch (NoClassDefFoundError e) {
						// 未找到apache net模块
						JOptionPane jop = new JOptionPane();
						// 未找到apache net模块
						Font font = jop.getFont();

					    // create some css from the label's font
					    StringBuffer style = new StringBuffer("font-family:" + font.getFamily() + ";");
					    style.append("font-weight:" + (font.isBold() ? "bold" : "normal") + ";");
					    style.append("font-size:" + font.getSize() + "pt;");

					    JEditorPane ep = new JEditorPane("text/html", "<html><body style=\"" + style + "\">需要<a href=\"http://commons.apache.org/net/\">Apache Commons Net™</a>" +
								"模块支持,请下载该模块后重新启动程序</body></html>");
						// handle link events
						ep.addHyperlinkListener(new HyperlinkListener() {
							public void hyperlinkUpdate(HyperlinkEvent e) {
								try {
									if (e.getEventType().equals(
											HyperlinkEvent.EventType.ACTIVATED)) {
										java.awt.Desktop dp = java.awt.Desktop
												.getDesktop();
										// 判断系统桌面是否支持要执行的功能
										if (dp.isSupported(java.awt.Desktop.Action.BROWSE)) {
											// 获取系统默认浏览器打开链接
											dp.browse(e.getURL().toURI());
										}
									}
								} catch (Exception e2) {
								}
							}
						});
					    ep.setEditable(false);
					    ep.setBackground(jop.getBackground());
						
						JOptionPane.showMessageDialog(null, ep, "缺少模块", JOptionPane.ERROR_MESSAGE);
						return;
					}
				}
				String connTit = btnConn.getText();
				try {
					btnConn.setText("连接中..");
					enabledControls(false);
					
					// 已连接并已登录
					if(txtHost.equals(ftp.getHost()) && ftp.isConnected() && ftp.isLogged()) {
						logger.info("Ftp已连接成功");
					} else {
						// 未连接
						if(!ftp.isConnected()) {
							ftp.connect();
						}
						if(ftp.isConnected()) {
							logger.info("Ftp连接成功!");
							if(!ftp.isLogged()) {
								logger.info("正在登录到Ftp..");
								ftp.login();
							}
						}
					}
					if(ftp.isConnected() && ftp.isLogged()) {
						logger.info("登录Ftp服务器成功!");
						// 保存用户记录数据
						saveConfig();
						try {
							// 切换工作目录
							ftp.cwd(folder);
							logger.info("当前工作目录:" + ftp.printWorkingDirectory());
						} catch (Exception e) {
							if(ftp.ls(folder).length > 0) {
								logger.info("切换到工作目录("+folder+")时出现异常!" + e);
							} else {
								int flag = JOptionPane
										.showConfirmDialog(
												null,
												("服务器不存在工作目录:\"" + folder + "\",是否创建该目录?"),
												"工作目录",
												JOptionPane.YES_NO_OPTION,
												JOptionPane.WARNING_MESSAGE);
								if(flag == JOptionPane.YES_OPTION) {
									try {
										ftp.mkdir(folder.endsWith("/") ? (folder + "/") : folder);
										ftp.cwd(folder);
									} catch (Exception e2) {
										JOptionPane.showMessageDialog(null, "创建目录失败!请手动在服务器上创建该工作目录!", "目录创建", JOptionPane.ERROR_MESSAGE);
										return;
									}
								} else {
									return;
								}
							}
						}
					}
					complete = true;
					close();
					new FtpManager(folder, txtUfolder.getText(), hostname, ftp, autoCloseFtp);
				} catch (Exception e) {
					JOptionPane.showMessageDialog(null, ("无法连接到Ftp主机(" + host
							+ "),请检查网络!\n" + e.getMessage()), "连接失败",
							JOptionPane.WARNING_MESSAGE);
				} finally {
					enabledControls(true);
					btnConn.setText(connTit);
				}
			}
		}, "ftp_connect").start();
	}
}
