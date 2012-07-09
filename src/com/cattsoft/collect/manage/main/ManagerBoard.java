/**
 * 
 */
package com.cattsoft.collect.manage.main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.logging.Logger;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import com.cattsoft.collect.manage.data.ConfigUtils;
import com.cattsoft.collect.manage.data.MonitorStatus;
import com.cattsoft.collect.manage.data.StatusGrab;
import com.cattsoft.collect.manage.logging.LogManager;
import com.cattsoft.collect.manage.ui.AboutDialog;
import com.cattsoft.collect.manage.ui.MonitorFtpConnect;
import com.cattsoft.collect.manage.ui.MonitorModel;
import com.cattsoft.collect.manage.ui.MonitorTable;
import com.cattsoft.collect.manage.ui.MonitorTerminalTable;
import com.cattsoft.collect.manage.ui.ServerSetDialog;
import com.cattsoft.collect.manage.ui.ShellInfoDialog;
import com.cattsoft.collect.manage.ui.UIUtils;

/** 管理界面主窗口
 * @author ChenXiaohong
 * @since JDK1.6
 */
public class ManagerBoard extends JFrame {
	private static final long serialVersionUID = 1L;
	private final static Logger logger = LogManager.getLogger(ManagerBoard.class.getSimpleName());
	private final static int width = 800; // 窗口宽度
	private final static int height = 520; // 窗口高度
	/*** 状态栏*/
	private JToolBar toolBar = null;
	/*** 数据显示表格*/
	private JTable table = null;
	/*** 数据获取 */
	private StatusGrab grab = null;
	/*** 数据服务地址 */
	private String server;
	/*** 数据服务端口 */
	private int port;
	/*** 服务设置对话框 */
	private ServerSetDialog dialog = null;
	
	// 菜单项
	private JMenuItem menu_item_set;
	private JMenuItem menu_item_terminal_upload;
	private JMenuItem menu_item_terminal_mgr;
	private JMenuItem menu_item_terminal_table;
	
	public ManagerBoard() {
		super("云平台基础数据采集管理");
		logger.info("正在启动采集管理程序..");
		init();
		logger.info("系统界面初始化已完成");
	}
	
	private void init() {
		pack();
		logger.info("正在初始化管理窗口界面..");
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			logger.severe("设置UI样式失败!将使用默认样式显示窗口.");
		}
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setSize(width, height);
		com.sun.awt.AWTUtilities.setWindowOpacity(this, 0.98f);
		com.sun.awt.AWTUtilities.setWindowShape(this, new RoundRectangle2D.Double(0, 0, this.getWidth(), this.getHeight(), 8, 8));
		/**
		 * 居中显示
		 */
		UIUtils.setCenter(this);
		
		getContentPane().setLayout(new BorderLayout());

