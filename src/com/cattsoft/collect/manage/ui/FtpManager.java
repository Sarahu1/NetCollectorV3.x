/**
 * 
 */
package com.cattsoft.collect.manage.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.DefaultCaret;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import com.cattsoft.collect.io.file.utils.FileUtils;
import com.cattsoft.collect.manage.ftp.ftp4j.FTPClient;
import com.cattsoft.collect.manage.ftp.ftp4j.FTPCommunicationListener;
import com.cattsoft.collect.manage.ftp.ftp4j.FTPDataTransferListener;
import com.cattsoft.collect.manage.ftp.ftp4j.FTPFile;
import com.cattsoft.collect.manage.logging.LogManager;

/**
 * @author Xiaohong
 *
 */
public class FtpManager extends JFrame {
	private static final long serialVersionUID = 1L;
	private Logger logger = LogManager.getLogger(getName());
	/*** 根目录 */
	private String rootDirectory;
	private FTPClient ftp = null;
	private SimpleDateFormat sdf_time = new SimpleDateFormat("HH:mm:ss");
	/*** Ftp信息打印面板 */
	private JTextPane outputPanel;
	private HTMLEditorKit htmlKit = new HTMLEditorKit(); 
	private HTMLDocument htmlDocument = (HTMLDocument) htmlKit.createDefaultDocument(); 
	/*** 待上传文件列表 */
	private Map<String, File[]> files = new HashMap<String, File[]>();
	/*** 打印 */
	private StringBuffer infos = new StringBuffer();
	/*** 本地上传目录名称 */
	private String upload_folder;
	/*** 主机名称 */
	private String hostname = "";
	/*** 是否打印FTP输出信息 */
	private boolean printSent = false;
	/*** 是否自动关闭窗口 */
	private boolean autoClose = false;
	/*** 右键菜单 */
	private JPopupMenu menu_operate;
	/*** 是否正在上传 */
	private boolean uploading = false;
	/*** 最大上传重试次数 */
	private int maxReTry = 3;
	/*** 终端管理菜单项 */
	private JMenu menu_terminal_item = null;
	/*** 窗口关闭菜单项 */
	private JMenuItem menu_close_item = null;
	
	/**
	 * @param rootDirectory 根目录
	 * @param uploadDirectory 上传目录
	 * @param hostname 主机名称
	 * @param ftp
	 * @param autoClose 完成后是否自动关闭窗口
	 */
	public FtpManager(String rootDirectory, String uploadDirectory, String hostname, FTPClient ftp, boolean autoClose) {
		this.rootDirectory = rootDirectory;
		this.upload_folder = uploadDirectory;
		this.autoClose = autoClose;
		this.ftp = ftp;
		if(null == this.ftp) {
			logger.severe("FTP未进行初始化连接!");
			return;
		}
		if(null == hostname || "".equals(hostname)) {
			hostname = ftp.getHost();
		}
		this.hostname = hostname;
		// 添加FTP文件输出
		ftp.addCommunicationListener(new FTPCommunicationListener() {
			public void sent(String statement) {
				if(printSent) {
					out("> " + statement);
				}
			}
			public void received(String statement) {
				if(printSent) {
					out("< " + statement);
				}
			}
		});
		try {
			init();
		} catch (Exception e) {
			out("<b style = \"font-weight:bold,color:red\">上传过程出现错误!"+e.getMessage()+"</b>");
		}
	}
	
	public FtpManager(String rootDirectory, String uploadDirectory, String hostname, FTPClient ftp) {
		this(rootDirectory, uploadDirectory, hostname, ftp, false);
	}
	
	private void init() {
		setTitle("FTP上传 - " + hostname);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		pack();
		setSize(640, 480);
		setLayout(new BorderLayout());
		com.sun.awt.AWTUtilities.setWindowOpacity(this, 0.99f);
		com.sun.awt.AWTUtilities.setWindowShape(this, new RoundRectangle2D.Double(0, 0, this.getWidth(), this.getHeight(), 8, 8));
		
		outputPanel = new JTextPane();
		outputPanel.setEditable(false);
		// 设置HTML显示
		outputPanel.setEditorKit(htmlKit); 
		outputPanel.setDocument(htmlDocument); 
		
		// 自动滚动
		DefaultCaret caret = (DefaultCaret) outputPanel.getCaret();  
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);  
		
		JScrollPane scrollPane = new JScrollPane(outputPanel);
		getContentPane().add(scrollPane);
		
		// 输出面板右键菜单
		
