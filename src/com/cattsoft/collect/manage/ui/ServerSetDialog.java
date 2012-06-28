/**
 * 
 */
package com.cattsoft.collect.manage.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Properties;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.cattsoft.collect.manage.data.ConfigUtils;
import com.cattsoft.collect.manage.logging.LogManager;

/** 服务设置窗口对话框.
 * @author ChenXiaohong
 *
 */
public class ServerSetDialog extends JDialog {
	private Logger logger = LogManager.getLogger(getClass().getSimpleName());
	private static final long serialVersionUID = 1L;
	/*** 主机地址 */
	private JTextField txtHost;
	/*** 端口 */
	private JTextField txtPort;
	
	/*** 默认主机地址 */
	private String lastHost;
	/*** 默认主机端口 */
	private int lastPort;
	
	private JButton btnConnect;
	private JButton btnClose;
	
	public ServerSetDialog(JFrame parent, String host, int port) {
		super(parent, "服务配置");
		// 模式窗口
		setModal(true);
		// 居中
		UIUtils.setCenter(this);
		// 配置读取
		Properties config = ConfigUtils.getProp();
		if(!config.isEmpty()) {
			// 取值
			host = config.getProperty("host");
			lastHost = host;
			port = Integer.parseInt(config.getProperty("port", "8089"));
			lastPort = port;
			// 读取后自动关闭
			if((null != host && !"".equals(host)) && port > 0) {
				setting(host, port);
				close();
			}
		} else {
			host = System.getProperty("service.host", "");
			port = Integer.parseInt(System.getProperty("service.port", "8089"));
		}
		init(host, port);
	}
	
	public void setVisible(boolean b) {
		if(b) {
			// 填写默认值
			if(txtHost.getText().trim().isEmpty())
				txtHost.setText(lastHost);
			if(txtPort.getText().trim().isEmpty())
				txtPort.setText(String.valueOf(lastPort));
			txtHost.selectAll();
			txtHost.requestFocus();
		}
		super.setVisible(b);
	}
	
	/**
	 * 初始化界面,数据
	 */
	private void init(String host, int port) {
		// 初始化界面
		setLayout(null);
		UIUtils.addEscapeListener(this);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setResizable(false);
		pack();
		
		setSize(320, 200);
		// 添加组件
		JLabel lblServer = new JLabel("地址:");
		lblServer.setBounds(49, 30, 60, 24);
		add(lblServer);
		JLabel lblPort = new JLabel("端口:");
		lblPort.setBounds(49, 64, 60, 24);
		add(lblPort);
		txtHost = new JTextField(host);
		txtHost.setBounds(89, 30, 180, 24);
		
		txtPort = new JTextField(port);
		txtPort.setBounds(89, 64, 180, 24);
		UIUtils.setNumberDocument(txtPort);
		txtPort.setText(String.valueOf(port));
		
		btnClose = new JButton("关闭");
		btnClose.setBounds(135, 104, 62, 24);
		btnClose.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionevent) {
				close();
			}
		});
		add(btnClose);
		
		btnConnect = new JButton("连接");
		btnConnect.setBounds(208, 104, 62, 24);
		btnConnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionevent) {
				try {
					setting(getHost(), getPort());
					close();
				} catch (NumberFormatException e) {
					txtPort.setText("");
					txtPort.requestFocus();
				} catch (Exception e) {
					txtHost.setText("");
					txtHost.requestFocus();
				}
			}
		});
		add(btnConnect);
		
		add(txtHost);
		add(txtPort);
	}
	
	private void save(String host , int port) {
		try {
			Properties config = ConfigUtils.getProp();
			// 写入到文件
			config.put("host", host);
			config.put("port", String.valueOf(port));
			ConfigUtils.store(config, "service config");
		} catch (Exception e) {
			logger.severe("服务配置信息保存失败!" + e.toString());
		}
	}
	
	private void close() {
		UIUtils.dispatchCloseEvent(this);
	}

	public void setting(String host, int port) {
		save(host, port);
	}
	
	public String getHost() {
		return txtHost.getText();
	}
	
	public int getPort() {
		return Integer.valueOf(txtPort.getText());
	}
}