		// 菜单栏
		JMenuBar mb = new JMenuBar();
		JMenu menu_file = new JMenu(" 文件(F) ");
		menu_file.setMnemonic('f');
		JMenu menu_help = new JMenu(" 帮助(H) ");
		menu_help.setMnemonic('h');
		menu_item_set = new JMenuItem("设置(O)"); 
		menu_item_set.setMnemonic('o');
		menu_item_set.setToolTipText("设置数据服务");
		menu_item_set.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionevent) {
				showSettingDialog();
			}
		});
		// 单独终端管理
		JMenu menu_item_terminal = new JMenu("终端(T)");
		menu_item_terminal.setMnemonic('t');
		menu_item_terminal_mgr = new JMenuItem("管理(M)");
		menu_item_terminal_mgr.setMnemonic('m');
		menu_item_terminal_mgr.setToolTipText("启动/停止终端程序");
		menu_item_terminal_mgr.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new ShellInfoDialog(null, "", null, null, null, null);
			}
		});
		menu_item_terminal_upload = new JMenuItem("上传(U)");
		menu_item_terminal_upload.setMnemonic('u');
		menu_item_terminal_upload.setToolTipText("管理终端程序文件");
		menu_item_terminal_upload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new MonitorFtpConnect(null, "", "", null);
			}
		});
		menu_item_terminal_table = new JMenuItem("对照表(I)");
		menu_item_terminal_table.setMnemonic('i');
		menu_item_terminal_table.setToolTipText("监测终端对照表");
		menu_item_terminal_table.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new Thread(new Runnable() {
					public void run() {
						new MonitorTerminalTable();
					}
				}, "terminal_table").start();
			}
		});
		menu_item_terminal.add(menu_item_terminal_mgr);
		menu_item_terminal.addSeparator();
		menu_item_terminal.add(menu_item_terminal_upload);
		menu_item_terminal.addSeparator();
		menu_item_terminal.add(menu_item_terminal_table);
		
		JMenuItem menu_item_quit = new JMenuItem("退出(X)");
		menu_item_quit.setMnemonic('x');
		menu_item_quit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				close();
			}
		});
		
		// 文件
		menu_file.add(menu_item_set);
		menu_file.addSeparator();
		menu_file.add(menu_item_terminal);
		menu_file.addSeparator();
		menu_file.add(menu_item_quit);
		mb.add(menu_file);
		// 帮助
		JMenuItem menu_item_about = new JMenuItem("关于(A)"); 
		menu_item_about.setMnemonic('a');
		menu_item_about.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new AboutDialog();
			}
		});
		menu_help.add(menu_item_about);
		mb.add(menu_help);
		
		setJMenuBar(mb);
		
		// 状态栏
		toolBar = new JToolBar();
		toolBar.add(new JLabel("无数据"));
		toolBar.addSeparator(new Dimension(10, 2));
		toolBar.add(new JLabel());
		toolBar.addSeparator(new Dimension(10, 2));
		JLabel pre_update = new JLabel();
		pre_update.setToolTipText("数据更新时间");
		toolBar.add(pre_update);
		toolBar.addSeparator(new Dimension(10, 2));
		toolBar.add(new JLabel());
		toolBar.setFloatable(false);
		getContentPane().add(toolBar, BorderLayout.SOUTH);
		
		// 数据展示列表
		final JScrollPane scrollPane = new JScrollPane();
		getContentPane().add(scrollPane);

		table = new MonitorTable(); // 创建表格,载入表格模型
		table.setBorder(null);
		
		// 添加数据行的双击事件
		table.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent mevt) {
				super.mouseClicked(mevt);
				// 双击事件
				if (mevt.getButton() == MouseEvent.BUTTON1
						&& mevt.getClickCount() >= 2) {
					try {
						MonitorTable tab = (MonitorTable) mevt.getSource();
						int row = tab.rowAtPoint(mevt.getPoint());
						// 获取行下标
						int modelRow = tab.getRowSorter().convertRowIndexToModel(row);
						MonitorStatus status = ((MonitorModel) ((MonitorTable) mevt
								.getSource()).getModel()).getValue(modelRow);
						// 显示操作窗口
						tab.operate(status);
					} catch (Exception e) {
						JOptionPane.showMessageDialog(getOwner(), "无法获取该记录的更多信息进行操作!", "错误",
								JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		});
		((MonitorTable)table).setToolBar(toolBar);
		scrollPane.setViewportView(table); // 在滚动框中加入表格
		dialog = new ServerSetDialog(this, server, port) {
			private static final long serialVersionUID = 1L;
			public void setting(String host, int d) {
				super.setting(host, d);
				server = host;
				port = d;
				if(null == host || "".equals(host)) {
					close();
				} else {
					if(null != grab) {
						grab.stop();
					}
					initData();
				}
			}
		};
		
		// 热键注册
		// 上传
		menu_item_terminal_upload.registerKeyboardAction(new ActionListener() {  
			public void actionPerformed(ActionEvent e) {  
				menu_item_terminal_upload.doClick();
			}  
		}, KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW); 
		// 服务设置
		menu_item_set.registerKeyboardAction(new ActionListener() {  
			public void actionPerformed(ActionEvent e) {  
				menu_item_set.doClick();
			}  
		}, KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
		// 终端管理
		menu_item_terminal_mgr.registerKeyboardAction(new ActionListener() {  
			public void actionPerformed(ActionEvent e) {  
				menu_item_terminal_mgr.doClick();
			}  
		}, KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
		// 对照表信息
		menu_item_terminal_table.registerKeyboardAction(new ActionListener() {  
			public void actionPerformed(ActionEvent e) {  
				menu_item_terminal_table.doClick();
			}  
		}, KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
		
		
		
		setVisible(true);
		if(null == server || server.isEmpty()) {
			showSettingDialog();
		}
	}
	
	private void close() {
		UIUtils.dispatchCloseEvent(this);
	}
	
	/**
	 * 显示服务连接设置对话框
	 */
	public void showSettingDialog() {
		UIUtils.setCenter(dialog);
		dialog.setVisible(true);
	}
	
	/** 显示状态栏信息
	 * @param tip 显示状态栏信息
	 */
	public void showTip(final String tip, final boolean error) {
		((JLabel)toolBar.getComponentAtIndex(0)).setForeground(null);
		((JLabel)toolBar.getComponentAtIndex(0)).setText(tip);
		if(error) {
			((JLabel)toolBar.getComponentAtIndex(0)).setForeground(Color.RED);
		}
	}
	
	public JTable getTable() {
		return table;
	}
	
	public String getServer() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
	public JToolBar getToolBar() {
		return toolBar;
	}

	private void initData() {
		// 注册数据服务
		final ManagerBoard board = this;
		new Thread(new Runnable() {
			public void run() {
				grab = new StatusGrab(server, port, board);
			}
		}).start();
	}
	
	protected void processWindowEvent(WindowEvent e) {
		if (e.getID() == WindowEvent.WINDOW_CLOSING) {
			logger.info("正在关闭..");
			if(null != grab) {
				grab.stop();
			}
		}
		super.processWindowEvent(e);
	}
	
	public static void main(String[] args) {
		try {
			// 读取日志配置
			System.setProperty("java.util.logging.config.file", System
					.getProperty("java.util.logging.config.file",
							ConfigUtils.getCatalog() + File.separator
							+ "logging.properties"));
			// 重新读取日志配置文件
			java.util.logging.LogManager.getLogManager().readConfiguration();
		} catch (Exception e1) {
		}
		try {
			for (int i = 0; i < args.length; i++) {
				if(args[i].equals("-logprint")) {
					System.setProperty("enablePrint", args[i+1]);
				}
			}
		} catch (Exception e) {
			System.err.println("Dynamic parameter settings abnormal");
		}
		java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
            	try {
            		new ManagerBoard();
				} catch (Exception e) {
					System.err.println("启动失败!");
				}
            }
		});
	}
}