		menu_operate = new JPopupMenu("管理");
		JMenuItem menu_clear = new JMenuItem("清除(C)");
		menu_clear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// 清空内容
				infos.setLength(0);
				outputPanel.setText("");
			}
		});
		// 终端项
		menu_terminal_item = new JMenu("终端(T)");
		// 不可用
		menu_terminal_item.setEnabled(false);
		menu_terminal_item.setMnemonic('t');
		JMenuItem menu_terminal_restart = new JMenuItem("重启(R)");
		menu_terminal_restart.setMnemonic('r');
		menu_terminal_restart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// 重启服务对话框
				new ShellInfoDialog(ftp.getHost(), hostname, null, rootDirectory, ShellInfoDialog.OperateType.RESTART);
			}
		});
		JMenuItem menu_terminal_stop = new JMenuItem("停止(S)");
		menu_terminal_stop.setMnemonic('s');
		menu_terminal_stop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// 停止服务对话框
				new ShellInfoDialog(ftp.getHost(), hostname, null, rootDirectory, ShellInfoDialog.OperateType.STOP);
			}
		});
		menu_terminal_item.add(menu_terminal_restart);
		menu_terminal_item.add(menu_terminal_stop);
		// 关闭项
		menu_close_item = new JMenuItem("关闭(X)");
		menu_close_item.setMnemonic('x');
		menu_close_item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				close();
			}
		});
		// 菜单项
		menu_operate.add(menu_clear);
		menu_operate.addSeparator();
		menu_operate.add(menu_terminal_item);
		menu_operate.addSeparator();
		menu_operate.add(menu_close_item);
		// 添加右键事件
		outputPanel.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				// 右键
				if(e.getButton() == MouseEvent.BUTTON3) {
					menu_operate.show(e.getComponent(), e.getX(), e.getY());
				}
				super.mousePressed(e);
			}
		});
		
		UIUtils.addEscapeListener(this);
		UIUtils.setCenter(this);
		setVisible(true);
		
		try {
			String currentDirectory = ftp.currentDirectory();
			
			out("当前工作目录:<b>" + currentDirectory +"</b>");
			try {
				if(!rootDirectory.equals(currentDirectory)) {
					out("切换至主目录:" + rootDirectory);
					changeDirectory(rootDirectory);
				}
			} catch (Exception e) {
				return;
			}
			if(rootDirectory.equals(currentDirectory)) {
				File upload_file = new File(upload_folder);
				if(upload_file.exists()) {
					for (File file : upload_file.listFiles()) {
						if(file.isDirectory()) {
							files.put(file.getName(), FileUtils.listFiles(file));
						} else {
							files.put("", FileUtils.listFiles(upload_file));
						}
					}
				} else {
					out("<font color=\"red\">" + "未找到目录 " + upload_folder +",请添加该目录后重新进行上传</font>");
					out("<b>添加目录"+upload_folder+"后,该目录下所有文件将自动上传至服务器</b>");
					out("<b>如果存在子目录,目录名称请与服务器目录名称相同,否则文件无法上传</b>");
					return;
				}
				// 打印文件清单
				if(!files.isEmpty()) {
					out("<b>上传文件清单:</b>");
					logger.info("文件清单:");
					for(Map.Entry<String, File[]> entry : files.entrySet()) {
						out("目录:<b>" + (entry.getKey().isEmpty() ? "/" : entry.getKey()) + "</b>");
						logger.info("目录:" + entry.getKey());
						for (File entry_file : entry.getValue()) {
							out("&emsp;" + entry_file.getAbsolutePath());
							logger.info("\t" + entry_file.getAbsolutePath());
						}
					}
					// 等等2秒后进行上传
					// 期间可以进行取消,清除文件列表
					Thread.sleep(2000);
					upload();
					if(autoClose) {
						// 2秒后关闭
						Thread.sleep(2000);
						// 关闭窗口
						UIUtils.dispatchCloseEvent(this);
					}
				} else {
					out("目录<b>" + upload_folder + "</b>没有可上传的文件");
				}
			}
		} catch (Exception e) {
			out("无法上传文件!" + e.getMessage());
		}
	}
	
	/** 输出信息到面板
	 * @param info
	 * @param append 是否追加
	 */
	private void out(String info, boolean append) {
		String str = "";
		if(append) {
			str = infos.append("<br>").append("[").append(sdf_time.format(System.currentTimeMillis())).append("] ").append(info).toString();
		} else {
			str = infos.toString() + "<br>[" + sdf_time.format(System.currentTimeMillis()) + "] " + info;
		}
		outputPanel
				.setText("<p style=\"font-family:微软雅黑,宋体,verdana,arial,sans-serif;font-size:12pt\">"
						+ str + "</p>");
	}
	
	/**
	 * @param info
	 */
	public void out(String info) {
		out(info, true);
	}
	

	/**
	 * 上传重试与断点续传.
	 * @param file 文件
	 * @param restartAt 断点位置
	 */
	private boolean uploadRetry(final File file, final long restartAt) {
		try {
			ftp.upload(file, restartAt, new FTPDataTransferListener() {
				double step = (file.length() / 100d);
				double process = 0;
				double totalBytesTransferred = 0;
				double preProcess = 0;
				// 传输进度
				public void transferred(int length) {
					totalBytesTransferred += length;
					if (totalBytesTransferred / step != process) {
						process = (totalBytesTransferred) / step;
						// 更新上传进度
						double round_process = Math.floor(process);
						if (((round_process - preProcess) >= 10d)
								&& (round_process % 10 == 0)) {
							preProcess = round_process;
							out("文件<b>"+file.getName()+"</b>上传进度:<b>"+round_process+"%</b>", round_process == 100);
						}
					}
				}
				public void started() {
					out("文件<b>"+file.getName()+"</b>上传进度:<b>0%</b>", false);
				}
				public void failed() {
					autoClose = false;
				}
				public void completed() {
					out("<b>文件"+file.getPath()+"上传完成</b>");
				}
				public void aborted() {}
			});
			return true;
		} catch (Exception e) {
			autoClose = false;
			out("<b style = \"color:red\">文件" + file.getPath() + "上传失败!" + e.getMessage() + "</b>");
		}
		return false;
	}
	
	/**
	 * 上传文件
	 */
	private void upload() {
		uploading = true;
		int errorFileCount = 0;
		int errorFolderCount = 0;
		out("<b>正在准备上传..</b>");
		Iterator<Map.Entry<String, File[]>> iterator = files.entrySet().iterator();
		while(iterator.hasNext()) {
			Map.Entry<String, File[]> entry = iterator.next();
			// 路径分隔符固定使用 /,Linux不识别 \ 这样的符号.未使用 File.separator
			String directroy = rootDirectory + "/" + entry.getKey();
			if("".equals(entry.getKey().trim())) {
				// 根目录
				directroy = rootDirectory;
			}
			// 切换到相应目录
			try {
				ftp.changeDirectory(directroy);
			} catch (Exception e) {
				try {
					if(ftp.list(directroy).length == 0) {
						out("<b>服务器中未找到目录\""+directroy+"\",正在创建..</b>");
						ftp.createDirectory(directroy);
						// 切换
						changeDirectory(directroy);
					} else {
						errorFolderCount++;
						out("<b style = \"font-weight:bold;color:red\">切换工作目录("+directroy+")失败!目录无法上传!</b>");
						autoClose = false;
						iterator.remove();
						continue;
					}
				} catch (Exception e2) {
					errorFolderCount++;
					out("<b style=\"color:red\">无法在服务器创建目录"+entry.getKey()+",文件无法上传!</b>");
					autoClose = false;
					iterator.remove();
					continue;
				}
			}
			for (final File file : entry.getValue()) {
				boolean uploaded = false;
				try {
					int retries = 0;
					while(true) {
						retries++;
						if(retries > maxReTry) {
							out("文件(<b>"+ file.getAbsolutePath() +"</b>)无法上传至服务器,<b>上传失败!</b>");
							break;
						}
						long restartAt = 0;
						// 已重试上传,读取断点位置
						if(retries > 1) {
							restartAt = getRestartAt(file);
							out("正在尝试重新上传该文件("+ file.getName() +")" + (restartAt > 0 ? (", <b>断点续传</b>位置:" + restartAt) : ""));
						}
						// 上传,返回是否上传成功
						uploaded = uploadRetry(file, restartAt);
						if(uploaded) {
							break;
						}
					}
				} catch (Exception e2) {
					out("上传文件(<b>"+ file.getAbsolutePath() +"</b>)时出现异常!<b>上传失败!</b>" + e2.getMessage());
				}
				if(!uploaded) {
					// 错误文件数量增加
					errorFileCount++;
				}
			}
			// 上传完成后删除当前节点
			iterator.remove();
		}
		printSent = true;
		// 打印根目录文件清单
		out("<b>文件上传完成</b>");
		printFileInventory();
		if(errorFolderCount > 0) {
			out("<b style=\"color:red\">共有"+errorFolderCount+"个目录文件上传失败!</b>");
		}
		if(errorFileCount > 0) {
			out("<b style=\"color:red\">共有"+errorFileCount+"个文件上传失败!</b>");
		}
		out("按<b>ESC</b>键关闭窗口");
		
		// 没有失败项
		if(errorFileCount == 0 && errorFileCount == 0) {
			out("单击<b>右键</b>执行其它操作<br><br>");
			// 启用终端管理菜单
			menu_terminal_item.setEnabled(true);
		}
		// 标记完成
		uploading = false;
	}
	
	/**
	 * 打印根目录文件清单
	 */
	private void printFileInventory() {
		try {
			FTPFile[] files = ftp.list(rootDirectory);
			out("<b>目录("+rootDirectory+")清单:</b>");
			printSent = false;
			SimpleDateFormat sdf_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			for (FTPFile ftpFile : files) {
				if (ftpFile.getName().equals(".")
						|| ftpFile.getName().equals("..")) {
					continue;
				}
				if (ftpFile.getType() == FTPFile.TYPE_DIRECTORY) {
					out("<b>"+ftpFile.getName()+"</b>&emsp;&emsp;" + sdf_date.format(ftpFile.getModifiedDate()));
					// 打印子目录
					try {
						FTPFile[] child_files = ftp.list(rootDirectory + "/" + ftpFile.getName());
						for (FTPFile ftpFile2 : child_files) {
							if (ftpFile2.getName().equals(".")
									|| ftpFile2.getName().equals("..")) {
								continue;
							}
							if (ftpFile2.getType() == FTPFile.TYPE_DIRECTORY) {
								out("&emsp;&emsp;<b>"+ftpFile2.getName()+"</b>&emsp;&emsp;" + sdf_date.format(ftpFile2.getModifiedDate()));
							} else if (ftpFile2.getType() == FTPFile.TYPE_FILE) {
								out("&emsp;&emsp;" + ftpFile2.getName() + "&emsp;&emsp;" + sdf_date.format(ftpFile2.getModifiedDate()));
							}
						}
					} catch (Exception e) {
					}
				} else if (ftpFile.getType() == FTPFile.TYPE_FILE) {
					out(ftpFile.getName() + "&emsp;&emsp;" + sdf_date.format(ftpFile.getModifiedDate()));
				}
			}
		} catch (Exception e) {
			//
		}
	}
	
	public void close() {
		UIUtils.dispatchCloseEvent(this);
	}
	
	/** 切换工作目录
	 * @param directroy
	 * @throws Exception
	 */
	private void changeDirectory(String directroy) throws Exception {
		try {
			ftp.changeDirectory(directroy);
			out("当前工作目录:<b>" + ftp.currentDirectory() + "</b>");
		} catch (Exception e) {
			out("<b style = \"font-weight:bold;color:red\">切换工作目录("+directroy+")失败!</b>");
			throw e;
		}
	}
	
	/**
	 * 获取文件断点续传位置
	 */
	private long getRestartAt(File file) {
		long restartAt = 0;
		try {
			// 查找文件,长度是否小于本地文件长度
			FTPFile[] ftpfiles = ftp.list(file.getName());
			if(ftpfiles.length > 0) {
				// 比对第一个文件长度
				long ftpFileLen = ftpfiles[0].getSize();
				if(ftpFileLen <= file.length()) {
					restartAt = ftpFileLen;
				} else if(ftpFileLen > file.length()) {
					// 远程文件长度大于本地文件,删除远程文件重新上传
					// 删除该文件
					ftp.deleteFile(file.getName());
				}
			}
		} catch (Exception e) {
			out("无法读取文件("+ file.getName() +")断点位置,将重新上传该文件");
		}
		return restartAt;
	}
	
	protected void processWindowEvent(WindowEvent e) {
		if (e.getID() == WindowEvent.WINDOW_CLOSING) {
			try {
				if(uploading) {
					uploading = false;
					try {
						ftp.abortCurrentDataTransfer(false);
					} catch (Exception e1) {
					}
					out("<b>文件上传已取消</b>");
					return;
				} else if(!files.isEmpty()) {
					int flag = JOptionPane.showConfirmDialog(null, "文件上传尚未完成,是否中断?", "取消上传", JOptionPane.YES_NO_OPTION);
					if(flag != JOptionPane.YES_OPTION) {
						return;
					}
					ftp.abortCurrentConnectionAttempt();
					ftp.abortCurrentDataTransfer(false);
					out("<b>用户取消上传<b>");
					logger.info("用户已取消上传");
				}
				files.clear();
			} catch (Exception e2) {
				out("无法中断上传!");
				return;
			}
			try {
				super.processWindowEvent(e);
				// 关闭后断开FTP连接
				ftp.logout();
				ftp.disconnect(true);
			} catch (Exception e3) {
			}
		}
	}
}
